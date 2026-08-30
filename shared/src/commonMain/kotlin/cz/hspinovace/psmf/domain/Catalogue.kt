package cz.hspinovace.psmf.domain

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.serialization.Serializable

/**
 * League reference data: the things the referee selects from and never
 * invents. A referee creating a team is a data-integrity failure, not a
 * feature (analysis section 7).
 *
 * # Identity
 *
 * Every entity here carries **an opaque [id] and a readable [ref]**, and
 * they do different jobs.
 *
 * - The `id` is a UUID minted once and **never regenerated**. Persisted
 *   matches on a device reference these, so a regenerated id orphans every
 *   report that mentioned it.
 * - The `ref` is a slug for hand-editing and debugging, and it is what the
 *   seed files use to point at each other. It is allowed to go stale — a
 *   team that renames keeps its old ref, which is the point.
 *
 * The previous model encoded names in ids (`t-kominici`, `p-kominici-01`).
 * A rename or a transfer then made the id a lie, and the analysis permits
 * one transfer per season. See the seed README for the full rule.
 */

@Serializable
data class Season(
    val id: SeasonId,
    /** e.g. "2026 Hanspaulská liga podzim". */
    val name: String,
)

/**
 * The unit of competition, e.g. `6. liga K`.
 *
 * Not merely a label: yellow-card accumulation is per group per season
 * (analysis section 2.6), and the group is what appears in the `Liga`
 * field of the report header.
 */
@Serializable
data class Group(
    val id: GroupId,
    val seasonId: SeasonId,
    /** Display name, e.g. "6. liga K". */
    val name: String,
    /** As written in the `Liga` header field, e.g. "6K". */
    val reportCode: String,
    /**
     * Half length in minutes for this group.
     *
     * 2 x 30 is universal across Hanspaulská liga as far as anyone knows,
     * but it is read from the group definition rather than hardcoded, so a
     * competition with a different length costs a data change and not a
     * code change. **The league sets this. A referee changing it is a
     * defect**, so nothing in the UI may edit it.
     */
    val halfLengthMinutes: Int = Minute.HALF_LENGTH,
    /**
     * How many periods are played. Two everywhere in Hanspaulská liga, but
     * veteran and futsal competitions may differ, so it is data for the
     * same reason [halfLengthMinutes] is.
     */
    val periods: Int = DEFAULT_PERIODS,
) {
    init {
        require(halfLengthMinutes > 0) { "Half length must be positive" }
        require(periods > 0) { "A match must have at least one period" }
    }

    /** Full match length, e.g. 60 minutes for 2 x 30. */
    val fullLengthMinutes: Int get() = halfLengthMinutes * periods

    companion object {
        const val DEFAULT_PERIODS: Int = 2
    }
}

/**
 * One of the **two kit sets a team owns**.
 *
 * A team does not have "a kit colour". It owns two and picks one per match
 * so that the two sides are not in similar colours, which is exactly why
 * `Barva dresů` sits on the lineup block of the ZoU and is filled in at the
 * match: the form records what was actually worn that day.
 */
@Serializable
data class Kit(
    val id: KitId,
    /**
     * **Verbatim from PSMF, and authoritative for the report.**
     *
     * Never derived from [colours]. "bílo-černá" is not mechanically
     * obtainable from `["bílá", "černá"]` — the first element takes a
     * different grammatical suffix in Czech — and the ZoU takes exactly
     * what PSMF writes.
     */
    val label: String,
    /**
     * **For the app only**: team chips and clash hints. Never written on
     * the report; [label] is what gets written.
     */
    val colours: List<String> = emptyList(),
) {
    init {
        require(label.isNotBlank()) {
            "A kit label cannot be blank: the report cannot be generated without Barva dresů."
        }
    }
}

@Serializable
data class Team(
    val id: TeamId,
    /** Readable slug. Identity is [id]; this is for humans. */
    val ref: String,
    val groupId: GroupId,
    val name: String,
    /**
     * The kit sets this team owns. **Order is meaningful: the first is the
     * primary**, and is what a lineup defaults to.
     */
    val kits: List<Kit>,
) {
    init {
        require(kits.isNotEmpty()) { "$name owns no kits; every team has at least one." }
        require(kits.distinctBy { it.id }.size == kits.size) { "$name has two kits with the same id" }
    }

    val primaryKit: Kit get() = kits.first()

    fun kit(id: KitId): Kit? = kits.firstOrNull { it.id == id }

    fun owns(kitId: KitId): Boolean = kit(kitId) != null
}

/** Where a player record came from, and therefore what may be done to it. */
@Serializable
enum class PlayerOrigin {
    /** Came from league data. Has, or will have, an RP number from PSMF. */
    LEAGUE_RECORD,

    /**
     * Added by the referee at the pitch, because someone turned up who was
     * not in the squad list.
     *
     * **Carries no RP number and cannot be given one by a user** — see
     * [Player.addedAtThePitch]. The flag exists so these can be reconciled
     * against PSMF's database once the player is registered.
     */
    ADDED_AT_PITCH,
}

