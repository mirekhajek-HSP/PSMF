package cz.hspinovace.psmf.usecase

import cz.hspinovace.psmf.data.league.LeagueRepository
import cz.hspinovace.psmf.data.team.FollowedTeamRepository
import cz.hspinovace.psmf.domain.Group
import cz.hspinovace.psmf.domain.Team

/** One team, as the Týmy tab lists it. */
data class TeamCard(
    val team: Team,
    val group: Group,
    val followed: Boolean,
    /**
     * How many players the seed data holds for this team.
     *
     * Shown because an empty squad is the most likely shape of bad
     * reference data, and a referee who can see it before the match is a
     * referee who can raise it before the match.
     */
    val squadSize: Int,
)

/** A league heading and the teams under it. */
data class LeagueTeams(
    val group: Group,
    val teams: List<TeamCard>,
)

/**
 * The Týmy tab's contents.
 *
 * Followed teams are a separate list rather than a flag on the browse
 * list, because they are the reason the tab exists: the referee's own
 * handful out of the league's hundreds.
 */
data class TeamDirectory(
    val query: String,
    val followed: List<TeamCard>,
    val leagues: List<LeagueTeams>,
) {
    val searching: Boolean get() = query.isNotBlank()

    val isEmpty: Boolean get() = followed.isEmpty() && leagues.all { it.teams.isEmpty() }
}

/**
 * Search and browse every bundled team.
 *
 * **The query filters the followed list too.** A search field that leaves
 * a section unfiltered looks broken — the referee typed a name and a team
 * that does not match is still on screen. Following a team is a shortcut,
 * not an exemption.
 *
 * Teams are ordered by name inside every section, including the followed
 * one. The order they were followed in is recorded and deliberately not
 * used for display: a list that is neither alphabetical nor anything the
 * referee can predict is a list they have to read all of.
 */
class BrowseTeams(
    private val league: LeagueRepository,
    private val followedTeams: FollowedTeamRepository,
) {
    suspend operator fun invoke(query: String = ""): TeamDirectory {
        val followed = followedTeams.followed()
        val needle = query.foldForSearch()

        val cards =
            league.groups().flatMap { leagueGroup ->
                leagueGroup.teams.map { team ->
                    TeamCard(
                        team = team,
                        group = leagueGroup.group,
                        followed = team.id in followed,
                        squadSize = leagueGroup.playersOf(team.id).size,
                    )
                }
            }

        val matching = cards.filter { needle.isEmpty() || it.matches(needle) }

        return TeamDirectory(
            query = query,
            followed = matching.filter { it.followed }.sortedBy { it.team.name.foldForSearch() },
            leagues =
                matching
                    .groupBy { it.group }
                    .entries
                    .sortedBy { it.key.name }
                    .map { (group, teams) ->
                        LeagueTeams(group, teams.sortedBy { it.team.name.foldForSearch() })
                    },
        )
    }

    /**
     * Matched on the name and on the readable ref.
     *
     * The ref is in because it is the slug the seed files use, and anyone
     * chasing bad data types `sp-sumys` rather than hunting for the ý.
     */
    private fun TeamCard.matches(needle: String): Boolean =
        team.name.foldForSearch().contains(needle) || team.ref.foldForSearch().contains(needle)
}

/**
 * Lower-cased and stripped of Czech diacritics, for comparison only.
 *
 * **Typing `sumys` has to find `Sp. Sumýš`.** Every second team name in the
 * league carries a diacritic and a phone keyboard puts them behind a long
 * press, so a search that demands them is a search most referees will
 * decide is broken.
 *
 * A table rather than Unicode normalisation, because `java.text.Normalizer`
 * does not exist in common code and the alphabet that actually occurs here
 * is closed: Czech, plus the Slovak letters that turn up in surnames.
 * Anything unlisted passes through unchanged, so an unexpected letter
 * degrades to an exact match rather than vanishing.
 */
internal fun String.foldForSearch(): String {
    // Lowered before `buildString`, because inside it `this` is the
    // StringBuilder and `trim()` would resolve against that instead.
    val lowered = trim().lowercase()
    return buildString(lowered.length) {
        for (character in lowered) {
            val at = ACCENTED.indexOf(character)
            append(if (at < 0) character else PLAIN[at])
        }
    }
}

// Two aligned rows, so a wrong pairing is visible rather than buried in a
// map literal. `FoldForSearchTest` asserts they are the same length.
internal const val ACCENTED = "áäčďéěíĺľňóôŕřšťúůýž"
internal const val PLAIN = "aacdeeillnoorrstuuyz"
