package cz.hspinovace.psmf.data.seed

import cz.hspinovace.psmf.domain.GroupId
import cz.hspinovace.psmf.domain.IdentificationSource
import cz.hspinovace.psmf.domain.JerseyNumber
import cz.hspinovace.psmf.domain.PlayerOrigin
import cz.hspinovace.psmf.domain.RpNumber
import cz.hspinovace.psmf.domain.TeamId
import cz.hspinovace.psmf.domain.VenueCode
import cz.hspinovace.psmf.domain.suspensionWarning
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * A seed reader backed by a map. Hand-written, because shared tests use
 * fakes: MockK is JVM-only and cannot compile for iOS.
 */
private class FakeSeedFileReader(
    private val files: Map<String, String>,
) : SeedFileReader {
    override suspend fun read(fileName: String): String? = files[fileName]
}

/**
 * THE REQUIREMENT: **adding a league group must be a data change.**
 *
 * Drop in a file, add one line to the index, rebuild. No Kotlin changes,
 * ever. The test named [addingAGroupFileAndAnIndexLineMakesItAppear] is
 * that requirement written down; if someone ever hardcodes a group id or a
 * filename, it fails.
 */
class SeedLeagueCatalogTest {
    private fun indexJson(vararg entries: String) = """{ "groups": [ ${entries.joinToString(",")} ] }"""

    private fun indexEntry(
        id: String,
        file: String,
    ) = """
        {
          "id": "$id",
          "name": "Group $id",
          "seasonId": "2026-podzim",
          "seasonName": "Hanspaulská liga podzim 2026",
          "file": "$file"
        }
        """.trimIndent()

    private fun groupJson(
        id: String,
        halfLength: Int = 30,
        periods: Int = 2,
        teams: String = DEFAULT_TEAMS,
        fixtures: String = DEFAULT_FIXTURES,
    ) = """
        {
          "id": "$id",
          "name": "Group $id",
          "reportCode": "${id.uppercase()}",
          "halfLengthMinutes": $halfLength,
          "periods": $periods,
          "teams": [ $teams ],
          "fixtures": [ $fixtures ]
        }
        """.trimIndent()

    /** Every catalog needs venues: codes are league-wide and always checked. */
    private fun files(vararg entries: Pair<String, String>) = mapOf(VENUES_JSON_ENTRY) + entries.toMap()

    private fun catalogOf(vararg entries: Pair<String, String>) = SeedLeagueCatalog(FakeSeedFileReader(files(*entries)))

    private fun oneGroup(id: String = "6k") =
        catalogOf(
            "index.json" to indexJson(indexEntry(id, "$id.json")),
            "$id.json" to groupJson(id),
        )

    @Test
    fun loadsASingleGroupFromTheIndex() =
        runTest {
            val groups = oneGroup().loadAll()

            assertEquals(1, groups.size)
            assertEquals(GroupId("6k"), groups.single().group.id)
            assertEquals(2, groups.single().teams.size)
            assertEquals(1, groups.single().fixtures.size)
        }

    @Test
    fun addingAGroupFileAndAnIndexLineMakesItAppear() =
        runTest {
            // Before: one group.
            val before =
                files(
                    "index.json" to indexJson(indexEntry("6k", "6k.json")),
                    "6k.json" to groupJson("6k"),
                )
            assertEquals(1, SeedLeagueCatalog(FakeSeedFileReader(before)).loadAll().size)

            // After: a second file, and one more line in the index. That is the
            // entire change. No Kotlin was edited between these two assertions,
            // which is the property this test exists to hold.
            val after =
                before +
                    mapOf(
                        "index.json" to
                            indexJson(
                                indexEntry("6k", "6k.json"),
                                indexEntry("5a", "5a.json"),
                            ),
                        "5a.json" to groupJson("5a"),
                    )

            val groups = SeedLeagueCatalog(FakeSeedFileReader(after)).loadAll()

            assertEquals(2, groups.size)
            assertEquals(listOf(GroupId("6k"), GroupId("5a")), groups.map { it.group.id })
            assertEquals("5A", groups.last().group.reportCode)
        }

