package cz.hspinovace.psmf.export

import cz.hspinovace.psmf.data.league.LeagueRepository
import cz.hspinovace.psmf.data.player.AddedPlayerRepository
import cz.hspinovace.psmf.domain.AppearanceId
import cz.hspinovace.psmf.domain.CardEvent
import cz.hspinovace.psmf.domain.CardSubject
import cz.hspinovace.psmf.domain.ConfirmingParty
import cz.hspinovace.psmf.domain.Lineup
import cz.hspinovace.psmf.domain.Match
import cz.hspinovace.psmf.domain.PlayerId
import cz.hspinovace.psmf.domain.PlayerName
import cz.hspinovace.psmf.domain.RedCard
import cz.hspinovace.psmf.domain.TeamSide
import cz.hspinovace.psmf.domain.YellowCard

/**
 * Turns a report in progress into the *Zápis o utkání*.
 *
 * All the resolution happens here — appearance ids to names, team ids to
 * team names, sides to `D` and `H` — so that the three formatters cannot
 * disagree about what the report says.
 *
 * Returns null only when the fixture is unknown, which cannot happen for a
 * report the app created. Everything else that might be missing is a
 * *readiness* problem rather than a failure to build: the recap screen has
 * to be able to show an incomplete report, because showing what is missing
 * is the single most useful thing the app does that paper does not.
 */
class BuildZouReport(
    private val league: LeagueRepository,
    private val addedPlayers: AddedPlayerRepository,
) {
    suspend operator fun invoke(match: Match): ZouReport? {
        val fixture = league.fixture(match.fixtureId) ?: return null
        val added = addedPlayers.forMatch(match.id)
        val names = (fixture.leagueGroup.players + added).associate { it.id to it.name }

        val appearances = appearanceIndex(match, names)
        val teamNames =
            mapOf(TeamSide.HOME to fixture.homeTeam.name, TeamSide.AWAY to fixture.awayTeam.name)

        return ZouReport(
            header =
                ZouHeader(
                    pitch = fixture.fixture.venue.value,
                    date = fixture.fixture.date,
                    time = fixture.fixture.time,
                    league = fixture.leagueGroup.group.reportCode,
                    homeTeam = fixture.homeTeam.name,
                    awayTeam = fixture.awayTeam.name,
                    referee =
                        match.officials
                            ?.main
                            ?.name
                            ?.value
                            .orEmpty(),
                    refereeLicensedHire = match.officials?.main?.licensedHire == true,
                    assistant =
                        match.officials
                            ?.assistant
                            ?.name
                            ?.value,
                    assistantLicensedHire = match.officials?.assistant?.licensedHire == true,
                    delegatingTeam = match.officials?.delegatingTeam.orEmpty(),
                ),
            lineups =
                TeamSide.entries.mapNotNull { side ->
                    match.lineup(side)?.toZou(teamNames.getValue(side), names)
                },
            goals =
                match.goals.map { goal ->
                    val scorer = goal.scorer?.let { appearances[it] }
                    ZouGoal(
                        side = goal.side.asZouSide(),
                        minute = goal.minute.written,
                        jerseyNumber = scorer?.jerseyNumber,
                        scorer = scorer?.name,
                        scoreAfter = goal.scoreAfter.asWrittenOnReport,
                    )
                },
            cards =
                ZouCards(
                    // Null means the referee has not accounted for the block
                    // at all, which is not the same as affirming none.
                    accountedFor = match.cards != null,
                    yellow = match.cardEvents.filterIsInstance<YellowCard>().map { it.toZou(appearances) },
                    red = match.cardEvents.filterIsInstance<RedCard>().map { it.toZou(appearances) },
                ),
            result =
                match.result?.let { result ->
                    ZouResult(
                        halfTime = result.halfTime.asWrittenOnReport,
                        fullTime = result.fullTime.asWrittenOnReport,
                        winner = result.winner?.let { teamNames.getValue(it) } ?: ZouWords.DRAW,
                    )
                },
            assessment =
                ZouAssessment(
                    home = match.assessment.home.toZou(),
                    away = match.assessment.away.toZou(),
                    commentary = match.assessment.commentary,
                ),
            confirmations =
                match.confirmations.map { confirmation ->
                    ZouConfirmation(
                        party = confirmation.party.asZouLabel(),
                        by = confirmation.confirmedBy.value,
                        at = confirmation.at,
                        asDeputy = confirmation.asDeputy,
                    )
                },
        )
    }

    /** Appearance id to the number and name that go beside it on every row. */
    private fun appearanceIndex(
        match: Match,
        names: Map<PlayerId, PlayerName>,
    ): Map<AppearanceId, ZouAppearance> =
        TeamSide.entries
            .mapNotNull { match.lineup(it) }
            .flatMap { it.appearances }
            .associate { appearance ->
                appearance.id to
                    ZouAppearance(
                        jerseyNumber = appearance.jerseyNumber?.value,
                        identification = appearance.reportedIdentification.value,
                        name = names[appearance.playerId]?.asWrittenOnReport.orEmpty(),
                    )
            }
}

private fun Lineup.toZou(
    teamName: String,
    names: Map<PlayerId, PlayerName>,
): ZouLineup =
    ZouLineup(
        side = side.asZouSide(),
        teamName = teamName,
        // The snapshot, never a lookup: a kit rename must not rewrite this.
        kitLabel = kitLabel,
        rows =
            appearances
                .map { appearance ->
                    ZouAppearance(
                        jerseyNumber = appearance.jerseyNumber?.value,
                        identification = appearance.reportedIdentification.value,
                        name = names[appearance.playerId]?.asWrittenOnReport.orEmpty(),
                    )
                    // By shirt number, as the block is written out.
                }.sortedBy { it.jerseyNumber ?: Int.MAX_VALUE },
    )

private fun CardEvent.toZou(appearances: Map<AppearanceId, ZouAppearance>): ZouCard {
    val row = (subject as? CardSubject.Player)?.let { appearances[it.appearance] }
    return ZouCard(
        side = side.asZouSide(),
        minute = minute.written,
        jerseyNumber = row?.jerseyNumber,
        // A card may be shown to somebody with no number on the sheet.
        name = row?.name ?: (subject as? CardSubject.NamedPerson)?.name?.value.orEmpty(),
        reason = reason.text,
    )
}

private fun cz.hspinovace.psmf.domain.TeamAssessment.toZou(): ZouTeamAssessment =
    ZouTeamAssessment(
        bestPlayer = bestPlayer?.value,
        waitingTimeMinutes = waitingTimeMinutes,
        shirtsProperlyNumbered = shirtsProperlyNumbered,
        uniformKitColour = uniformKitColour,
    )

private fun ConfirmingParty.asZouLabel(): String =
    when (this) {
        ConfirmingParty.HOME_CAPTAIN -> ZouWords.HOME_CAPTAIN
        ConfirmingParty.AWAY_CAPTAIN -> ZouWords.AWAY_CAPTAIN
        ConfirmingParty.REFEREE -> ZouWords.REFEREE
    }
