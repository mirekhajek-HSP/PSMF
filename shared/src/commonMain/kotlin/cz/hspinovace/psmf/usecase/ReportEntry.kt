package cz.hspinovace.psmf.usecase

import cz.hspinovace.psmf.data.match.MatchRepository
import cz.hspinovace.psmf.domain.Assessment
import cz.hspinovace.psmf.domain.CardsSection
import cz.hspinovace.psmf.domain.Confirmation
import cz.hspinovace.psmf.domain.ConfirmingParty
import cz.hspinovace.psmf.domain.JerseyNumber
import cz.hspinovace.psmf.domain.Match
import cz.hspinovace.psmf.domain.MatchStatus
import cz.hspinovace.psmf.domain.PersonName
import cz.hspinovace.psmf.domain.TeamAssessment
import cz.hspinovace.psmf.domain.TeamSide
import cz.hspinovace.psmf.domain.afterAChangeFrom
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
    /** A complaint or protest the captain asks the referee to note; see [TeamAssessment.note]. */
    val note: String = "",
) {
    fun toDomain(): TeamAssessment =
        TeamAssessment(
            bestPlayer = JerseyNumber.orNull(bestPlayer.trim().toIntOrNull()),
            waitingTimeMinutes = waitingTimeMinutes.trim().toIntOrNull()?.coerceAtLeast(0) ?: 0,
            shirtsProperlyNumbered = shirtsProperlyNumbered,
            uniformKitColour = uniformKitColour,
            note = note,
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
                note = assessment.note,
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
 *
 * **It touches no confirmation.** The assessment, the commentary and the team
 * notes are the referee's to finish until export, and nobody signs them: an
 * edit here leaves the captains' and the referee's signatures where they are.
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
 * The referee affirming that no cards were issued.
 *
 * **An empty list is not "no cards".** The paper form requires the boxes
 * to be struck through, which makes "none" something the referee actively
 * says — distinct from a block nobody has filled in. Nothing else in the
 * app can set it: the console only ever adds cards, so without this a
 * clean match could never be exported.
 *
 * It is a statement about the cards, which the captains attest, so on a
 * signed report it takes the signatures back ([afterAChangeFrom]).
 */
class AffirmNoCards(
    private val matches: MatchRepository,
) {
    suspend operator fun invoke(match: Match): Match {
        // Only when there is genuinely nothing there. Striking the boxes
        // through must not be a way to delete cards already recorded.
        if (match.cardEvents.isNotEmpty()) return match
        val updated = match.copy(cards = CardsSection.NoneIssued).afterAChangeFrom(match)
        matches.save(updated)
        return updated
    }
}

/**
 * Taking the "no cards" affirmation back: the block is unaccounted for again.
 *
 * Not "some cards" and not "none": **unaccounted for**, which is what it was
 * before the referee said anything, and what the readiness check then asks
 * about. A mis-tap on *Žádné karty nebyly uděleny* is no longer final. Only an
 * affirmation can be withdrawn -- never recorded cards, which Undo takes back
 * one at a time -- and, like making it, it takes signatures back.
 */
class WithdrawNoCards(
    private val matches: MatchRepository,
) {
    suspend operator fun invoke(match: Match): Match {
        if (match.cards != CardsSection.NoneIssued) return match
        val updated = match.copy(cards = null).afterAChangeFrom(match)
        matches.save(updated)
        return updated
    }
}

/**
 * One confirmation per party, replaced rather than accumulated.
 *
 * **One captain per team confirms** — settled 2026-08-30 — and **by a tap**:
 * the name is the one the screen showed on the button, the captain marked in
 * the lineup or the referee from the header, so there is nothing to type and
 * no deputy to switch on (prompt 13, part 4). These are the *final*
 * confirmations, under the finished report. The captain's confirmation of the
 * lineup before kickoff is a different record ([cz.hspinovace.psmf.domain.Lineup.confirmation]),
 * and giving one does not give the other.
 *
 * Re-confirming updates the time rather than adding a second signature,
 * which is why [Match.confirmedBy] filters by party before appending. All
 * three in, and the report is CONFIRMED.
 */
class ConfirmReport(
    private val matches: MatchRepository,
) {
    suspend operator fun invoke(
        match: Match,
        party: ConfirmingParty,
        by: PersonName,
        at: Instant,
    ): Match {
        val confirmed = match.confirmedBy(Confirmation(party = party, at = at, confirmedBy = by))
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
