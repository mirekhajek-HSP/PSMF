package cz.hspinovace.psmf.data.seed

import cz.hspinovace.psmf.domain.BirthNumber
import cz.hspinovace.psmf.domain.DisciplinaryRecord
import cz.hspinovace.psmf.domain.Fixture
import cz.hspinovace.psmf.domain.FixtureId
import cz.hspinovace.psmf.domain.Group
import cz.hspinovace.psmf.domain.GroupId
import cz.hspinovace.psmf.domain.JerseyNumber
import cz.hspinovace.psmf.domain.Kit
import cz.hspinovace.psmf.domain.KitId
import cz.hspinovace.psmf.domain.PersonName
import cz.hspinovace.psmf.domain.Player
import cz.hspinovace.psmf.domain.PlayerId
import cz.hspinovace.psmf.domain.PlayerName
import cz.hspinovace.psmf.domain.PlayerOrigin
import cz.hspinovace.psmf.domain.RpNumber
import cz.hspinovace.psmf.domain.Season
import cz.hspinovace.psmf.domain.SeasonId
import cz.hspinovace.psmf.domain.Team
import cz.hspinovace.psmf.domain.TeamId
import cz.hspinovace.psmf.domain.Venue
import cz.hspinovace.psmf.domain.VenueCode
import kotlinx.serialization.json.Json

/** One league group, fully loaded. */
data class LeagueGroup(
    val season: Season,
    val group: Group,
    val teams: List<Team>,
    val players: List<Player>,
    val fixtures: List<Fixture>,
    /** League-wide, from `venues.json`; carried here so a screen has them. */
    val venues: List<Venue>,
) {
    fun playersOf(teamId: TeamId): List<Player> = players.filter { it.teamId == teamId }

    fun team(id: TeamId): Team? = teams.firstOrNull { it.id == id }

    fun venue(code: VenueCode): Venue? = venues.firstOrNull { it.code == code }
}

/**
 * Loads league reference data from the seed files.
 *
 * **Adding a group is a data change, never a code change.** Drop a file in
 * `composeResources/files/leagues/`, add one line to `index.json`,
 * rebuild. Nothing here knows the name of any group, and
 * `SeedLeagueCatalogTest` is the test that keeps it that way.
 *
 * Venues are the one thing loaded from outside the group file: codes are
 * league-wide (analysis section 2.2), so they live in `venues.json` and
 * every fixture is checked against them.
 */
