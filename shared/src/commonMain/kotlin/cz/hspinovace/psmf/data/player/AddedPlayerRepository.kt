package cz.hspinovace.psmf.data.player

import cz.hspinovace.psmf.db.Added_player_record
import cz.hspinovace.psmf.db.PsmfDatabase
import cz.hspinovace.psmf.domain.JerseyNumber
import cz.hspinovace.psmf.domain.MatchId
import cz.hspinovace.psmf.domain.PersonName
import cz.hspinovace.psmf.domain.Player
import cz.hspinovace.psmf.domain.PlayerId
import cz.hspinovace.psmf.domain.PlayerName
import cz.hspinovace.psmf.domain.TeamId
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.datetime.LocalDate

/**
 * Players the referee added at the pitch.
 *
 * They exist in no league file — that is the whole point of them — so
 * nothing else knows their names. Without this, reopening a report would
 * show an appearance pointing at a player id that resolves to nobody.
 *
 * **Scoped to the match.** A pitch-added player is a fact about one
 * afternoon until PSMF reconciles them against their own database and
 * issues an RP number; promoting them to league reference data on the
 * referee's say-so would be exactly the data-integrity failure the
 * read-only squad list exists to prevent.
 */
interface AddedPlayerRepository {
    suspend fun add(
        matchId: MatchId,
        player: Player,
    )

    suspend fun forMatch(matchId: MatchId): List<Player>

    suspend fun remove(playerId: PlayerId)
}

class SqlDelightAddedPlayerRepository(
    private val database: PsmfDatabase,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
) : AddedPlayerRepository {
    private val queries get() = database.matchRecordQueries

    override suspend fun add(
        matchId: MatchId,
        player: Player,
    ): Unit =
        withContext(dispatcher) {
            queries.insertAddedPlayer(
                id = player.id.value,
                match_id = matchId.value,
                team_id = player.teamId.value,
                player_ref = player.ref,
                surname = player.name.surname.value,
                first_name = player.name.firstName.value,
                // Non-null by construction: addedAtThePitch requires one,
                // because it is the only identification such a player has.
                date_of_birth = requireNotNull(player.dateOfBirth).toString(),
                default_jersey_number = player.defaultJerseyNumber?.value?.toLong(),
            )
        }

    override suspend fun forMatch(matchId: MatchId): List<Player> =
        withContext(dispatcher) {
            queries.selectAddedPlayers(matchId.value).executeAsList().map { it.toDomain() }
        }

    override suspend fun remove(playerId: PlayerId): Unit =
        withContext(dispatcher) {
            queries.deleteAddedPlayer(playerId.value)
        }
}

/**
 * Rebuilt through `addedAtThePitch`, not through the `Player` constructor.
 *
 * That is deliberate: the factory is the thing that cannot take an RP
 * number, so reading a row back cannot smuggle one in either.
 */
private fun Added_player_record.toDomain(): Player =
    Player.addedAtThePitch(
        id = PlayerId(id),
        ref = player_ref,
        teamId = TeamId(team_id),
        name = PlayerName(surname = PersonName.of(surname), firstName = PersonName.of(first_name)),
        dateOfBirth = LocalDate.parse(date_of_birth),
        defaultJerseyNumber = default_jersey_number?.let { JerseyNumber(it.toInt()) },
    )
