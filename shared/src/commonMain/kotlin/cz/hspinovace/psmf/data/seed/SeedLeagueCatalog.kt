package cz.hspinovace.psmf.data.seed

import cz.hspinovace.psmf.domain.Fixture
import cz.hspinovace.psmf.domain.FixtureId
import cz.hspinovace.psmf.domain.Group
import cz.hspinovace.psmf.domain.GroupId
import cz.hspinovace.psmf.domain.JerseyNumber
import cz.hspinovace.psmf.domain.PersonName
import cz.hspinovace.psmf.domain.Player
import cz.hspinovace.psmf.domain.PlayerId
import cz.hspinovace.psmf.domain.PlayerIdentifier
import cz.hspinovace.psmf.domain.PlayerIdentifierType
import cz.hspinovace.psmf.domain.PlayerName
import cz.hspinovace.psmf.domain.Season
import cz.hspinovace.psmf.domain.SeasonId
import cz.hspinovace.psmf.domain.Team
import cz.hspinovace.psmf.domain.TeamId
import cz.hspinovace.psmf.domain.VenueCode
import kotlinx.serialization.json.Json

/** One league group, fully loaded. */
data class LeagueGroup(
    val season: Season,
    val group: Group,
    val teams: List<Team>,
    val players: List<Player>,
    val fixtures: List<Fixture>,
) {
    fun playersOf(teamId: TeamId): List<Player> = players.filter { it.teamId == teamId }

    fun team(id: TeamId): Team? = teams.firstOrNull { it.id == id }
}

/**
 * Loads league reference data from the seed files.
 *
 * **Adding a group is a data change, never a code change.** Drop a file in
 * `composeResources/files/leagues/`, add one line to `index.json`,
 * rebuild. Nothing here knows the name of any group, and
 * `SeedLeagueCatalogTest` is the test that keeps it that way.
 */
class SeedLeagueCatalog(
    private val reader: SeedFileReader,
    private val json: Json = DEFAULT_JSON,
) {
    /** The groups on offer, without loading any of their contents. */
    suspend fun listGroups(): List<SeedIndexEntryDto> = readIndex().groups

    /** Loads every group named in the index. */
    suspend fun loadAll(): List<LeagueGroup> = readIndex().groups.map { load(it) }

    /** Loads one group by its index id. */
    suspend fun load(groupId: String): LeagueGroup {
        val entry =
            readIndex().groups.firstOrNull { it.id == groupId }
                ?: throw SeedException(
                    SeedProblem.InconsistentData(INDEX_FILE, "No group '$groupId' in the index"),
                )
        return load(entry)
    }

    private suspend fun readIndex(): SeedIndexDto = decode(INDEX_FILE)

    private suspend fun load(entry: SeedIndexEntryDto): LeagueGroup {
        val dto: SeedGroupDto = decode(entry.file)

        if (dto.id != entry.id) {
            throw SeedException(
                SeedProblem.InconsistentData(
                    entry.file,
                    "File declares id '${dto.id}' but the index calls it '${entry.id}'",
                ),
            )
        }

        val seasonId = SeasonId(entry.seasonId)
        val groupId = GroupId(dto.id)

        val group =
            Group(
                id = groupId,
                seasonId = seasonId,
                name = dto.name,
                reportCode = dto.reportCode,
                halfLengthMinutes = dto.halfLengthMinutes,
            )

        val teams =
            dto.teams.map { team ->
                Team(id = TeamId(team.id), groupId = groupId, name = team.name, kitColour = team.kitColour)
            }

        val players =
            dto.teams.flatMap { team ->
                team.players.map { player -> player.toDomain(TeamId(team.id), entry.file) }
            }

        val knownTeamIds = teams.map { it.id }.toSet()
        val fixtures = dto.fixtures.map { fixture -> fixture.toDomain(groupId, knownTeamIds, entry.file) }

        return LeagueGroup(
            season = Season(seasonId, entry.seasonName),
            group = group,
            teams = teams,
            players = players,
            fixtures = fixtures,
        )
    }

    private suspend inline fun <reified T> decode(fileName: String): T {
        val raw = reader.read(fileName) ?: throw SeedException(SeedProblem.FileMissing(fileName))
        return try {
            json.decodeFromString<T>(raw)
        } catch (e: IllegalArgumentException) {
            // kotlinx.serialization reports malformed JSON and missing
            // fields as SerializationException, an IllegalArgumentException.
            // The cause is kept: it names the field and offset, which is
            // what someone hand-editing a group file actually needs.
            throw SeedException(SeedProblem.Unparseable(fileName, e.message ?: "malformed JSON"), e)
        }
    }

    companion object {
        /** The one filename the app is allowed to know. */
        const val INDEX_FILE: String = "index.json"

        /** Directory within Compose resources, for the platform reader. */
        const val DIRECTORY: String = "files/leagues"

        val DEFAULT_JSON: Json =
            Json {
                ignoreUnknownKeys = true
                isLenient = false
            }
    }
}

private fun SeedPlayerDto.toDomain(
    teamId: TeamId,
    fileName: String,
): Player =
    Player(
        id = PlayerId(id),
        teamId = teamId,
        name =
            PlayerName(
                surname = latinName(surname, "surname", id, fileName),
                givenName = latinName(givenName, "given name", id, fileName),
            ),
        identifier = identifier?.let { value -> toIdentifier(value, fileName) },
        defaultJerseyNumber = JerseyNumber.orNull(defaultJerseyNumber),
    )

/** Names are Latin throughout, because PSMF's own records are. */
private fun latinName(
    raw: String,
    field: String,
    playerId: String,
    fileName: String,
): PersonName =
    PersonName.orNull(raw) ?: throw SeedException(
        SeedProblem.InconsistentData(fileName, "Player $playerId has a $field '$raw' that is not a Latin name"),
    )

private fun SeedPlayerDto.toIdentifier(
    value: String,
    fileName: String,
): PlayerIdentifier {
    // The ZoU has one column holding either an RP number or a date of
    // birth, so a value without its kind is not something the model can
    // represent, and guessing would be worse than refusing.
    val rawType =
        identifierType ?: throw SeedException(
            SeedProblem.InconsistentData(
                fileName,
                "Player $id has an identifier but no identifierType; the ZoU column holds either " +
                    "an RP number or a date of birth and the model must say which",
            ),
        )
    val type =
        PlayerIdentifierType.entries.firstOrNull { it.name == rawType } ?: throw SeedException(
            SeedProblem.InconsistentData(
                fileName,
                "Player $id has identifierType '$rawType'; expected one of " +
                    PlayerIdentifierType.entries.joinToString { it.name },
            ),
        )
    return PlayerIdentifier(value, type)
}

private fun SeedFixtureDto.toDomain(
    groupId: GroupId,
    knownTeamIds: Set<TeamId>,
    fileName: String,
): Fixture {
    val homeId = TeamId(home)
    val awayId = TeamId(away)

    // A fixture pointing at a team that is not in the file is the most
    // likely hand-editing mistake, and the least obvious at runtime.
    listOf(homeId, awayId).forEach { id ->
        if (id !in knownTeamIds) {
            throw SeedException(
                SeedProblem.InconsistentData(fileName, "Fixture $id refers to unknown team '${id.value}'"),
            )
        }
    }

    return Fixture(
        id = FixtureId(id),
        groupId = groupId,
        round = round,
        date = date,
        time = time,
        venue = VenueCode(venue),
        homeTeamId = homeId,
        awayTeamId = awayId,
    )
}
