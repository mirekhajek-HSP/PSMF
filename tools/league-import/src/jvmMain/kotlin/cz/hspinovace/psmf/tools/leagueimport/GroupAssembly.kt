package cz.hspinovace.psmf.tools.leagueimport

import cz.hspinovace.psmf.domain.AppearanceId
import cz.hspinovace.psmf.domain.CardEvent
import cz.hspinovace.psmf.domain.CardReason
import cz.hspinovace.psmf.domain.CardSubject
import cz.hspinovace.psmf.domain.Dismissal
import cz.hspinovace.psmf.domain.Minute
import cz.hspinovace.psmf.domain.PersonName
import cz.hspinovace.psmf.domain.RedCard
import cz.hspinovace.psmf.domain.TeamSide
import cz.hspinovace.psmf.domain.YellowCard
import cz.hspinovace.psmf.domain.yellowsAccumulatedBy

/** A squad member as the group file will hold them: names split, yellows counted. */
data class SquadPlayer(
    val teamSlug: String,
    val surname: String,
    val firstName: String,
    val yellows: Int,
)

data class AssembledTeam(
    val slug: String,
    val name: String,
    val kitLabels: List<String>,
    val players: List<SquadPlayer>,
)

/** One group, read and reconciled: still no ids, which [SeedFiles] assigns. */
data class AssembledGroup(
    val slug: String,
    val teams: List<AssembledTeam>,
    val fixtures: List<ScrapedFixture>,
    val detailedMatches: Int,
)

/**
 * Turns one group's pages into one group's data, and reports everything it
 * could not reconcile instead of guessing.
 *
 * - **Squad = *Statistiky*.** Everyone who has appeared, nobody else.
 * - **Lineups and cards are matched to it by name** ([Names.matchKey]);
 *   a name that matches no row, or more than one, is reported.
 * - **Yellows are counted with the app's own rule**,
 *   `yellowsAccumulatedBy`: two in one match count zero, a yellow and a
 *   straight red count one. Reds are not counted at all.
 * - **Fixtures** are the union of every team page's *Výsledky* and
 *   *Utkání*, one per home-away pair.
 */
