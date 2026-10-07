package cz.hspinovace.psmf.data.player

import cz.hspinovace.psmf.db.PsmfDatabase
import cz.hspinovace.psmf.domain.PlayerId
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.datetime.LocalDate

/**
 * Dates of birth a referee has written at the pitch, remembered per player.
 *
 * Every league player read from psmf.cz arrives with nothing for the
 * `Číslo RP` column (DECISIONS 2026-10-07), so the referee writes the date
 * of birth when fielding them. Remembering it means the next match offers
 * it instead of asking again -- **the same standing as a corrected jersey
 * number**: a pre-fill on this device, editable every time, never required
 * to match, and never part of a report. What a report wrote is on the
 * appearance, snapshotted on the day; see `JerseyOverrideRepository` for
 * the same rule argued in full.
 *
 * A table and not an edit to the seed file, for the reason that one gives:
 * seed data is replaced wholesale on every update.
 */
interface RememberedDateOfBirthRepository {
    suspend fun all(): Map<PlayerId, LocalDate>

    /** Replaces whatever was remembered for [playerId]: the latest date written wins. */
    suspend fun remember(
        playerId: PlayerId,
        dateOfBirth: LocalDate,
    )
}

class SqlDelightRememberedDateOfBirthRepository(
    private val database: PsmfDatabase,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
) : RememberedDateOfBirthRepository {
    private val queries get() = database.teamRecordQueries

    override suspend fun all(): Map<PlayerId, LocalDate> =
        withContext(dispatcher) {
            queries
                .selectRememberedDatesOfBirth()
                .executeAsList()
                .associate { PlayerId(it.player_id) to LocalDate.parse(it.date_of_birth) }
        }

    override suspend fun remember(
        playerId: PlayerId,
        dateOfBirth: LocalDate,
    ): Unit =
        withContext(dispatcher) {
            queries.rememberDateOfBirth(playerId.value, dateOfBirth.toString())
        }
}
