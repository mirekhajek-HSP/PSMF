package cz.hspinovace.psmf.usecase

import cz.hspinovace.psmf.data.league.LeagueRepository
import cz.hspinovace.psmf.data.league.LoadedFixture
import cz.hspinovace.psmf.data.match.MatchRepository
import cz.hspinovace.psmf.data.player.AddedPlayerRepository
import cz.hspinovace.psmf.data.player.RememberedCardNumberRepository
import cz.hspinovace.psmf.data.player.RememberedDateOfBirthRepository
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
 * players who turned up, so a player is present exactly when the saved
 * lineup has an appearance for them -- and nobody is, before one has been
 * saved (DECISIONS 2026-10-17: the lineup marks who came). That is also why
 * a lineup written before 0.3.0 reads the same way: it only ever held the
 * players who were there, so the switch from marking who stayed away to
 * marking who came takes nobody off an old report.
 */
class BuildLineupEntry(
    private val league: LeagueRepository,
    private val addedPlayers: AddedPlayerRepository,
    private val rememberedDatesOfBirth: RememberedDateOfBirthRepository,
    private val rememberedCardNumbers: RememberedCardNumberRepository,
    private val newId: NewId,
) {
    suspend operator fun invoke(match: Match): LineupEntry? {
        val fixture = league.fixture(match.fixtureId) ?: return null
        val added = addedPlayers.forMatch(match.id)
        val remembered = Remembered(rememberedDatesOfBirth.all(), rememberedCardNumbers.all())

        return LineupEntry(
            home = build(match, TeamSide.HOME, fixture, added, remembered),
            away = build(match, TeamSide.AWAY, fixture, added, remembered),
        )
    }

    /** What this device offers to pre-fill, by player. Pre-fills only: see the repositories. */
    private class Remembered(
        val datesOfBirth: Map<PlayerId, LocalDate>,
        val cardNumbers: Map<PlayerId, String>,
    )

    private fun build(
        match: Match,
        side: TeamSide,
        fixture: LoadedFixture,
        added: List<Player>,
        remembered: Remembered,
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
                        present = appearance != null,
                        jerseyNumber = appearance?.jerseyNumber ?: player.defaultJerseyNumber,
                        registrationCardPresent = appearance?.usedTheRegistrationCard() ?: true,
                    ).withCardNumberTyped(remembered.cardNumbers[player.id].orEmpty())
                        .withDateOfBirthTyped(remembered.datesOfBirth[player.id]?.asTypedDateOfBirth().orEmpty())
                        .keepingWhatWasWritten(appearance)
                },
            kitId = saved?.kitId ?: team.primaryKit.id,
            captain = saved?.captain?.let { saved.appearance(it)?.playerId },
            goalkeeper = saved?.goalkeeper?.let { saved.appearance(it)?.playerId },
            confirmation = saved?.confirmation,
        )
    }
}

/** True when the row was written from a number on the card rather than a fallback. */
private fun Appearance.usedTheRegistrationCard(): Boolean =
    reportedIdentification.source == IdentificationSource.RP ||
        reportedIdentification.source == IdentificationSource.RP_TYPED

/**
 * A row already written keeps what it wrote in `Číslo RP`.
 *
 * What a device remembers is a pre-fill and may have been changed since, at
 * another match. A lineup reopened later must not quietly rewrite an old row
 * with it -- the same principle as a kit rename. So a number typed from the
 * card comes back as the text that was typed, and a date of birth the league
 * record cannot supply stays what was written, with its field left empty
 * rather than showing a date that is not the one written.
 */
private fun SquadMemberEntry.keepingWhatWasWritten(appearance: Appearance?): SquadMemberEntry {
    val written = appearance?.reportedIdentification ?: return this
    return when (written.source) {
        IdentificationSource.RP_TYPED -> {
            copy(cardNumberTyped = written.value, cardNumberWritten = written)
        }

        IdentificationSource.DATE_OF_BIRTH -> {
            if (!asksForDateOfBirth || written == dateOfBirthWritten) {
                this
            } else {
                copy(dateOfBirthTyped = "", dateOfBirthWritten = written)
            }
        }

        IdentificationSource.RP, IdentificationSource.BIRTH_NUMBER -> {
            this
        }
    }
}

/**
 * Remembers a date of birth the referee wrote at the pitch, so the next
 * match offers it. A pre-fill only; see [RememberedDateOfBirthRepository].
 */
class RememberDateOfBirth(
    private val remembered: RememberedDateOfBirthRepository,
) {
    suspend operator fun invoke(
        playerId: PlayerId,
        dateOfBirth: LocalDate,
    ) = remembered.remember(playerId, dateOfBirth)
}

/**
 * Remembers a number the referee copied from a card, so the next match offers
 * it. **A pre-fill and nothing else, and not the player's RP number**; see
 * [RememberedCardNumberRepository].
 */
