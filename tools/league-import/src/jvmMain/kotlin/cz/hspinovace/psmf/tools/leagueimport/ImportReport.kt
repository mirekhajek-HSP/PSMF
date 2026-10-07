package cz.hspinovace.psmf.tools.leagueimport

/**
 * Everything a run noticed and did not guess about. Printed at the end of
 * the run and written to `tools/league-import/last-run.md`, so the next
 * person to run it can see what this one could not decide.
 */
class ImportReport {
    data class GroupCounts(
        val groupId: String,
        val teams: Int,
        val players: Int,
        val fixtures: Int,
        val played: Int,
        val detailed: Int,
        val playersWithYellows: Int,
    )

    val groups = mutableListOf<GroupCounts>()
    val pitches = mutableListOf<String>()

    /** A lineup or card name with no row in that team's *Statistiky*. */
    val unmatchedNames = mutableListOf<String>()

    /** A name that fits more than one *Statistiky* row of one team. */
    val ambiguousNames = mutableListOf<String>()

    /** Two different players whose names make the same ref; how each was resolved. */
    val refCollisions = mutableListOf<String>()
    val kitOddities = mutableListOf<String>()
    val fixtureOddities = mutableListOf<String>()
    val nameOddities = mutableListOf<String>()

    /** Refs in the previous files and not on the site now; carried over, never dropped. */
    val departed = mutableListOf<String>()

    /** Every card class the markup used, and how often. */
    val cardClasses = sortedMapOf<String, Int>()

    /** A player with two yellow cards in one match, and how the site marked the cards. */
    val secondYellows = mutableListOf<String>()
    var requests = 0
    var cacheHits = 0
    var seconds = 0L

    fun countCard(cssClass: String) {
        cardClasses[cssClass] = (cardClasses[cssClass] ?: 0) + 1
    }

    fun asMarkdown(asOf: String): String =
        buildString {
            appendLine("# Last league import")
            appendLine()
            appendLine("Data as of $asOf. Written by `tools/league-import` at the end of every run.")
            appendLine()
            appendLine("| Group | Teams | Players | Fixtures | Played | In detail | Players with a yellow |")
            appendLine("|---|---|---|---|---|---|---|")
            groups.forEach {
                appendLine(
                    "| ${it.groupId} | ${it.teams} | ${it.players} | ${it.fixtures} | ${it.played} | " +
                        "${it.detailed} | ${it.playersWithYellows} |",
                )
            }
            appendLine(
                "| **all** | ${groups.sumOf { it.teams }} | ${groups.sumOf { it.players }} | " +
                    "${groups.sumOf { it.fixtures }} | ${groups.sumOf { it.played }} | " +
                    "${groups.sumOf { it.detailed }} | ${groups.sumOf { it.playersWithYellows }} |",
            )
            appendLine()
            appendLine(
                "Pitches: ${pitches.size}. Requests to psmf.cz: $requests; from the cache: $cacheHits; ${seconds}s.",
            )
            appendLine()
            appendLine("Card classes in the markup: $cardClasses.")
            section("Two yellows in one match", secondYellows)
            section("Names in a lineup or on a card with no Statistiky row (not guessed)", unmatchedNames)
            section("Names fitting more than one Statistiky row", ambiguousNames)
            section("Player ref collisions", refCollisions)
            section("Name oddities", nameOddities)
            section("Kit oddities", kitOddities)
            section("Fixture oddities", fixtureOddities)
            section("Departed refs, carried over", departed)
        }

    private fun StringBuilder.section(
        title: String,
        lines: List<String>,
    ) {
        appendLine()
        appendLine("## $title (${lines.size})")
        appendLine()
        if (lines.isEmpty()) appendLine("None.")
        lines.forEach { appendLine("- $it") }
    }
}
