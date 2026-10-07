package cz.hspinovace.psmf.usecase

import cz.hspinovace.psmf.data.match.MatchRepository
import cz.hspinovace.psmf.domain.AppearanceId
import cz.hspinovace.psmf.domain.CardEvent
import cz.hspinovace.psmf.domain.CardReason
import cz.hspinovace.psmf.domain.CardSubject
import cz.hspinovace.psmf.domain.CardsSection
import cz.hspinovace.psmf.domain.Dismissal
import cz.hspinovace.psmf.domain.GoalEvent
import cz.hspinovace.psmf.domain.Match
import cz.hspinovace.psmf.domain.MatchStatus
import cz.hspinovace.psmf.domain.Minute
import cz.hspinovace.psmf.domain.PeriodBreak
import cz.hspinovace.psmf.domain.PersonName
import cz.hspinovace.psmf.domain.PowerPlay
import cz.hspinovace.psmf.domain.RedCard
import cz.hspinovace.psmf.domain.Score
import cz.hspinovace.psmf.domain.TeamSide
import cz.hspinovace.psmf.domain.YellowCard
import cz.hspinovace.psmf.domain.inPeriodInterval
import cz.hspinovace.psmf.domain.isPastTheFinalWhistle
import kotlin.time.Instant

/**
 * The whistle. Stores the instant the whole clock is derived from.
 *
 * The only other instants ever recorded are the period boundaries
 * ([EndPeriod], [StartNextPeriod]), because there is no stoppage during
 * play: inside a period the clock runs continuously and the referee adds
 * time rather than stopping it. A paused-at or accumulated-time field would
 * be the first step towards a clock that can drift, be killed, or disagree
 * with itself.
 */
class StartMatch(
    private val matches: MatchRepository,
) {
    suspend operator fun invoke(
        match: Match,
        at: Instant,
    ): Match {
        if (match.kickoffAt != null) return match
        val started = match.copy(kickoffAt = at, status = MatchStatus.IN_PROGRESS)
        matches.save(started)
        return started
    }
}

/** The final whistle. Cards may still be issued afterwards, at `60´+`. */
class FinishMatch(
    private val matches: MatchRepository,
) {
    suspend operator fun invoke(match: Match): Match {
        val finished = match.copy(status = MatchStatus.FINISHED)
        matches.save(finished)
        return finished
    }
}

/**
 * The whistle for the end of a period -- not the whole match.
 *
 * A half-time exists (analysis section 2.6: 2 x 30 with a break between
 * them), and this is the only new thing Phase 1 adds to the clock: one
 * more instant, persisted, so the interval can be told apart from play
 * without anything ticking through it. There is still no pause, stop,
 * resume or adjust operation -- ending a period is a fact about the match,
 * recorded once, exactly like the whistle [StartMatch] stores.
 */
class EndPeriod(
    private val matches: MatchRepository,
) {
    suspend operator fun invoke(
        match: Match,
        at: Instant,
    ): Match {
        if (match.kickoffAt == null) return match
        if (match.inPeriodInterval) return match
        val updated = match.copy(periodBreaks = match.periodBreaks + PeriodBreak(endedAt = at))
        matches.save(updated)
        return updated
    }
}

/**
 * The whistle to resume play.
 *
 * Nothing ticked in the interval; this only records when it ended. The
 * minute then restarts at the half length -- the second half kicks off at
 * `30´` whatever the first added, which `30´+` has already absorbed
 * (DECISIONS 2026-10-07, see [cz.hspinovace.psmf.domain.PlayClock.minuteAt]) --
 * while play keeps counting on from where the break began, which is what a
 * power play carried over the interval needs.
 */
class StartNextPeriod(
    private val matches: MatchRepository,
) {
    suspend operator fun invoke(
        match: Match,
        at: Instant,
    ): Match {
        val open = match.periodBreaks.lastOrNull()?.takeIf { it.nextStartedAt == null } ?: return match
        val updated =
            match.copy(
                periodBreaks = match.periodBreaks.dropLast(1) + open.copy(nextStartedAt = at),
            )
        matches.save(updated)
        return updated
    }
}

/**
 * One row of the `Góly` block.
 *
 * **[scorer] is nullable and that is not an oversight.** The worked
 * example contains `13´ — 2:1`: a time, a resulting score and no scorer.
 * Own goals and unattributed goals both land there, and a console that
 * demanded a scorer could not record a match the paper form handles
 * without difficulty.
 */
