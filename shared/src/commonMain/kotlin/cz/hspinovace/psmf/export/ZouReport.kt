package cz.hspinovace.psmf.export

import cz.hspinovace.psmf.domain.TeamSide
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.number
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.time.Instant

/**
 * The *Zápis o utkání*, assembled and ready to write out.
 *
 * **Everything here is already in its final Czech form.** No ids, no
 * references to resolve, no decisions left: a formatter's job is layout,
 * not lookup. That is deliberate — the three formatters must not be able
 * to disagree about what the report says, only about how it looks.
 *
 * **Nothing here is localised and nothing here may become localised.** The
 * app is readable in Czech, English and Ukrainian; the report that goes to
 * PSMF is Czech whatever the referee is reading. See [ZouLabels].
 */
@Serializable
data class ZouReport(
    val header: ZouHeader,
    /** Home first, then away — the order the form prints them. */
    val lineups: List<ZouLineup>,
    val goals: List<ZouGoal>,
    val cards: ZouCards,
    val result: ZouResult?,
    val assessment: ZouAssessment,
    val confirmations: List<ZouConfirmation>,
)

@Serializable
data class ZouHeader(
    val pitch: String,
    val date: LocalDate,
    val time: LocalTime,
    /** `Liga` — the group code, e.g. `6K`. */
    val league: String,
    val homeTeam: String,
    val awayTeam: String,
    val referee: String,
    /** The `R` mark: a licensed referee hired by the delegating team. */
    val refereeLicensedHire: Boolean,
    val assistant: String?,
    val assistantLicensedHire: Boolean,
    /** `Týmy` — who delegated the referees, and who is fined for a bad report. */
    val delegatingTeam: String,
) {
    /** `31.8.2026`, as the form writes dates. */
    val dateWritten: String get() = "${date.day}.${date.month.number}.${date.year}"

    /** `19:00`. */
    val timeWritten: String get() = "${time.hour}:${time.minute.toString().padStart(TWO, '0')}"

    /** `Jiří Vlk ®` when licensed, otherwise just the name. */
    val refereeWritten: String get() = referee.withHireMark(refereeLicensedHire)

    val assistantWritten: String? get() = assistant?.withHireMark(assistantLicensedHire)
}

@Serializable
data class ZouLineup(
    val side: ZouSide,
    val teamName: String,
    /**
     * `Barva dresů`, **as it stood on the day**.
     *
     * The snapshot the lineup took, never a lookup against the team's
     * current kits: a rename must not rewrite a report already written.
     */
    val kitLabel: String,
    /**
     * In the order the paper form has them: the captain first, then the
     * goalkeeper, then everyone else by jersey number. One person who is
     * both is a single row, first. A lineup written before 0.3.0 has no
     * captain or goalkeeper and is simply in jersey order, as it always was.
     */
    val rows: List<ZouAppearance>,
    /**
     * *Podpis kapitána před zahájením utkání*: the captain's confirmation
     * under the lineup, before kickoff. Null until given, and always null on
     * a lineup that has no marked captain. **Not the signature under the
     * finished report**, which is in [ZouReport.confirmations].
     */
    val confirmation: ZouLineupConfirmation? = null,
)

@Serializable
data class ZouAppearance(
    @SerialName("jersey") val jerseyNumber: Int?,
    /**
     * The `Číslo RP` column: an RP number, or a date of birth for a player
     * without their card, exactly as recorded on the day.
     */
    val identification: String,
    /**
     * **Which of the three that value is.** The form has one column and the
     * TXT keeps it, but a number the referee copied from a card and a
     * number PSMF's own data supplied are not the same kind of evidence, and
     * a date of birth is neither.
     */
    @SerialName("identificationKind") val kind: ZouIdentificationKind,
    /** `Příjmení a jméno` — surname first, as the column is written. */
    val name: String,
    /** The one captain of this team in this match. */
    val captain: Boolean = false,
    /** The one goalkeeper. May be the same person as the captain. */
    val goalkeeper: Boolean = false,
) {
    /**
     * What the TXT writes after the name, in the form's own language:
     * `kapitán`, `brankář`, or both for one person. Empty for everyone else.
     */
    val rolesWritten: String
        get() =
            listOfNotNull(
                ZouLabels.Lineup.CAPTAIN
                    .lowercase()
                    .takeIf { captain },
                ZouLabels.Lineup.GOALKEEPER
                    .lowercase()
                    .takeIf { goalkeeper },
            ).joinToString(", ")
}

/**
 * Where the value in the `Číslo RP` column came from.
 *
 * Serialised as stable ASCII identifiers, like every other key in the JSON;
 * [written] is the Czech the CSV prints beside it.
 */
@Serializable
enum class ZouIdentificationKind(
    val written: String,
) {
    /** The number in PSMF's own league data. */
    @SerialName("rp_league")
    LEAGUE_RP(ZouLabels.Lineup.KIND_LEAGUE_RP),

    /** The number the referee copied from the player's card on the day. */
    @SerialName("rp_typed")
    TYPED_RP(ZouLabels.Lineup.KIND_TYPED_RP),

    /** The date of birth, for a player with no card: the form's printed rule. */
    @SerialName("date_of_birth")
    DATE_OF_BIRTH(ZouLabels.Lineup.KIND_DATE_OF_BIRTH),

    /** Nothing the app produces today; here so that reading any stored value cannot fail. */
    @SerialName("other")
    OTHER(ZouLabels.Lineup.KIND_OTHER),
}

@Serializable
data class ZouLineupConfirmation(
    val by: String,
    val at: Instant,
    /** Only ever true for a confirmation written before 0.3.0, which let a deputy sign. */
    val asDeputy: Boolean = false,
) {
    val byWritten: String get() = if (asDeputy) "$by ${ZouWords.DEPUTY}" else by
}

