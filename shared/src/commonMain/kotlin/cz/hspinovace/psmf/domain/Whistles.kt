package cz.hspinovace.psmf.domain

// Taking a whistle back (prompt 13, part 3; DECISIONS 2026-10-17).
//
// A whistle is one more thing the referee records, so it has a place in the
// same recording order that goals and cards have (`MatchEvent.sequence`), and
// Undo takes back the LAST thing recorded, whichever kind it is.
//
// THIS IS NOT A PAUSE, A STOP, A RESUME OR AN ADJUST. Nothing here edits an
// instant. Taking a boundary back removes it from the instants the clock is
// worked out from, and what is left says what the clock is: end the first
// half by mistake, take it back, and play simply went on -- there is no gap,
// because the clock never knew of one.

/** The four whistles a match has. */
enum class Whistle {
    KICKOFF,
    END_OF_PERIOD,
    START_OF_PERIOD,
    FINAL,
}

/** What Undo would take back next. */
sealed interface UndoStep {
    /** A goal or a card: taken back at once, as it always was. */
    data object Event : UndoStep

    /**
     * A whistle. It changes the clock, so the console asks before it goes.
     *
     * [period] is the period the whistle belongs to, 1-based: the one the
     * kickoff began, the one that [Whistle.END_OF_PERIOD] ended, the one that
     * [Whistle.START_OF_PERIOD] began, the one running at the final whistle.
     */
    data class TakesBackWhistle(
        val whistle: Whistle,
        val period: Int,
    ) : UndoStep
}

/**
 * What Undo would take back, or null when there is nothing to take back.
 *
 * Whistles recorded before 0.3.0 have no place in the order. They are older
 * than anything that has one, so they come back only after everything
 * recorded since has gone, and among themselves in the order the clock
 * says: the final whistle, then the last period's start, then its end, then
 * the kickoff.
 *
 * **A signed report's whistles are offered like any other**, and taking one
 * back takes the signatures back with it: the whistles are among the things
 * the captains attest ([afterAChangeFrom]). The console says so before it
 * asks.
 */
fun Match.nextUndoStep(): UndoStep? {
    val eventSequence = (goals + cardEvents).mapNotNull { it.sequence }.maxOrNull()
    val whistle = latestSequencedWhistle()
    return when {
        whistle != null && (eventSequence == null || whistle.first > eventSequence) -> whistle.second
        eventSequence != null || timeline().isNotEmpty() -> UndoStep.Event
        else -> earliestPressedWhistleWithNoSequence()
    }
}

private fun Match.latestSequencedWhistle(): Pair<Int, UndoStep.TakesBackWhistle>? {
    val numbered =
        buildList {
            kickoffSequence?.let { add(it to UndoStep.TakesBackWhistle(Whistle.KICKOFF, 1)) }
            periodBreaks.forEachIndexed { index, brk ->
                brk.endedSequence?.let { add(it to UndoStep.TakesBackWhistle(Whistle.END_OF_PERIOD, index + 1)) }
                brk.nextStartedSequence?.let {
                    add(it to UndoStep.TakesBackWhistle(Whistle.START_OF_PERIOD, index + 2))
                }
            }
            finishedSequence?.let { add(it to UndoStep.TakesBackWhistle(Whistle.FINAL, periodBreaks.size + 1)) }
        }
    return numbered.maxByOrNull { it.first }
}

/** Nothing is numbered any more: whatever whistle is left, last pressed first. */
private fun Match.earliestPressedWhistleWithNoSequence(): UndoStep.TakesBackWhistle? {
    val last = periodBreaks.lastOrNull()
    return when {
        status.isPastTheFinalWhistle -> UndoStep.TakesBackWhistle(Whistle.FINAL, periodBreaks.size + 1)
        last?.nextStartedAt != null -> UndoStep.TakesBackWhistle(Whistle.START_OF_PERIOD, periodBreaks.size + 1)
        last != null -> UndoStep.TakesBackWhistle(Whistle.END_OF_PERIOD, periodBreaks.size)
        kickoffAt != null -> UndoStep.TakesBackWhistle(Whistle.KICKOFF, 1)
        else -> null
    }
}

/**
 * This match with the last [whistle] of that kind gone, and the clock what the
 * remaining instants make it. Goals, cards and power plays are untouched: a
 * power play is ten minutes *of play*, worked out on demand from the clock,
 * so it follows the clock without anything being recomputed here.
 */
fun Match.withoutWhistle(whistle: Whistle): Match =
    when (whistle) {
        Whistle.KICKOFF -> {
            copy(kickoffAt = null, kickoffSequence = null, status = MatchStatus.SETUP)
        }

        Whistle.END_OF_PERIOD -> {
            copy(periodBreaks = periodBreaks.dropLast(1))
        }

        Whistle.START_OF_PERIOD -> {
            val open = periodBreaks.lastOrNull() ?: return this
            copy(periodBreaks = periodBreaks.dropLast(1) + open.copy(nextStartedAt = null, nextStartedSequence = null))
        }

        Whistle.FINAL -> {
            copy(status = MatchStatus.IN_PROGRESS, finishedAt = null, finishedSequence = null)
        }
    }
