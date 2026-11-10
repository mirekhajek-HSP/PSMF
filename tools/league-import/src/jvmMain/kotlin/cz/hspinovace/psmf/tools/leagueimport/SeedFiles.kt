package cz.hspinovace.psmf.tools.leagueimport

import cz.hspinovace.psmf.data.seed.SeedDisciplineDto
import cz.hspinovace.psmf.data.seed.SeedFixtureDto
import cz.hspinovace.psmf.data.seed.SeedGroupDto
import cz.hspinovace.psmf.data.seed.SeedIdentity
import cz.hspinovace.psmf.data.seed.SeedIndexDto
import cz.hspinovace.psmf.data.seed.SeedIndexEntryDto
import cz.hspinovace.psmf.data.seed.SeedKitDto
import cz.hspinovace.psmf.data.seed.SeedPlayerDto
import cz.hspinovace.psmf.data.seed.SeedTeamDto
import cz.hspinovace.psmf.data.seed.SeedVenueDto
import cz.hspinovace.psmf.data.seed.SeedVenuesDto
import kotlinx.datetime.LocalDate

/** The seed files as they stand before a run: the ids it must keep. */
data class ExistingSeed(
    val index: SeedIndexDto?,
    val groups: List<SeedGroupDto>,
    val venues: SeedVenuesDto?,
) {
    companion object {
        val EMPTY = ExistingSeed(null, emptyList(), null)
    }
}

/** Index, groups and venues, ready to write. */
data class SeedOutput(
    val index: SeedIndexDto,
    val groups: List<SeedGroupDto>,
    val venues: SeedVenuesDto,
)

/**
 * Assigns every id, **keeping every id whose ref the files already hold**
 * -- the seed README's one rule, with `SeedIdentity` doing the keeping.
 *
 * # Refs
 *
 * - **Team**: PSMF's own slug, `krabice`. Unique within a group.
 * - **Fixture**: `6k-krabice-vs-hustec`. Upcoming fixtures carry no PSMF
 *   id, and a pairing meets once a half-season, so the pair is the natural
 *   key. It survives a reschedule, which is what PSMF changes.
 * - **Player**: the name, `belohlavek-jan`, **not team-scoped** (a transfer
 *   keeps it) and league-wide. Two different players with one name would
 *   share it, so the rule is: the first, in import order (groups a to l,
 *   teams by slug, squads as listed), keeps the plain slug; each later one
 *   gets `-<team slug>`, then `-<group>-<team slug>` if that is taken too.
 *   A re-run never re-decides: an existing player is found by group, team
 *   and name first, and keeps the ref they already have.
 *
 * # Nothing is dropped
 *
 * A ref in the previous files that is not on the site now -- a team that
 * withdrew, a player PSMF renamed, a fixture taken off -- is **carried
 * over unchanged and reported**. Matches recorded against it keep resolving.
 * Deleting it is a person's decision, made in the report, not the tool's.
 */