    @Test
    fun aGroupCanBeLoadedByIdWithoutLoadingTheOthers() =
        runTest {
            val catalog =
                catalogOf(
                    "index.json" to indexJson(indexEntry("6k", "6k.json"), indexEntry("5a", "5a.json")),
                    "6k.json" to groupJson("6k"),
                    // 5a.json deliberately absent: loading 6k must not need it.
                )

            assertEquals(GroupId("6k"), catalog.load("6k").group.id)
            assertEquals(2, catalog.listGroups().size)
        }

    @Test
    fun theMatchDurationIsReadFromTheFileAndNotHardcoded() =
        runTest {
            // 2 x 30 everywhere in Hanspaulská liga as far as anyone knows, but
            // a competition with a different length must cost a data change and
            // not a code change.
            val catalog =
                catalogOf(
                    "index.json" to indexJson(indexEntry("vet", "vet.json")),
                    "vet.json" to groupJson("vet", halfLength = 35),
                )

            assertEquals(35, catalog.load("vet").group.halfLengthMinutes)
        }

    @Test
    fun thePeriodCountIsDataToo() =
        runTest {
            // 2 x 30 in HL, but veteran and futsal competitions may differ and
            // both numbers should be data rather than constants.
            val catalog =
                catalogOf(
                    "index.json" to indexJson(indexEntry("fut", "fut.json")),
                    "fut.json" to groupJson("fut", halfLength = 20, periods = 3),
                )

            val group = catalog.load("fut").group
            assertEquals(3, group.periods)
            assertEquals(60, group.fullLengthMinutes)
        }

    // --- identity ----------------------------------------------------------

    @Test
    fun idsAndRefsAreBothCarriedAndAreNotTheSameThing() =
        runTest {
            val league = oneGroup().load("6k")
            val team = league.teams.first()

            // The id is opaque; the ref is the readable handle fixtures use.
            assertEquals(TeamId("11111111-1111-4111-8111-111111111111"), team.id)
            assertEquals("t-a", team.ref)
        }

    @Test
    fun fixturesPointAtTeamsByRefAndAreResolvedToIds() =
        runTest {
            // 66 fixtures full of UUIDs would be unmaintainable by hand, so
            // the FILE uses refs. What comes out is ids, because that is what
            // a persisted match stores.
            val league = oneGroup().load("6k")
            val fixture = league.fixtures.single()

            assertEquals(league.teams.first().id, fixture.homeTeamId)
            assertEquals(league.teams.last().id, fixture.awayTeamId)
            assertEquals("f1", fixture.ref)
        }

    @Test
    fun playerRefsAreNotTeamScopedSoATransferDoesNotBreakIdentity() =
        runTest {
            val league = oneGroup().load("6k")
            league.players.forEach { player ->
                assertTrue(
                    league.teams.none { player.ref.startsWith(it.ref) },
                    "Player ref '${player.ref}' looks team-scoped; a transfer would change it",
                )
            }
        }

    // --- kits --------------------------------------------------------------

    @Test
    fun aTeamOwnsItsKitsInOrderWithThePrimaryFirst() =
        runTest {
            val team = oneGroup().load("6k").teams.first()

            assertEquals(2, team.kits.size)
            assertEquals("modrá", team.primaryKit.label)
            assertEquals("bílo-modrá", team.kits.last().label)
            // The label is verbatim and is NOT derived from the colours.
            assertEquals(listOf("bílá", "modrá"), team.kits.last().colours)
        }

    // --- venues ------------------------------------------------------------

    @Test
    fun venuesAreLeagueWideAndComeFromTheirOwnFile() =
        runTest {
            val catalog = oneGroup()

            assertEquals(
                listOf(VenueCode("ZAKOS"), VenueCode("METE1")),
                catalog.loadVenues().map { it.code },
            )
            // And they are carried on the group so a screen has them.
            assertEquals(2, catalog.load("6k").venues.size)
            assertNotNull(catalog.load("6k").venue(VenueCode("ZAKOS")))
        }

    // --- players -----------------------------------------------------------

    @Test
    fun playerDefaultsAndAbsentRpNumbersSurviveTheRoundTrip() =
        runTest {
            val group = oneGroup().load("6k")

            val player = group.playersOf(group.teams.first().id).first()
            assertEquals(JerseyNumber(7), player.defaultJerseyNumber)
            // No RP numbers exist yet: the one roster dependency not obtainable
            // from public data. The date of birth is what identifies them.
            assertNull(player.rpNumber)
            assertEquals(LocalDate(1990, 6, 15), player.dateOfBirth)
            assertEquals(PlayerOrigin.LEAGUE_RECORD, player.origin)
        }

