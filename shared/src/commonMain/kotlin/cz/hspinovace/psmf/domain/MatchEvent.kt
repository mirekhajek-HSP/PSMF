package cz.hspinovace.psmf.domain

import kotlinx.serialization.Serializable
import kotlin.jvm.JvmInline

/**
 * Anything the referee records against a minute of the match.
 *
 * The form keeps goals and personal punishments in **separate blocks**, so
 * [Match] does too. This interface exists for the live console, which shows
 * one merged timeline; see [Match.timeline].
 */
@Serializable
sealed interface MatchEvent {
    val minute: Minute
    val side: TeamSide
}

/**
 * A goal, as one row of the `Góly` block: `Čas | Číslo | Střelec | Stav`.
 */
@Serializable
data class GoalEvent(
    override val minute: Minute,
    override val side: TeamSide,
    /**
     * **Nullable, and legitimately so.** The worked example in analysis
     * section 2.5 contains `13´ — 2:1`: a goal with a time and a resulting
     * score but no scorer. Own goals and unattributed goals both land here.
     * Requiring a scorer would make the app unable to record a match the
     * paper form handles without difficulty.
     */
    val scorer: AppearanceId?,
    /** `Stav` — the score *after* this goal. */
    val scoreAfter: Score,
) : MatchEvent

/**
 * The reason written next to a card. Mandatory on every card, yellow or red.
 *
 * The form is emphatic that a red card reason must be unambiguous:
 * *"Zmaření vyložené šance soupeře" není relevantní důvod, protože mohlo
 * být provedeno čistě!* Whether a reason is *good* cannot be checked in
 * code; that it is *present* can, and is.
 */
@Serializable
@JvmInline
value class CardReason(
    val text: String,
) {
    init {
        require(text.isNotBlank()) {
            "Every card carries a mandatory free-text reason (analysis section 2.5)."
        }
    }

    override fun toString(): String = text
}

/**
 * Who a card was shown to.
 *
 * Usually a player in the lineup, identified by their appearance so that
 * the jersey number and name both follow from one reference. But **not
 * always**: the worked example carries `30´+ Lepiš A. - nesp. chování`,
 * a card with a name and no number, shown to a deputy captain. Modelling
 * the subject as a mandatory appearance would make that unrecordable.
 */
@Serializable
sealed interface CardSubject {
    @Serializable
    data class Player(
        val appearance: AppearanceId,
    ) : CardSubject

    /** Someone with no jersey number on the sheet. */
    @Serializable
    data class NamedPerson(
        val name: PersonName,
    ) : CardSubject
}

/** Why a player was sent off. The distinction is not cosmetic. */
@Serializable
enum class Dismissal {
    /** A red card in its own right. */
    STRAIGHT,

    /**
     * Written on the form literally as `2. ŽK`.
     *
     * This must be recorded separately from a straight red because
     * suspension arithmetic depends on it (analysis section 2.6): yellow
     * cards accumulate per group per season and trigger an automatic ban on
     * even totals, but **two yellows in one match contribute zero to that
     * total**. A straight red and a second-yellow red look identical on the
     * pitch and have different consequences afterwards.
     */
    SECOND_YELLOW,
}

/** A personal punishment: one row of the `Osobní tresty` block. */
@Serializable
sealed interface CardEvent : MatchEvent {
    val subject: CardSubject
    val reason: CardReason
}

@Serializable
data class YellowCard(
    override val minute: Minute,
    override val side: TeamSide,
    override val subject: CardSubject,
    override val reason: CardReason,
) : CardEvent

@Serializable
data class RedCard(
    override val minute: Minute,
    override val side: TeamSide,
    override val subject: CardSubject,
    override val reason: CardReason,
    val dismissal: Dismissal,
) : CardEvent

/**
 * The state of the `Osobní tresty` block.
 *
 * **An empty list is not "no cards".** The form requires the referee to
 * strike the boxes through when nothing was issued (`políčka proškrtne`),
 * which makes "none" an affirmation the referee makes, distinct from a
 * report where the block has simply not been filled in yet. A
 * `List<CardEvent>` alone cannot tell those two apart, so this type does:
 * [Issued] cannot be empty, and [NoneIssued] is something the referee has
 * actively said.
 */
@Serializable
sealed interface CardsSection {
    /** The referee struck the boxes through: nothing was issued. */
    @Serializable
    data object NoneIssued : CardsSection

    @Serializable
    data class Issued(
        val cards: List<CardEvent>,
    ) : CardsSection {
        init {
            require(cards.isNotEmpty()) {
                "Use NoneIssued to affirm that no cards were issued; " +
                    "an empty Issued list is the ambiguity this type exists to prevent."
            }
        }
    }

    companion object {
        /**
         * Builds the section from a list, choosing [NoneIssued] for an empty
         * one. Only for places where the referee has genuinely accounted for
         * the block — never as a default.
         */
        fun of(cards: List<CardEvent>): CardsSection = if (cards.isEmpty()) NoneIssued else Issued(cards)
    }
}

/** The cards actually in the section, empty for [CardsSection.NoneIssued]. */
fun CardsSection.cards(): List<CardEvent> =
    when (this) {
        is CardsSection.NoneIssued -> emptyList()
        is CardsSection.Issued -> cards
    }
