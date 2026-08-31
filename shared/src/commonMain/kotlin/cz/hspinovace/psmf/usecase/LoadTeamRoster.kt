package cz.hspinovace.psmf.usecase

import cz.hspinovace.psmf.data.league.LeagueRepository
import cz.hspinovace.psmf.data.team.FollowedTeamRepository
import cz.hspinovace.psmf.data.team.JerseyOverrideRepository
import cz.hspinovace.psmf.domain.Group
import cz.hspinovace.psmf.domain.JerseyNumber
import cz.hspinovace.psmf.domain.Kit
import cz.hspinovace.psmf.domain.Player
import cz.hspinovace.psmf.domain.PlayerId
import cz.hspinovace.psmf.domain.Team
import cz.hspinovace.psmf.domain.TeamId

/**
 * One player on a team screen.
 *
 * [jerseyNumber] is the number in force — the referee's correction where
 * there is one, the seed value otherwise. [corrected] says which, so the
 * screen can offer to put it back and the referee can see at a glance what
 * they have changed.
 */
data class RosterRow(
    val player: Player,
    val jerseyNumber: JerseyNumber?,
    val corrected: Boolean,
) {
    /**
     * The `Číslo RP` column's value, where the league has issued one.
     *
     * Null for every player in the bundled data as it stands: PSMF have not
     * supplied RP numbers, which is the one roster dependency that cannot
     * be met from public sources. The screen shows the field only when
     * there is something in it rather than a row of dashes.
     */
    val rpNumber: String? get() = player.rpNumber?.value
}

/**
 * A team as the Týmy tab shows it.
 *
 * [kits] is both sets, in order, because a team owns two and the primary is
 * merely the first — see [Team.kits]. Which one was worn is a fact about a
 * match and is not here.
 */
data class TeamRoster(
    val group: Group,
    val team: Team,
    val followed: Boolean,
    val rows: List<RosterRow>,
) {
    val kits: List<Kit> get() = team.kits
}

/**
 * Everything a team screen needs, for one team.
 *
 * # What may be edited, and what may not
 *
 * **Jersey numbers, and only jersey numbers.** A default number is a
 * standing attribute of a player, which is what a team screen is for.
 * Names, RP numbers and card history are league records: a referee editing
 * a registered player is a data-integrity failure, which is the same rule
 * the lineup screen already follows.
 *
 * **Absence is not here.** Absence is a fact about one match, not about a
 * player. Set on a team screen it would either persist forever or need a
 * fixture attached, at which point it is the lineup screen with extra
 * steps. It stays on the lineup screen, where the referee is standing next
 * to the captain.
 *
 * # Ordering
 *
 * By surname, then first name — **not by number**. The numbers are the
 * thing being edited, and a list that reorders itself while the referee
 * types into it is hostile. The report orders by number; a reference screen
 * orders by the name being looked up.
 */
class LoadTeamRoster(
    private val league: LeagueRepository,
    private val followedTeams: FollowedTeamRepository,
    private val overrides: JerseyOverrideRepository,
) {
    suspend operator fun invoke(teamId: TeamId): TeamRoster? {
        val corrected: Map<PlayerId, JerseyNumber> = overrides.overrides()
        val followed = followedTeams.followed()

        return league.groups().firstNotNullOfOrNull { leagueGroup ->
            leagueGroup.team(teamId)?.let { team ->
                TeamRoster(
                    group = leagueGroup.group,
                    team = team,
                    followed = team.id in followed,
                    rows =
                        leagueGroup
                            .playersOf(team.id)
                            .sortedWith(bySurnameThenFirstName)
                            .map { player ->
                                RosterRow(
                                    player = player,
                                    // The league repository has already applied the
                                    // override; this only asks whether it did.
                                    jerseyNumber = player.defaultJerseyNumber,
                                    corrected = player.id in corrected,
                                )
                            },
                )
            }
        }
    }

    private val bySurnameThenFirstName =
        compareBy<Player>(
            {
                it.name.surname.value
                    .foldForSearch()
            },
            {
                it.name.firstName.value
                    .foldForSearch()
            },
        )
}

/**
 * Records a corrected default jersey number.
 *
 * A use case rather than a call straight to the repository, because the
 * screen should not know that "clear the correction" and "set it to
 * nothing" are the same gesture: [number] null restores the seed value.
 *
 * **Nothing already written moves.** The number on a report belongs to the
 * appearance, snapshotted when the referee filled the lineup in.
 * `JerseyOverrideTest` proves it by storing a report, changing a default,
 * and reading the report back out.
 */
class SetDefaultJerseyNumber(
    private val overrides: JerseyOverrideRepository,
) {
    suspend operator fun invoke(
        playerId: PlayerId,
        number: JerseyNumber?,
    ) = overrides.setDefaultJerseyNumber(playerId, number)
}

/** Follows or unfollows a team. */
class ToggleFollowedTeam(
    private val followedTeams: FollowedTeamRepository,
) {
    suspend operator fun invoke(
        teamId: TeamId,
        followed: Boolean,
    ) = followedTeams.setFollowed(teamId, followed)
}