    @Test
    fun anRpNumberIsReadAsItsOwnFieldAndNotAsAPolymorphicValue() =
        runTest {
            val teams =
                """
                {
                  "id": "$ID_A", "ref": "t-a", "name": "Team A", "kits": [ $KIT ],
                  "players": [
                    { "id": "$ID_P1", "ref": "hlok-petr", "surname": "Hlok", "firstName": "Petr",
                      "rpNumber": "59001", "dateOfBirth": "1999-01-21", "defaultJerseyNumber": 33 }
                  ]
                },
                { "id": "$ID_B", "ref": "t-b", "name": "Team B", "kits": [ $KIT ], "players": [] }
                """.trimIndent()

            val player =
                catalogOf(
                    "index.json" to indexJson(indexEntry("6k", "6k.json")),
                    "6k.json" to groupJson("6k", teams = teams),
                ).load("6k").players.single()

            assertEquals(RpNumber("59001"), player.rpNumber)
            assertEquals(LocalDate(1999, 1, 21), player.dateOfBirth)

            // Both are on file, and which one gets written is decided per
            // match rather than baked into the player.
            assertEquals(
                IdentificationSource.RP,
                player.identificationFor(registrationCardPresent = true)?.source,
            )
            assertEquals(
                IdentificationSource.DATE_OF_BIRTH,
                player.identificationFor(registrationCardPresent = false)?.source,
            )
        }

    @Test
    fun theDisciplinaryRecordArrivesWithItsAsOfDate() =
        runTest {
            val teams =
                """
                {
                  "id": "$ID_A", "ref": "t-a", "name": "Team A", "kits": [ $KIT ],
                  "players": [
                    { "id": "$ID_P1", "ref": "novak-jan", "surname": "Novak", "firstName": "Jan",
                      "dateOfBirth": "1990-06-15",
                      "discipline": { "yellowsThisSeason": 4, "asOf": "2026-10-05" } }
                  ]
                },
                { "id": "$ID_B", "ref": "t-b", "name": "Team B", "kits": [ $KIT ], "players": [] }
                """.trimIndent()

            val player =
                catalogOf(
                    "index.json" to indexJson(indexEntry("6k", "6k.json")),
                    "6k.json" to groupJson("6k", teams = teams),
                ).load("6k").players.single()

            val record = assertNotNull(player.discipline)
            assertEquals(4, record.yellowsThisSeason)
            assertEquals(LocalDate(2026, 10, 5), record.asOf)
            // Advisory only, and it carries the date so a screen can show it.
            assertEquals(LocalDate(2026, 10, 5), record.suspensionWarning()?.asOf)
        }

    // --- the ways hand-edited data goes wrong ------------------------------

    private suspend fun problemFrom(
        teams: String = DEFAULT_TEAMS,
        fixtures: String = DEFAULT_FIXTURES,
    ): SeedProblem =
        assertFailsWith<SeedException> {
            catalogOf(
                "index.json" to indexJson(indexEntry("6k", "6k.json")),
                "6k.json" to groupJson("6k", teams = teams, fixtures = fixtures),
            ).loadAll()
        }.problem

    @Test
    fun aMissingFileIsReportedAgainstItsName() =
        runTest {
            val catalog = SeedLeagueCatalog(FakeSeedFileReader(emptyMap()))
            assertEquals(
                SeedProblem.FileMissing("venues.json"),
                assertFailsWith<SeedException> { catalog.loadAll() }.problem,
            )
        }

    @Test
    fun anIndexEntryPointingAtANonexistentFileIsReported() =
        runTest {
            val catalog = catalogOf("index.json" to indexJson(indexEntry("6k", "nope.json")))
            assertEquals(
                SeedProblem.FileMissing("nope.json"),
                assertFailsWith<SeedException> { catalog.loadAll() }.problem,
            )
        }

    @Test
    fun aFileWhoseIdDisagreesWithTheIndexIsReported() =
        runTest {
            val catalog =
                catalogOf(
                    "index.json" to indexJson(indexEntry("6k", "6k.json")),
                    "6k.json" to groupJson("typo"),
                )
            assertTrue(assertFailsWith<SeedException> { catalog.loadAll() }.problem is SeedProblem.InconsistentData)
        }

