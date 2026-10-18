package cz.hspinovace.psmf.export

import cz.hspinovace.psmf.domain.Assessment
import cz.hspinovace.psmf.domain.Confirmation
import cz.hspinovace.psmf.domain.ConfirmingParty
import cz.hspinovace.psmf.domain.Lineup
import cz.hspinovace.psmf.domain.LineupConfirmation
import cz.hspinovace.psmf.domain.Match
import cz.hspinovace.psmf.domain.PersonName
import cz.hspinovace.psmf.domain.TeamAssessment
import cz.hspinovace.psmf.domain.reportProblems
import cz.hspinovace.psmf.usecase.FakeLeagueRepository
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.time.Instant

/**
 * A note per team, and both of each captain's confirmations with their times
 * (prompt 13, part 4).
 *
 * The notes sit in the commentary block, labelled per team in fixed Czech, and
 * are fields in the JSON and rows in the CSV. A note is never a readiness
 * problem. The times are Prague time whatever zone the phone is in, so the
 * same report reads the same everywhere; the JSON keeps the instant.
 */
class TeamNotesAndSignaturesInTheReportTest {
    private val league = FakeLeagueRepository(listOf(CompleteReport.leagueGroup))

    private suspend fun reportOf(match: Match): ZouReport =
        assertNotNull(BuildZouReport(league, NoAddedPlayers())(match))

    private fun withNotes(
        home: String = "",
        away: String = "",
    ): Match =
        CompleteReport.match.copy(
            assessment =
                Assessment(
                    home = TeamAssessment(note = home),
                    away = TeamAssessment(note = away),
                    commentary = "Utkání bez potíží.",
                ),
        )

    // ------------------------------------------------------------------
    // The notes
    // ------------------------------------------------------------------

    @Test
    fun theNotesAreInTheCommentaryBlockLabelledPerTeam() =
        runTest {
            val text = ZouText.format(reportOf(withNotes(home = "Protest proti 2. gólu.", away = "Hráč bez průkazu.")))

            assertContains(text, "Poznámka týmu D: Protest proti 2. gólu.")
            assertContains(text, "Poznámka týmu H: Hráč bez průkazu.")
            // After the commentary, in the same block.
            assertEquals(true, text.indexOf("Komentář:") < text.indexOf("Poznámka týmu D"))
        }

    @Test
    fun aTeamWithNoNoteIsWrittenAsNotGivenSoItCanBeToldFromOneLeftOut() =
        runTest {
            val text = ZouText.format(reportOf(withNotes(home = "Protest.")))

            assertContains(text, "Poznámka týmu D: Protest.")
            assertContains(text, "Poznámka týmu H: ${ZouWords.NOT_GIVEN}")
        }

    @Test
    fun theCsvHasARowPerTeamAndTheJsonAFieldPerTeam() =
        runTest {
            val report = reportOf(withNotes(home = "Protest.", away = "Hráč; bez průkazu."))

            val csv = ZouCsv.format(report)
            val json = ZouJson.format(report)

            assertContains(csv, "Poznámka týmu D;Protest.")
            // A cell with the separator is quoted, as every free text is.
            assertContains(csv, "Poznámka týmu H;\"Hráč; bez průkazu.\"")
            assertContains(json, "\"note\": \"Protest.\"")
            assertEquals("Protest.", report.assessment.home.note)
            assertEquals(report, ZouJson.parse(json))
        }

    @Test
    fun aNoteIsNeverAReadinessProblem() {
        val with = withNotes(home = "Protest.", away = "Stížnost.")
        val without = withNotes()

        assertEquals(without.reportProblems(), with.reportProblems())
    }

    // ------------------------------------------------------------------
    // Both of each captain's confirmations, with their times
    // ------------------------------------------------------------------

    private val beforeKickoff = LineupConfirmation(Instant.parse("2026-08-31T18:55:00Z"), PersonName.of("Houžev Karel"))
    private val atTheRecap = Instant.parse("2026-08-31T20:05:00Z")

    private fun signedBoth(lineup: Lineup): Match =
        CompleteReport.match.copy(
            homeLineup = lineup.copy(confirmation = beforeKickoff),
            confirmations =
                listOf(
                    Confirmation(ConfirmingParty.HOME_CAPTAIN, atTheRecap, PersonName.of("Houžev Karel")),
                    Confirmation(ConfirmingParty.REFEREE, atTheRecap, PersonName.of("Vlk Jiří")),
                ),
        )

    @Test
    fun eachCaptainsTwoConfirmationsAreOnTheReportWithTheirTimes() =
        runTest {
            val report = reportOf(signedBoth(requireNotNull(CompleteReport.match.homeLineup)))

            val text = ZouText.format(report)

            // Before kickoff, under the lineup: 18:55 UTC is 20:55 in Prague (summer time).
            assertContains(text, "${ZouLabels.Lineup.CAPTAIN_BEFORE_KICKOFF}: Houžev Karel (31. 8. 2026 20:55)")
            // At the recap, under the finished report: 20:05 UTC is 22:05.
            assertContains(text, "${ZouWords.HOME_CAPTAIN}: Houžev Karel (31. 8. 2026 22:05)")
            assertContains(text, "${ZouWords.REFEREE}: Vlk Jiří (31. 8. 2026 22:05)")
            // The away captain has not signed the finished report: said so.
            assertContains(text, "${ZouWords.AWAY_CAPTAIN}: ${ZouWords.NOT_GIVEN}")
        }

    @Test
    fun theCsvCarriesTheNameAndTheTimeInTwoCells() =
        runTest {
            val csv = ZouCsv.format(reportOf(signedBoth(requireNotNull(CompleteReport.match.homeLineup))))

            assertContains(csv, "${ZouLabels.Lineup.CAPTAIN_BEFORE_KICKOFF};Houžev Karel;31. 8. 2026 20:55")
            assertContains(csv, "${ZouWords.HOME_CAPTAIN};Houžev Karel;31. 8. 2026 22:05")
        }

    @Test
    fun theJsonKeepsTheInstantsThemselves() =
        runTest {
            val json = ZouJson.format(reportOf(signedBoth(requireNotNull(CompleteReport.match.homeLineup))))

            assertContains(json, "2026-08-31T18:55:00Z")
            assertContains(json, "2026-08-31T20:05:00Z")
        }

    @Test
    fun theTimeIsPragueTimeInWinterAndInSummer() {
        assertEquals("31. 8. 2026 20:55", ZouTimes.written(Instant.parse("2026-08-31T18:55:00Z")))
        assertEquals("1. 12. 2026 19:55", ZouTimes.written(Instant.parse("2026-12-01T18:55:00Z")))
        // The minutes are padded, the day and month are not.
        assertEquals("5. 3. 2026 09:07", ZouTimes.written(Instant.parse("2026-03-05T08:07:00Z")))
    }
}
