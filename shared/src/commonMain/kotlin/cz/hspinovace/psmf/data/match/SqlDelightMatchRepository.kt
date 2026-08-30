package cz.hspinovace.psmf.data.match

import cz.hspinovace.psmf.db.Appearance_record
import cz.hspinovace.psmf.db.Card_record
import cz.hspinovace.psmf.db.Goal_record
import cz.hspinovace.psmf.db.MatchRecordQueries
import cz.hspinovace.psmf.db.Match_record
import cz.hspinovace.psmf.db.Power_play_record
import cz.hspinovace.psmf.db.PsmfDatabase
import cz.hspinovace.psmf.domain.Appearance
import cz.hspinovace.psmf.domain.AppearanceId
import cz.hspinovace.psmf.domain.Assessment
import cz.hspinovace.psmf.domain.CardEvent
import cz.hspinovace.psmf.domain.CardReason
import cz.hspinovace.psmf.domain.CardSubject
import cz.hspinovace.psmf.domain.CardsSection
import cz.hspinovace.psmf.domain.Confirmation
import cz.hspinovace.psmf.domain.ConfirmingParty
import cz.hspinovace.psmf.domain.Dismissal
import cz.hspinovace.psmf.domain.FixtureId
import cz.hspinovace.psmf.domain.GoalEvent
import cz.hspinovace.psmf.domain.GroupId
import cz.hspinovace.psmf.domain.IdentificationSource
import cz.hspinovace.psmf.domain.JerseyNumber
import cz.hspinovace.psmf.domain.KitId
import cz.hspinovace.psmf.domain.Lineup
import cz.hspinovace.psmf.domain.Match
import cz.hspinovace.psmf.domain.MatchId
import cz.hspinovace.psmf.domain.MatchResult
import cz.hspinovace.psmf.domain.MatchStatus
import cz.hspinovace.psmf.domain.Minute
import cz.hspinovace.psmf.domain.Official
import cz.hspinovace.psmf.domain.PersonName
import cz.hspinovace.psmf.domain.PlayerId
import cz.hspinovace.psmf.domain.PowerPlay
import cz.hspinovace.psmf.domain.RedCard
import cz.hspinovace.psmf.domain.RefereeAssignment
import cz.hspinovace.psmf.domain.ReportedIdentification
import cz.hspinovace.psmf.domain.Score
import cz.hspinovace.psmf.domain.TeamAssessment
import cz.hspinovace.psmf.domain.TeamId
import cz.hspinovace.psmf.domain.TeamSide
import cz.hspinovace.psmf.domain.YellowCard
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.time.Instant

/**
 * Writes the report through to SQLite on every change.
 *
 * The whole aggregate is rewritten rather than diffed. A match is tens of
 * rows, the write is local, and a referee tapping a goal in the cold is
 * not going to wait on it — where a partial write after a crash would cost
 * the pilot. Correctness is worth more than the microseconds here.
 *
 * Mapping lives in top-level functions at the bottom of this file rather
 * than as members, so the class itself stays a thin four-method repository.
 */
class SqlDelightMatchRepository(
    private val database: PsmfDatabase,
    // There is no Dispatchers.IO in common code; Default is the portable
    // choice, and these are short local writes.
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
) : MatchRepository {
    private val queries: MatchRecordQueries get() = database.matchRecordQueries

    override suspend fun save(match: Match): Unit =
        withContext(dispatcher) {
            database.transaction {
                queries.writeHeader(match)
                queries.clearChildren(match.id.value)
                queries.writeLineups(match)
                queries.writeGoals(match)
                queries.writeCards(match)
                queries.writePowerPlays(match)
                queries.writeConfirmations(match)
            }
        }

    override suspend fun load(id: MatchId): Match? =
        withContext(dispatcher) {
            queries.selectMatch(id.value).executeAsOneOrNull()?.let { queries.hydrate(it) }
        }

    override suspend fun findByStatus(status: MatchStatus): List<Match> =
        withContext(dispatcher) {
            queries.selectMatchesByStatus(status.name).executeAsList().map { queries.hydrate(it) }
        }

    override suspend fun delete(id: MatchId): Unit =
        withContext(dispatcher) {
            database.transaction {
                queries.clearChildren(id.value)
                queries.deleteMatch(id.value)
            }
        }
}

// ---------------------------------------------------------------------------
// Writing
// ---------------------------------------------------------------------------