    @Test
    fun aFixtureReferringToAnUnknownTeamIsReported() =
        runTest {
            // The likeliest hand-editing mistake, and the least obvious later.
            val problem =
                problemFrom(
                    fixtures =
                        """
                        { "id": "$ID_F1", "ref": "f1", "round": 1, "date": "2026-08-31", "time": "19:00",
                          "venue": "ZAKOS", "home": "t-a", "away": "t-ghost" }
                        """.trimIndent(),
                )
            assertTrue(problem is SeedProblem.InconsistentData)
            assertTrue(problem.detail.contains("t-ghost"))
        }

    @Test
    fun aFixtureAtAVenueThatIsNotInVenuesJsonIsReported() =
        runTest {
            // The same class of check as the unknown-team one. Venue codes are
            // league-wide, so the fixture is checked against venues.json.
            val problem =
                problemFrom(
                    fixtures =
                        """
                        { "id": "$ID_F1", "ref": "f1", "round": 1, "date": "2026-08-31", "time": "19:00",
                          "venue": "NOWHERE", "home": "t-a", "away": "t-b" }
                        """.trimIndent(),
                )
            assertTrue(problem is SeedProblem.InconsistentData)
            assertTrue(problem.detail.contains("NOWHERE"))
            assertTrue(problem.detail.contains("venues.json"))
        }

    @Test
    fun aBlankKitLabelIsReportedBecauseTheReportCannotBeGeneratedWithoutIt() =
        runTest {
            val problem =
                problemFrom(
                    teams =
                        """
                        {
                          "id": "$ID_A", "ref": "t-a", "name": "Team A",
                          "kits": [ { "id": "$ID_K1", "label": "  ", "colours": ["modrá"] } ],
                          "players": []
                        },
                        { "id": "$ID_B", "ref": "t-b", "name": "Team B", "kits": [ $KIT ], "players": [] }
                        """.trimIndent(),
                )
            assertTrue(problem is SeedProblem.InconsistentData)
            assertTrue(problem.detail.contains("blank label"))
        }

    @Test
    fun aTeamWithNoKitsAtAllIsReported() =
        runTest {
            val problem =
                problemFrom(
                    teams =
                        """
                        { "id": "$ID_A", "ref": "t-a", "name": "Team A", "kits": [], "players": [] },
                        { "id": "$ID_B", "ref": "t-b", "name": "Team B", "kits": [ $KIT ], "players": [] }
                        """.trimIndent(),
                )
            assertTrue(problem is SeedProblem.InconsistentData)
            assertTrue(problem.detail.contains("no kits"))
        }

    @Test
    fun aPlayerWithNoIdentificationAtAllIsReported() =
        runTest {
            // At least one of rpNumber, dateOfBirth or birthNumber. A player
            // who cannot be identified cannot be put on a report.
            val problem =
                problemFrom(
                    teams =
                        """
                        {
                          "id": "$ID_A", "ref": "t-a", "name": "Team A", "kits": [ $KIT ],
                          "players": [ { "id": "$ID_P1", "ref": "novak-jan",
                            "surname": "Novak", "firstName": "Jan" } ]
                        },
                        { "id": "$ID_B", "ref": "t-b", "name": "Team B", "kits": [ $KIT ], "players": [] }
                        """.trimIndent(),
                )
            assertTrue(problem is SeedProblem.InconsistentData)
            assertTrue(problem.detail.contains("dateOfBirth"))
        }

    @Test
    fun aPitchAddedPlayerCarryingAnRpNumberIsReported() =
        runTest {
            // RP numbers are issued by PSMF. A row claiming both that the
            // referee added this player and that they have an RP number is
            // data that should not exist.
            val problem =
                problemFrom(
                    teams =
                        """
                        {
                          "id": "$ID_A", "ref": "t-a", "name": "Team A", "kits": [ $KIT ],
                          "players": [ { "id": "$ID_P1", "ref": "novak-jan", "surname": "Novak",
                            "firstName": "Jan", "dateOfBirth": "1990-06-15",
                            "rpNumber": "59001", "origin": "ADDED_AT_PITCH" } ]
                        },
                        { "id": "$ID_B", "ref": "t-b", "name": "Team B", "kits": [ $KIT ], "players": [] }
                        """.trimIndent(),
                )
            assertTrue(problem is SeedProblem.InconsistentData)
            assertTrue(problem.detail.contains("ADDED_AT_PITCH"))
        }

