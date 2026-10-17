package cz.hspinovace.psmf.usecase

import cz.hspinovace.psmf.domain.Appearance
import cz.hspinovace.psmf.domain.AppearanceId
import cz.hspinovace.psmf.domain.JerseyNumber
import cz.hspinovace.psmf.domain.Kit
import cz.hspinovace.psmf.domain.KitId
import cz.hspinovace.psmf.domain.Lineup
import cz.hspinovace.psmf.domain.LineupConfirmation
import cz.hspinovace.psmf.domain.Player
import cz.hspinovace.psmf.domain.PlayerId
import cz.hspinovace.psmf.domain.PlayerOrigin
import cz.hspinovace.psmf.domain.ReportedIdentification
import cz.hspinovace.psmf.domain.SuspensionWarning
import cz.hspinovace.psmf.domain.Team
import cz.hspinovace.psmf.domain.TeamSide
import cz.hspinovace.psmf.domain.suspensionWarning
import kotlinx.datetime.LocalDate

/**
 * One row of a team's block on page 1, while it is still being edited.
 *
 * Distinct from [Appearance] the way [MatchHeaderEntry] is distinct from
 * `RefereeAssignment`: an appearance is a finished row on a report and
 * cannot hold nonsense, whereas this is a form that is allowed to be
 * momentarily wrong — two players sharing a number while the referee is
 * halfway through typing the second one.
 *
 * **Presence, not absence** (DECISIONS 2026-10-17, reversing 2026-08-29).
 * Nobody is [present] until the referee taps them. The squad from psmf.cz is
 * everyone who has played this season, so it only grows, and marking the
 * absentees meant a forgotten tap put a no-show on the report, where it
 * counts as an appearance. Marking who came turns the same slip into a
 * missing name, which the captain sees at once.
 */
