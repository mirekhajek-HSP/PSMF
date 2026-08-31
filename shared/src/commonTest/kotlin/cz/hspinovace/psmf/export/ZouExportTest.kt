package cz.hspinovace.psmf.export

import cz.hspinovace.psmf.domain.Fixtures
import cz.hspinovace.psmf.domain.Match
import cz.hspinovace.psmf.domain.MatchId
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Assembling the report: everything resolved, nothing left to look up. */
class BuildZouReportTest {
    @Test
    fun theHeaderComesFromTheFixtureAndTheOfficialsFromTheReferee() =
        runTest {
            val report = CompleteReport.report()

            assertEquals("ZAKOS", report.header.pitch)
            assertEquals("31.8.2026", report.header.dateWritten)
            assertEquals("19:00", report.header.timeWritten)
            assertEquals("6K", report.header.league)
            // The R mark, for a licensed referee hired by the delegating team.
            assertEquals("Roman Liska R", report.header.assistantWritten)
            assertEquals("Jiri Vlk", report.header.refereeWritten)
            // The team that gets fined, and neither of the two playing.
            assertEquals("Kominici", report.header.delegatingTeam)
        }

    @Test
    fun appearancesResolveToNamesAndNumbersAndKeepTheirRpColumn() =
        runTest {
            val report = CompleteReport.report()

            val home = report.lineups.single { it.side == ZouSide.HOME }
            assertEquals(Fixtures.homeTeam.name, home.teamName)
            // The kit label as it stood on the day, not a lookup.
            assertEquals("modrá", home.kitLabel)
            val poupe = home.rows.single { it.jerseyNumber == 9 }
            assertEquals("Poupě Petr", poupe.name)
            assertEquals("900615", poupe.identification)
        }

    @Test
    fun aGoalWithNoScorerKeepsItsTimeAndItsScore() =
        runTest {
            val report = CompleteReport.report()

            val unattributed = report.goals.single { it.minute == "13´" }
            assertNull(unattributed.scorer)
            assertNull(unattributed.jerseyNumber)
            assertEquals("1:1", unattributed.scoreAfter)
        }

    @Test
    fun cardsSplitIntoTheFormsTwoBlocksAndKeepTheirReasons() =
        runTest {
            val report = CompleteReport.report()

            assertTrue(report.cards.accountedFor)
            assertEquals(2, report.cards.yellow.size)
            assertEquals(1, report.cards.red.size)
            assertEquals(
                ZouLabels.Cards.SECOND_YELLOW,
                report.cards.red
                    .single()
                    .reason,
            )
            assertTrue(report.cards.yellow.all { it.reason.isNotBlank() })
        }

    @Test
    fun aCardToSomebodyWithNoNumberKeepsTheirName() =
        runTest {
            val report = CompleteReport.report()

            val atHalfTime = report.cards.yellow.single { it.minute == "30´+" }
            assertNull(atHalfTime.jerseyNumber)
            assertEquals("Lepis A.", atHalfTime.name)
        }

    @Test
    fun theWinnerIsATeamNameRatherThanASide() =
        runTest {
            val report = CompleteReport.report()

            assertEquals(Fixtures.homeTeam.name, assertNotNull(report.result).winner)
        }

    @Test
    fun aDrawSaysSoInWords() =
        runTest {
            val drawn =
                CompleteReport.match.copy(
                    result =
                        cz.hspinovace.psmf.domain.MatchResult(
                            halfTime =
                                cz.hspinovace.psmf.domain
                                    .Score(1, 1),
                            fullTime =
                                cz.hspinovace.psmf.domain
                                    .Score(2, 2),
                        ),
                )
            val report = assertNotNull(BuildZouReport(CompleteReport.league(), NoAddedPlayers())(drawn))

            assertEquals(ZouWords.DRAW, assertNotNull(report.result).winner)
        }

    @Test
    fun aDeputyConfirmationIsMarkedAsOne() =
        runTest {
            val report = CompleteReport.report()

            val away = report.confirmations.single { it.party == ZouWords.AWAY_CAPTAIN }
            assertTrue(away.asDeputy)
            assertEquals("Lepis ${ZouWords.DEPUTY}", away.byWritten)
        }

    @Test
    fun anUnaccountedCardsBlockIsNotTheSameAsNoneIssued() =
        runTest {
            val unaccounted = CompleteReport.match.copy(cards = null)
            val report = assertNotNull(BuildZouReport(CompleteReport.league(), NoAddedPlayers())(unaccounted))

            assertFalse(report.cards.accountedFor)
            assertFalse(report.cards.noneIssued)

            val affirmed = CompleteReport.match.copy(cards = cz.hspinovace.psmf.domain.CardsSection.NoneIssued)
            val clean = assertNotNull(BuildZouReport(CompleteReport.league(), NoAddedPlayers())(affirmed))
            assertTrue(clean.cards.accountedFor)
            assertTrue(clean.cards.noneIssued)
        }