class SeedLeagueCatalog(
    private val reader: SeedFileReader,
    private val json: Json = DEFAULT_JSON,
) {
    /** The groups on offer, without loading any of their contents. */
    suspend fun listGroups(): List<SeedIndexEntryDto> = readIndex().groups

    /** Every pitch in the league. */
    suspend fun loadVenues(): List<Venue> =
        decode<SeedVenuesDto>(VENUES_FILE).venues.map { Venue(VenueCode(it.code), it.name) }

    /** Loads every group named in the index. */
    suspend fun loadAll(): List<LeagueGroup> {
        val venues = loadVenues()
        return readIndex().groups.map { load(it, venues) }
    }

    /** Loads one group by its index id. */
    suspend fun load(groupId: String): LeagueGroup {
        val entry =
            readIndex().groups.firstOrNull { it.id == groupId }
                ?: throw SeedException(
                    SeedProblem.InconsistentData(INDEX_FILE, "No group '$groupId' in the index"),
                )
        return load(entry, loadVenues())
    }

    private suspend fun readIndex(): SeedIndexDto = decode(INDEX_FILE)

    private suspend fun load(
        entry: SeedIndexEntryDto,
        venues: List<Venue>,
    ): LeagueGroup {
        val dto: SeedGroupDto = decode(entry.file)
        val file = entry.file

        if (dto.id != entry.id) {
            throw SeedException(
                SeedProblem.InconsistentData(
                    file,
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
                periods = dto.periods,
            )

        val teams = dto.teams.map { it.toDomain(groupId, file) }
        val teamIdByRef = teams.associate { it.ref to it.id }
        requireDistinct(teams.map { it.ref }, "team ref", file)
        requireDistinct(teams.map { it.id.value }, "team id", file)

        val players =
            dto.teams.flatMap { team ->
                val teamId = teamIdByRef.getValue(team.ref)
                team.players.map { it.toDomain(teamId, file) }
            }
        requireDistinct(players.map { it.ref }, "player ref", file)
        requireDistinct(players.map { it.id.value }, "player id", file)

        val venueCodes = venues.map { it.code }.toSet()
        val fixtures = dto.fixtures.map { it.toDomain(groupId, teamIdByRef, venueCodes, file) }
        requireDistinct(fixtures.map { it.id.value }, "fixture id", file)

        return LeagueGroup(
            season = Season(seasonId, entry.seasonName),
            group = group,
            teams = teams,
            players = players,
            fixtures = fixtures,
            venues = venues,
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

        /** League-wide pitch list. Codes are not group-specific. */
        const val VENUES_FILE: String = "venues.json"

        /** Directory within Compose resources, for the platform reader. */
        const val DIRECTORY: String = "files/leagues"

        val DEFAULT_JSON: Json =
            Json {
                ignoreUnknownKeys = true
                isLenient = false
            }
    }
}

// ---------------------------------------------------------------------------
// Mapping. Every failure is a SeedProblem naming the file and the row, because
// the thing being parsed is hand-edited and the error is read by whoever
// edited it.
// ---------------------------------------------------------------------------

private fun inconsistent(
    fileName: String,
    detail: String,
): Nothing = throw SeedException(SeedProblem.InconsistentData(fileName, detail))

private fun requireDistinct(
    values: List<String>,
    what: String,
    fileName: String,
) {
    val duplicates =
        values
            .groupingBy { it }
            .eachCount()
            .filterValues { it > 1 }
            .keys
    if (duplicates.isNotEmpty()) {
        inconsistent(fileName, "Duplicate $what: ${duplicates.sorted().joinToString()}")
    }
}

private fun SeedTeamDto.toDomain(
    groupId: GroupId,
    fileName: String,
): Team {
    if (kits.isEmpty()) inconsistent(fileName, "Team '$ref' has no kits; every team owns at least one")
    kits.forEach { kit ->
        if (kit.label.isBlank()) {
            inconsistent(
                fileName,
                "Team '$ref' has a kit with a blank label; Barva dresů is written verbatim on the " +
                    "report and cannot be derived from the colour list",
            )
        }
    }
    return Team(
        id = TeamId(id),
        ref = ref,
        groupId = groupId,
        name = name,
        kits = kits.map { Kit(KitId(it.id), it.label, it.colours) },
    )
}

private fun SeedPlayerDto.toDomain(
    teamId: TeamId,
    fileName: String,
): Player {
    val playerOrigin =
        PlayerOrigin.entries.firstOrNull { it.name == origin }
            ?: inconsistent(
                fileName,
                "Player '$ref' has origin '$origin'; expected one of " +
                    PlayerOrigin.entries.joinToString { it.name },
            )

    // The invariant is enforced by Player itself; catching it here turns a
    // crash into a message naming the row that is wrong.
    if (rpNumber == null && dateOfBirth == null && birthNumber == null) {
        inconsistent(
            fileName,
            "Player '$ref' has no rpNumber, dateOfBirth or birthNumber. At least one is required: " +
                "a player who cannot be identified cannot be put on a report.",
        )
    }
    if (playerOrigin == PlayerOrigin.ADDED_AT_PITCH && rpNumber != null) {
        inconsistent(
            fileName,
            "Player '$ref' is ADDED_AT_PITCH and carries an rpNumber. RP numbers are issued by " +
                "PSMF and are never entered by a user.",
        )
    }

    return Player(
        id = PlayerId(id),
        ref = ref,
        teamId = teamId,
        name =
            PlayerName(
                surname = latinName(surname, "surname", ref, fileName),
                firstName = latinName(firstName, "first name", ref, fileName),
            ),
        rpNumber = rpNumber?.let(::RpNumber),
        dateOfBirth = dateOfBirth,
        birthNumber = birthNumber?.let(::BirthNumber),
        defaultJerseyNumber = JerseyNumber.orNull(defaultJerseyNumber),
        origin = playerOrigin,
        discipline = discipline?.let { DisciplinaryRecord(it.yellowsThisSeason, it.asOf) },
    )
}

/** Names are Latin throughout, because PSMF's own records are. */
private fun latinName(
    raw: String,
    field: String,
    playerRef: String,
    fileName: String,
): PersonName =
    PersonName.orNull(raw)
        ?: inconsistent(fileName, "Player '$playerRef' has a $field '$raw' that is not a Latin name")

private fun SeedFixtureDto.toDomain(
    groupId: GroupId,
    teamIdByRef: Map<String, TeamId>,
    venueCodes: Set<VenueCode>,
    fileName: String,
): Fixture {
    // A fixture pointing at a team that is not in the file is the most
    // likely hand-editing mistake, and the least obvious at runtime.
    val homeId = teamIdByRef[home] ?: inconsistent(fileName, "Fixture '$ref' refers to unknown team '$home'")
    val awayId = teamIdByRef[away] ?: inconsistent(fileName, "Fixture '$ref' refers to unknown team '$away'")

    val code = VenueCode(venue)
    if (code !in venueCodes) {
        inconsistent(
            fileName,
            "Fixture '$ref' is at venue '$venue', which is not in ${SeedLeagueCatalog.VENUES_FILE}",
        )
    }

    return Fixture(
        id = FixtureId(id),
        ref = ref,
        groupId = groupId,
        round = round,
        date = date,
        time = time,
        venue = code,
        homeTeamId = homeId,
        awayTeamId = awayId,
    )
}
