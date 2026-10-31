package cz.hspinovace.psmf.usecase

import cz.hspinovace.psmf.data.match.LineupDraftRepository
import cz.hspinovace.psmf.data.match.MatchRepository
import cz.hspinovace.psmf.domain.Assessment
import cz.hspinovace.psmf.domain.MatchId
import cz.hspinovace.psmf.domain.MatchStatus

/**
 * What a report holds, in the terms the question before discarding it
 * names: *hlavička, obě sestavy, 3 góly, 1 karta*.
 *
 * Counts and flags, no strings: the words are the screen's.
 */
data class ReportContents(
    val matchId: MatchId,
    val status: MatchStatus,
    /** The officials are filled in. */
    val header: Boolean,
    /** Teams with a lineup, saved or still being built: 0, 1 or 2. */
    val lineups: Int,
    val goals: Int,
    /** Every card on the report; a second yellow is its yellow and its red. */
    val cards: Int,
    /** Something in the assessment differs from a blank one. */
    val assessment: Boolean,
    /** Captains' and referee's signatures under the report. */
    val signatures: Int,
) {
    /** Signed by both captains and the referee, and so perhaps already sent. */
    val signed: Boolean get() = status == MatchStatus.CONFIRMED

    /** Nothing filled in: what a tap on the wrong fixture leaves. */
    val isEmpty: Boolean
        get() = !header && lineups == 0 && goals == 0 && cards == 0 && !assessment && signatures == 0
}

/**
 * Discards a report: the one way the app removes one (prompt 17, Gate 2, the
 * tester's point 1).
 *
 * A report exists from the first tap on its fixture, by design -- nothing
 * lives only in memory -- so a tap on the wrong fixture left one for good,
 * and Zápasy kept that fixture under *Nadcházející* for good after it. The
 * testers' own test reports piled up the same way.
 *
 * **Two steps, and the second takes what the first answered.** [contentsOf]
 * is what the referee is asked about; [invoke] removes the report that
 * question was about and takes nothing else, so there is no way in through
 * here that skips the question. [MatchRepository.delete] has no other caller.
 *
 * It removes the report's rows, the lineups being built for it and the
 * players added for it at the pitch, in one transaction (the repository's).
 * It leaves what the referee typed once so as not to type again -- card
 * numbers and dates of birth remembered per player, jersey overrides,
 * followed teams -- and the files the export saved to the device's folder,
 * which the app does not own.
 */
class DiscardReport(
    private val matches: MatchRepository,
    private val drafts: LineupDraftRepository,
) {
    /** What discarding [id] would take with it; null when there is no such report. */
    suspend fun contentsOf(id: MatchId): ReportContents? {
        val match = matches.load(id) ?: return null
        val sidesBeingBuilt = drafts.forMatch(id).map { it.side }
        val sidesSaved = listOfNotNull(match.homeLineup, match.awayLineup).map { it.side }
        return ReportContents(
            matchId = id,
            status = match.status,
            header = match.officials != null,
            lineups = (sidesSaved + sidesBeingBuilt).distinct().size,
            goals = match.goals.size,
            cards = match.cardEvents.size,
            assessment = match.assessment != Assessment(),
            signatures = match.confirmations.size,
        )
    }

    /** Removes the report [asked] describes: the referee said yes to that question. */
    suspend operator fun invoke(asked: ReportContents) {
        matches.delete(asked.matchId)
    }
}