data class SquadMemberEntry(
    val player: Player,
    /**
     * Minted once when the row is first built and kept.
     *
     * Goals and cards are attributed to an appearance, so this id must not
     * change when the referee takes someone's mark back and gives it again,
     * or a card logged in the first half would lose its subject.
     */
    val appearanceId: AppearanceId,
    val present: Boolean = false,
    val jerseyNumber: JerseyNumber? = player.defaultJerseyNumber,
    /**
     * Whether the player has their registration card with them.
     *
     * True by default because it usually is — the captain typically
     * carries the whole set (DEMO_SCOPE, A15). With the card the row takes
     * the number on it; without, the date of birth, which is the form's own
     * printed rule.
     */
    val registrationCardPresent: Boolean = true,
    /**
     * The number copied from the player's card, as typed into this row --
     * the field's text, allowed to be half-typed. Pre-filled from the number
     * remembered on this device, if any. See [withCardNumberTyped].
     */
    val cardNumberTyped: String = "",
    /**
     * What that number puts in the `Číslo RP` column, once it is a whole
     * one: the number **as written that day**, an appearance's and never the
     * player's. Null until the text is a usable number.
     */
    val cardNumberWritten: ReportedIdentification? = null,
    /**
     * The date of birth as the referee typed it into this row, for a player
     * with no card and none on file -- the field's text, allowed to be
     * half-typed. Pre-filled from the date remembered on this device, if
     * any. See [withDateOfBirthTyped].
     */
    val dateOfBirthTyped: String = "",
    /**
     * What that date puts in the column: the form's own rule, *"uvedou místo
     * čísla RP jejich datum narození"*, rendered YYMMDD by the app. Null
     * until a whole, real date has been typed.
     */
    val dateOfBirthWritten: ReportedIdentification? = null,
) {
    /**
     * The league has an RP number for this player and the card is here, so
     * that number is what goes in the column: **shown, and never edited**.
     */
    val showsLeagueRp: Boolean get() = registrationCardPresent && player.rpNumber != null

    /**
     * No RP number from the league and the card is here: the referee copies
     * the number from it, as the captain does on paper.
     */
    val asksForCardNumber: Boolean get() = registrationCardPresent && player.rpNumber == null

    /**
     * What the league record itself can write when there is no card -- a
     * date of birth on file -- shown and never edited. Null is the usual
     * case: no player read from psmf.cz has one.
     */
    val fileIdentification: ReportedIdentification? get() = player.identificationFor(registrationCardPresent = false)

    /** No card and nothing on file: the referee writes the date of birth, in this row. */
    val asksForDateOfBirth: Boolean get() = !registrationCardPresent && fileIdentification == null

    /**
     * What will be written in the `Číslo RP` column for this row. Null means
     * the row cannot be fielded yet.
     */
    val identification: ReportedIdentification?
        get() =
            when {
                showsLeagueRp -> player.identificationFor(registrationCardPresent = true)
                asksForCardNumber -> cardNumberWritten
                else -> fileIdentification ?: dateOfBirthWritten
            }

    /** The typed date, parsed, echoed back so a mistyped one is visible. */
    val typedDateOfBirth: LocalDate? get() = parseDateOfBirth(dateOfBirthTyped)

    /** The typed number is not a usable one: say so rather than leave the column quietly empty. */
    val cardNumberRejected: Boolean get() = cardNumberTyped.isNotBlank() && cardNumberWritten == null

    /**
     * The referee typed into this row's card-number field.
     *
     * Read by [parseCardNumber]; a usable number becomes what the column
     * will say, and anything else leaves the column empty until it is one.
     * **It never touches the player**: the result is a [ReportedIdentification]
     * on this row, and there is no way from here to an `RpNumber`.
     */
    fun withCardNumberTyped(raw: String): SquadMemberEntry =
        copy(
            cardNumberTyped = raw,
            cardNumberWritten = parseCardNumber(raw)?.let { ReportedIdentification.typedFromCard(it) },
        )

    /**
     * The referee typed into this row's date-of-birth field.
     *
     * Read by [parseDateOfBirth], the same reader the add-a-player form
     * uses; a whole, real date becomes the YYMMDD the column takes, and
     * anything else leaves the column empty until it is one.
     */
    fun withDateOfBirthTyped(raw: String): SquadMemberEntry =
        copy(
            dateOfBirthTyped = raw,
            dateOfBirthWritten = parseDateOfBirth(raw)?.let { ReportedIdentification.of(it) },
        )

    /**
     * Advisory only, and stale by construction.
     *
     * Null is **not** clearance. See [SuspensionWarning] and the note on
     * [TeamLineupEntry.problems].
     */
    val suspensionWarning: SuspensionWarning? get() = player.discipline?.suspensionWarning()

    val addedAtThePitch: Boolean get() = player.origin == PlayerOrigin.ADDED_AT_PITCH

    fun toAppearance(): Appearance? =
        identification?.let {
            Appearance(
                id = appearanceId,
                playerId = player.id,
                jerseyNumber = jerseyNumber,
                reportedIdentification = it,
            )
        }
}

