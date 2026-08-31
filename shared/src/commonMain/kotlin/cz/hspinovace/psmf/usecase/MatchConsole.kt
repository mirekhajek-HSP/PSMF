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
import cz.hspinovace.psmf.domain.cards
import cz.hspinovace.psmf.domain.inPeriodInterval
import kotlin.time.Instant

/**
 * The whistle. Stores the one instant the whole clock is derived from.
 *
 * Nothing else about time is recorded, because there is nothing else: the
 * match clock runs continuously and the referee adds time rather than
 * stopping it. A paused-at or accumulated-time field would be the first
 * step towards a clock that can drift, be killed, or disagree with itself.
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
 * Nothing ticked in the interval; this only records when it ended, which
 * is what lets the minute pick up from where the break began rather than
 * from zero -- 2 x 30 is gross time, and the second period continues the
 * same sixty minutes rather than starting a new count.
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
        val goal = GoalEvent(minute = minute, side = side, scorer = scorer, scoreAfter = Score.GOALLESS)
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

    fun problems(): List<CardProblem> =
        buildList {
            if (subject() == null) add(CardProblem.NO_SUBJECT)
            if (reason.isBlank()) add(CardProblem.NO_REASON)
            if (isRed && dismissal == null) add(CardProblem.NO_DISMISSAL_KIND)
            if (minute.toMinute() == null) add(CardProblem.NO_MINUTE)
        }

    fun toCard(): CardEvent? {
        if (problems().isNotEmpty()) return null
        val subject = subject() ?: return null
        val at = minute.toMinute() ?: return null
        return if (isRed) {
            RedCard(at, side, subject, CardReason(reason.trim()), requireNotNull(dismissal))
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
 * Writes a card, and starts a power play if it was a dismissal.
 *
 * **Ten minutes, and a second dismissal starts a second, independent
 * period rather than extending the first** (analysis section 2.6). It is
 * not shortened by a goal either — see [PowerPlay], which stores only the
 * instant it began, because nothing else can change it.
 */
class LogCard(
    private val matches: MatchRepository,
) {
    suspend operator fun invoke(
        match: Match,
        draft: CardDraft,
        at: Instant,
    ): Match? {
        val card = draft.toCard() ?: return null

        val updated =
            match.copy(
                cards = CardsSection.Issued(match.cardEvents + card),
                powerPlays =
                    if (card is RedCard) {
                        match.powerPlays +
                            PowerPlay(
                                shortHandedSide = card.side,
                                startedAt = at,
                                dismissedAtMinute = card.minute,
                            )
                    } else {
                        match.powerPlays
                    },
            )
        matches.save(updated)
        return updated
    }
}

/**
 * Takes back the last thing recorded.
 *
 * "Last" is the end of the merged timeline rather than the last item of
 * either list, because the referee thinks in one sequence of events and
 * not in the form's two blocks. Undoing a dismissal also takes back the
 * power play it began; nothing else could have started one.
 *
 * This is undo, not editing. Amending a finished report is screen 9 and is
 * out of the demo (DEMO_SCOPE).
 */
class UndoLastEvent(
    private val matches: MatchRepository,
) {
    suspend operator fun invoke(match: Match): Match {
        val last = match.timeline().lastOrNull() ?: return match

        val updated =
            when (last) {
                is GoalEvent -> {
                    match.copy(goals = match.goals.minusLast(last).rescored())
                }

                is CardEvent -> {
                    match.copy(
                        cards = withoutCard(match, last),
                        powerPlays = withoutPowerPlayFor(match, last),
                    )
                }
            }
        matches.save(updated)
        return updated
    }

    private fun withoutCard(
        match: Match,
        card: CardEvent,
    ): CardsSection? {
        val remaining =
            match.cards
                ?.cards()
                .orEmpty()
                .minusLast(card)
        // Back to null rather than NoneIssued: taking a card back does not
        // amount to the referee affirming that none were issued.
        return if (remaining.isEmpty()) null else CardsSection.Issued(remaining)
    }

    private fun withoutPowerPlayFor(
        match: Match,
        card: CardEvent,
    ): List<PowerPlay> {
        if (card !is RedCard) return match.powerPlays
        val started =
            match.powerPlays.lastOrNull {
                it.shortHandedSide == card.side && it.dismissedAtMinute == card.minute
            } ?: return match.powerPlays
        return match.powerPlays.minusLast(started)
    }
}

/** Removes the last occurrence, so identical events do not all disappear. */
private fun <T> List<T>.minusLast(item: T): List<T> {
    val index = lastIndexOf(item)
    return if (index < 0) this else take(index) + drop(index + 1)
}