/**
 * A registered player.
 *
 * The jersey number is deliberately absent: it belongs to the [Appearance].
 * What lives here is only the *default* to pre-fill, since numbers change
 * between matches (analysis section 3.6).
 *
 * Identification is **three separate fields**, not one polymorphic one; see
 * [RpNumber] for why. What was actually written on the report is a
 * different thing again and lives on the appearance as
 * [ReportedIdentification].
 */
@Serializable
data class Player(
    val id: PlayerId,
    /**
     * Readable slug, and **deliberately not team-scoped**: the analysis
     * permits one transfer per season, and a ref like `kominici-01` would
     * change on transfer, which would in turn mint a new id and orphan
     * every match the player already appears in.
     */
    val ref: String,
    val teamId: TeamId,
    val name: PlayerName,
    /**
     * League-issued, immutable, **never user-editable**. Null until PSMF
     * supplies RP numbers, which is the one roster dependency that cannot
     * be met from public data (analysis section 2.9).
     */
    val rpNumber: RpNumber?,
    /** The fallback a person enters when there is no RP number to use. */
    val dateOfBirth: LocalDate?,
    /** Blocked on A28; see [BirthNumber]. */
    val birthNumber: BirthNumber?,
    /** Last known number, offered as a default and corrected by exception. */
    val defaultJerseyNumber: JerseyNumber? = null,
    val origin: PlayerOrigin = PlayerOrigin.LEAGUE_RECORD,
    /** Advisory only, and stale by construction. See [DisciplinaryRecord]. */
    val discipline: DisciplinaryRecord? = null,
) {
    init {
        require(rpNumber != null || dateOfBirth != null || birthNumber != null) {
            "${name.asWrittenOnReport} has no RP number, date of birth or birth number. " +
                "A player who cannot be identified at all cannot be put on a report."
        }
        require(origin != PlayerOrigin.ADDED_AT_PITCH || rpNumber == null) {
            "${name.asWrittenOnReport} was added at the pitch and carries an RP number. " +
                "RP numbers are issued by PSMF and must never be entered by a user."
        }
    }

    /**
     * What to pre-fill in the `Číslo RP` column, for the three situations
     * analysis section 2.5 distinguishes.
     *
     * 1. registered, card present → the RP number
     * 2. registered, card **not to hand** → the date of birth, which is the
     *    form's own printed rule
     * 3. not yet registered → whichever fallback exists
     *
     * Null when nothing can be written — a player with an RP number on file
     * who did not bring their card and whose date of birth is unknown. The
     * referee has to supply a value, which is why [Appearance] requires one
     * rather than accepting null.
     */
    fun identificationFor(registrationCardPresent: Boolean): ReportedIdentification? =
        when {
            registrationCardPresent && rpNumber != null -> ReportedIdentification.of(rpNumber)
            dateOfBirth != null -> ReportedIdentification.of(dateOfBirth)
            birthNumber != null -> ReportedIdentification.of(birthNumber)
            else -> null
        }

    /**
     * The reconciliation an [PlayerOrigin.ADDED_AT_PITCH] player is waiting
     * for. Only PSMF data reaches this; there is no user-facing path.
     */
    fun registeredWith(issued: RpNumber): Player = copy(rpNumber = issued, origin = PlayerOrigin.LEAGUE_RECORD)

    companion object {
        /**
         * Someone who turned up and is not in the squad list.
         *
         * Screen 3 collects **first name, surname and date of birth. No RP
         * field is offered**, and this signature is what makes that true in
         * the model rather than only in the UI.
         */
        fun addedAtThePitch(
            id: PlayerId,
            ref: String,
            teamId: TeamId,
            name: PlayerName,
            dateOfBirth: LocalDate,
            defaultJerseyNumber: JerseyNumber? = null,
        ): Player =
            Player(
                id = id,
                ref = ref,
                teamId = teamId,
                name = name,
                rpNumber = null,
                dateOfBirth = dateOfBirth,
                birthNumber = null,
                defaultJerseyNumber = defaultJerseyNumber,
                origin = PlayerOrigin.ADDED_AT_PITCH,
            )
    }
}

/** A pitch. Codes are league-wide, not group-specific (analysis section 2.2). */
@Serializable
data class Venue(
    val code: VenueCode,
    /**
     * Null for now. PSMF publishes the short codes; the long names are not
     * in the analysis and are not worth inventing.
     */
    val name: String? = null,
)

/**
 * A scheduled match. Public and scrapeable per analysis section 2.9,
 * though for the demo it comes from seed data.
 */
@Serializable
data class Fixture(
    val id: FixtureId,
    /** Readable slug, e.g. "6k-r1-1". */
    val ref: String,
    val groupId: GroupId,
    val round: Int,
    val date: LocalDate,
    val time: LocalTime,
    val venue: VenueCode,
    val homeTeamId: TeamId,
    val awayTeamId: TeamId,
) {
    init {
        require(round > 0) { "Round number must be positive, was $round" }
        require(homeTeamId != awayTeamId) { "A team cannot play itself" }
    }

    fun teamId(side: TeamSide): TeamId =
        when (side) {
            TeamSide.HOME -> homeTeamId
            TeamSide.AWAY -> awayTeamId
        }
}