    @Test
    fun anIncompleteReportStillBuildsBecauseTheRecapHasToShowIt() =
        runTest {
            // Showing what is missing is the one thing the app does that
            // paper cannot, so building must not be all-or-nothing.
            val bare = Match(MatchId("m2"), Fixtures.fixtureId, Fixtures.groupId)

            val report = assertNotNull(BuildZouReport(CompleteReport.league(), NoAddedPlayers())(bare))

            assertEquals("", report.header.referee)
            assertTrue(report.lineups.isEmpty())
            assertNull(report.result)
        }
}

/**
 * RULE: **the report is Czech whatever language the app is in.**
 *
 * There is no locale anywhere in this package and there must not be: these
 * tests assert the form's own words come out, which is the same thing said
 * from the other side.
 */
class ZouLanguageTest {
    @Test
    fun everyFormatUsesTheFormsOwnCzechLabels() =
        runTest {
            val report = CompleteReport.report()

            listOf(ZouText.format(report), ZouCsv.format(report)).forEach { output ->
                assertContains(output, ZouLabels.Header.PITCH)
                assertContains(output, ZouLabels.Lineup.KIT_COLOUR)
                assertContains(output, ZouLabels.Goals.SECTION)
                assertContains(output, ZouLabels.Cards.SECTION)
                assertContains(output, ZouLabels.Result.WINNER)
                assertContains(output, ZouLabels.Assessment.COMMENTARY)
            }
        }

    @Test
    fun ratingsAreAnoAndNeRatherThanYesAndNo() =
        runTest {
            val text = ZouText.format(CompleteReport.report())

            assertContains(text, ZouWords.YES)
            assertContains(text, ZouWords.NO)
            assertFalse(text.contains("yes", ignoreCase = true))
        }

    @Test
    fun anUnansweredRatingSaysSoRatherThanReadingAsAPass() =
        runTest {
            // Č and B feed into fines. An unanswered one must not arrive at
            // PSMF looking like a yes.
            val unassessed =
                CompleteReport.match.copy(
                    assessment =
                        CompleteReport.match.assessment.copy(
                            home =
                                cz.hspinovace.psmf.domain
                                    .TeamAssessment(),
                        ),
                )
            val report = assertNotNull(BuildZouReport(CompleteReport.league(), NoAddedPlayers())(unassessed))

            assertNull(report.assessment.home.shirtsProperlyNumbered)
            assertContains(ZouText.format(report), "${ZouLabels.Assessment.SHIRTS_NUMBERED}: ${ZouWords.NOT_GIVEN}")
        }
}

/** The plain-text report: what goes in the email body. */
class ZouTextTest {
    @Test
    fun everyBlockOfTheFormIsPresent() =
        runTest {
            val text = ZouText.format(CompleteReport.report())

            assertContains(text, ZouWords.TITLE)
            assertContains(text, "Kominíci - Sp. Sumýš")
            assertContains(text, "Poupě Petr")
            assertContains(text, "5´")
            assertContains(text, "30´+")
            assertContains(text, ZouLabels.Cards.SECOND_YELLOW)
            assertContains(text, "2:1")
            assertContains(text, "Nastřelená tyč")
            assertContains(text, ZouLabels.Lineup.CAPTAIN_CONFIRMS)
            assertContains(text, "Lepis ${ZouWords.DEPUTY}")
        }

    @Test
    fun anAffirmedCleanSheetSaysBezKaretRatherThanNothing() =
        runTest {
            val clean = CompleteReport.match.copy(cards = cz.hspinovace.psmf.domain.CardsSection.NoneIssued)
            val report = assertNotNull(BuildZouReport(CompleteReport.league(), NoAddedPlayers())(clean))

            assertContains(ZouText.format(report), ZouLabels.Cards.NONE_ISSUED)
        }

    @Test
    fun aMissingResultIsMarkedRatherThanOmitted() =
        runTest {
            val noResult = CompleteReport.match.copy(result = null)
            val report = assertNotNull(BuildZouReport(CompleteReport.league(), NoAddedPlayers())(noResult))

            assertContains(ZouText.format(report), "${ZouLabels.Result.FINAL}: ${ZouWords.NOT_GIVEN}")
        }
}

/**
 * The spreadsheet. **This is the artefact the demo is selling**, so the
 * two details that decide whether it opens are tested rather than assumed.
 */
