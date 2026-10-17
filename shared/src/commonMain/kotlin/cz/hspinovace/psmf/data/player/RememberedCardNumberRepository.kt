package cz.hspinovace.psmf.data.player

import cz.hspinovace.psmf.db.PsmfDatabase
import cz.hspinovace.psmf.domain.PlayerId
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The number a referee copied from a player's registration card, remembered
 * per player so the next match offers it (DECISIONS 2026-10-17).
 *
 * The same standing as [RememberedDateOfBirthRepository]: typed once, offered
 * next time, editable every time, never part of a report -- what a report
 * says is what the appearance wrote on the day.
 *
 * **It is not the player's RP number and nothing here may become one.** The
 * values are strings a person typed, and the interface has no way to hand one
 * to anything that could mistake it for PSMF's record: it is read back as
 * text for a pre-fill and for nothing else. `RememberedCardNumberTest` keeps
 * the player record, the league data and the jersey overrides out of reach.
 */
interface RememberedCardNumberRepository {
    suspend fun all(): Map<PlayerId, String>

    /** Replaces whatever was remembered for [playerId]: the latest number written wins. */
    suspend fun remember(
        playerId: PlayerId,
        cardNumber: String,
    )
}

class SqlDelightRememberedCardNumberRepository(
    private val database: PsmfDatabase,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
) : RememberedCardNumberRepository {
    private val queries get() = database.teamRecordQueries

    override suspend fun all(): Map<PlayerId, String> =
        withContext(dispatcher) {
            queries
                .selectRememberedCardNumbers()
                .executeAsList()
                .associate { PlayerId(it.player_id) to it.card_number }
        }

    override suspend fun remember(
        playerId: PlayerId,
        cardNumber: String,
    ): Unit =
        withContext(dispatcher) {
            queries.rememberCardNumber(playerId.value, cardNumber)
        }
}
