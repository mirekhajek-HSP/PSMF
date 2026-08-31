package cz.hspinovace.psmf.usecase

import cz.hspinovace.psmf.data.match.MatchRepository
import cz.hspinovace.psmf.domain.Assessment
import cz.hspinovace.psmf.domain.CardsSection
import cz.hspinovace.psmf.domain.Confirmation
import cz.hspinovace.psmf.domain.ConfirmingParty
import cz.hspinovace.psmf.domain.JerseyNumber
import cz.hspinovace.psmf.domain.Match
import cz.hspinovace.psmf.domain.MatchResult
import cz.hspinovace.psmf.domain.MatchStatus
import cz.hspinovace.psmf.domain.PersonName
import cz.hspinovace.psmf.domain.Score
import cz.hspinovace.psmf.domain.TeamAssessment
import cz.hspinovace.psmf.domain.TeamSide
import kotlin.time.Instant

/**
 * `NH`, `Čd`, `Č`, `B` for one team, while they are being typed.
 *
 * **`Č` and `B` start unset and stay unset until the referee answers.**
 * Defaulting them to "yes" would quietly waive a fine: both feed straight
 * into the disciplinary money (analysis section 2.7), so an unanswered
 * rating has to read as unanswered on the report and in the readiness
 * check, never as a pass.
 */
data class TeamAssessmentDraft(
    val bestPlayer: String = "",
    val waitingTimeMinutes: String = "0",
    val shirtsProperlyNumbered: Boolean? = null,
    val uniformKitColour: Boolean? = null,
) {
    fun toDomain(): TeamAssessment =
        TeamAssessment(
            bestPlayer = JerseyNumber.orNull(bestPlayer.trim().toIntOrNull()),
            waitingTimeMinutes = waitingTimeMinutes.trim().toIntOrNull()?.coerceAtLeast(0) ?: 0,
            shirtsProperlyNumbered = shirtsProperlyNumbered,
            uniformKitColour = uniformKitColour,
        )

    companion object {
        fun from(assessment: TeamAssessment) =
            TeamAssessmentDraft(
                bestPlayer =
                    assessment.bestPlayer
                        ?.value
                        ?.toString()
                        .orEmpty(),
                waitingTimeMinutes = assessment.waitingTimeMinutes.toString(),
                shirtsProperlyNumbered = assessment.shirtsProperlyNumbered,
                uniformKitColour = assessment.uniformKitColour,
            )
    }
}

data class AssessmentDraft(
    val home: TeamAssessmentDraft = TeamAssessmentDraft(),
    val away: TeamAssessmentDraft = TeamAssessmentDraft(),
    val commentary: String = "",
) {
    fun side(side: TeamSide): TeamAssessmentDraft = if (side == TeamSide.HOME) home else away

    fun with(
        side: TeamSide,
        team: TeamAssessmentDraft,
    ): AssessmentDraft = if (side == TeamSide.HOME) copy(home = team) else copy(away = team)

    fun toDomain(): Assessment = Assessment(home = home.toDomain(), away = away.toDomain(), commentary = commentary)

    companion object {
        fun from(assessment: Assessment) =
            AssessmentDraft(
                home = TeamAssessmentDraft.from(assessment.home),
                away = TeamAssessmentDraft.from(assessment.away),
                commentary = assessment.commentary,
            )
    }
}

/**
 * Writes the assessment through on every change.
 *
 * Unlike the header and the lineup, there is nothing here that cannot be
 * stored half-finished: [Assessment] is nullable all the way down by
 * design, because the commentary stays editable until export (A6).
 */
class SaveAssessment(
    private val matches: MatchRepository,
) {
    suspend operator fun invoke(
        match: Match,
        draft: AssessmentDraft,
    ): Match {
        val updated = match.copy(assessment = draft.toDomain())
        matches.save(updated)
        return updated
    }
}

/**
 * `poločas` and `Konečný výsledek`, while they are being typed.
 *
 * Both are entered rather than derived. The goals give the app a number,
 * but the referee's is the one that counts and the two disagreeing is
 * exactly what the readiness check exists to surface.
 */
