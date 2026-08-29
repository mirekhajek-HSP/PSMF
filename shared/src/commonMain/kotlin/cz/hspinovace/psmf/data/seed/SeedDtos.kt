package cz.hspinovace.psmf.data.seed

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// The on-disk shape of the seed files.
//
// These mirror the JSON exactly and are deliberately separate from the
// domain types: the file format is a contract with whoever produces the
// data (today a person, later a scraper of psmf.cz), and letting the
// domain model double as a wire format means every rename becomes a data
// migration.
//
// Parsed with kotlinx.serialization. Never hand-written JSON parsing --
// that was the source of a whole class of "forgot to add the field" bugs
// in golblok.

/** `index.json` — the one file the app knows the name of. */
@Serializable
data class SeedIndexDto(
    val groups: List<SeedIndexEntryDto>,
)

@Serializable
data class SeedIndexEntryDto(
    val id: String,
    /** Display name, e.g. "6. liga K". */
    val name: String,
    val seasonId: String,
    val seasonName: String,
    /** Filename within the same directory, e.g. "6k.json". */
    val file: String,
)

/** One group file, e.g. `6k.json`. */
@Serializable
data class SeedGroupDto(
    val id: String,
    val name: String,
    /** As written in the `Liga` header field, e.g. "6K". */
    val reportCode: String,
    /**
     * Half length in minutes. 2 x 30 everywhere in Hanspaulská liga as far
     * as anyone knows, but read from the file so a competition with a
     * different length is a data change rather than a code change.
     */
    val halfLengthMinutes: Int = 30,
    val teams: List<SeedTeamDto>,
    val fixtures: List<SeedFixtureDto>,
)

@Serializable
data class SeedTeamDto(
    val id: String,
    val name: String,
    /** `Barva dresů`, e.g. "modrá". Public data. */
    val kitColour: String,
    val players: List<SeedPlayerDto>,
)

@Serializable
data class SeedPlayerDto(
    val id: String,
    /** `Příjmení` — Latin only, as PSMF's records are. */
    val surname: String,
    @SerialName("givenName") val givenName: String,
    /**
     * The `Číslo RP` value, if known. Null for now: RP numbers are the one
     * roster dependency that cannot be met from public data.
     */
    val identifier: String? = null,
    /** `RP`, `DATE_OF_BIRTH` or `BIRTH_NUMBER`. Required when identifier is set. */
    val identifierType: String? = null,
    /** Last known shirt number, offered as a default and corrected by exception. */
    val defaultJerseyNumber: Int? = null,
)

@Serializable
data class SeedFixtureDto(
    val id: String,
    val round: Int,
    /** ISO date, e.g. "2026-08-31". */
    val date: LocalDate,
    /** 24-hour time, e.g. "19:00". Kickoffs run 19:00 to 20:45 in 15-minute steps. */
    val time: LocalTime,
    /** Short pitch code, e.g. "ZAKOS". */
    val venue: String,
    /** Team ids, referring to `teams[].id` in this same file. */
    val home: String,
    val away: String,
)