class LogGoal(
    private val matches: MatchRepository,
) {
    suspend operator fun invoke(
        match: Match,
        side: TeamSide,
        scorer: AppearanceId?,
        minute: Minute,
    ): Match {
        val goal =
            GoalEvent(
                minute = minute,
                side = side,
                scorer = scorer,
                scoreAfter = Score.GOALLESS,
                sequence = match.nextSequence(),
            )
        val updated = match.copy(goals = (match.goals + goal).rescored())
        matches.save(updated)
        return updated
    }
}

/**
 * `Stav` is the score *after* each goal, so it has to be recomputed
 * whenever the list changes — including when the last one is undone.
 * Storing it and letting it go stale would put a wrong running score on
 * the report while the totals still added up.
 */
internal fun List<GoalEvent>.rescored(): List<GoalEvent> {
    var running = Score.GOALLESS
    return map { goal ->
        running = running.scoredBy(goal.side)
        goal.copy(scoreAfter = running)
    }
}

/** Yellow or red. The domain models them as separate types; a form needs a choice. */
enum class CardColour {
    YELLOW,
    RED,
}

/**
 * A card being written, before it is one.
 *
 * Every field the `Osobní tresty` block requires — *time, number, name and
 * reason* — plus the straight-versus-second-yellow distinction, which is
 * not cosmetic: yellows accumulate per group per season and two in one
 * match contribute zero, so a red has to say which kind it was.
 *
 * **Whether the player is already booked is a fact about the match, not
 * about the draft**, so the functions that depend on it take it as
 * `booked`: true when the player this card is for already has a yellow in
 * this match. [LogCard] reads it from the match it records into; the
 * console reads it from the row it opened the card from.
 */
data class CardDraft(
    val side: TeamSide,
    /** The appearance the card was raised against, or null for [namedPerson]. */
    val appearance: AppearanceId? = null,
    /**
     * Someone with no jersey number on the sheet.
     *
     * The worked example has `30´+ Lepiš A. - nesp. chování`, shown to a
     * deputy captain. A console that could only card an appearance could
     * not record it.
     */
    val namedPerson: String = "",
    val colour: CardColour = CardColour.YELLOW,
    val dismissal: Dismissal? = null,
    val reason: String = "",
    val minute: MinuteDraft = MinuteDraft(),
) {
    val isRed: Boolean get() = colour == CardColour.RED

    fun subject(): CardSubject? =
        when {
            appearance != null -> CardSubject.Player(appearance)
            else -> PersonName.orNull(namedPerson)?.let { CardSubject.NamedPerson(it) }
        }

    /**
     * Whether a red may be `2. ŽK` here: for a player already booked in this
     * match, or for someone not in the lineup -- who gets no automatic
     * second yellow, so the referee says which kind it was. For any other
     * player a red can only be straight.
     */
    fun offersSecondYellowKind(booked: Boolean): Boolean = appearance == null || booked

    /** The kind of red this records, or null for a yellow or a kind not yet chosen. */
    fun dismissalKind(booked: Boolean): Dismissal? =
        when {
            !isRed -> null
            !offersSecondYellowKind(booked) -> Dismissal.STRAIGHT
            else -> dismissal
        }

    /**
     * **Saving this sends a booked player off for a second yellow**: the
     * yellow and the red it becomes, recorded together as one action. True
     * for a yellow to a booked player, and for a red marked `2. ŽK` to one.
     * Never for someone not in the lineup.
     */
    fun sendsOffForSecondYellow(booked: Boolean): Boolean =
        appearance != null && booked && (!isRed || dismissal == Dismissal.SECOND_YELLOW)

    fun problems(booked: Boolean = false): List<CardProblem> =
        buildList {
            if (subject() == null) add(CardProblem.NO_SUBJECT)
            if (reason.isBlank()) add(CardProblem.NO_REASON)
            if (isRed && dismissalKind(booked) == null) add(CardProblem.NO_DISMISSAL_KIND)
            if (minute.toMinute() == null) add(CardProblem.NO_MINUTE)
        }

    fun toCard(booked: Boolean = false): CardEvent? {
        if (problems(booked).isNotEmpty()) return null
        val subject = subject() ?: return null
        val at = minute.toMinute() ?: return null
        return if (isRed) {
            RedCard(at, side, subject, CardReason(reason.trim()), requireNotNull(dismissalKind(booked)))
        } else {
            YellowCard(at, side, subject, CardReason(reason.trim()))
        }
    }
}