/** A team's block on page 1, while it is still being edited. */
data class TeamLineupEntry(
    val side: TeamSide,
    val team: Team,
    val members: List<SquadMemberEntry>,
    /** Defaults to [Team.primaryKit]; the label is snapshotted on save. */
    val kitId: KitId,
    /**
     * The one captain and the one goalkeeper, among the players [present]
     * and possibly the same person. Both are needed before the block is
     * written down.
     */
    val captain: PlayerId? = null,
    val goalkeeper: PlayerId? = null,
    /**
     * The captain's one tap under this lineup, before kickoff. Not the
     * signature under the finished report. Withdrawn when the captain
     * changes: it was given by the person who was captain then.
     */
    val confirmation: LineupConfirmation? = null,
) {
    val present: List<SquadMemberEntry> get() = members.filter { it.present }

    val kit: Kit? get() = team.kit(kitId)

    /** The marked captain, if they are still among those present. */
    val markedCaptain: SquadMemberEntry? get() = present.firstOrNull { it.player.id == captain }

    val markedGoalkeeper: SquadMemberEntry? get() = present.firstOrNull { it.player.id == goalkeeper }

    /** Numbers worn by more than one player who is actually playing. */
    val duplicateJerseyNumbers: Set<JerseyNumber>
        get() =
            present
                .mapNotNull { it.jerseyNumber }
                .groupingBy { it }
                .eachCount()
                .filterValues { it > 1 }
                .keys

    /**
     * What stops this block being written down yet.
     *
     * **Nothing here is about eligibility.** A suspension warning is
     * advisory and never appears in this list: the app must never claim a
     * player may not play, and the absence of a problem must never read as
     * clearance. Fielding an ineligible player is a technical forfeit, so
     * an app that said "clear" would have caused it.
     */
    fun problems(): List<LineupProblem> =
        buildList {
            if (present.isEmpty()) {
                add(LineupProblem.NobodyPresent(side))
            } else {
                if (markedCaptain == null) add(LineupProblem.NoCaptain(side))
                if (markedGoalkeeper == null) add(LineupProblem.NoGoalkeeper(side))
            }
            duplicateJerseyNumbers.sortedBy { it.value }.forEach {
                add(LineupProblem.DuplicateJerseyNumber(side, it))
            }
            present.filter { it.identification == null }.forEach {
                add(LineupProblem.NoIdentification(side, it.player.id))
            }
        }

    /** The finished block, or null while [problems] is not empty. */
    fun toLineup(): Lineup? {
        if (problems().isNotEmpty()) return null
        val chosen = kit ?: return null
        val appearances = present.map { it.toAppearance() ?: return null }
        // Snapshots the label as well as the reference: a report says what
        // was written on the day, and kits get renamed.
        return Lineup.wearing(
            side = side,
            teamId = team.id,
            appearances = appearances,
            kit = chosen,
            captain = markedCaptain?.appearanceId,
            goalkeeper = markedGoalkeeper?.appearanceId,
            confirmation = confirmation,
        )
    }

    fun withMember(
        playerId: PlayerId,
        change: (SquadMemberEntry) -> SquadMemberEntry,
    ): TeamLineupEntry = copy(members = members.map { if (it.player.id == playerId) change(it) else it })

    /**
     * Marks a player as having come, or takes the mark back.
     *
     * Taking it back from the captain or the goalkeeper vacates the role: a
     * role among the players present cannot be held by somebody who is not.
     * Their number and what they were asked for stay in the row, so the mark
     * can be given again without typing it twice.
     */
    fun withPresence(
        playerId: PlayerId,
        present: Boolean,
    ): TeamLineupEntry {
        val marked = withMember(playerId) { it.copy(present = present) }
        if (present) return marked
        return marked
            .let { if (it.captain == playerId) it.withCaptain(null) else it }
            .let { if (it.goalkeeper == playerId) it.copy(goalkeeper = null) else it }
    }

    /**
     * Names the captain. **A different captain withdraws the confirmation**:
     * it said that the person who was captain then confirmed this lineup, and
     * it no longer says that of this one.
     */
    fun withCaptain(playerId: PlayerId?): TeamLineupEntry =
        copy(captain = playerId, confirmation = if (playerId == captain) confirmation else null)
}

/** A reason a team's block cannot be written down yet. */
sealed interface LineupProblem {
    val side: TeamSide

    /** A team that turned nobody up did not play; that is a forfeit, not a lineup. */
    data class NobodyPresent(
        override val side: TeamSide,
    ) : LineupProblem

    /** Somebody is present and none of them is marked captain. */
    data class NoCaptain(
        override val side: TeamSide,
    ) : LineupProblem

    /** Somebody is present and none of them is marked goalkeeper. */
    data class NoGoalkeeper(
        override val side: TeamSide,
    ) : LineupProblem

    /**
     * Goals and cards are attributed by number, so two players sharing one
     * makes the rest of the report ambiguous.
     */
    data class DuplicateJerseyNumber(
        override val side: TeamSide,
        val number: JerseyNumber,
    ) : LineupProblem

    /**
     * Nothing can go in the `Číslo RP` column yet: the league record cannot
     * fill it, and neither the number from the card nor, with no card, the
     * date of birth has been written in the row. Fixed on the same screen.
     */
    data class NoIdentification(
        override val side: TeamSide,
        val playerId: PlayerId,
    ) : LineupProblem
}
