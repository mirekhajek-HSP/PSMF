package cz.hspinovace.psmf.domain

/**
 * A reason a report cannot yet be sent to PSMF.
 *
 * These are the things that get a report queried, sent back, or fined —
 * and the fine lands on the team that delegated the referee, not on the
 * referee (analysis section 2.5, rule 10). Catching them before export is
 * the single most useful thing the app does that paper does not.
 */
sealed interface ReportProblem {
    /** The `Osobní tresty` block has not been accounted for at all. */
    data object CardsNotAccountedFor : ReportProblem

    /** A card was recorded but the reason is missing. Structurally impossible. */
    data class CardWithoutReason(
        val minute: Minute,
    ) : ReportProblem

    data object MissingCommentary : ReportProblem

    data class AssessmentIncomplete(
        val side: TeamSide,
    ) : ReportProblem

    data object MissingResult : ReportProblem

    data class ScoreDisagreesWithGoals(
        val recorded: Score,
        val fromGoals: Score,
    ) : ReportProblem

    data object MissingOfficials : ReportProblem

    data class MissingLineup(
        val side: TeamSide,
    ) : ReportProblem

    data class MissingConfirmation(
        val party: ConfirmingParty,
    ) : ReportProblem
}

/**
 * What still stands between this report and the email to PSMF.
 *
 * Deliberately a list rather than a boolean: the recap screen has to tell
 * the referee *what* is missing, at the pitch, in the dark.
 */
fun Match.reportProblems(): List<ReportProblem> =
    buildList {
        if (officials == null) add(ReportProblem.MissingOfficials)
        if (homeLineup == null) add(ReportProblem.MissingLineup(TeamSide.HOME))
        if (awayLineup == null) add(ReportProblem.MissingLineup(TeamSide.AWAY))

        // "No cards" is an affirmation the referee makes by striking the boxes
        // through. A report that simply never mentioned cards is incomplete,
        // and looks identical to one where none were issued unless this is
        // checked (analysis section 2.5).
        if (cards == null) add(ReportProblem.CardsNotAccountedFor)

        if (!assessment.hasCommentary) add(ReportProblem.MissingCommentary)
        if (!assessment.home.isComplete) add(ReportProblem.AssessmentIncomplete(TeamSide.HOME))
        if (!assessment.away.isComplete) add(ReportProblem.AssessmentIncomplete(TeamSide.AWAY))

        val recorded = result
        if (recorded == null) {
            add(ReportProblem.MissingResult)
        } else {
            val derived = scoreFromGoals()
            if (recorded.fullTime != derived) {
                add(ReportProblem.ScoreDisagreesWithGoals(recorded.fullTime, derived))
            }
        }

        ConfirmingParty.entries
            .filterNot { hasConfirmationFrom(it) }
            .forEach { add(ReportProblem.MissingConfirmation(it)) }
    }

/** True when nothing stands in the way of generating the ZoU. */
fun Match.isReadyForExport(): Boolean = reportProblems().isEmpty()
