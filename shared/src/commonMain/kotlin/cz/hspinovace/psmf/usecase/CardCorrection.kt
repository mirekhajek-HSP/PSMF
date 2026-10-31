package cz.hspinovace.psmf.usecase

import cz.hspinovace.psmf.data.match.MatchRepository
import cz.hspinovace.psmf.domain.CardEvent
import cz.hspinovace.psmf.domain.CardReason
import cz.hspinovace.psmf.domain.CardSubject
import cz.hspinovace.psmf.domain.CardsSection
import cz.hspinovace.psmf.domain.Dismissal
import cz.hspinovace.psmf.domain.Match
import cz.hspinovace.psmf.domain.Minute
import cz.hspinovace.psmf.domain.PowerPlay
import cz.hspinovace.psmf.domain.RedCard
import cz.hspinovace.psmf.domain.YellowCard
import cz.hspinovace.psmf.domain.afterAChangeFrom
import kotlin.time.Instant

// A card already in the log, corrected or removed (0.4.0, Gate 6; the testers' point 5,
// analysis section 2.12), as a goal is in GoalCorrection.kt.
//
// **A card is addressed by its place in the recording order** ([CardEvent.sequence]), and
// what one action recorded is one card here: a second yellow's yellow and red share a
// number, so they are corrected or removed whole, and a red's power play carries the same
// number and follows it. A card from before 0.2.0 has none and is never touched here; Undo
// still takes it back (DECISIONS 2026-10-31, Gate 6). **This is not versioning.**

/**
 * The player, the reason and the minute of a card already recorded, changed in place.
 *
 * **Refused** -- the match comes back as it was, and nothing is saved -- when no card has
 * that [sequence], and when [subject] may not have it ([mayBeGivenTo]): another player of
 * the same team with no card in this match, or, for someone not in the lineup, another
 * name; never between the two, and never for a second yellow or a first yellow a second
 * yellow stands on. The colour, and a red's kind, are not changed here: a card of the
 * wrong colour is removed and recorded again. The minute follows the card sheet's own
 * rules, which the type already holds.
 *
 * **A red's power play follows a corrected minute**, placed as [LogCard] places a written
 * one: from the start of that minute on the clock, never later than [at]. A red moved to
 * `60´+` takes it away, as [LogCard] would start none; one that had none gets one only
 * where [LogCard] would start one now. The card keeps its place in the recording order.
 * A correction that changes nothing saves nothing.
 */
class CorrectCard(
    private val matches: MatchRepository,
) {
    suspend operator fun invoke(
        match: Match,
        sequence: Int,
        subject: CardSubject,
        reason: CardReason,
        minute: Minute,
        at: Instant,
        halfLengthMinutes: Int = Minute.HALF_LENGTH,
    ): Match {
        val action = match.cardEvents.filter { it.sequence == sequence }
        if (action.isEmpty() || !match.cardEvents.mayBeGivenTo(sequence, subject, match)) return match
        if (match.cardCorrectionChangesNothing(sequence, subject, reason, minute)) return match

        val corrected =
            match.cardEvents.map { if (it.sequence == sequence) it.correctedTo(subject, reason, minute) else it }
        val red = corrected.filterIsInstance<RedCard>().firstOrNull { it.sequence == sequence }
        val updated =
            match
                .copy(
                    cards = CardsSection.Issued(corrected),
                    powerPlays = match.powerPlaysAfter(red, action.first().minute, at, halfLengthMinutes, sequence),
                ).afterAChangeFrom(match)
        matches.save(updated)
        return updated
    }
}

/**
 * A card already recorded, removed: both cards of a second yellow, and the power play a red
 * began. **Refused** for a player's first yellow while their `2. ŽK` stands, which would
 * leave a second yellow without a first ([secondYellowStandingOn]). Removing the last card
 * leaves the block unaccounted for, as Undo does, never "no cards": that is the referee's
 * to say. Taken back with the signatures on a signed report, as any change to a card is.
 */
class RemoveCard(
    private val matches: MatchRepository,
) {
    suspend operator fun invoke(
        match: Match,
        sequence: Int,
    ): Match {
        if (match.cardEvents.none { it.sequence == sequence }) return match
        if (match.cardEvents.secondYellowStandingOn(sequence) != null) return match
        val updated =
            match
                .copy(
                    cards = cardsSectionOf(match.cardEvents.filterNot { it.sequence == sequence }),
                    powerPlays = match.powerPlays.filterNot { it.sequence == sequence },
                ).afterAChangeFrom(match)
        matches.save(updated)
        return updated
    }
}