    @Test
    fun twoTeamsSharingARefAreReportedBecauseFixturesPointAtRefs() =
        runTest {
            val problem =
                problemFrom(
                    teams =
                        """
                        { "id": "$ID_A", "ref": "t-a", "name": "Team A", "kits": [ $KIT ], "players": [] },
                        { "id": "$ID_B", "ref": "t-a", "name": "Team B", "kits": [ $KIT ], "players": [] }
                        """.trimIndent(),
                    fixtures = "",
                )
            assertTrue(problem is SeedProblem.InconsistentData)
            assertTrue(problem.detail.contains("Duplicate team ref"))
        }

    @Test
    fun aCyrillicPlayerNameIsRejectedAtTheSeedBoundary() =
        runTest {
            // Names are Latin throughout, because PSMF's records are.
            val problem =
                problemFrom(
                    teams =
                        """
                        {
                          "id": "$ID_A", "ref": "t-a", "name": "Team A", "kits": [ $KIT ],
                          "players": [ { "id": "$ID_P1", "ref": "koval-oleksandr", "surname": "Коваль",
                            "firstName": "Олександр", "dateOfBirth": "1990-06-15" } ]
                        },
                        { "id": "$ID_B", "ref": "t-b", "name": "Team B", "kits": [ $KIT ], "players": [] }
                        """.trimIndent(),
                )
            assertTrue(problem is SeedProblem.InconsistentData)
            assertTrue(problem.detail.contains("Latin"))
        }

    @Test
    fun malformedJsonIsReportedAgainstItsFileRatherThanCrashing() =
        runTest {
            val catalog =
                SeedLeagueCatalog(
                    FakeSeedFileReader(files("index.json" to "{ this is not json")),
                )
            val problem = assertFailsWith<SeedException> { catalog.loadAll() }.problem
            assertTrue(problem is SeedProblem.Unparseable)
            assertEquals("index.json", problem.fileName)
        }

    private companion object {
        // Opaque in the domain, readable here on purpose.
        const val ID_A = "11111111-1111-4111-8111-111111111111"
        const val ID_B = "22222222-2222-4222-8222-222222222222"
        const val ID_P1 = "33333333-3333-4333-8333-333333333333"
        const val ID_P2 = "44444444-4444-4444-8444-444444444444"
        const val ID_F1 = "55555555-5555-4555-8555-555555555555"
        const val ID_K1 = "66666666-6666-4666-8666-666666666666"
        const val ID_K2 = "77777777-7777-4777-8777-777777777777"

        const val KIT = """{ "id": "$ID_K1", "label": "modrá", "colours": ["modrá"] }"""

        val VENUES_JSON_ENTRY =
            "venues.json" to """{ "venues": [ { "code": "ZAKOS" }, { "code": "METE1" } ] }"""

        val DEFAULT_TEAMS =
            """
            {
              "id": "$ID_A", "ref": "t-a", "name": "Team A",
              "kits": [
                { "id": "$ID_K1", "label": "modrá", "colours": ["modrá"] },
                { "id": "$ID_K2", "label": "bílo-modrá", "colours": ["bílá", "modrá"] }
              ],
              "players": [ { "id": "$ID_P1", "ref": "novak-jan", "surname": "Novak", "firstName": "Jan",
                "dateOfBirth": "1990-06-15", "defaultJerseyNumber": 7 } ]
            },
            {
              "id": "$ID_B", "ref": "t-b", "name": "Team B", "kits": [ $KIT ],
              "players": [ { "id": "$ID_P2", "ref": "svoboda-petr", "surname": "Svoboda",
                "firstName": "Petr", "dateOfBirth": "1988-02-02", "defaultJerseyNumber": 9 } ]
            }
            """.trimIndent()

        val DEFAULT_FIXTURES =
            """
            { "id": "$ID_F1", "ref": "f1", "round": 1, "date": "2026-08-31", "time": "19:00",
              "venue": "ZAKOS", "home": "t-a", "away": "t-b" }
            """.trimIndent()
    }
}
