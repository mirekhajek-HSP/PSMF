package cz.hspinovace.psmf.data.seed

import cz.hspinovace.psmf.data.db.DatabaseDriverFactory
import cz.hspinovace.psmf.data.league.JerseyOverridingLeagueRepository
import cz.hspinovace.psmf.data.league.SeedLeagueRepository
import cz.hspinovace.psmf.data.match.SqlDelightMatchRepository
import cz.hspinovace.psmf.data.player.SqlDelightAddedPlayerRepository
import cz.hspinovace.psmf.data.player.SqlDelightRememberedDateOfBirthRepository
import cz.hspinovace.psmf.data.team.SqlDelightFollowedTeamRepository
import cz.hspinovace.psmf.data.team.SqlDelightJerseyOverrideRepository
import cz.hspinovace.psmf.db.PsmfDatabase
import cz.hspinovace.psmf.domain.JerseyNumber
import cz.hspinovace.psmf.domain.MatchStatus
import cz.hspinovace.psmf.domain.Minute
import cz.hspinovace.psmf.domain.TeamSide
import cz.hspinovace.psmf.export.BuildZouReport
import cz.hspinovace.psmf.usecase.BrowseTeams
import cz.hspinovace.psmf.usecase.BuildConsoleEntry
import cz.hspinovace.psmf.usecase.BuildLineupEntry
import cz.hspinovace.psmf.usecase.ListFixtures
import cz.hspinovace.psmf.usecase.LoadTeamRoster
import cz.hspinovace.psmf.usecase.LogGoal
import cz.hspinovace.psmf.usecase.NewId
import cz.hspinovace.psmf.usecase.ObserveReportInProgress
import cz.hspinovace.psmf.usecase.SaveLineup
import cz.hspinovace.psmf.usecase.StartMatch
import cz.hspinovace.psmf.usecase.StartOrResumeMatch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import java.io.File
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

/**
 * **0.1.0 to 0.2.0 on a tester's phone.** The invented 6-K retires and real
 * league 6 ships instead (DECISIONS 2026-10-07): none of the placeholder's
 * ids exist any more. A match saved against it, a team followed in it and a
 * jersey number corrected in it must not crash anything.
 *
 * What the referee sees: **nothing of them.** The report tab is not badged,
 * the fixture list has no row for the old match, the followed list is
 * empty and the old number corrects nobody. Each screen's own loader,
 * asked directly, answers "not here" rather than throwing. And the report
 * is still in the database, byte for byte -- nothing is deleted.
 */
class PlaceholderMatchAfterTheUpgradeTest {
    private val file: File = File.createTempFile("psmf-upgrade-", ".db").also { it.delete() }

    @AfterTest
    fun cleanUp() {
        file.delete()
    }

    private fun catalogIn(directory: String) =
        SeedLeagueCatalog(SeedFileReader { File(directory, it).takeIf { file -> file.isFile }?.readText() })

    private val placeholder = SeedLeagueRepository(catalogIn("src/jvmTest/resources/placeholder-league"))
    private val shipped = catalogIn("../composeApp/src/commonMain/composeResources/files/leagues")

    private var minted = 0
    private val newId = NewId { "id-${++minted}" }

    @Test
    fun aMatchATeamAndANumberFromThePlaceholderLeagueNeitherCrashNorAppearAfterTheUpgrade() =
        runTest {
            val driver = DatabaseDriverFactory("jdbc:sqlite:${file.absolutePath}").create()
            val database = PsmfDatabase(driver)
            val matches = SqlDelightMatchRepository(database)
            val added = SqlDelightAddedPlayerRepository(database)
            val followed = SqlDelightFollowedTeamRepository(database)
            val overrides = SqlDelightJerseyOverrideRepository(database)
            val remembered = SqlDelightRememberedDateOfBirthRepository(database)

            // Before: 0.1.0 and the invented 6-K. A match under way at
            // Kominíci, the team followed, Růžička's number corrected.
            val sixK = placeholder.groups().single()
            val opener = sixK.fixtures.first()
            val kominici = sixK.teams.single { it.ref == "kominici" }
            var match = assertNotNull(StartOrResumeMatch(placeholder, matches, newId)(opener.id))
            match =
                SaveLineup(
                    matches,
                )(match, assertNotNull(BuildLineupEntry(placeholder, added, remembered, newId)(match)))
            match = StartMatch(matches)(match, Instant.parse("2026-09-06T08:00:00Z"))
            match = LogGoal(matches)(match, TeamSide.HOME, null, Minute.Played(5))
            assertEquals(MatchStatus.IN_PROGRESS, match.status)
            followed.setFollowed(kominici.id, true)
            overrides.setDefaultJerseyNumber(sixK.players.single { it.ref == "ruzicka-radek" }.id, JerseyNumber(99))

            // After: the real league 6, through the same layers as the app.
            val league = JerseyOverridingLeagueRepository(SeedLeagueRepository(shipped), overrides)

            // Nothing offers the match: no badge, no row in Zápasy.
            assertNull(ObserveReportInProgress(matches, league)().first())
            val rows = ListFixtures(league, matches)().groups.flatMap { g -> g.rounds.flatMap { it.fixtures } }
            assertTrue(rows.isNotEmpty())
            assertTrue(rows.none { it.reportStatus != null }, "an old report is attached to a new fixture")
            // Nor the team, followed or opened directly.
            assertTrue(BrowseTeams(league, followed)().followed.isEmpty())
            assertNull(LoadTeamRoster(league, followed, overrides)(kominici.id))
            // Every screen's own loader says "not here" rather than throwing.
            assertNull(league.fixture(match.fixtureId))
            assertNull(BuildConsoleEntry(league, added)(match))
            assertNull(BuildLineupEntry(league, added, remembered, newId)(match))
            assertNull(BuildZouReport(league, added)(match))
            assertNull(StartOrResumeMatch(league, matches, newId)(opener.id))
            // And the report is exactly as it was: kept, not deleted.
            assertEquals(match, matches.load(match.id))

            driver.close()
        }
}
