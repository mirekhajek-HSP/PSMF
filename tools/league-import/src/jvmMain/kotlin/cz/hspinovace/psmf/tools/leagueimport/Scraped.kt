package cz.hspinovace.psmf.tools.leagueimport

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime

// What the pages say, as plainly as they say it. No ids, no refs, no
// decisions: those are made later, once, in GroupAssembly and SeedFiles.

/** A team as a group page links it: `/souteze/<season>/6-k/tymy/<slug>/`. */
data class TeamLink(
    val slug: String,
    val name: String,
)

/** One row of `/hriste/`. A row may carry several codes for one ground. */
data class Pitch(
    val code: String,
    val name: String?,
    val address: String?,
)

/**
 * One fixture as a team page lists it, under *Výsledky* (played) or
 * *Utkání* (to come). [date], [time] and [venue] can be missing for a
 * fixture PSMF has not scheduled; that is reported, not guessed.
 */
data class ScrapedFixture(
    val round: Int,
    val date: LocalDate?,
    val time: LocalTime?,
    val venue: String?,
    val homeSlug: String,
    val awaySlug: String,
    val played: Boolean,
)

/** One row of a team page's *Statistiky*: `Bělohlávek Jan · 3 · 0`, **surname first**. */
data class SquadRow(
    val surnameFirst: String,
    val matches: Int,
    val goals: Int,
)

/** `is-yellow`, `is-red`, or whatever else the markup says -- kept so it can be counted. */
data class ScrapedCard(
    val cssClass: String,
    val minute: Int?,
    val name: String,
) {
    val isYellow: Boolean get() = cssClass == "is-yellow"
    val isRed: Boolean get() = cssClass == "is-red"
}

/** One side of a played match: the lineup **first name first**, goalkeeper first, and its cards. */
data class MatchSide(
    val teamSlug: String,
    val goalkeeper: String?,
    val outfield: List<String>,
    val cards: List<ScrapedCard>,
) {
    val lineup: List<String> get() = listOfNotNull(goalkeeper) + outfield
}

/** A played match in detail, keyed by PSMF's own id (`GameResultItem311832`). */
data class MatchDetail(
    val gameId: String,
    val round: Int,
    val date: LocalDate?,
    val home: MatchSide,
    val away: MatchSide,
)

/** Everything one team page holds. */
data class TeamPage(
    val fixtures: List<ScrapedFixture>,
    val squad: List<SquadRow>,
    val details: List<MatchDetail>,
)
