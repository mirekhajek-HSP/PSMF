package cz.hspinovace.psmf.tools.leagueimport

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

/**
 * The four kinds of psmf.cz page the importer reads, as of 2026-10-07.
 *
 * Every function here takes HTML and returns what it says, and nothing
 * else: no ids, no matching, no guessing. When the site changes shape these
 * are what break, and `PagesTest` -- against pages saved on 2026-10-07 --
 * is what says so before a run writes anything.
 */
object Pages {
    /** The groups a league page links to: `6-a` ... `6-l`. */
    fun groupsOfLeague(
        html: String,
        seasonPath: String,
        league: Int,
    ): List<String> {
        val pattern = Regex("^" + Regex.escape(seasonPath) + "(" + league + "-[a-z]+)/$")
        return Jsoup
            .parse(html)
            .select("a[href]")
            .mapNotNull { pattern.find(it.attr("href"))?.groupValues?.get(1) }
            .distinct()
            .sorted()
    }

    /** The teams a group page links to, by slug. */
    fun teamsOfGroup(
        html: String,
        seasonPath: String,
        groupSlug: String,
    ): List<TeamLink> {
        val pattern = Regex("^" + Regex.escape("$seasonPath$groupSlug/tymy/") + "([^/]+)/$")
        return Jsoup
            .parse(html)
            .select("a[href]")
            .mapNotNull { link ->
                pattern.find(link.attr("href"))?.let { match ->
                    TeamLink(match.groupValues[1], link.attr("title").ifBlank { link.text() }.trim())
                }
            }.distinctBy { it.slug }
            .sortedBy { it.slug }
    }

    /**
     * `/dresy/`: team name to the kit cell, verbatim -- `černá, bílá`. A
     * comma separates a team's two kits; splitting is [KitLabels]' job.
     */
    fun kits(html: String): List<Pair<String, String>> =
        Jsoup
            .parse(html)
            .select("section.component--content table tr")
            .mapNotNull { row ->
                val cells = row.select("td")
                if (cells.size == 2) cells[0].text().trim() to cells[1].text().trim() else null
            }

    /** `/hriste/`: name, code(s), and the address as the first line of the description. */
    fun pitches(html: String): List<Pitch> =
        Jsoup
            .parse(html)
            .select("table tr")
            .flatMap { row ->
                val cells = row.select("td")
                if (cells.size < PITCH_COLUMNS) return@flatMap emptyList()
                val codes = cells[1].select("a").map { it.text().trim() }.filter { CODE.matches(it) }
                val name = cells[0].text().trim().ifBlank { null }
                val address =
                    cells[2]
                        .textNodes()
                        .map { it.text().trim() }
                        .firstOrNull { it.isNotEmpty() }
                codes.map { Pitch(it, name, address) }
            }

    /** A team page: its fixtures, its squad, and its played matches in detail. */
    fun team(html: String): TeamPage {
        val document = Jsoup.parse(html)
        return TeamPage(
            fixtures =
                section(document, "Výsledky")
                    ?.select("table.games-old-table tr")
                    .orEmpty()
                    .mapNotNull { fixtureRow(it, played = true) } +
                    section(document, "Utkání")
                        ?.select("table.games-new-table tr")
                        .orEmpty()
                        .mapNotNull { fixtureRow(it, played = false) },
            squad =
                section(document, "Statistiky")
                    ?.select("tr")
                    .orEmpty()
                    .mapNotNull(::squadRow),
            details = document.select("div.component__table-wrap[id^=GameResultItem]").mapNotNull(::matchDetail),
        )
    }

    // -----------------------------------------------------------------------

    /** The block under the `h2` whose text is exactly [title]. */
    private fun section(
        document: Document,
        title: String,
    ): Element? = document.select("h2.component__title").firstOrNull { it.text().trim() == title }?.parent()

    private fun fixtureRow(
        row: Element,
        played: Boolean,
    ): ScrapedFixture? {
        val cells = row.select("> td")
        val teams = teamSlugs(cells.getOrNull(TEAMS_COLUMN) ?: return null)
        if (teams.size != 2) return null
        return ScrapedFixture(
            round = cells.getOrNull(ROUND_COLUMN)?.let { roundOf(it.text()) } ?: return null,
            date = dateOf(cells[0].text()),
            time = timeOf(cells[1].text()),
            venue = cells[2].text().trim().ifBlank { null },
            homeSlug = teams[0],
            awaySlug = teams[1],
            played = played,
        )
    }

    private fun squadRow(row: Element): SquadRow? {
        val cells = row.select("> td")
        if (cells.size != SQUAD_COLUMNS) return null
        return SquadRow(
            surnameFirst = cells[0].text().trim(),
            matches = cells[1].text().trim().toIntOrNull() ?: return null,
            goals = cells[2].text().trim().toIntOrNull() ?: return null,
        )
    }

