package cz.hspinovace.psmf.usecase

import cz.hspinovace.psmf.data.league.LeagueRepository
import cz.hspinovace.psmf.data.league.LoadedFixture
import cz.hspinovace.psmf.data.match.MatchRepository
import cz.hspinovace.psmf.data.player.AddedPlayerRepository
import cz.hspinovace.psmf.domain.Appearance
import cz.hspinovace.psmf.domain.AppearanceId
import cz.hspinovace.psmf.domain.IdentificationSource
import cz.hspinovace.psmf.domain.JerseyNumber
import cz.hspinovace.psmf.domain.Match
import cz.hspinovace.psmf.domain.PersonName
import cz.hspinovace.psmf.domain.Player
import cz.hspinovace.psmf.domain.PlayerId
import cz.hspinovace.psmf.domain.PlayerName
import cz.hspinovace.psmf.domain.Team
import cz.hspinovace.psmf.domain.TeamId
import cz.hspinovace.psmf.domain.TeamSide
import kotlinx.datetime.LocalDate

/**
 * Both teams' blocks, as screen 3 edits them.
 *
 * One value rather than two, because it is one screen: the referee moves
 * between the teams with each captain beside them in turn.
 */
data class LineupEntry(
    val home: TeamLineupEntry,
    val away: TeamLineupEntry,
) {
    fun side(side: TeamSide): TeamLineupEntry =
        when (side) {
            TeamSide.HOME -> home
            TeamSide.AWAY -> away
        }

    fun with(entry: TeamLineupEntry): LineupEntry =
        when (entry.side) {
            TeamSide.HOME -> copy(home = entry)
            TeamSide.AWAY -> copy(away = entry)
        }

    fun problems(): List<LineupProblem> = home.problems() + away.problems()
}

/**
 * Builds the editable lineup: the squads from league data, plus anyone
 * added at the pitch, plus whatever was already recorded.
 *
 * **Restoring is the interesting half.** A saved `Lineup` holds only the
 * players who turned up, so "not in the saved lineup" is what absence
 * looks like on the way back in — but only once a lineup has been saved at
 * all, because before that everybody is present by default. Getting that
 * backwards would mark a full squad absent on the second visit.
 */
class BuildLineupEntry(
    private val league: LeagueRepository,
    private val addedPlayers: AddedPlayerRepository,
    private val newId: NewId,
) {
    suspend operator fun invoke(match: Match): LineupEntry? {
        val fixture = league.fixture(match.fixtureId) ?: return null
        val added = addedPlayers.forMatch(match.id)

        return LineupEntry(
            home = build(match, TeamSide.HOME, fixture, added),
            away = build(match, TeamSide.AWAY, fixture, added),
        )
    }

    private fun build(
        match: Match,
        side: TeamSide,
        fixture: LoadedFixture,
        added: List<Player>,
    ): TeamLineupEntry {
        val team: Team = if (side == TeamSide.HOME) fixture.homeTeam else fixture.awayTeam
        val saved = match.lineup(side)
        val savedByPlayer = saved?.appearances?.associateBy { it.playerId }.orEmpty()
        val squad = fixture.leagueGroup.playersOf(team.id) + added.filter { it.teamId == team.id }

        return TeamLineupEntry(
            side = side,
            team = team,
            members =
                squad.map { player ->
                    val appearance = savedByPlayer[player.id]
                    SquadMemberEntry(
                        player = player,
                        // Reuse the stored id: goals and cards point at it, so
                        // minting a new one would orphan them.
                        appearanceId = appearance?.id ?: AppearanceId(newId()),
                        absent = saved != null && appearance == null,
                        jerseyNumber = appearance?.jerseyNumber ?: player.defaultJerseyNumber,
                        registrationCardPresent = appearance?.usedTheRegistrationCard() ?: true,
                    )
                },
            kitId = saved?.kitId ?: team.primaryKit.id,
        )
    }
}

/** True when the row was written from an RP number rather than a fallback. */
private fun Appearance.usedTheRegistrationCard(): Boolean = reportedIdentification.source == IdentificationSource.RP

/**
 * Writes a team's block through.
 *
 * Saves nothing while the block has [TeamLineupEntry.problems]: there is
 * no valid lineup to save, because two players sharing a number makes
 * every goal ambiguous and the type refuses to hold it. The last good
 * state stays on disk and the screen says what is wrong, so the window in
 * which work could be lost is the seconds between typing a duplicate
 * number and correcting it.
 */