class ZouCsvTest {
    @Test
    fun itStartsWithAByteOrderMarkOrExcelRuinsEveryDiacritic() =
        runTest {
            val csv = ZouCsv.format(CompleteReport.report())

            // The code point, not the constant: asserting against the
            // constant passes happily when the constant has gone empty.
            assertEquals('\uFEFF', csv.first())
            assertEquals("\uFEFF", ZouCsv.BYTE_ORDER_MARK)
            // And the diacritics are actually in there to be ruined.
            assertContains(csv, "Poupě Petr")
        }

    @Test
    fun cellsAreSeparatedBySemicolonsBecauseCzechExcelExpectsThem() =
        runTest {
            val csv = ZouCsv.format(CompleteReport.report())

            assertEquals(";", ZouCsv.SEPARATOR)
            assertContains(csv, "${ZouLabels.Header.PITCH};ZAKOS")
        }

    @Test
    fun aCommentaryContainingASemicolonDoesNotSplitTheRow() =
        runTest {
            // A 400-character free-text commentary will contain a separator,
            // a quote or a newline sooner or later.
            val awkward =
                CompleteReport.match.copy(
                    assessment =
                        CompleteReport.match.assessment.copy(
                            commentary = "PK ve 12.; hráč křičel \"dost\" a odešel",
                        ),
                )
            val report = assertNotNull(BuildZouReport(CompleteReport.league(), NoAddedPlayers())(awkward))

            val csv = ZouCsv.format(report)
            assertContains(csv, "\"PK ve 12.; hráč křičel \"\"dost\"\" a odešel\"")
        }

    @Test
    fun everyBlockOfTheFormHasItsOwnHeaderRow() =
        runTest {
            val csv = ZouCsv.format(CompleteReport.report())

            assertContains(csv, ZouLabels.Lineup.JERSEY_NUMBER)
            assertContains(csv, ZouLabels.Goals.SCORER)
            assertContains(csv, ZouLabels.Assessment.BEST_PLAYER)
        }

    @Test
    fun rowsEndWithCarriageReturnLineFeed() =
        runTest {
            // What every spreadsheet on Windows expects.
            assertTrue(ZouCsv.format(CompleteReport.report()).endsWith("\r\n"))
        }
}

/** The structured format, for whatever PSMF's vendor eventually reads. */
class ZouJsonTest {
    @Test
    fun theReportSurvivesARoundTrip() =
        runTest {
            val report = CompleteReport.report()

            assertEquals(report, ZouJson.parse(ZouJson.format(report)))
        }

    @Test
    fun theTwoSidesAreDAndHAsTheFormMarksThem() =
        runTest {
            val json = ZouJson.format(CompleteReport.report())

            assertContains(json, "\"D\"")
            assertContains(json, "\"H\"")
        }

    @Test
    fun anAbsentAssistantIsNullRatherThanMissing() =
        runTest {
            // A reader cannot tell an omitted key from an unsupported one.
            val alone =
                CompleteReport.match.copy(
                    officials = CompleteReport.match.officials?.copy(assistant = null),
                )
            val report = assertNotNull(BuildZouReport(CompleteReport.league(), NoAddedPlayers())(alone))

            assertContains(ZouJson.format(report), "\"assistant\": null")
        }
}

/** All three at once, with filenames somebody can actually open. */
class ExportZouTest {
    @Test
    fun allThreeFormatsAreProducedEveryTime() =
        runTest {
            val documents = ExportZou()(CompleteReport.report())

            assertEquals(ZouFormat.entries.toSet(), documents.map { it.format }.toSet())
            assertTrue(documents.all { it.content.isNotBlank() })
        }

    @Test
    fun theFileNameIsAsciiSoItSurvivesTheJourney() =
        runTest {
            val documents = ExportZou()(CompleteReport.report())

            val names = documents.map { it.fileName }
            assertEquals(
                listOf(
                    "zapis_6K_2026-08-31_Kominici_Sp--Sumys.txt",
                    "zapis_6K_2026-08-31_Kominici_Sp--Sumys.csv",
                    "zapis_6K_2026-08-31_Kominici_Sp--Sumys.json",
                ),
                names,
            )
            // Diacritics folded rather than dropped: Kominici, not Kominci.
            assertTrue(names.all { name -> name.all { it.code < ASCII_LIMIT } })
        }

    @Test
    fun eachFormatCarriesItsOwnMimeType() =
        runTest {
            val documents = ExportZou()(CompleteReport.report())

            assertEquals("text/csv", documents.single { it.format == ZouFormat.CSV }.mimeType)
            assertEquals("application/json", documents.single { it.format == ZouFormat.JSON }.mimeType)
        }

    @Test
    fun theAddressIsTheOnePsmfAlreadyAccepts() {
        assertEquals("psmf@psmf.cz", PSMF_REPORT_ADDRESS)
    }
}

private const val ASCII_LIMIT = 128