class RememberCardNumber(
    private val remembered: RememberedCardNumberRepository,
) {
    suspend operator fun invoke(
        playerId: PlayerId,
        cardNumber: String,
    ) = remembered.remember(playerId, cardNumber)
}

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
    /**
     * Both blocks at once.
     *
     * What "Continue" means: the lineups stand as they are. Needed because
     * write-through happens on *edit*, and a team with nobody absent and
     * every number already right is never edited — so without this its
     * block would never be written, and the console would have nobody on
     * it.
     */
    suspend operator fun invoke(
        match: Match,
        entry: LineupEntry,
    ): Match = invoke(invoke(match, entry.home), entry.away)

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
 * does this use case. The number a referee copies from the player's card is
 * not an RP number in this sense: it is what the appearance writes that day
 * (see [AddPlayerToLineup]) and it never reaches the player, so there is
 * still no path from a keyboard to an `RpNumber` anywhere in the app.
 *
 * The player is stored against the match, because they are in no league
 * file and will not be until PSMF reconciles them — and reopening the
 * report still has to know their name. A date of birth is stored with them
 * only when they have no card to copy a number from.
 */
class AddPlayerAtThePitch(
    private val addedPlayers: AddedPlayerRepository,
    private val newId: NewId,
) {
    suspend operator fun invoke(
        match: Match,
        request: NewPlayerRequest,
    ): Player? {
        if (request.problems().isNotEmpty()) return null
        val surname = PersonName.orNull(request.surname) ?: return null
        val firstName = PersonName.orNull(request.firstName) ?: return null

        val player =
            Player.addedAtThePitch(
                id = PlayerId(newId()),
                // Not team-scoped, for the same reason seed refs are not.
                ref = "pitch-${newId()}",
                teamId = request.teamId,
                name = PlayerName(surname = surname, firstName = firstName),
                // The form's fallback, for a player with no card. One with a
                // card has their number on the appearance and nothing here.
                dateOfBirth = request.parsedDateOfBirth.takeIf { request.noCard },
                defaultJerseyNumber = JerseyNumber.orNull(request.jerseyNumber),
            )
        addedPlayers.add(match.id, player)
        return player
    }
}

/**
 * Adds a player at the pitch **and puts them straight into the lineup.**
 *
 * The second half is not a convenience. A player is present when the saved
 * lineup has them, and somebody the referee has just typed in is not in it
 * yet. They are standing in front of the referee, so the add marks them
 * present and writes them in.
 *
 * **It works on the entry the screen is holding, not on one rebuilt from the
 * database.** A block is only saved once it is whole, so a referee who has
 * marked eight players and not yet named a captain has marks that exist
 * nowhere but on the screen; rebuilding would throw them away.
 *
 * Composed here rather than in the ViewModel so that the rule is testable
 * without a screen.
 */
class AddPlayerToLineup(
    private val addPlayerAtThePitch: AddPlayerAtThePitch,
    private val saveLineup: SaveLineup,
    private val newId: NewId,
) {
    /** The match and the team with the player in it, or null if the request was unusable. */
    suspend operator fun invoke(
        match: Match,
        team: TeamLineupEntry,
        request: NewPlayerRequest,
    ): AddedToLineup? {
        if (request.teamId != team.team.id) return null
        val player = addPlayerAtThePitch(match, request) ?: return null

        val member =
            SquadMemberEntry(
                player = player,
                appearanceId = AppearanceId(newId()),
                present = true,
                jerseyNumber = player.defaultJerseyNumber,
                registrationCardPresent = !request.noCard,
            ).withCardNumberTyped(if (request.noCard) "" else request.cardNumber)

        val updated = team.copy(members = team.members + member)
        return AddedToLineup(match = saveLineup(match, updated), team = updated)
    }
}

/** What adding a player leaves: the report as saved, and the team block as it now stands. */
data class AddedToLineup(
    val match: Match,
    val team: TeamLineupEntry,
)

/**
 * What screen 3 collects for a player who is not in the squad list:
 * **first name, surname, and either the number from the player's card or,
 * with no card, the date of birth** (DECISIONS 2026-10-17).
 *
 * Neither is an RP number as PSMF issues them. The number is what the
 * referee copies from the card, as the captain does on paper; it goes on the
 * appearance and nowhere else.
 */
data class NewPlayerRequest(
    val teamId: TeamId,
    val firstName: String = "",
    val surname: String = "",
    /** The number from the card, as typed. Used unless [noCard]. */
    val cardNumber: String = "",
    /** No card to hand: the date of birth is written instead, which is the form's printed rule. */
    val noCard: Boolean = false,
    val dateOfBirth: String = "",
    val jerseyNumber: Int? = null,
) {
    /** Which fields are not usable yet, in the order the form shows them. */
    fun problems(): List<NewPlayerProblem> =
        buildList {
            if (PersonName.orNull(surname) == null) add(NewPlayerProblem.SURNAME)
            if (PersonName.orNull(firstName) == null) add(NewPlayerProblem.FIRST_NAME)
            if (noCard) {
                if (parseDateOfBirth(dateOfBirth) == null) add(NewPlayerProblem.DATE_OF_BIRTH)
            } else {
                if (parseCardNumber(cardNumber) == null) add(NewPlayerProblem.CARD_NUMBER)
            }
        }

    /** The date as parsed, echoed back so the referee can check it. */
    val parsedDateOfBirth: LocalDate? get() = parseDateOfBirth(dateOfBirth)
}

enum class NewPlayerProblem {
    /** Surname first, as the `Příjmení a jméno` column is written. */
    SURNAME,
    FIRST_NAME,
    CARD_NUMBER,
    DATE_OF_BIRTH,
}