class SaveLineup(
    private val matches: MatchRepository,
) {
    suspend operator fun invoke(
        match: Match,
        entry: TeamLineupEntry,
    ): Match {
        val lineup = entry.toLineup() ?: return match
        val updated =
            when (entry.side) {
                TeamSide.HOME -> match.copy(homeLineup = lineup)
                TeamSide.AWAY -> match.copy(awayLineup = lineup)
            }
        matches.save(updated)
        return updated
    }
}

/**
 * Someone turned up who is not in the squad list.
 *
 * **No RP number is taken, because none can be.** RP numbers are issued by
 * PSMF; `Player.addedAtThePitch` has no parameter for one, and neither
 * does this use case or [NewPlayerRequest]. There is no path from a
 * keyboard to an `RpNumber` anywhere in the app.
 *
 * The player is stored against the match, because they are in no league
 * file and will not be until PSMF reconciles them — and reopening the
 * report still has to know their name.
 */
class AddPlayerAtThePitch(
    private val addedPlayers: AddedPlayerRepository,
    private val newId: NewId,
) {
    suspend operator fun invoke(
        match: Match,
        request: NewPlayerRequest,
    ): Player? {
        val surname = PersonName.orNull(request.surname) ?: return null
        val firstName = PersonName.orNull(request.firstName) ?: return null
        val dateOfBirth = parseDateOfBirth(request.dateOfBirth) ?: return null

        val player =
            Player.addedAtThePitch(
                id = PlayerId(newId()),
                // Not team-scoped, for the same reason seed refs are not.
                ref = "pitch-${newId()}",
                teamId = request.teamId,
                name = PlayerName(surname = surname, firstName = firstName),
                dateOfBirth = dateOfBirth,
                defaultJerseyNumber = JerseyNumber.orNull(request.jerseyNumber),
            )
        addedPlayers.add(match.id, player)
        return player
    }
}

/**
 * Adds a player at the pitch **and puts them straight into the lineup.**
 *
 * The second half is not a convenience. Absence is derived — a player is
 * absent when they are not in the saved lineup — and that cannot
 * distinguish "did not turn up" from "was added after the lineup was
 * saved". Somebody the referee has just typed in is standing in front of
 * them, so the add writes them into the lineup and the ambiguity never
 * arises.
 *
 * Composed here rather than in the ViewModel so that the rule is testable
 * without a screen.
 */
class AddPlayerToLineup(
    private val addPlayerAtThePitch: AddPlayerAtThePitch,
    private val buildLineupEntry: BuildLineupEntry,
    private val saveLineup: SaveLineup,
) {
    /** The match with the player in it, or null if the request was unusable. */
    suspend operator fun invoke(
        match: Match,
        request: NewPlayerRequest,
    ): Match? {
        val player = addPlayerAtThePitch(match, request) ?: return null
        val rebuilt = buildLineupEntry(match) ?: return match

        val side =
            when (request.teamId) {
                rebuilt.home.team.id -> TeamSide.HOME
                rebuilt.away.team.id -> TeamSide.AWAY
                else -> return match
            }

        val team = rebuilt.side(side).withMember(player.id) { it.copy(absent = false) }
        return saveLineup(match, team)
    }
}

/**
 * What screen 3 collects for a player who is not in the squad list:
 * **first name, surname and date of birth. Nothing else, and in
 * particular no RP number.**
 */
data class NewPlayerRequest(
    val teamId: TeamId,
    val firstName: String = "",
    val surname: String = "",
    val dateOfBirth: String = "",
    val jerseyNumber: Int? = null,
) {
    /** Which fields are not usable yet, in the order the form shows them. */
    fun problems(): List<NewPlayerProblem> =
        buildList {
            if (PersonName.orNull(surname) == null) add(NewPlayerProblem.SURNAME)
            if (PersonName.orNull(firstName) == null) add(NewPlayerProblem.FIRST_NAME)
            if (parseDateOfBirth(dateOfBirth) == null) add(NewPlayerProblem.DATE_OF_BIRTH)
        }

    /** The date as parsed, echoed back so the referee can check it. */
    val parsedDateOfBirth: LocalDate? get() = parseDateOfBirth(dateOfBirth)
}

enum class NewPlayerProblem {
    /** Surname first, as the `Příjmení a jméno` column is written. */
    SURNAME,
    FIRST_NAME,
    DATE_OF_BIRTH,
}