/**
 * The ways an event already in the log changes, for the console, which takes them together:
 * it already takes a use case for each other thing it records (DECISIONS 2026-10-31, Gate 5,
 * call 10, and Gate 6).
 */
class LogCorrections(
    val correctGoal: CorrectGoal,
    val removeGoal: RemoveGoal,
    val correctCard: CorrectCard,
    val removeCard: RemoveCard,
)

/**
 * The `2. ŽK` that stands on the yellow recorded as [sequence], or null: that yellow is the
 * player's first, recorded on its own, and the same player has a red for a second yellow
 * recorded since. Taking it away, or giving it to someone else, would leave that second
 * yellow without a first.
 */
fun List<CardEvent>.secondYellowStandingOn(sequence: Int): RedCard? {
    // A second yellow's own yellow shares its number with the red: not a single card.
    val yellow = singleOrNull { it.sequence == sequence } as? YellowCard ?: return null
    return filterIsInstance<RedCard>().firstOrNull {
        it.dismissal == Dismissal.SECOND_YELLOW && it.subject == yellow.subject && it.sequence != sequence
    }
}

/**
 * Whether the card recorded as [sequence] keeps its player: a second yellow is one action
 * against one player, and a first yellow a second stands on belongs to the same player.
 */
fun List<CardEvent>.keepsItsPlayer(sequence: Int): Boolean =
    count { it.sequence == sequence } > 1 || secondYellowStandingOn(sequence) != null

/**
 * Whether the card recorded as [sequence] may be given to [subject]: whoever it names
 * already; or, unless it keeps its player, another player of the same team's lineup with
 * no card in this match; or, for someone not in the lineup, another name.
 */
fun List<CardEvent>.mayBeGivenTo(
    sequence: Int,
    subject: CardSubject,
    match: Match,
): Boolean {
    val card = firstOrNull { it.sequence == sequence } ?: return false
    if (subject == card.subject) return true
    if (keepsItsPlayer(sequence)) return false
    return when (subject) {
        is CardSubject.NamedPerson -> {
            card.subject is CardSubject.NamedPerson
        }

        is CardSubject.Player -> {
            card.subject is CardSubject.Player &&
                match
                    .lineup(card.side)
                    ?.appearances
                    .orEmpty()
                    .any { it.id == subject.appearance } &&
                none { it.subject == subject }
        }
    }
}

/**
 * A correction that would leave the card recorded as [sequence] as it is, or of a card no
 * longer there: nothing to save, and nothing to ask a signed report about.
 */
fun Match.cardCorrectionChangesNothing(
    sequence: Int,
    subject: CardSubject,
    reason: CardReason,
    minute: Minute,
): Boolean =
    cardEvents
        .filter { it.sequence == sequence }
        .all { it.subject == subject && it.reason == reason && it.minute == minute }

private fun CardEvent.correctedTo(
    subject: CardSubject,
    reason: CardReason,
    minute: Minute,
): CardEvent =
    when (this) {
        is YellowCard -> copy(subject = subject, reason = reason, minute = minute)
        is RedCard -> copy(subject = subject, reason = reason, minute = minute)
    }

/**
 * The power plays once the red recorded as [sequence] is at its corrected minute. Untouched
 * unless the minute moved: a new reason or player changes nothing about who is short.
 */
private fun Match.powerPlaysAfter(
    red: RedCard?,
    before: Minute,
    at: Instant,
    halfLengthMinutes: Int,
    sequence: Int,
): List<PowerPlay> {
    if (red == null || red.minute == before) return powerPlays
    val started = powerPlays.firstOrNull { it.sequence == sequence }
    val moved =
        when {
            started == null -> {
                powerPlayFor(red, at, halfLengthMinutes, sequence)
            }

            red.minute == Minute.AfterFinalWhistle -> {
                null
            }

            else -> {
                started.copy(
                    startedAt = powerPlayStart(red.minute, at, halfLengthMinutes),
                    dismissedAtMinute = red.minute,
                )
            }
        }
    return powerPlays.filterNot { it.sequence == sequence } + listOfNotNull(moved)
}