class SeedFiles(
    private val existing: ExistingSeed,
    private val report: ImportReport,
    private val asOf: LocalDate,
    private val mintId: () -> String,
) {
    private val existingPlayers =
        existing.groups.flatMap { group ->
            group.teams.flatMap { team -> team.players.map { ExistingPlayer(group.id, team.ref, it) } }
        }
    private val playerIdsByRef = existingPlayers.associate { it.dto.ref to it.dto.id }
    private val claimedPlayers = mutableSetOf<String>()
    private val givenPlayerRefs = mutableSetOf<String>()

    fun build(
        groups: List<AssembledGroup>,
        pitches: List<Pitch>,
    ): SeedOutput {
        val onTheSite =
            groups
                .flatMap { g ->
                    g.teams.flatMap { t -> t.players.map { Triple(g.competition.groupId(g.slug), t.slug, it.key) } }
                }.toSet()
        // Two passes: every player is claimed before anything is called
        // departed, or one who moved to a later group would be carried twice.
        val built = groups.map { group(it, onTheSite) }.map(::withDeparted)
        // The index is written from this run's groups alone: one the files hold and
        // the run did not read would leave the app without a word.
        val dropped = existing.groups.map { it.id } - built.map { it.id }.toSet()
        check(dropped.isEmpty()) {
            "This run would take $dropped out of index.json: name every competition the files hold (--competition)"
        }
        return SeedOutput(
            index =
                SeedIndexDto(
                    built.zip(groups) { group, assembled ->
                        SeedIndexEntryDto(
                            id = group.id,
                            name = group.name,
                            seasonId = assembled.competition.seasonId,
                            seasonName = assembled.competition.seasonName,
                            file = "${group.id}.json",
                        )
                    },
                ),
            groups = built,
            venues = venues(pitches, built),
        )
    }

    private fun group(
        assembled: AssembledGroup,
        onTheSite: Set<Triple<String, String, String>>,
    ): SeedGroupDto {
        val competition = assembled.competition
        val id = competition.groupId(assembled.slug)
        val before = existing.groups.firstOrNull { it.id == id }
        val teamIds =
            SeedIdentity.assign(
                before
                    ?.teams
                    ?.associate {
                        it.ref to it.id
                    }.orEmpty(),
                assembled.teams.map { it.slug },
            ) { mintId() }
        val teams = assembled.teams.map { team(assembled, it, teamIds.getValue(it.slug), before, onTheSite) }
        val fixtureRefs = assembled.fixtures.map { "$id-${it.homeSlug}-vs-${it.awaySlug}" }
        val fixtureIds =
            SeedIdentity.assign(
                before
                    ?.fixtures
                    ?.associate {
                        it.ref to it.id
                    }.orEmpty(),
                fixtureRefs,
            ) { mintId() }
        val fixtures =
            assembled.fixtures.map { fixture ->
                val ref = "$id-${fixture.homeSlug}-vs-${fixture.awaySlug}"
                SeedFixtureDto(
                    id = fixtureIds.getValue(ref),
                    ref = ref,
                    round = fixture.round,
                    date = requireNotNull(fixture.date),
                    time = requireNotNull(fixture.time),
                    venue = requireNotNull(fixture.venue),
                    home = fixture.homeSlug,
                    away = fixture.awaySlug,
                )
            }
        return SeedGroupDto(
            id = id,
            name = competition.groupName(assembled.slug),
            reportCode = competition.reportCode(assembled.slug),
            halfLengthMinutes = HALF_LENGTH,
            periods = PERIODS,
            teams = teams,
            fixtures = (fixtures + departedFixtures(id, before, fixtures)).sortedWith(FIXTURE_ORDER),
        )
    }

    /** The second pass: whatever the previous files held that nobody on the site claimed. */
    private fun withDeparted(group: SeedGroupDto): SeedGroupDto {
        val before = existing.groups.firstOrNull { it.id == group.id }
        val here =
            group.teams.map { team ->
                team.copy(
                    players =
                        team.players + departedPlayers(group.id, team.ref, team.players),
                )
            }
        val gone =
            departedTeams(group.id, before, group.teams).map { team ->
                team.copy(players = team.players.filter { it.ref !in claimedPlayers })
            }
        return group.copy(teams = (here + gone).sortedBy { it.ref })
    }

    private fun team(
        group: AssembledGroup,
        team: AssembledTeam,
        teamId: String,
        before: SeedGroupDto?,
        onTheSite: Set<Triple<String, String, String>>,
    ): SeedTeamDto {
        val competition = group.competition
        val groupId = competition.groupId(group.slug)
        val previous = before?.teams?.firstOrNull { it.ref == team.slug }
        val kitIds = previous?.kits?.associate { it.label to it.id }.orEmpty()
        val players =
            team.players.map { player ->
                val ref = refFor(competition, groupId, team.slug, player, onTheSite)
                SeedPlayerDto(
                    id = playerIdsByRef[ref] ?: mintId(),
                    ref = ref,
                    surname = player.surname,
                    firstName = player.firstName,
                    discipline = SeedDisciplineDto(player.yellows, asOf),
                )
            }
        return SeedTeamDto(
            id = teamId,
            ref = team.slug,
            name = team.name,
            kits = team.kitLabels.map { SeedKitDto(kitIds[it] ?: mintId(), it, KitLabels.colours(it)) },
            players = players,
        )
    }

    /** See the class comment, *Refs*. */
    private fun refFor(
        competition: Competition,
        groupId: String,
        teamSlug: String,
        player: SquadPlayer,
        onTheSite: Set<Triple<String, String, String>>,
    ): String {
        val unclaimed = existingPlayers.filter { it.key == player.key && it.dto.ref !in claimedPlayers }
        val sameTeam = unclaimed.firstOrNull { it.groupId == groupId && it.teamRef == teamSlug }
        // A transfer: the one existing player of that name in this competition, no
        // longer on the site where they were. Never from another competition: a
        // veteran is not the league 6 player who left, whatever their name.
        val moved =
            unclaimed
                .filter { competition.holds(it.groupId) }
                .singleOrNull()
                ?.takeIf { Triple(it.groupId, it.teamRef, it.key) !in onTheSite }
        (sameTeam ?: moved)?.let {
            claimedPlayers += it.dto.ref
            givenPlayerRefs += it.dto.ref
            return it.dto.ref
        }
        val base = Names.slug("${player.surname} ${player.firstName}")
        val taken = playerIdsByRef.keys + givenPlayerRefs
        val ref =
            sequenceOf(base, "$base-$teamSlug", "$base-$groupId-$teamSlug")
                .plus(generateSequence(2) { it + 1 }.map { "$base-$teamSlug-$it" })
                .first { it !in taken }
        if (ref != base) {
            report.refCollisions += "'$base' was taken; ${player.surname} ${player.firstName} " +
                "($groupId $teamSlug) is '$ref'"
        }
        givenPlayerRefs += ref
        return ref
    }

    private fun departedTeams(
        groupId: String,
        before: SeedGroupDto?,
        now: List<SeedTeamDto>,
    ): List<SeedTeamDto> =
        before
            ?.teams
            .orEmpty()
            .filter { old -> now.none { it.ref == old.ref } }
            .onEach { report.departed += "$groupId team '${it.ref}' (${it.name}), with ${it.players.size} players" }

    private fun departedPlayers(
        groupId: String,
        teamRef: String,
        now: List<SeedPlayerDto>,
    ): List<SeedPlayerDto> =
        existingPlayers
            .filter { it.groupId == groupId && it.teamRef == teamRef && it.dto.ref !in claimedPlayers }
            .filter { old -> now.none { it.ref == old.dto.ref } }
            .map { it.dto }
            .onEach { report.departed += "$groupId $teamRef player '${it.ref}'" }

    private fun departedFixtures(
        groupId: String,
        before: SeedGroupDto?,
        now: List<SeedFixtureDto>,
    ): List<SeedFixtureDto> =
        before
            ?.fixtures
            .orEmpty()
            .filter { old -> now.none { it.ref == old.ref } }
            .onEach { report.departed += "$groupId fixture '${it.ref}'" }

    /** Every pitch /hriste/ lists, any code a fixture uses that it does not, and any kept from before. */
    private fun venues(
        pitches: List<Pitch>,
        groups: List<SeedGroupDto>,
    ): SeedVenuesDto {
        val listed = pitches.distinctBy { it.code }.map { SeedVenueDto(it.code, it.name, it.address) }
        val used = groups.flatMap { g -> g.fixtures.map { it.venue } }.toSet()
        val unlisted =
            (used - listed.map { it.code }.toSet()).map {
                report.fixtureOddities += "venue '$it' is used by a fixture and not listed on /hriste/"
                SeedVenueDto(it)
            }
        val kept =
            existing.venues
                ?.venues
                .orEmpty()
                .filter { old -> (listed + unlisted).none { it.code == old.code } }
                .onEach { report.departed += "venue '${it.code}'" }
        return SeedVenuesDto((listed + unlisted + kept).sortedBy { it.code })
    }

    private data class ExistingPlayer(
        val groupId: String,
        val teamRef: String,
        val dto: SeedPlayerDto,
    ) {
        val key: String get() = Names.matchKey("${dto.surname} ${dto.firstName}")
    }

    companion object {
        /** 2 x 30 in every competition read so far, Ultraveteránská liga included. */
        const val HALF_LENGTH = 30
        const val PERIODS = 2

        private val FIXTURE_ORDER =
            compareBy<SeedFixtureDto>({ it.round }, { it.date }, { it.time }, { it.ref })
    }
}

private val SquadPlayer.key: String get() = Names.matchKey("$surname $firstName")