enum class CardProblem {
    NO_SUBJECT,

    /** Mandatory on every card, yellow or red (analysis section 2.5). */
    NO_REASON,

    /** A red must say whether it was straight or a second yellow. */
    NO_DISMISSAL_KIND,
    NO_MINUTE,
}

/** Which of the form's three kinds of minute is being written. */
enum class MinuteMark {
    PLAYED,

    /** `30´+` — issued during the half-time interval. */
    HALF_TIME,

    /** `60´+` — after the final whistle, before the captains sign. */
    AFTER_FINAL_WHISTLE,
}

/**
 * A minute being typed.
 *
 * The two marked values are not decoration: the form requires cards issued
 * at half-time to be timed `30´+` and those issued after the whistle
 * `60´+`, and no integer can hold either.
 */
data class MinuteDraft(
    val played: String = "",
    val mark: MinuteMark = MinuteMark.PLAYED,
) {
    fun toMinute(): Minute? =
        when (mark) {
            MinuteMark.HALF_TIME -> {
                Minute.HalfTime
            }

            MinuteMark.AFTER_FINAL_WHISTLE -> {
                Minute.AfterFinalWhistle
            }

            MinuteMark.PLAYED -> {
                played
                    .trim()
                    .toIntOrNull()
                    ?.takeIf { it >= 0 }
                    ?.let { Minute.Played(it) }
            }
        }

    companion object {
        fun of(minute: Minute?): MinuteDraft =
            when (minute) {
                is Minute.Played -> MinuteDraft(minute.value.toString(), MinuteMark.PLAYED)
                Minute.HalfTime -> MinuteDraft(mark = MinuteMark.HALF_TIME)
                Minute.AfterFinalWhistle -> MinuteDraft(mark = MinuteMark.AFTER_FINAL_WHISTLE)
                null -> MinuteDraft()
            }
    }
}

/**
 * Writes a card, and starts a power play if it sent a player off.
 *
 * **A second yellow is one action that records two cards** (DECISIONS
 * 2026-10-07). A yellow for a player already booked in this match -- or a
 * red marked `2. ŽK` for one -- records the second [YellowCard] *and* a
 * [RedCard] of kind [Dismissal.SECOND_YELLOW], at the same minute, with
 * one [Match.nextSequence] between them, so one Undo takes both back. No
 * referee makes three entries for what is one card on the pitch, and
 * nothing else leaves a player holding two yellows and no dismissal. None
 * of this applies to someone not in the lineup (`NamedPerson`), whose
 * cards stay exactly as the referee writes them.
 *
 * **The power play is ten minutes of play**, and a second dismissal starts
 * a second, independent one rather than extending the first (analysis
 * section 2.6). None starts after the final whistle -- the match `FINISHED`
 * or the card written `60´+` -- and none for a `NamedPerson`, who is not on
 * the pitch to be replaced.
 *
 * Where it starts: from [at], the moment of saving, when the card carries
 * the minute the clock shows; from the start of the written minute when
 * the referee wrote an earlier one, because the side has been short since
 * then. A written minute is placed on the clock period by period
 * ([cz.hspinovace.psmf.domain.PlayClock.instantOfMinute]), so a first-half red written up in the
 * second half still skips the interval. Never later than [at].
 * [halfLengthMinutes] is the group's, the same number the console's own
 * minute is built from.
 */
class LogCard(
    private val matches: MatchRepository,
) {
    suspend operator fun invoke(
        match: Match,
        draft: CardDraft,
        at: Instant,
        halfLengthMinutes: Int = Minute.HALF_LENGTH,
    ): Match? {
        val subject = draft.subject()
        val booked = subject is CardSubject.Player && match.isBookedWithoutDismissal(subject)
        val card = draft.toCard(booked) ?: return null
        val sequence = match.nextSequence()

        val recorded =
            if (draft.sendsOffForSecondYellow(booked)) {
                listOf(
                    YellowCard(card.minute, card.side, card.subject, card.reason, sequence),
                    RedCard(card.minute, card.side, card.subject, card.reason, Dismissal.SECOND_YELLOW, sequence),
                )
            } else {
                listOf(card.recordedAs(sequence))
            }
        val powerPlay =
            recorded
                .filterIsInstance<RedCard>()
                .firstOrNull()
                ?.let { match.powerPlayFor(it, at, halfLengthMinutes, sequence) }

        val updated =
            match.copy(
                cards = CardsSection.Issued(match.cardEvents + recorded),
                powerPlays = match.powerPlays + listOfNotNull(powerPlay),
            )
        matches.save(updated)
        return updated
    }
}

