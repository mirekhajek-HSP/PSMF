package cz.hspinovace.psmf.tools.leagueimport

import kotlinx.datetime.LocalDate
import java.io.File
import java.util.UUID
import kotlin.system.exitProcess

/** What a run is told. Every default is the one a normal run wants. */
data class ImportOptions(
    val output: File = File("composeApp/src/commonMain/composeResources/files/leagues"),
    val cache: File = File("tools/league-import/cache"),
    val summary: File = File("tools/league-import/last-run.md"),
    val seasonPath: String = "/souteze/2026-hanspaulska-liga-podzim/",
    val league: Int = 6,
    val offline: Boolean = false,
    val asOf: LocalDate =
        LocalDate.parse(
            java.time.LocalDate
                .now(java.time.ZoneId.of("Europe/Prague"))
                .toString(),
        ),
) {
    companion object {
        fun parse(args: Array<String>): ImportOptions {
            var options = ImportOptions()
            val queue = ArrayDeque(args.toList())
            while (queue.isNotEmpty()) {
                options =
                    when (val flag = queue.removeFirst()) {
                        "--offline" -> options.copy(offline = true)
                        "--output" -> options.copy(output = File(queue.removeFirst()))
                        "--cache" -> options.copy(cache = File(queue.removeFirst()))
                        "--as-of" -> options.copy(asOf = LocalDate.parse(queue.removeFirst()))
                        else -> error("Unknown option '$flag'")
                    }
            }
            return options
        }
    }
}

/**
 * One full run: league page, `/hriste/`, then per group its page, its
 * `/dresy/` and every team page. About 170 requests from an empty cache,
 * none from a full one.
 */
class LeagueImport(
    private val pages: PageSource,
    private val options: ImportOptions,
    private val report: ImportReport,
    private val mintId: () -> String = { UUID.randomUUID().toString() },
) {
    fun run(existing: ExistingSeed): SeedOutput {
        val season = options.seasonPath
        val groups = Pages.groupsOfLeague(pages.get("$season${options.league}/"), season, options.league)
        check(groups.isNotEmpty()) { "No groups linked from $season${options.league}/ -- has the site changed?" }
        val pitches = Pages.pitches(pages.get("/hriste/"))
        report.pitches += pitches.map { it.code }

        val assembled =
            groups.map { slug ->
                val links = Pages.teamsOfGroup(pages.get("$season$slug/"), season, slug)
                val kits = Pages.kits(pages.get("$season$slug/dresy/"))
                val teamPages = links.associate { it.slug to Pages.team(pages.get("$season$slug/tymy/${it.slug}/")) }
                GroupAssembly(report).assemble(slug, links, kits, teamPages).also { count(it, teamPages) }
            }
        return SeedFiles(existing, report, options.asOf, mintId).build(assembled, pitches)
    }

    private fun count(
        group: AssembledGroup,
        pages: Map<String, TeamPage>,
    ) {
        val teamless = group.teams.filter { it.kitLabels.isEmpty() }
        check(teamless.isEmpty()) {
            "${group.slug}: no kit for ${teamless.map { it.slug }}; Barva dresů cannot be invented"
        }
        report.groups +=
            ImportReport.GroupCounts(
                groupId = SeedFiles.groupId(group.slug),
                teams = group.teams.size,
                players = group.teams.sumOf { it.players.size },
                fixtures = group.fixtures.size,
                played = group.fixtures.count { it.played },
                detailed = group.detailedMatches,
                playersWithYellows = group.teams.sumOf { team -> team.players.count { it.yellows > 0 } },
            )
        val teamCount = pages.size
        val expected = teamCount * (teamCount - 1) / 2
        if (group.fixtures.size != expected) {
            report.fixtureOddities +=
                "${group.slug}: ${group.fixtures.size} fixtures, $expected expected for $teamCount teams"
        }
    }
}

fun main(args: Array<String>) {
    val options = ImportOptions.parse(args)
    val started = System.nanoTime()
    val fetcher = PoliteCachedFetcher(options.cache, options.offline)
    val report = ImportReport()

    val output = LeagueImport(fetcher, options, report).run(SeedJson.read(options.output))
    val files = SeedJson.render(output, options.asOf)
    // Through the app's own catalogue first: a run that would not load writes nothing.
    val loaded = SeedJson.loadAsTheAppWould(files)

    files.forEach { (name, text) -> File(options.output, name).writeText(text) }
    report.requests = fetcher.networkRequests
    report.cacheHits = fetcher.cacheHits
    report.seconds = (System.nanoTime() - started) / NANOS_PER_SECOND
    options.summary.writeText(report.asMarkdown(options.asOf.toString()))

    println(report.asMarkdown(options.asOf.toString()))
    println("Wrote ${files.size} files to ${options.output}; ${loaded.size} groups load through SeedLeagueCatalog.")
    if (loaded.size != output.groups.size) exitProcess(1)
}

private const val NANOS_PER_SECOND = 1_000_000_000L