data class ResultDraft(
    val halfTimeHome: String = "",
    val halfTimeAway: String = "",
    val fullTimeHome: String = "",
    val fullTimeAway: String = "",
) {
    fun toResult(): MatchResult? {
        val halfTime = scoreOf(halfTimeHome, halfTimeAway) ?: return null
        val fullTime = scoreOf(fullTimeHome, fullTimeAway) ?: return null
        // Goals cannot be un-scored. A full-time score below the half-time
        // one is a typo, and MatchResult refuses to hold it.
        if (fullTime.home < halfTime.home || fullTime.away < halfTime.away) return null
        return MatchResult(halfTime = halfTime, fullTime = fullTime)
    }

    fun problems(): List<ResultProblem> =
        buildList {
            if (scoreOf(halfTimeHome, halfTimeAway) == null) add(ResultProblem.HALF_TIME_MISSING)
            if (scoreOf(fullTimeHome, fullTimeAway) == null) add(ResultProblem.FULL_TIME_MISSING)
            if (isEmpty() && toResult() == null) add(ResultProblem.FULL_TIME_BELOW_HALF_TIME)
        }

    private fun scoreOf(
        home: String,
        away: String,
    ): Score? {
        val h = home.trim().toIntOrNull()?.takeIf { it >= 0 } ?: return null
        val a = away.trim().toIntOrNull()?.takeIf { it >= 0 } ?: return null
        return Score(h, a)
    }

    companion object {
        fun from(result: MatchResult?) =
            ResultDraft(
                halfTimeHome =
                    result
                        ?.halfTime
                        ?.home
                        ?.toString()
                        .orEmpty(),
                halfTimeAway =
                    result
                        ?.halfTime
                        ?.away
                        ?.toString()
                        .orEmpty(),
                fullTimeHome =
                    result
                        ?.fullTime
                        ?.home
                        ?.toString()
                        .orEmpty(),
                fullTimeAway =
                    result
                        ?.fullTime
                        ?.away
                        ?.toString()
                        .orEmpty(),
            )

        /**
         * Pre-filled from the goals: full time from what was recorded, half
         * time left blank because only the referee knows where the break
         * fell — the clock never stops, so added time makes minute 31 as
         * likely to be first half as second.
         */
        fun suggestedFrom(match: Match): ResultDraft {
            val fromGoals = match.scoreFromGoals()
            return ResultDraft(
                fullTimeHome = fromGoals.home.toString(),
                fullTimeAway = fromGoals.away.toString(),
            )
        }
    }
}

enum class ResultProblem {
    HALF_TIME_MISSING,
    FULL_TIME_MISSING,
    FULL_TIME_BELOW_HALF_TIME,
}

class RecordResult(
    private val matches: MatchRepository,
) {
    suspend operator fun invoke(
        match: Match,
        draft: ResultDraft,
    ): Match {
        val result = draft.toResult() ?: return match
        val updated = match.copy(result = result)
        matches.save(updated)
        return updated
    }
}

/**
 * The referee affirming that no cards were issued.
 *
 * **An empty list is not "no cards".** The paper form requires the boxes
 * to be struck through, which makes "none" something the referee actively
 * says — distinct from a block nobody has filled in. Nothing else in the
 * app can set it: the console only ever adds cards, so without this a
 * clean match could never be exported.
 */
class AffirmNoCards(
    private val matches: MatchRepository,
) {
    suspend operator fun invoke(match: Match): Match {
        // Only when there is genuinely nothing there. Striking the boxes
        // through must not be a way to delete cards already recorded.
        if (match.cardEvents.isNotEmpty()) return match
        val updated = match.copy(cards = CardsSection.NoneIssued)
        matches.save(updated)
        return updated
    }
}

/**
 * One confirmation per party, replaced rather than accumulated.
 *
 * **One captain per team confirms** — settled 2026-08-30. The captain
 * confirms the lineup before kickoff and the finished report afterwards,
 * and those are the same act: re-confirming updates the timestamp rather
 * than adding a second signature, which is why [Match.confirmedBy] filters
 * by party before appending.
 */
class ConfirmReport(
    private val matches: MatchRepository,
) {
    suspend operator fun invoke(
        match: Match,
        party: ConfirmingParty,
        by: String,
        asDeputy: Boolean,
        at: Instant,
    ): Match? {
        val name = PersonName.orNull(by) ?: return null
        val confirmed =
            match.confirmedBy(
                Confirmation(party = party, at = at, confirmedBy = name, asDeputy = asDeputy),
            )
        // All three in: the report is what the parties agreed it says.
        val updated =
            if (ConfirmingParty.entries.all { confirmed.hasConfirmationFrom(it) }) {
                confirmed.copy(status = MatchStatus.CONFIRMED)
            } else {
                confirmed
            }
        matches.save(updated)
        return updated
    }
}
