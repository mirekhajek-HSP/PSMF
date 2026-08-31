package cz.hspinovace.psmf.data.team

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import cz.hspinovace.psmf.db.PsmfDatabase
import cz.hspinovace.psmf.domain.JerseyNumber
import cz.hspinovace.psmf.domain.PlayerId
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/**
 * Corrected **default** jersey numbers.
 *
 * # Why this is a table and not an edit to the seed file
 *
 * Seed data is a bundled resource. It is replaced wholesale on every app
 * update, so a number written back into it would survive until the next
 * release and then silently revert — which is worse than not being editable
 * at all, because the referee would have no reason to check.
 *
 * # Why editing it cannot damage a report
 *
 * A default is what the lineup screen *offers*. What a report says is on
 * `appearance_record.jersey_number`, written when the referee filled the
 * block in, and nothing here touches it. That is the same rule as the kit
 * label and `reportedIdentification`: the report records what was true on
 * the day, not what is true now.
 *
 * `JerseyOverrideTest` proves it rather than asserting it, by storing a
 * finished report, changing a default, and reading the report back.
 *
 * # Why clearing is a delete
 *
 * No row means the seed value stands. A row holding null would be a second
 * way of saying the same thing, and two representations of one state is how
 * "why did the number not come back" bugs start.
 */
interface JerseyOverrideRepository {
    /** Emits the current overrides, then again on every change. */
    fun observe(): Flow<Map<PlayerId, JerseyNumber>>

    suspend fun overrides(): Map<PlayerId, JerseyNumber>

    /** [number] null clears the override and restores the seed value. */
    suspend fun setDefaultJerseyNumber(
        playerId: PlayerId,
        number: JerseyNumber?,
    )
}

class SqlDelightJerseyOverrideRepository(
    private val database: PsmfDatabase,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
) : JerseyOverrideRepository {
    private val queries get() = database.teamRecordQueries

    override fun observe(): Flow<Map<PlayerId, JerseyNumber>> =
        queries
            .selectJerseyOverrides()
            .asFlow()
            .mapToList(dispatcher)
            .map { rows -> rows.associate { PlayerId(it.player_id) to JerseyNumber(it.jersey_number.toInt()) } }

    override suspend fun overrides(): Map<PlayerId, JerseyNumber> =
        withContext(dispatcher) {
            queries
                .selectJerseyOverrides()
                .executeAsList()
                .associate { PlayerId(it.player_id) to JerseyNumber(it.jersey_number.toInt()) }
        }

    override suspend fun setDefaultJerseyNumber(
        playerId: PlayerId,
        number: JerseyNumber?,
    ): Unit =
        withContext(dispatcher) {
            if (number == null) {
                queries.clearJerseyOverride(playerId.value)
            } else {
                queries.setJerseyOverride(playerId.value, number.value.toLong())
            }
        }
}
