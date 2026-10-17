package cz.hspinovace.psmf.export

import cz.hspinovace.psmf.domain.AppearanceId
import cz.hspinovace.psmf.domain.Fixtures
import cz.hspinovace.psmf.domain.IdentificationSource
import cz.hspinovace.psmf.domain.Lineup
import cz.hspinovace.psmf.domain.LineupConfirmation
import cz.hspinovace.psmf.domain.Match
import cz.hspinovace.psmf.domain.PersonName
import cz.hspinovace.psmf.domain.ReportedIdentification
import cz.hspinovace.psmf.domain.TeamSide
import cz.hspinovace.psmf.usecase.FakeLeagueRepository
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

/**
 * Who is captain, who is goalkeeper, and what kind of number each row has
 * (DECISIONS 2026-10-17).
 *
 * **The paper form was not seen**: nobody has told us how it marks the two, so
 * the report says it in words and leaves the choosing to a photograph of a
 * real one (TODO). The words are unambiguous in each format -- including for
 * one person who is both -- and a lineup written before 0.3.0, which has
 * neither, comes out exactly as it always did.
 */
class LineupRolesInTheReportTest {
    private val houzev =
        Fixtures.appearance(
            "app-houzev",
            "p-houzev",
            12,
            ReportedIdentification("59001", IdentificationSource.RP),
        )
    private val novak =
        Fixtures.appearance(
            "app-novak",
            "p-novak",
            4,
            ReportedIdentification("59123", IdentificationSource.RP_TYPED),
        )
    private val poupe = Fixtures.appearance("app-poupe", "p-poupe", 9)

    private val confirmedBeforeKickoff =
        LineupConfirmation(Instant.parse("2026-08-31T18:55:00Z"), PersonName.of("Houžev Karel"))

    private fun lineup(
        captain: String? = "app-houzev",
        goalkeeper: String? = "app-novak",
        confirmation: LineupConfirmation? = confirmedBeforeKickoff,
    ) = Lineup.wearing(
        side = TeamSide.HOME,
        teamId = Fixtures.homeTeamId,
        // In the order a squad list has them, which is neither jersey order
        // nor the form's.
        appearances = listOf(novak, poupe, houzev),
        kit = Fixtures.homePrimaryKit,
        captain = captain?.let { AppearanceId(it) },
        goalkeeper = goalkeeper?.let { AppearanceId(it) },
        confirmation = confirmation,
    )

    private val league =
        FakeLeagueRepository(
            listOf(
                CompleteReport.leagueGroup.copy(
                    players = CompleteReport.leagueGroup.players + Fixtures.player("p-novak", "Novák", "Jan", 4),
                ),
            ),
        )

    private suspend fun reportOf(lineup: Lineup): ZouReport {
        val match: Match = CompleteReport.match.copy(homeLineup = lineup)
        return assertNotNull(BuildZouReport(league, NoAddedPlayers())(match))
    }

    private fun ZouReport.home() = lineups.single { it.side == ZouSide.HOME }

    @Test
    fun theCaptainIsFirstThenTheGoalkeeperThenTheRestByJerseyNumber() =
        runTest {
            val rows = reportOf(lineup()).home().rows

            // Captain Houžev (12), goalkeeper Novák (4), then Poupě (9).
            assertEquals(listOf("Houžev Karel", "Novák Jan", "Poupě Petr"), rows.map { it.name })
            assertEquals(listOf(true, false, false), rows.map { it.captain })
            assertEquals(listOf(false, true, false), rows.map { it.goalkeeper })
        }

    @Test
    fun oneManWhoIsBothIsOneRowAndIsFirst() =
        runTest {
            val rows = reportOf(lineup(captain = "app-poupe", goalkeeper = "app-poupe")).home().rows

            assertEquals(listOf("Poupě Petr", "Novák Jan", "Houžev Karel"), rows.map { it.name })
            assertTrue(rows.first().captain && rows.first().goalkeeper)
            assertEquals(1, rows.count { it.captain })
            assertEquals(1, rows.count { it.goalkeeper })
            // Everyone else in jersey order: 4, then 12.
            assertEquals(listOf(4, 12), rows.drop(1).map { it.jerseyNumber })
        }

    @Test
    fun aLineupWithNoCaptainComesOutInJerseyOrderAsItAlwaysDid() =
        runTest {
            // Written before 0.3.0, so it has neither -- and is not given one.
            val rows = reportOf(lineup(captain = null, goalkeeper = null, confirmation = null)).home().rows

            assertEquals(listOf(4, 9, 12), rows.map { it.jerseyNumber })
            assertTrue(rows.none { it.captain || it.goalkeeper })
        }

