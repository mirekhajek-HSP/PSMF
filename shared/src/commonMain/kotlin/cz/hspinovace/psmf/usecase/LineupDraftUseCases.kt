package cz.hspinovace.psmf.usecase

import cz.hspinovace.psmf.data.match.DraftedMember
import cz.hspinovace.psmf.data.match.LineupDraft
import cz.hspinovace.psmf.data.match.LineupDraftRepository
import cz.hspinovace.psmf.domain.IdentificationSource
import cz.hspinovace.psmf.domain.MatchId
import cz.hspinovace.psmf.domain.ReportedIdentification

/**
 * Keeps the lineup screen's copy of a team, so that the app being killed does
 * not take what was done before the lineup was whole (prompt 15, Gate 3).
 *
 * **This does not write a lineup.** [SaveLineup] does, and only for a block
 * that is whole. A draft sits in tables of its own that the console, the
 * export and the signatures never read, so a lineup that is not whole is still
 * not a lineup to any of them, and its problems are listed as before.
 *
 * Called on every change, before [SaveLineup]: a draft is never older than
 * the lineup written from it.
 */
class SaveLineupDraft(
    private val drafts: LineupDraftRepository,
) {
    suspend operator fun invoke(
        matchId: MatchId,
        team: TeamLineupEntry,
    ) = drafts.save(team.toDraft(matchId))
}

/**
 * Puts a team's draft back over the lineup built from the report.
 *
 * The report decides who is in the squad, which appearance ids goals and
 * cards point at, and what a name is; the draft decides what the screen
 * showed -- who was marked, the roles, the numbers, what was typed. A team
 * with no draft is as it was built, which is how a report written by 0.3.0
 * opens.
 */
class RestoreLineupDraft(
    private val drafts: LineupDraftRepository,
) {
    suspend operator fun invoke(
        matchId: MatchId,
        entry: LineupEntry,
    ): LineupEntry =
        drafts.forMatch(matchId).fold(entry) { restored, draft ->
            restored.with(restored.side(draft.side).restoring(draft))
        }
}

internal fun TeamLineupEntry.toDraft(matchId: MatchId): LineupDraft =
    LineupDraft(
        matchId = matchId,
        side = side,
        kitId = kitId,
        captain = captain,
        goalkeeper = goalkeeper,
        confirmation = confirmation,
        members = members.map { it.toDrafted() },
    )

private fun SquadMemberEntry.toDrafted(): DraftedMember =
    DraftedMember(
        playerId = player.id,
        present = present,
        jerseyNumber = jerseyNumber,
        registrationCardPresent = registrationCardPresent,
        cardNumberTyped = cardNumberTyped,
        cardNumberWritten = cardNumberWritten?.value,
        dateOfBirthTyped = dateOfBirthTyped,
        dateOfBirthWritten = dateOfBirthWritten?.value,
    )

/**
 * What the draft says, over what the report built.
 *
 * Anything the draft names that the report no longer has -- a player who has
 * left the squad list, a kit the team no longer owns -- is dropped rather than
 * trusted, and a player the draft knows nothing of stays as built.
 */
internal fun TeamLineupEntry.restoring(draft: LineupDraft): TeamLineupEntry {
    val rows = draft.members.associateBy { it.playerId }
    val squad = members.map { it.player.id }.toSet()
    return copy(
        members = members.map { member -> rows[member.player.id]?.let(member::restoring) ?: member },
        kitId = draft.kitId.takeIf { team.kit(it) != null } ?: kitId,
        captain = draft.captain?.takeIf { it in squad },
        goalkeeper = draft.goalkeeper?.takeIf { it in squad },
        confirmation = draft.confirmation,
    )
}

/**
 * Both written values come back as they were, not worked out again from the
 * text: a row read from a saved lineup can have a value written and an empty
 * field, and an empty field would work out to nothing written.
 */
private fun SquadMemberEntry.restoring(row: DraftedMember): SquadMemberEntry =
    copy(
        present = row.present,
        jerseyNumber = row.jerseyNumber,
        registrationCardPresent = row.registrationCardPresent,
        cardNumberTyped = row.cardNumberTyped,
        cardNumberWritten =
            row.cardNumberWritten?.takeIf { it.isNotBlank() }?.let(
                ReportedIdentification::typedFromCard,
            ),
        dateOfBirthTyped = row.dateOfBirthTyped,
        dateOfBirthWritten =
            row.dateOfBirthWritten
                ?.takeIf { it.isNotBlank() }
                ?.let { ReportedIdentification(it, IdentificationSource.DATE_OF_BIRTH) },
    )