private fun MatchRecordQueries.writeHeader(match: Match) {
    val assessment = match.assessment
    upsertMatch(
        id = match.id.value,
        fixture_id = match.fixtureId.value,
        group_id = match.groupId.value,
        status = match.status.name,
        // Stored, never ticked: the clock is derived from this, so it
        // survives the process dying and cannot drift.
        kickoff_at = match.kickoffAt?.toString(),
        referee_name =
            match.officials
                ?.main
                ?.name
                ?.value,
        referee_licensed =
            match.officials
                ?.main
                ?.licensedHire
                ?.toLong(),
        assistant_name =
            match.officials
                ?.assistant
                ?.name
                ?.value,
        assistant_licensed =
            match.officials
                ?.assistant
                ?.licensedHire
                ?.toLong(),
        delegating_team = match.officials?.delegatingTeam,
        half_time_home =
            match.result
                ?.halfTime
                ?.home
                ?.toLong(),
        half_time_away =
            match.result
                ?.halfTime
                ?.away
                ?.toLong(),
        full_time_home =
            match.result
                ?.fullTime
                ?.home
                ?.toLong(),
        full_time_away =
            match.result
                ?.fullTime
                ?.away
                ?.toLong(),
        home_best_player =
            assessment.home.bestPlayer
                ?.value
                ?.toLong(),
        home_waiting = assessment.home.waitingTimeMinutes.toLong(),
        home_shirts = assessment.home.shirtsProperlyNumbered?.toLong(),
        home_kit = assessment.home.uniformKitColour?.toLong(),
        away_best_player =
            assessment.away.bestPlayer
                ?.value
                ?.toLong(),
        away_waiting = assessment.away.waitingTimeMinutes.toLong(),
        away_shirts = assessment.away.shirtsProperlyNumbered?.toLong(),
        away_kit = assessment.away.uniformKitColour?.toLong(),
        commentary = assessment.commentary,
        // Null, NONE_ISSUED and ISSUED are three distinct states, and
        // collapsing the first two would turn "not filled in" into "the
        // referee affirmed there were none".
        cards_state = match.cards?.stateName(),
    )
}

private fun MatchRecordQueries.clearChildren(matchId: String) {
    deleteConfirmations(matchId)
    deletePowerPlays(matchId)
    deleteCards(matchId)
    deleteGoals(matchId)
    deleteAppearances(matchId)
    deleteLineups(matchId)
}

private fun MatchRecordQueries.writeLineups(match: Match) {
    listOfNotNull(match.homeLineup, match.awayLineup).forEach { lineup ->
        insertLineup(match.id.value, lineup.side.name, lineup.teamId.value, lineup.kitId.value)
        lineup.appearances.forEachIndexed { index, appearance ->
            insertAppearance(
                id = appearance.id.value,
                match_id = match.id.value,
                side = lineup.side.name,
                position = index.toLong(),
                player_id = appearance.playerId.value,
                // The number belongs to the appearance, not the player.
                jersey_number = appearance.jerseyNumber?.value?.toLong(),
                // What was written on the day, not what the player record
                // says today.
                identification_value = appearance.reportedIdentification.value,
                identification_source = appearance.reportedIdentification.source.name,
            )
        }
    }
}

private fun MatchRecordQueries.writeGoals(match: Match) {
    match.goals.forEachIndexed { index, goal ->
        insertGoal(
            match_id = match.id.value,
            position = index.toLong(),
            minute_kind = goal.minute.kindName(),
            minute_value = goal.minute.numericValue(),
            side = goal.side.name,
            // Nullable: the worked example contains a goal with no scorer.
            scorer_appearance_id = goal.scorer?.value,
            score_home = goal.scoreAfter.home.toLong(),
            score_away = goal.scoreAfter.away.toLong(),
        )
    }
}

private fun MatchRecordQueries.writeCards(match: Match) {
    match.cardEvents.forEachIndexed { index, card ->
        insertCard(
            match_id = match.id.value,
            position = index.toLong(),
            colour = if (card is RedCard) COLOUR_RED else COLOUR_YELLOW,
            minute_kind = card.minute.kindName(),
            minute_value = card.minute.numericValue(),
            side = card.side.name,
            subject_kind = card.subject.kindName(),
            subject_appearance_id = (card.subject as? CardSubject.Player)?.appearance?.value,
            subject_name = (card.subject as? CardSubject.NamedPerson)?.name?.value,
            reason = card.reason.text,
            // Straight versus second yellow changes the suspension.
            dismissal = (card as? RedCard)?.dismissal?.name,
        )
    }
}