    @Test
    fun theTextSaysInWordsWhoIsWhoAndLeavesTheOthersAlone() =
        runTest {
            val text = ZouText.format(reportOf(lineup()))

            assertContains(text, "Houžev Karel  (kapitán)")
            assertContains(text, "Novák Jan  (brankář)")
            // Poupě is neither, and carries nothing after the name.
            val poupeLine = text.lines().single { it.contains("Poupě Petr") && it.startsWith("9") }
            assertFalse(poupeLine.contains("("))
        }

    @Test
    fun theTextSaysBothForOnePersonWhoIsBoth() =
        runTest {
            val text = ZouText.format(reportOf(lineup(captain = "app-poupe", goalkeeper = "app-poupe")))

            assertContains(text, "Poupě Petr  (kapitán, brankář)")
        }

    @Test
    fun theTextKeepsTheFormsSingleColumnForTheRpNumberWhateverKindItIs() =
        runTest {
            val text = ZouText.format(reportOf(lineup()))

            // The league's number, the one copied from a card, and a date of
            // birth: all in the one column, none labelled.
            assertContains(text, "12      59001       Houžev Karel")
            assertContains(text, "4       59123       Novák Jan")
            assertContains(text, "9       900615      Poupě Petr")
            assertFalse(text.contains(ZouLabels.Lineup.KIND_TYPED_RP))
        }

    @Test
    fun theTextShowsTheCaptainsConfirmationUnderTheLineupAndNothingWhenThereIsNone() =
        runTest {
            val given = ZouText.format(reportOf(lineup()))
            val missing = ZouText.format(reportOf(lineup(confirmation = null)))

            assertContains(given, "${ZouLabels.Lineup.CAPTAIN_BEFORE_KICKOFF}: Houžev Karel")
            assertContains(missing, "${ZouLabels.Lineup.CAPTAIN_BEFORE_KICKOFF}: ${ZouWords.NOT_GIVEN}")
        }

    @Test
    fun theCsvHasAColumnForEachRoleAndOneSayingWhichKindOfNumber() =
        runTest {
            val csv = ZouCsv.format(reportOf(lineup()))

            assertContains(csv, "Dres č.;Číslo RP;Příjmení a jméno;Kapitán;Brankář;Druh údaje")
            // Captain: yes in the first role column. Goalkeeper: the second.
            assertContains(csv, "12;59001;Houžev Karel;ano;;RP z ligových dat")
            assertContains(csv, "4;59123;Novák Jan;;ano;RP opsané z průkazu")
            // Neither: blank, so the column filters.
            assertContains(csv, "9;900615;Poupě Petr;;;datum narození")
            assertContains(csv, "${ZouLabels.Lineup.CAPTAIN_BEFORE_KICKOFF};Houžev Karel")
        }

    @Test
    fun theJsonSaysTheSameInMachineReadableKeys() =
        runTest {
            val report = reportOf(lineup())
            val json = ZouJson.format(report)

            assertContains(json, "\"captain\": true")
            assertContains(json, "\"goalkeeper\": true")
            assertContains(json, "\"identificationKind\": \"rp_league\"")
            assertContains(json, "\"identificationKind\": \"rp_typed\"")
            assertContains(json, "\"identificationKind\": \"date_of_birth\"")
            // And it reads back as the report it was.
            assertEquals(report, ZouJson.parse(json))
        }

    @Test
    fun theJsonRecordsWhenTheCaptainConfirmedAndWhoConfirmed() =
        runTest {
            val report = reportOf(lineup())

            val confirmation = assertNotNull(report.home().confirmation)
            assertEquals("Houžev Karel", confirmation.by)
            assertEquals(Instant.parse("2026-08-31T18:55:00Z"), confirmation.at)
            assertContains(ZouJson.format(report), "2026-08-31T18:55:00Z")
        }

    @Test
    fun aLineupWithNoConfirmationHasNullInTheJsonAndNotAMissingKey() =
        runTest {
            val report = reportOf(lineup(confirmation = null))

            assertNull(report.home().confirmation)
            // A reader cannot tell an absent key from an unsupported one.
            assertContains(ZouJson.format(report), "\"confirmation\": null")
        }

    @Test
    fun nothingInTheReportSaysRodneCisloInAnyFormat() =
        runTest {
            val report = reportOf(lineup())
            val everything = ZouText.format(report) + ZouCsv.format(report) + ZouJson.format(report)

            assertFalse(everything.contains("rodn", ignoreCase = true))
            assertFalse(everything.contains("birth_number", ignoreCase = true))
        }
}
