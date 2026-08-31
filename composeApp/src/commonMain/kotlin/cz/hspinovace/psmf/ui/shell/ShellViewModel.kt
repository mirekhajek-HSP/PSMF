package cz.hspinovace.psmf.ui.shell

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cz.hspinovace.psmf.domain.MatchId
import cz.hspinovace.psmf.usecase.ObserveReportInProgress
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlin.time.Duration.Companion.seconds

data class ShellUiState(
    /**
     * The match under way, if there is one.
     *
     * The id and not just a flag: the shell both badges the report tab with
     * this and seeds the tab from it, so that tapping the badge lands on
     * the console even on the first launch after a kill.
     */
    val inProgress: MatchId? = null,
)

/**
 * What the frame itself needs to know, which is one thing: whether a match
 * is under way.
 *
 * A stream rather than a read on entry, because the answer changes while
 * the referee is looking at a different tab — the whistle goes on the
 * console, and the badge has to appear on a tab bar that is already on
 * screen.
 */
class ShellViewModel(
    observeReportInProgress: ObserveReportInProgress,
) : ViewModel() {
    val state: StateFlow<ShellUiState> =
        observeReportInProgress()
            .map { ShellUiState(inProgress = it) }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(SUBSCRIPTION_GRACE.inWholeMilliseconds),
                initialValue = ShellUiState(),
            )

    private companion object {
        /**
         * Long enough to ride out a configuration change without dropping
         * the database query and starting it again.
         */
        val SUBSCRIPTION_GRACE = 5.seconds
    }
}