    private fun matchDetail(wrap: Element): MatchDetail? {
        val gameId = wrap.id().removePrefix("GameResultItem")
        val header = wrap.select("table.is-inside tr").firstOrNull { it.select("> td").size >= ROUND_COLUMN + 1 }
        val headerCells = header?.select("> td") ?: return null
        val teams = teamSlugs(headerCells[TEAMS_COLUMN])
        if (teams.size != 2) return null
        val people = wrap.select("table.is-inside.has-smaller-text")
        val lineups =
            people
                .getOrNull(0)
                ?.select("tr")
                ?.firstOrNull { it.select("> td").size == LINEUP_COLUMNS }
                ?.select("> td")
        val cards =
            people
                .getOrNull(1)
                ?.select("tr")
                ?.firstOrNull { it.select("> td").size == CARD_COLUMNS }
                ?.select("> td")
        return MatchDetail(
            gameId = gameId,
            round = roundOf(headerCells[ROUND_COLUMN].text()) ?: return null,
            date = dateOf(headerCells[0].text()),
            home = side(teams[0], lineups?.getOrNull(0), cards?.getOrNull(1)),
            away = side(teams[1], lineups?.getOrNull(AWAY_LINEUP), cards?.getOrNull(AWAY_CARDS)),
        )
    }

    /** `Michal Šeda – Jan Bělohlávek, Jakub Kašpárek, …`: the goalkeeper before the dash. */
    private fun side(
        teamSlug: String,
        lineup: Element?,
        cards: Element?,
    ): MatchSide {
        val text = lineup?.text()?.trim().orEmpty()
        val dash = text.indexOf('–')
        val goalkeeper = if (dash >= 0) text.substring(0, dash).trim().ifBlank { null } else null
        val rest = if (dash >= 0) text.substring(dash + 1) else text
        return MatchSide(
            teamSlug = teamSlug,
            goalkeeper = goalkeeper,
            outfield = rest.split(',').map { it.trim() }.filter { it.isNotEmpty() },
            cards = cards?.select("span.component__table-card")?.map(::card).orEmpty(),
        )
    }

    /** `<span class="component__table-card is-yellow">52. Jakub Kumšta</span>`. */
    private fun card(span: Element): ScrapedCard {
        val kind = span.classNames().firstOrNull { it != "component__table-card" }.orEmpty()
        val text = span.text().trim()
        val minutes = MINUTES.find(text)
        return ScrapedCard(
            cssClass = kind,
            minute = minutes?.groupValues?.get(1)?.toIntOrNull(),
            name = if (minutes != null) text.substring(minutes.range.last + 1).trim() else text,
        )
    }

    private fun teamSlugs(cell: Element): List<String> =
        cell
            .select("a[href*=/tymy/]")
            .filterNot { it.hasClass("component__table-shirt") }
            .mapNotNull { TEAM_SLUG.find(it.attr("href"))?.groupValues?.get(1) }

    /** `Ne 11.10.26` and `20. 9. 26` alike. Two-digit years are this century. */
    internal fun dateOf(text: String): LocalDate? {
        val match = DATE.find(text) ?: return null
        val (day, month, year) = match.destructured
        val fullYear = year.toInt().let { if (it < CENTURY) it + YEAR_2000 else it }
        return runCatching { LocalDate(fullYear, month.toInt(), day.toInt()) }.getOrNull()
    }

    internal fun timeOf(text: String): LocalTime? {
        val match = TIME.find(text) ?: return null
        val (hour, minute) = match.destructured
        return runCatching { LocalTime(hour.toInt(), minute.toInt()) }.getOrNull()
    }

    private fun roundOf(text: String): Int? = text.filter { it.isDigit() }.toIntOrNull()

    private val CODE = Regex("^[A-Z0-9]+$")
    private val TEAM_SLUG = Regex("/tymy/([^/]+)/")
    private val DATE = Regex("""(\d{1,2})\.\s*(\d{1,2})\.\s*(\d{2,4})""")
    private val TIME = Regex("""(\d{1,2}):(\d{2})""")

    /** `52. ` or `48., 54. `: the leading minute(s) before a name. */
    private val MINUTES = Regex("""^(\d+)\.(?:\s*,\s*\d+\.)*\s*""")

    private const val TEAMS_COLUMN = 3
    private const val ROUND_COLUMN = 4
    private const val SQUAD_COLUMNS = 3
    private const val PITCH_COLUMNS = 3

    // A match detail: lineups as home | vs | away, cards as goals | cards | gap | goals | cards.
    private const val LINEUP_COLUMNS = 3
    private const val AWAY_LINEUP = 2
    private const val CARD_COLUMNS = 5
    private const val AWAY_CARDS = 4
    private const val CENTURY = 100
    private const val YEAR_2000 = 2000
}