private fun MatchRecordQueries.writePowerPlays(match: Match) {
    match.powerPlays.forEachIndexed { index, powerPlay ->
        insertPowerPlay(
            match_id = match.id.value,
            position = index.toLong(),
            short_handed_side = powerPlay.shortHandedSide.name,
            started_at = powerPlay.startedAt.toString(),
            minute_kind = powerPlay.dismissedAtMinute.kindName(),
            minute_value = powerPlay.dismissedAtMinute.numericValue(),
        )
    }
}

private fun MatchRecordQueries.writeConfirmations(match: Match) {
    match.confirmations.forEach { confirmation ->
        insertConfirmation(
            match_id = match.id.value,
            party = confirmation.party.name,
            confirmed_at = confirmation.at.toString(),
            confirmed_by = confirmation.confirmedBy.value,
            as_deputy = confirmation.asDeputy.toLong(),
        )
    }
}

// ---------------------------------------------------------------------------
// Reading
// ---------------------------------------------------------------------------

private fun MatchRecordQueries.hydrate(row: Match_record): Match {
    val id = row.id
    val appearances = selectAppearances(id).executeAsList()

    val lineups =
        selectLineups(id).executeAsList().associate { lineupRow ->
            val side = TeamSide.valueOf(lineupRow.side)
            side to
                Lineup(
                    side = side,
                    teamId = TeamId(lineupRow.team_id),
                    appearances = appearances.filter { it.side == lineupRow.side }.map { it.toDomain() },
                    kitId = KitId(lineupRow.kit_id),
                )
        }

    return Match(
        id = MatchId(id),
        fixtureId = FixtureId(row.fixture_id),
        groupId = GroupId(row.group_id),
        status = MatchStatus.valueOf(row.status),
        officials = row.toOfficials(),
        homeLineup = lineups[TeamSide.HOME],
        awayLineup = lineups[TeamSide.AWAY],
        kickoffAt = row.kickoff_at?.let { Instant.parse(it) },
        goals = selectGoals(id).executeAsList().map { it.toDomain() },
        cards = cardsSection(row.cards_state, selectCards(id).executeAsList().map { it.toDomain() }),
        powerPlays = selectPowerPlays(id).executeAsList().map { it.toDomain() },
        assessment = row.toAssessment(),
        result = row.toResult(),
        confirmations =
            selectConfirmations(id).executeAsList().map {
                Confirmation(
                    party = ConfirmingParty.valueOf(it.party),
                    at = Instant.parse(it.confirmed_at),
                    confirmedBy = PersonName.of(it.confirmed_by),
                    asDeputy = it.as_deputy == 1L,
                )
            },
    )
}

private fun Match_record.toOfficials(): RefereeAssignment? {
    val refereeName = referee_name ?: return null
    val delegating = delegating_team ?: return null
    return RefereeAssignment(
        main = Official(PersonName.of(refereeName), referee_licensed == 1L),
        assistant = assistant_name?.let { Official(PersonName.of(it), assistant_licensed == 1L) },
        delegatingTeam = delegating,
    )
}

private fun Match_record.toAssessment() =
    Assessment(
        home =
            TeamAssessment(
                bestPlayer = home_best_player?.let { JerseyNumber(it.toInt()) },
                waitingTimeMinutes = home_waiting.toInt(),
                shirtsProperlyNumbered = home_shirts?.let { it == 1L },
                uniformKitColour = home_kit?.let { it == 1L },
            ),
        away =
            TeamAssessment(
                bestPlayer = away_best_player?.let { JerseyNumber(it.toInt()) },
                waitingTimeMinutes = away_waiting.toInt(),
                shirtsProperlyNumbered = away_shirts?.let { it == 1L },
                uniformKitColour = away_kit?.let { it == 1L },
            ),
        commentary = commentary,
    )

private fun Match_record.toResult(): MatchResult? {
    // Both scores are recorded together or not at all: a report with a
    // half-time score and no final score is not a partially known result,
    // it is a result that has not been entered.
    val halfTime = scoreOf(half_time_home, half_time_away) ?: return null
    val fullTime = scoreOf(full_time_home, full_time_away) ?: return null
    return MatchResult(halfTime = halfTime, fullTime = fullTime)
}

