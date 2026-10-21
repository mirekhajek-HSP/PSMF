package cz.hspinovace.psmf.data.match

import cz.hspinovace.psmf.db.Lineup_draft
import cz.hspinovace.psmf.db.Lineup_draft_member
import cz.hspinovace.psmf.db.MatchRecordQueries
import cz.hspinovace.psmf.db.PsmfDatabase
import cz.hspinovace.psmf.domain.JerseyNumber
import cz.hspinovace.psmf.domain.KitId
import cz.hspinovace.psmf.domain.LineupConfirmation
import cz.hspinovace.psmf.domain.MatchId
import cz.hspinovace.psmf.domain.PersonName
import cz.hspinovace.psmf.domain.PlayerId
import cz.hspinovace.psmf.domain.TeamSide
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.time.Instant

/**
 * One team's lineup as the lineup screen holds it, **before it is whole**
 * (prompt 15, Gate 3; DECISIONS 2026-10-21).
 *
 * A [cz.hspinovace.psmf.domain.Lineup] is a finished block of the report and
 * the type refuses to hold anything less. What a referee has done on the way
 * to one -- three players marked, a captain not yet chosen, half a card number
 * -- is not a lineup, and this is where it is kept so that the app being
 * killed does not take it.
 *
 * **Not in `domain`, on purpose.** A report, the console and the export are
 * built from `Match` and its `Lineup`s, and nothing in `domain` can hold or
 * return one of these, so none of them can mistake a draft for a lineup. Only
 * the lineup screen's use cases read and write it ([LineupDraftRepository]).
 */
data class LineupDraft(
    val matchId: MatchId,
    val side: TeamSide,
    val kitId: KitId,
    val captain: PlayerId?,
    val goalkeeper: PlayerId?,
    val confirmation: LineupConfirmation?,
    val members: List<DraftedMember>,
)

/**
 * One squad member's row: what was typed, as typed.
 *
 * The two `...Written` values are what the text puts in the `Číslo RP` column
 * once it is a whole number or a real date -- the value alone; the card number
 * is always `RP_TYPED` and the date always `DATE_OF_BIRTH`. **Never the
 * player's RP number**, and nothing here can become one.
 */
data class DraftedMember(
    val playerId: PlayerId,
    val present: Boolean,
    val jerseyNumber: JerseyNumber?,
    val registrationCardPresent: Boolean,
    val cardNumberTyped: String,
    val cardNumberWritten: String?,
    val dateOfBirthTyped: String,
    val dateOfBirthWritten: String?,
)

/**
 * Where a lineup in progress lives.
 *
 * Its own interface, not a method on [MatchRepository]: saving a report must
 * not be able to touch a draft, and a draft must not be able to touch a
 * report. They share a database and nothing else.
 */
interface LineupDraftRepository {
    /** Replaces this team's draft, members and all. */
    suspend fun save(draft: LineupDraft)

    /** Every draft of the match: none, one team's, or both. */
    suspend fun forMatch(matchId: MatchId): List<LineupDraft>
}

/**
 * A draft is replaced as a whole inside one transaction, so that a process
 * killed in the middle of a write leaves the draft as it was, not half of two.
 */
class SqlDelightLineupDraftRepository(
    private val database: PsmfDatabase,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
) : LineupDraftRepository {
    private val queries: MatchRecordQueries get() = database.matchRecordQueries

    override suspend fun save(draft: LineupDraft): Unit =
        withContext(dispatcher) {
            database.transaction {
                val match = draft.matchId.value
                val side = draft.side.name
                queries.deleteLineupDraftMembersOf(match, side)
                queries.upsertLineupDraft(
                    match_id = match,
                    side = side,
                    kit_id = draft.kitId.value,
                    captain_player_id = draft.captain?.value,
                    goalkeeper_player_id = draft.goalkeeper?.value,
                    confirmed_at = draft.confirmation?.at?.toString(),
                    confirmed_by = draft.confirmation?.by?.value,
                    confirmed_as_deputy = (draft.confirmation?.asDeputy == true).toLong(),
                )
                draft.members.forEach { member ->
                    queries.insertLineupDraftMember(
                        match_id = match,
                        side = side,
                        player_id = member.playerId.value,
                        present = member.present.toLong(),
                        jersey_number = member.jerseyNumber?.value?.toLong(),
                        registration_card = member.registrationCardPresent.toLong(),
                        card_number_typed = member.cardNumberTyped,
                        card_number_written = member.cardNumberWritten,
                        date_of_birth_typed = member.dateOfBirthTyped,
                        date_of_birth_written = member.dateOfBirthWritten,
                    )
                }
            }
        }

    override suspend fun forMatch(matchId: MatchId): List<LineupDraft> =
        withContext(dispatcher) {
            database.transactionWithResult {
                val members = queries.selectLineupDraftMembers(matchId.value).executeAsList().groupBy { it.side }
                queries
                    .selectLineupDrafts(matchId.value)
                    .executeAsList()
                    .mapNotNull { it.toDraft(members[it.side].orEmpty()) }
            }
        }
}

/** A draft goes with its match. Called from [SqlDelightMatchRepository.delete] and nowhere else. */
internal fun MatchRecordQueries.deleteLineupDraftsOf(matchId: String) {
    deleteLineupDraftsForMatch(matchId)
    deleteLineupDraftMembersForMatch(matchId)
}

/**
 * A row this version cannot read is skipped, not thrown: a draft is a
 * convenience, and the lineup screen must open whatever a table holds.
 */
private fun Lineup_draft.toDraft(rows: List<Lineup_draft_member>): LineupDraft? {
    val teamSide = TeamSide.entries.firstOrNull { it.name == side } ?: return null
    return LineupDraft(
        matchId = MatchId(match_id),
        side = teamSide,
        kitId = KitId(kit_id),
        captain = captain_player_id?.let(::PlayerId),
        goalkeeper = goalkeeper_player_id?.let(::PlayerId),
        confirmation = toConfirmation(),
        members = rows.map { it.toMember() },
    )
}

private fun Lineup_draft.toConfirmation(): LineupConfirmation? {
    val at = confirmed_at?.let { runCatching { Instant.parse(it) }.getOrNull() } ?: return null
    val by = confirmed_by?.let { PersonName.orNull(it) } ?: return null
    return LineupConfirmation(at = at, by = by, asDeputy = confirmed_as_deputy == 1L)
}

private fun Lineup_draft_member.toMember(): DraftedMember =
    DraftedMember(
        playerId = PlayerId(player_id),
        present = present == 1L,
        jerseyNumber = JerseyNumber.orNull(jersey_number?.toInt()),
        registrationCardPresent = registration_card == 1L,
        cardNumberTyped = card_number_typed,
        cardNumberWritten = card_number_written,
        dateOfBirthTyped = date_of_birth_typed,
        dateOfBirthWritten = date_of_birth_written,
    )
