package cz.hspinovace.psmf.ui.navigation

import cz.hspinovace.psmf.domain.MatchId
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Where the app can be.
 *
 * The demo is a wizard: seven screens in one order, entered from a list.
 * That is why there is no navigation library here — a back stack of
 * sealed values is the whole requirement, it is testable on the JVM
 * without a host, and it adds nothing to the version matrix, which
 * `docs/BUILD_MATRIX.md` is at pains to keep down to one real constraint.
 */
sealed interface Destination {
    data object Fixtures : Destination

    data class MatchHeader(
        val matchId: MatchId,
    ) : Destination
}

/**
 * The back stack.
 *
 * A Koin `single`, so it survives an Android configuration change. It
 * deliberately does **not** survive process death: after a kill the app
 * opens on the fixture list, where the report is waiting with its badge,
 * and tapping it resumes. That is the recovery path the referee would
 * take anyway, and it means no screen state has to be serialised — the
 * report itself is in the database, which is where recoverability belongs.
 */
class AppNavigator {
    private val stack = MutableStateFlow<List<Destination>>(listOf(Destination.Fixtures))

    val backStack: StateFlow<List<Destination>> = stack.asStateFlow()

    fun goTo(destination: Destination) {
        stack.update { current ->
            // Tapping the same fixture twice must not stack two copies of
            // the header screen; back would then appear not to work.
            if (current.lastOrNull() == destination) current else current + destination
        }
    }

    /** Returns false at the root, where the platform should handle back. */
    fun back(): Boolean {
        val current = stack.value
        if (current.size <= 1) return false
        stack.value = current.dropLast(1)
        return true
    }
}
