package cz.hspinovace.psmf.data.seed

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
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
//
// IDS AND REFS. Every entity carries both:
//   "id"  an opaque UUID, the real identity, NEVER regenerated
//   "ref" a readable slug, used for hand-editing and for pointing between
//         entities inside a file
// Files reference each other by ref, because 66 fixtures full of UUIDs
// would be unmaintainable by hand. Persisted matches reference ids,
// because those have to survive a rename. See SeedIdentity.

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

/**
 * `venues.json` — **one league-wide file, not one per group.**
 *
 * Venue codes are shared across the whole of Hanspaulská liga: the analysis
 * lists roughly 35 pitches across Prague with short codes (section 2.2), and
 * any group may be scheduled on any of them. Duplicating them into every
 * group file would guarantee they drift apart.
 */
@Serializable
data class SeedVenuesDto(
    val venues: List<SeedVenueDto>,
)

@Serializable
data class SeedVenueDto(
    /** Short pitch code, e.g. "ZAKOS". This is what the ZoU header carries. */
    val code: String,
    /**
     * Optional. PSMF publishes the codes; the long names are not in the
     * analysis and are not worth inventing.
     */
    val name: String? = null,
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
    /** Periods played. Two in HL; veteran and futsal competitions may differ. */
    val periods: Int = 2,
    val teams: List<SeedTeamDto>,
    val fixtures: List<SeedFixtureDto>,
)

@Serializable
data class SeedTeamDto(
    /** Opaque UUID. Never regenerate. */
    val id: String,
    /** Readable slug, and the key fixtures point at. */
    val ref: String,
    val name: String,
    /**
     * The kit sets this team owns. **Order matters: the first is primary.**
     * A team owns two and picks one per match so the sides do not clash.
     */
    val kits: List<SeedKitDto>,
    val players: List<SeedPlayerDto>,
)

@Serializable
data class SeedKitDto(
    val id: String,
    /**
     * `Barva dresů` verbatim, e.g. "bílo-černá". **Authoritative for the
     * report and never derived from [colours]** — the compound form is not
     * mechanically obtainable from the parts in Czech.
     */
    val label: String,
    /** For the app only: chips and clash hints. Never written on the report. */
    val colours: List<String> = emptyList(),
)

@Serializable
data class SeedPlayerDto(
    /** Opaque UUID. Never regenerate. */
    val id: String,
    /**
     * Readable slug, **not team-scoped**: a player may transfer once per
     * season, and a team-scoped ref would change and orphan them.
     */
    val ref: String,
    /** `Příjmení` — Latin only, as PSMF's records are. */
    val surname: String,
    val firstName: String,
    /**
     * `Číslo RP`, issued by PSMF. Null for now: RP numbers are the one
     * roster dependency that cannot be met from public data.
     *
     * **Never written from user input.** It arrives from PSMF or not at all.
     */
    val rpNumber: String? = null,
    /** The fallback a person enters when there is no RP number to use. */
    val dateOfBirth: LocalDate? = null,
    /** `Rodné číslo`. Blocked on A28 and normally absent. */
    val birthNumber: String? = null,
    /** Last known shirt number, offered as a default and corrected by exception. */
    val defaultJerseyNumber: Int? = null,
    /** `LEAGUE_RECORD` or `ADDED_AT_PITCH`. */
    val origin: String = "LEAGUE_RECORD",
    /** Advisory only. See [SeedDisciplineDto]. */
    val discipline: SeedDisciplineDto? = null,
)

/**
 * Yellow cards accumulated in this group this season.
 *
 * **Advisory, never authoritative.** [asOf] is mandatory: a count without a
 * date cannot be reasoned about, because matches played since are not in it.
 */
@Serializable
data class SeedDisciplineDto(
    val yellowsThisSeason: Int,
    val asOf: LocalDate,
)

@Serializable
data class SeedFixtureDto(
    /** Opaque UUID. Never regenerate. */
    val id: String,
    /** Readable slug, e.g. "6k-r1-1". */
    val ref: String,
    val round: Int,
    /** ISO date, e.g. "2026-08-31". */
    val date: LocalDate,
    /** 24-hour time, e.g. "19:00". Kickoffs run 19:00 to 20:45 in 15-minute steps. */
    val time: LocalTime,
    /** Short pitch code. Must exist in `venues.json`. */
    val venue: String,
    /** Team **ref**, referring to `teams[].ref` in this same file. */
    val home: String,
    val away: String,
)