/** A yellow in this match and no red yet: the next yellow is a dismissal. */
private fun Match.isBookedWithoutDismissal(subject: CardSubject.Player): Boolean =
    cardEvents.any { it is YellowCard && it.subject == subject } &&
        cardEvents.none { it is RedCard && it.subject == subject }

private fun CardEvent.recordedAs(sequence: Int): CardEvent =
    when (this) {
        is YellowCard -> copy(sequence = sequence)
        is RedCard -> copy(sequence = sequence)
    }

private fun Match.powerPlayFor(
    card: RedCard,
    at: Instant,
    halfLengthMinutes: Int,
    sequence: Int,
): PowerPlay? {
    if (card.subject !is CardSubject.Player) return null
    if (status.isPastTheFinalWhistle || card.minute == Minute.AfterFinalWhistle) return null
    val asItHappened = clock.minuteAt(at, status, halfLengthMinutes) == card.minute
    val written = if (asItHappened) null else clock.instantOfMinute(card.minute, halfLengthMinutes)
    return PowerPlay(
        shortHandedSide = card.side,
        startedAt = written?.let { minOf(it, at) } ?: at,
        dismissedAtMinute = card.minute,
        sequence = sequence,
    )
}

/**
 * Takes back the last thing recorded.
 *
 * **By recording order, not by minute** (DECISIONS 2026-10-07): a card at
 * 20´ then a goal at 20´ takes back the goal; a card typed in as 22´ after
 * a goal at 25´ takes back the card. Everything one action recorded goes
 * together -- a second yellow's yellow and red, and the power play a
 * dismissal began -- because they share a [MatchEvent.sequence].
 *
 * Events recorded before 0.2.0 carry no sequence. They are older than
 * anything that does, and among themselves the last is the end of the
 * timeline, exactly as Undo chose before.
 *
 * This is undo, not editing. Amending a finished report is screen 9 and is
 * out of the demo (DEMO_SCOPE).
 */
class UndoLastEvent(
    private val matches: MatchRepository,
) {
    suspend operator fun invoke(match: Match): Match {
        val updated = match.withoutLastAction() ?: return match
        matches.save(updated)
        return updated
    }
}

private fun Match.withoutLastAction(): Match? {
    val action = (goals + cardEvents).mapNotNull { it.sequence }.maxOrNull() ?: return withoutLastUnnumbered()
    return copy(
        goals = goals.filterNot { it.sequence == action }.rescored(),
        // Untouched unless a card goes: undoing a goal must not turn an
        // affirmed "no cards" back into a block nobody has accounted for.
        cards =
            if (cardEvents.any { it.sequence == action }) {
                cardsSectionOf(
                    cardEvents.filterNot {
                        it.sequence ==
                            action
                    },
                )
            } else {
                cards
            },
        powerPlays = powerPlays.filterNot { it.sequence == action },
    )
}

/** Before 0.2.0 no order was kept: the end of the timeline, as Undo did then. */
private fun Match.withoutLastUnnumbered(): Match? =
    when (val last = timeline().lastOrNull()) {
        null -> {
            null
        }

        is GoalEvent -> {
            copy(goals = goals.minusLast(last).rescored())
        }

        is CardEvent -> {
            copy(
                cards = cardsSectionOf(cardEvents.minusLast(last)),
                powerPlays = withoutUnnumberedPowerPlayFor(last),
            )
        }
    }

/**
 * Back to null rather than NoneIssued when nothing is left: taking a card
 * back does not amount to the referee affirming that none were issued.
 */
private fun cardsSectionOf(remaining: List<CardEvent>): CardsSection? =
    if (remaining.isEmpty()) null else CardsSection.Issued(remaining)

private fun Match.withoutUnnumberedPowerPlayFor(card: CardEvent): List<PowerPlay> {
    if (card !is RedCard) return powerPlays
    val started =
        powerPlays.lastOrNull {
            it.sequence == null && it.shortHandedSide == card.side && it.dismissedAtMinute == card.minute
        } ?: return powerPlays
    return powerPlays.minusLast(started)
}

/** Removes the last occurrence, so identical events do not all disappear. */
private fun <T> List<T>.minusLast(item: T): List<T> {
    val index = lastIndexOf(item)
    return if (index < 0) this else take(index) + drop(index + 1)
}
