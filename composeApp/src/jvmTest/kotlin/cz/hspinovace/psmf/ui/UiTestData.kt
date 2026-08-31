package cz.hspinovace.psmf.ui

import cz.hspinovace.psmf.domain.Fixture
import cz.hspinovace.psmf.domain.FixtureId
import cz.hspinovace.psmf.domain.Group
import cz.hspinovace.psmf.domain.GroupId
import cz.hspinovace.psmf.domain.Kit
import cz.hspinovace.psmf.domain.KitId
import cz.hspinovace.psmf.domain.Season
import cz.hspinovace.psmf.domain.SeasonId
import cz.hspinovace.psmf.domain.Team
import cz.hspinovace.psmf.domain.TeamId
import cz.hspinovace.psmf.domain.Venue
import cz.hspinovace.psmf.domain.VenueCode
import cz.hspinovace.psmf.usecase.FixtureListing
import cz.hspinovace.psmf.usecase.FixtureRow
import cz.hspinovace.psmf.usecase.GroupFixtures
import cz.hspinovace.psmf.usecase.RoundRows
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import java.util.Locale

/**
 * Data for the screen tests, built here rather than borrowed: `shared`'s
 * test fixtures live in its own `commonTest`, which this module cannot see.
 *
 * Names are taken from the worked example in the analysis so an assertion
 * can be checked against a real report.
 */
object UiTestData {
    val seasonId = SeasonId("2026-podzim")
    val groupId = GroupId("6k")
    val homeTeamId = TeamId("kominici")
    val awayTeamId = TeamId("sp-sumys")
    val fixtureId = FixtureId("6k-r1-01")

    val homeTeam =
        Team(homeTeamId, "kominici", groupId, "Kominíci", listOf(Kit(KitId("k1"), "modrá")))
    val awayTeam =
        Team(awayTeamId, "sp-sumys", groupId, "Sp. Sumýš", listOf(Kit(KitId("k2"), "černo-bílá")))

    val group = Group(groupId, seasonId, "6. liga K", "6K")
    val season = Season(seasonId, "Hanspaulská liga podzim 2026")
    val venue = Venue(VenueCode("ZAKOS"))

    val fixture =
        Fixture(
            id = fixtureId,
            ref = "6k-r1-01",
            groupId = groupId,
            round = 1,
            date = LocalDate(2026, 8, 31),
            time = LocalTime(19, 0),
            venue = venue.code,
            homeTeamId = homeTeamId,
            awayTeamId = awayTeamId,
        )

    fun listing(row: FixtureRow = row()): FixtureListing =
        FixtureListing(
            listOf(GroupFixtures(season, group, listOf(RoundRows(1, listOf(row))))),
        )

    fun row(status: cz.hspinovace.psmf.domain.MatchStatus? = null) =
        FixtureRow(
            fixture = fixture,
            homeTeam = homeTeam,
            awayTeam = awayTeam,
            venue = venue,
            reportStatus = status,
        )
}

/**
 * Runs [block] with the JVM default locale set, then puts it back.
 *
 * Compose resources on this target pick the language from the JVM default,
 * so this is how a test asks for the Czech, English or Ukrainian strings.
 * **The report is always Czech regardless**, which is why nothing in the
 * export path is reachable from here.
 *
 * **Wrap the whole test, not just `setContent`.** Compose resolves each
 * string the first time it is composed, so a string that first appears
 * *after* a tap resolves in whatever locale the host is in by then. Put the
 * assertions outside this block and a test reads Czech correctly for
 * everything drawn on the first frame and English for everything the tap
 * revealed -- which looks exactly like a navigation bug and is not one.
 */
fun withLanguage(
    tag: String,
    block: () -> Unit,
) {
    val original = Locale.getDefault()
    Locale.setDefault(Locale.forLanguageTag(tag))
    try {
        block()
    } finally {
        Locale.setDefault(original)
    }
}
