package cz.hspinovace.psmf.usecase

import cz.hspinovace.psmf.data.league.LeagueRepository
import cz.hspinovace.psmf.data.player.AddedPlayerRepository
import cz.hspinovace.psmf.domain.AppearanceId
import cz.hspinovace.psmf.domain.CardSubject
import cz.hspinovace.psmf.domain.JerseyNumber
import cz.hspinovace.psmf.domain.Match
import cz.hspinovace.psmf.domain.MatchEvent
import cz.hspinovace.psmf.domain.MatchStatus
import cz.hspinovace.psmf.domain.Minute
import cz.hspinovace.psmf.domain.PersonName
import cz.hspinovace.psmf.domain.PlayerId
import cz.hspinovace.psmf.domain.PlayerName
import cz.hspinovace.psmf.domain.PowerPlay
import cz.hspinovace.psmf.domain.RedCard
import cz.hspinovace.psmf.domain.Score
import cz.hspinovace.psmf.domain.TeamSide
import cz.hspinovace.psmf.domain.YellowCard
import kotlin.time.Instant

/** One player on the console, as a row that can be tapped. */
data class ConsoleRow(
    val appearanceId: AppearanceId,
    val jerseyNumber: JerseyNumber?,
    val name: PlayerName,
    /**
     * **Sent off. The row is disabled, not hidden.**
     *
     * Hiding it would lose the reason the player is unavailable, and the
     * referee still needs to see who is off — a side playing a player
     * short is exactly what the power play beside it is counting.
     */
    val dismissed: Boolean,
    /**
     * Yellows in *this match*.
     *
     * Shown because a second one is a dismissal, written on the form as
     * `2. ŽK`, and the referee needs to know they are about to issue one.
     * Unrelated to the season total on screen 3, which is advisory and
     * comes from PSMF.
     */
    val yellowsInThisMatch: Int,
)

data class ConsoleTeam(
    val side: TeamSide,
    val teamName: String,
    val rows: List<ConsoleRow>,
) {
    fun row(id: AppearanceId): ConsoleRow? = rows.firstOrNull { it.appearanceId == id }
}

/**
 * Everything screen 4 draws, with nothing left to look up.
 *
 * Deliberately does **not** hold the elapsed time. The clock is derived
 * from [kickoffAt] on demand, because nothing ticks in the background:
 * iOS cannot run a background timer at all, and a derived clock cannot
 * drift, cannot be killed and survives a reboot.
 */
data class ConsoleEntry(
    val home: ConsoleTeam,
    val away: ConsoleTeam,
    val score: Score,
    val kickoffAt: Instant?,
    val status: MatchStatus,
    /** Newest first: the referee checks what they have just logged. */
    val log: List<MatchEvent>,
    val powerPlays: List<PowerPlay>,
) {
    val started: Boolean get() = kickoffAt != null

    fun side(side: TeamSide): ConsoleTeam =
        when (side) {
            TeamSide.HOME -> home
            TeamSide.AWAY -> away
        }

    fun row(id: AppearanceId): ConsoleRow? = home.row(id) ?: away.row(id)

    /**
     * The minute now, derived rather than counted.
     *
     * **The clock never pauses** (analysis section 2.6): 2 x 30 gross, and
     * the referee adds time rather than stopping anything. There is
     * deliberately no pause, stop, resume or adjust operation anywhere on
     * this screen or behind it, and there must not be one.
     */
    fun minuteAt(now: Instant): Minute.Played? =
        kickoffAt?.let { Minute.Played(((now - it).inWholeMinutes).toInt().coerceAtLeast(0)) }

    fun powerPlaysRunningAt(now: Instant): List<PowerPlay> = powerPlays.filter { it.isRunningAt(now) }

    fun playersShortAt(
        side: TeamSide,
        now: Instant,
    ): Int = powerPlaysRunningAt(now).count { it.shortHandedSide == side }
}

/**
 * Builds the console from the report and the league data.
 *
 * Names come from the lineup's appearances resolved against the squad, so
 * a player added at the pitch appears here exactly like anyone else.
 */
class BuildConsoleEntry(
    private val league: LeagueRepository,
    private val addedPlayers: AddedPlayerRepository,
) {
    suspend operator fun invoke(match: Match): ConsoleEntry? {
        val fixture = league.fixture(match.fixtureId) ?: return null
        val added = addedPlayers.forMatch(match.id)
        val namesByPlayer = (fixture.leagueGroup.players + added).associate { it.id to it.name }

        return ConsoleEntry(
            home = team(match, TeamSide.HOME, fixture.homeTeam.name, namesByPlayer),
            away = team(match, TeamSide.AWAY, fixture.awayTeam.name, namesByPlayer),
            score = match.scoreFromGoals(),
            kickoffAt = match.kickoffAt,
            status = match.status,
            log = match.timeline().reversed(),
            powerPlays = match.powerPlays,
        )
    }

    private fun team(
        match: Match,
        side: TeamSide,
        teamName: String,
        namesByPlayer: Map<PlayerId, PlayerName>,
    ): ConsoleTeam {
        val cards = match.cardEvents
        return ConsoleTeam(
            side = side,
            teamName = teamName,
            rows =
                match
                    .lineup(side)
                    ?.appearances
                    .orEmpty()
                    .map { appearance ->
                        ConsoleRow(
                            appearanceId = appearance.id,
                            jerseyNumber = appearance.jerseyNumber,
                            name = namesByPlayer[appearance.playerId] ?: UNKNOWN_NAME,
                            dismissed = cards.any { it is RedCard && it.subject.isPlayer(appearance.id) },
                            yellowsInThisMatch =
                                cards.count { it is YellowCard && it.subject.isPlayer(appearance.id) },
                        )
                        // By shirt number, which is how the referee reads them off
                        // the pitch. Numberless players go last.
                    }.sortedBy { it.jerseyNumber?.value ?: Int.MAX_VALUE },
        )
    }

    private companion object {
        /**
         * For an appearance whose player is in no file we can read.
         *
         * Should be impossible — pitch-added players are stored and seed
         * players are shipped — but a live console is the worst place in
         * the app to throw.
         */
        val UNKNOWN_NAME = PlayerName(surname = PersonName.of("N"), firstName = PersonName.of("N"))
    }
}

private fun CardSubject.isPlayer(id: AppearanceId): Boolean = this is CardSubject.Player && appearance == id