private fun scoreOf(
    home: Long?,
    away: Long?,
): Score? = if (home == null || away == null) null else Score(home.toInt(), away.toInt())

private fun Appearance_record.toDomain() =
    Appearance(
        id = AppearanceId(id),
        playerId = PlayerId(player_id),
        jerseyNumber = jersey_number?.let { JerseyNumber(it.toInt()) },
        reportedIdentification =
            ReportedIdentification(
                identification_value,
                IdentificationSource.valueOf(identification_source),
            ),
    )

private fun Power_play_record.toDomain() =
    PowerPlay(
        shortHandedSide = TeamSide.valueOf(short_handed_side),
        startedAt = Instant.parse(started_at),
        dismissedAtMinute = minuteOf(minute_kind, minute_value),
    )

private fun Goal_record.toDomain() =
    GoalEvent(
        minute = minuteOf(minute_kind, minute_value),
        side = TeamSide.valueOf(side),
        scorer = scorer_appearance_id?.let { AppearanceId(it) },
        scoreAfter = Score(score_home.toInt(), score_away.toInt()),
    )

private fun Card_record.toDomain(): CardEvent {
    val subject =
        when (subject_kind) {
            SUBJECT_NAMED_PERSON -> CardSubject.NamedPerson(PersonName.of(requireNotNull(subject_name)))
            else -> CardSubject.Player(AppearanceId(requireNotNull(subject_appearance_id)))
        }
    val minute = minuteOf(minute_kind, minute_value)
    val teamSide = TeamSide.valueOf(side)
    val cardReason = CardReason(reason)

    return if (colour == COLOUR_RED) {
        RedCard(minute, teamSide, subject, cardReason, Dismissal.valueOf(requireNotNull(dismissal)))
    } else {
        YellowCard(minute, teamSide, subject, cardReason)
    }
}

/**
 * Rebuilds the three-way state of the cards block. A null column means the
 * referee never accounted for it, which is deliberately not the same as
 * having affirmed that no cards were issued.
 */
private fun cardsSection(
    state: String?,
    cards: List<CardEvent>,
): CardsSection? =
    when (state) {
        null -> null
        CARDS_NONE_ISSUED -> CardsSection.NoneIssued
        else -> CardsSection.Issued(cards)
    }

// ---------------------------------------------------------------------------
// Encoding
// ---------------------------------------------------------------------------

private const val COLOUR_RED = "RED"
private const val COLOUR_YELLOW = "YELLOW"
private const val CARDS_NONE_ISSUED = "NONE_ISSUED"
private const val CARDS_ISSUED = "ISSUED"
private const val SUBJECT_PLAYER = "PLAYER"
private const val SUBJECT_NAMED_PERSON = "NAMED_PERSON"

private fun Boolean.toLong(): Long = if (this) 1L else 0L

private fun CardsSection.stateName(): String =
    when (this) {
        CardsSection.NoneIssued -> CARDS_NONE_ISSUED
        is CardsSection.Issued -> CARDS_ISSUED
    }

private fun CardSubject.kindName(): String =
    when (this) {
        is CardSubject.Player -> SUBJECT_PLAYER
        is CardSubject.NamedPerson -> SUBJECT_NAMED_PERSON
    }

/** A minute is stored as a kind plus an optional number, never as one integer. */
private fun Minute.kindName(): String =
    when (this) {
        is Minute.Played -> MINUTE_PLAYED
        Minute.HalfTime -> MINUTE_HALF_TIME
        Minute.AfterFinalWhistle -> MINUTE_AFTER_FINAL_WHISTLE
    }

private fun Minute.numericValue(): Long? = (this as? Minute.Played)?.value?.toLong()

private fun minuteOf(
    kind: String,
    value: Long?,
): Minute =
    when (kind) {
        MINUTE_HALF_TIME -> Minute.HalfTime
        MINUTE_AFTER_FINAL_WHISTLE -> Minute.AfterFinalWhistle
        else -> Minute.Played(value?.toInt() ?: 0)
    }

private const val MINUTE_PLAYED = "PLAYED"
private const val MINUTE_HALF_TIME = "HALF_TIME"
private const val MINUTE_AFTER_FINAL_WHISTLE = "AFTER_FINAL_WHISTLE"