@Serializable
data class ZouGoal(
    val side: ZouSide,
    /** `Čas` — `5´`, `30´+`, `60´+`. */
    val minute: String,
    @SerialName("jersey") val jerseyNumber: Int?,
    /** `Střelec`. Null is legitimate: the worked example has `13´ — 2:1`. */
    val scorer: String?,
    /** `Stav` — the running score after this goal. */
    val scoreAfter: String,
)

/**
 * `Osobní tresty`, in the form's two blocks.
 *
 * [accountedFor] is the distinction the paper makes by requiring the boxes
 * to be struck through: an empty report is not the same as a referee
 * saying no cards were issued.
 */
@Serializable
data class ZouCards(
    val accountedFor: Boolean,
    val yellow: List<ZouCard>,
    val red: List<ZouCard>,
) {
    val noneIssued: Boolean get() = accountedFor && yellow.isEmpty() && red.isEmpty()
}

@Serializable
data class ZouCard(
    val side: ZouSide,
    val minute: String,
    @SerialName("jersey") val jerseyNumber: Int?,
    val name: String,
    /** Mandatory, and exactly what the referee wrote. */
    val reason: String,
    /**
     * For a red, which kind: **from the stored field, never from [reason]**
     * (DECISIONS 2026-10-07). Null for a yellow.
     */
    val dismissal: ZouDismissal? = null,
) {
    /**
     * What the TXT and CSV write after the name. A second-yellow red reads
     * `2. ŽK` whatever the referee typed, with their reason kept beside it
     * when they gave one other than `2. ŽK` itself; anything else reads its
     * reason, so a straight red never says `2. ŽK` unless the referee wrote
     * those words.
     */
    val written: String
        get() =
            when {
                dismissal != ZouDismissal.SECOND_YELLOW -> reason
                reason.isBlank() || reason.isJustTheSecondYellowMark() -> ZouLabels.Cards.SECOND_YELLOW
                else -> "${ZouLabels.Cards.SECOND_YELLOW} ($reason)"
            }
}

/**
 * The two kinds of red, as the report spells them. Serialised in Czech,
 * like every other value in the report; `2. ŽK` is the form's own notation.
 */
@Serializable
enum class ZouDismissal {
    @SerialName(ZouLabels.Cards.STRAIGHT_RED)
    STRAIGHT,

    @SerialName(ZouLabels.Cards.SECOND_YELLOW)
    SECOND_YELLOW,
}

/** `2. ŽK`, however it was spaced or capitalised -- what 0.1.0 pre-filled into a reason. */
private fun String.isJustTheSecondYellowMark(): Boolean =
    squeezed().equals(ZouLabels.Cards.SECOND_YELLOW.squeezed(), ignoreCase = true)

private fun String.squeezed(): String = filterNot { it.isWhitespace() }

@Serializable
data class ZouResult(
    val halfTime: String,
    val fullTime: String,
    /** `Vítěz utkání` — a team name, or the word for a draw. */
    val winner: String,
)

@Serializable
data class ZouAssessment(
    val home: ZouTeamAssessment,
    val away: ZouTeamAssessment,
    /** `Komentář` — mandatory on the form. */
    val commentary: String,
)

@Serializable
data class ZouTeamAssessment(
    /** `NH` — best player, by jersey number. */
    @SerialName("nh") val bestPlayer: Int?,
    /** `Čd` — waiting time in minutes. Zero is normal, not unassessed. */
    @SerialName("cd") val waitingTimeMinutes: Int,
    /** `Č` — shirts properly numbered. Null means not assessed. */
    @SerialName("c") val shirtsProperlyNumbered: Boolean?,
    /** `B` — uniform kit colour. Null means not assessed. */
    @SerialName("b") val uniformKitColour: Boolean?,
)

@Serializable
data class ZouConfirmation(
    /** `Podpis kapitána` / `Podpis rozhodčího`. */
    val party: String,
    val by: String,
    val at: Instant,
    /** Captaincy may be delegated: the example has `Lepiš (zást.)`. */
    val asDeputy: Boolean,
) {
    val byWritten: String get() = if (asDeputy) "$by ${ZouWords.DEPUTY}" else by
}

/** `D` and `H` on the form. */
@Serializable
enum class ZouSide {
    @SerialName("D")
    HOME,

    @SerialName("H")
    AWAY,
    ;

    val mark: String get() = if (this == HOME) ZouLabels.Assessment.HOME else ZouLabels.Assessment.AWAY
}

fun TeamSide.asZouSide(): ZouSide = if (this == TeamSide.HOME) ZouSide.HOME else ZouSide.AWAY

/**
 * The handful of Czech words the report needs that are not field labels.
 *
 * Here rather than in `composeResources` for the same reason [ZouLabels]
 * is: the report is Czech whatever language the app is in, and a localised
 * resource would make that silently untrue.
 */
object ZouWords {
    const val YES = "ano"
    const val NO = "ne"

    /** For a value the referee has not given. */
    const val NOT_GIVEN = "—"
    const val DRAW = "remíza"
    const val DEPUTY = "(zást.)"
    const val HOME_CAPTAIN = "Podpis kapitána (D)"
    const val AWAY_CAPTAIN = "Podpis kapitána (H)"
    const val REFEREE = "Podpis rozhodčího"
    const val MINUTES = "min"

    /** The report's own title. */
    const val TITLE = "ZÁPIS O UTKÁNÍ"

    fun of(value: Boolean?): String =
        when (value) {
            true -> YES
            false -> NO
            null -> NOT_GIVEN
        }
}

private const val TWO = 2

private fun String.withHireMark(licensed: Boolean): String =
    if (licensed) "$this ${ZouLabels.Header.LICENSED_HIRE_MARK}" else this
