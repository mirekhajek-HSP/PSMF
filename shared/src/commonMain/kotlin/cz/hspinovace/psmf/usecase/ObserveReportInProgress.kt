package cz.hspinovace.psmf.usecase

import cz.hspinovace.psmf.data.match.MatchRepository
import cz.hspinovace.psmf.domain.MatchId
import cz.hspinovace.psmf.domain.MatchStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/**
 * Whether a match is under way, for anywhere in the app that has to say so.
 *
 * The tab bar is the reason this exists. A referee who opens Settings
 * mid-half has to be able to see that there is a report to go back to, and
 * a tab bar cannot ask a question on entry — it is already on screen when
 * the whistle goes.
 *
 * Only `IN_PROGRESS` counts. A report being set up or waiting for
 * confirmations is not something the referee is in the middle of; the tab
 * still holds it, and a badge on everything is a badge on nothing.
 */
class ObserveReportInProgress(
    private val matches: MatchRepository,
) {
    operator fun invoke(): Flow<MatchId?> =
        matches
            .observeSummaries()
            .map { summaries -> summaries.firstOrNull { it.status == MatchStatus.IN_PROGRESS }?.id }
            .distinctUntilChanged()
}
