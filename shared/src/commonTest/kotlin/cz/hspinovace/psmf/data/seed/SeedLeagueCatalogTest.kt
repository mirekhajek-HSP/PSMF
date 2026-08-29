package cz.hspinovace.psmf.data.seed

import cz.hspinovace.psmf.domain.GroupId
import cz.hspinovace.psmf.domain.JerseyNumber
import cz.hspinovace.psmf.domain.PlayerIdentifierType
import cz.hspinovace.psmf.domain.TeamId
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
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
        teams: String = DEFAULT_TEAMS,
        fixtures: String = DEFAULT_FIXTURES,
    ) = """
        {
          "id": "$id",
          "name": "Group $id",
          "reportCode": "${id.uppercase()}",
          "halfLengthMinutes": $halfLength,
          "teams": [ $teams ],
          "fixtures": [ $fixtures ]
        }
        """.trimIndent()

    @Test
    fun loadsASingleGroupFromTheIndex() =
        runTest {
            val catalog =
                SeedLeagueCatalog(
                    FakeSeedFileReader(
                        mapOf(
                            "index.json" to indexJson(indexEntry("6k", "6k.json")),
                            "6k.json" to groupJson("6k"),
                        ),
                    ),
                )

            val groups = catalog.loadAll()

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
                mapOf(
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
                SeedLeagueCatalog(
                    FakeSeedFileReader(
                        mapOf(
                            "index.json" to indexJson(indexEntry("6k", "6k.json"), indexEntry("5a", "5a.json")),
                            "6k.json" to groupJson("6k"),
                            // 5a.json deliberately absent: loading 6k must not need it.
                        ),
                    ),
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
                SeedLeagueCatalog(
                    FakeSeedFileReader(
                        mapOf(
                            "index.json" to indexJson(indexEntry("vet", "vet.json")),
                            "vet.json" to groupJson("vet", halfLength = 35),
                        ),
                    ),
                )

            assertEquals(35, catalog.load("vet").group.halfLengthMinutes)
        }

    @Test
    fun playerDefaultsAndMissingIdentifiersSurviveTheRoundTrip() =
        runTest {
            val group =
                SeedLeagueCatalog(
                    FakeSeedFileReader(
                        mapOf(
                            "index.json" to indexJson(indexEntry("6k", "6k.json")),
                            "6k.json" to groupJson("6k"),
                        ),
                    ),
                ).load("6k")

            val player = group.playersOf(TeamId("t-a")).first()
            assertEquals(JerseyNumber(7), player.defaultJerseyNumber)
            // No RP numbers exist yet: the one roster dependency not obtainable
            // from public data.
            assertNull(player.identifier)
        }

    @Test
    fun anIdentifierIsReadTogetherWithItsKind() =
        runTest {
            val teams =
                """
                {
                  "id": "t-a", "name": "Team A", "kitColour": "modrá",
                  "players": [
                    { "id": "p1", "surname": "Hlok", "givenName": "Petr",
                      "identifier": "990121", "identifierType": "DATE_OF_BIRTH", "defaultJerseyNumber": 33 }
                  ]
                },
                { "id": "t-b", "name": "Team B", "kitColour": "bílá", "players": [] }
                """.trimIndent()

            val group =
                SeedLeagueCatalog(
                    FakeSeedFileReader(
                        mapOf(
                            "index.json" to indexJson(indexEntry("6k", "6k.json")),
                            "6k.json" to groupJson("6k", teams = teams),
                        ),
                    ),
                ).load("6k")

            val identifier = group.players.single().identifier
            assertEquals("990121", identifier?.value)
            assertEquals(PlayerIdentifierType.DATE_OF_BIRTH, identifier?.type)
        }

    // --- the ways hand-edited data goes wrong ------------------------------

    @Test
    fun aMissingFileIsReportedAgainstItsName() =
        runTest {
            val catalog = SeedLeagueCatalog(FakeSeedFileReader(emptyMap()))
            val problem = assertFailsWith<SeedException> { catalog.loadAll() }.problem
            assertEquals(SeedProblem.FileMissing("index.json"), problem)
        }

    @Test
    fun anIndexEntryPointingAtANonexistentFileIsReported() =
        runTest {
            val catalog =
                SeedLeagueCatalog(
                    FakeSeedFileReader(mapOf("index.json" to indexJson(indexEntry("6k", "nope.json")))),
                )
            assertEquals(
                SeedProblem.FileMissing("nope.json"),
                assertFailsWith<SeedException> { catalog.loadAll() }.problem,
            )
        }

    @Test
    fun aFileWhoseIdDisagreesWithTheIndexIsReported() =
        runTest {
            val catalog =
                SeedLeagueCatalog(
                    FakeSeedFileReader(
                        mapOf(
                            "index.json" to indexJson(indexEntry("6k", "6k.json")),
                            "6k.json" to groupJson("typo"),
                        ),
                    ),
                )
            val problem = assertFailsWith<SeedException> { catalog.loadAll() }.problem
            assertTrue(problem is SeedProblem.InconsistentData)
        }

    @Test
    fun aFixtureReferringToAnUnknownTeamIsReported() =
        runTest {
            // The likeliest hand-editing mistake, and the least obvious later.
            val catalog =
                SeedLeagueCatalog(
                    FakeSeedFileReader(
                        mapOf(
                            "index.json" to indexJson(indexEntry("6k", "6k.json")),
                            "6k.json" to
                                groupJson(
                                    "6k",
                                    fixtures =
                                        """
                                        { "id": "f1", "round": 1, "date": "2026-08-31", "time": "19:00",
                                          "venue": "ZAKOS", "home": "t-a", "away": "t-ghost" }
                                        """.trimIndent(),
                                ),
                        ),
                    ),
                )
            val problem = assertFailsWith<SeedException> { catalog.loadAll() }.problem
            assertTrue(problem is SeedProblem.InconsistentData)
            assertTrue(problem.detail.contains("t-ghost"))
        }

    @Test
    fun anIdentifierWithoutItsKindIsReported() =
        runTest {
            val teams =
                """
                {
                  "id": "t-a", "name": "Team A", "kitColour": "modrá",
                  "players": [ { "id": "p1", "surname": "Novak", "givenName": "Jan", "identifier": "59001" } ]
                },
                { "id": "t-b", "name": "Team B", "kitColour": "bílá", "players": [] }
                """.trimIndent()

            val catalog =
                SeedLeagueCatalog(
                    FakeSeedFileReader(
                        mapOf(
                            "index.json" to indexJson(indexEntry("6k", "6k.json")),
                            "6k.json" to groupJson("6k", teams = teams),
                        ),
                    ),
                )
            val problem = assertFailsWith<SeedException> { catalog.loadAll() }.problem
            assertTrue(problem is SeedProblem.InconsistentData)
            assertTrue(problem.detail.contains("identifierType"))
        }

    @Test
    fun aCyrillicPlayerNameIsRejectedAtTheSeedBoundary() =
        runTest {
            // Names are Latin throughout, because PSMF's records are.
            val teams =
                """
                {
                  "id": "t-a", "name": "Team A", "kitColour": "modrá",
                  "players": [ { "id": "p1", "surname": "Коваль", "givenName": "Олександр" } ]
                },
                { "id": "t-b", "name": "Team B", "kitColour": "bílá", "players": [] }
                """.trimIndent()

            val catalog =
                SeedLeagueCatalog(
                    FakeSeedFileReader(
                        mapOf(
                            "index.json" to indexJson(indexEntry("6k", "6k.json")),
                            "6k.json" to groupJson("6k", teams = teams),
                        ),
                    ),
                )
            val problem = assertFailsWith<SeedException> { catalog.loadAll() }.problem
            assertTrue(problem is SeedProblem.InconsistentData)
            assertTrue(problem.detail.contains("Latin"))
        }

    @Test
    fun malformedJsonIsReportedAgainstItsFileRatherThanCrashing() =
        runTest {
            val catalog =
                SeedLeagueCatalog(
                    FakeSeedFileReader(mapOf("index.json" to "{ this is not json")),
                )
            val problem = assertFailsWith<SeedException> { catalog.loadAll() }.problem
            assertTrue(problem is SeedProblem.Unparseable)
            assertEquals("index.json", problem.fileName)
        }

    private companion object {
        val DEFAULT_TEAMS =
            """
            {
              "id": "t-a", "name": "Team A", "kitColour": "modrá",
              "players": [ { "id": "p1", "surname": "Novak", "givenName": "Jan", "defaultJerseyNumber": 7 } ]
            },
            {
              "id": "t-b", "name": "Team B", "kitColour": "bílá",
              "players": [ { "id": "p2", "surname": "Svoboda", "givenName": "Petr", "defaultJerseyNumber": 9 } ]
            }
            """.trimIndent()

        val DEFAULT_FIXTURES =
            """
            { "id": "f1", "round": 1, "date": "2026-08-31", "time": "19:00",
              "venue": "ZAKOS", "home": "t-a", "away": "t-b" }
            """.trimIndent()
    }
}