class GroupAssembly(
    private val report: ImportReport,
) {
    fun assemble(
        groupSlug: String,
        links: List<TeamLink>,
        kitRows: List<Pair<String, String>>,
        pages: Map<String, TeamPage>,
    ): AssembledGroup {
        val details = pages.values.flatMap { it.details }.distinctBy { it.gameId }
        val evidence = CardsAndSpellings()
        details.forEach { detail ->
            listOf(detail.home, detail.away).forEach { side -> collect(groupSlug, detail, side, pages, evidence) }
        }
        val kits = kitsByTeam(groupSlug, links, kitRows)
        return AssembledGroup(
            slug = groupSlug,
            teams =
                links.map { link ->
                    AssembledTeam(
                        slug = link.slug,
                        name = link.name,
                        kitLabels = kits[link.slug].orEmpty(),
                        players = squad(groupSlug, link, pages[link.slug]?.squad.orEmpty(), evidence),
                    )
                },
            fixtures = fixtures(groupSlug, links, pages),
            detailedMatches = details.size,
        )
    }

    /** What the match details say about each *Statistiky* row: their cards, and how lineups spell them. */
    private class CardsAndSpellings {
        val cards = mutableMapOf<Pair<String, String>, MutableList<ScrapedCard>>()
        val spellings = mutableMapOf<String, String>()
    }

    private fun collect(
        groupSlug: String,
        detail: MatchDetail,
        side: MatchSide,
        pages: Map<String, TeamPage>,
        evidence: CardsAndSpellings,
    ) {
        val squad = pages[side.teamSlug]?.squad.orEmpty().groupBy { Names.matchKey(it.surnameFirst) }
        val where = "$groupSlug ${side.teamSlug}, game ${detail.gameId} (round ${detail.round})"

        fun rowFor(
            name: String,
            role: String,
        ): SquadRow? {
            val rows = squad[Names.matchKey(name)]
            when {
                rows == null -> report.unmatchedNames += "$where: $role '$name'"
                rows.size > 1 -> report.ambiguousNames += "$where: $role '$name' fits ${rows.map { it.surnameFirst }}"
                else -> return rows.single()
            }
            return null
        }
        side.lineup.forEach { name ->
            rowFor(name, "lineup")?.let { evidence.spellings.putIfAbsent(it.key(side.teamSlug), name) }
        }
        side.cards.forEach { card ->
            report.countCard(card.cssClass)
            val row = rowFor(card.name, "card") ?: return@forEach
            evidence.cards.getOrPut(row.key(side.teamSlug) to detail.gameId) { mutableListOf() } += card
        }
    }

    private fun squad(
        groupSlug: String,
        link: TeamLink,
        rows: List<SquadRow>,
        evidence: CardsAndSpellings,
    ): List<SquadPlayer> =
        rows.mapNotNull { row ->
            val key = row.key(link.slug)
            val split = Names.split(row.surnameFirst, evidence.spellings[key])
            if (split == null || PersonName.orNull(split.first) == null || PersonName.orNull(split.second) == null) {
                report.nameOddities +=
                    "$groupSlug ${link.slug}: '${row.surnameFirst}' is not a surname and a first name"
                return@mapNotNull null
            }
            val perMatch = evidence.cards.filterKeys { it.first == key }.values
            perMatch.filter { cards -> cards.count { it.isYellow } >= 2 }.forEach { cards ->
                report.secondYellows += "$groupSlug ${link.slug}: '${row.surnameFirst}', shown as " +
                    cards.map { "${it.cssClass} ${it.minute}´" }
            }
            SquadPlayer(link.slug, split.first, split.second, perMatch.sumOf(::yellowsCounted))
        }

    private fun fixtures(
        groupSlug: String,
        links: List<TeamLink>,
        pages: Map<String, TeamPage>,
    ): List<ScrapedFixture> {
        val teams = links.map { it.slug }.toSet()
        val byPair = linkedMapOf<Pair<String, String>, ScrapedFixture>()
        pages.values.flatMap { it.fixtures }.forEach { fixture ->
            val pair = fixture.homeSlug to fixture.awaySlug
            val known = byPair[pair]
            when {
                fixture.homeSlug !in teams || fixture.awaySlug !in teams -> {
                    report.fixtureOddities += "$groupSlug: ${pair.first}–${pair.second} names a team not in the group"
                }

                known == null -> {
                    byPair[pair] = fixture
                }

                known != fixture -> {
                    report.fixtureOddities +=
                        "$groupSlug: ${pair.first}–${pair.second} listed two ways: $known / $fixture"
                }
            }
        }
        return byPair.values.filter { fixture ->
            val complete = fixture.date != null && fixture.time != null && fixture.venue != null
            if (!complete) report.fixtureOddities += "$groupSlug: not scheduled, left out: $fixture"
            complete
        }
    }

    private fun kitsByTeam(
        groupSlug: String,
        links: List<TeamLink>,
        kitRows: List<Pair<String, String>>,
    ): Map<String, List<String>> {
        val bySpelling = kitRows.associate { (team, cell) -> Names.matchKey(team) to cell }
        kitRows
            .filter { (team, _) -> links.none { Names.matchKey(it.name) == Names.matchKey(team) } }
            .forEach { (team, cell) ->
                report.kitOddities +=
                    "$groupSlug: /dresy/ lists '$team' ('$cell'), not a team here"
            }
        return links.associate { link ->
            val cell = bySpelling[Names.matchKey(link.name)]
            val labels = cell?.let(KitLabels::split).orEmpty()
            when {
                cell == null -> {
                    report.kitOddities += "$groupSlug ${link.slug}: no row in /dresy/"
                }

                labels.size > 2 -> {
                    report.kitOddities += "$groupSlug ${link.slug}: ${labels.size} kits in '$cell'"
                }

                labels.any { label -> label.any { it in ODD_KIT_CHARACTERS } } -> {
                    report.kitOddities += "$groupSlug ${link.slug}: unusual label '$cell'"
                }
            }
            link.slug to labels
        }
    }

    private fun SquadRow.key(teamSlug: String) = "$teamSlug/${Names.matchKey(surnameFirst)}"

    private companion object {
        val ODD_KIT_CHARACTERS = setOf('/', '+', '(', ')', ';', '&')
        val SUBJECT = CardSubject.Player(AppearanceId("whoever"))
        val REASON = CardReason("psmf.cz")

        /** The app's own accumulation rule, applied to one player's cards in one match. */
        fun yellowsCounted(cards: List<ScrapedCard>): Int =
            cards
                .mapNotNull<ScrapedCard, CardEvent> { card ->
                    val minute = Minute.Played(card.minute ?: 0)
                    when {
                        card.isYellow -> YellowCard(minute, TeamSide.HOME, SUBJECT, REASON)
                        card.isRed -> RedCard(minute, TeamSide.HOME, SUBJECT, REASON, Dismissal.STRAIGHT)
                        else -> null
                    }
                }.yellowsAccumulatedBy(SUBJECT)
    }
}
