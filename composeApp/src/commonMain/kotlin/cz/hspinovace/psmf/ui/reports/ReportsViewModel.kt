package cz.hspinovace.psmf.ui.reports

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cz.hspinovace.psmf.usecase.ObserveReports
import cz.hspinovace.psmf.usecase.ReportListing
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlin.time.Duration.Companion.seconds

data class ReportsUiState(
    /**
     * The reports on the device. Null only until the first answer lands:
     * the screen then draws nothing rather than *Žádný zápis*, which would
     * be a flash of something untrue.
     */
    val listing: ReportListing? = null,
)

/**
 * The Zápis tab's first screen: the reports on the device.
 *
 * **A stream, not a read on entry** (TODO, "Known gaps"): a report started
 * from Zápasy, or moved on in the wizard, has to be in the list the next
 * time the referee looks at it, and the list may already be on screen.
 *
 * Built on [work], as Zápasy is: the first answer joins every report to the
 * league data, which may not have been parsed yet.
 */
class ReportsViewModel(
    observeReports: ObserveReports,
    work: CoroutineDispatcher = Dispatchers.Default,
) : ViewModel() {
    val state: StateFlow<ReportsUiState> =
        observeReports()
            .map { ReportsUiState(listing = it) }
            .flowOn(work)
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(SUBSCRIPTION_GRACE.inWholeMilliseconds),
                initialValue = ReportsUiState(),
            )

    private companion object {
        /** As the shell's: long enough to ride out a configuration change. */
        val SUBSCRIPTION_GRACE = 5.seconds
    }
}
