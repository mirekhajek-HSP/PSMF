package cz.hspinovace.psmf.usecase

import cz.hspinovace.psmf.data.league.JerseyOverridingLeagueRepository
import cz.hspinovace.psmf.domain.Fixtures
import cz.hspinovace.psmf.domain.JerseyNumber
import cz.hspinovace.psmf.domain.PlayerId
import cz.hspinovace.psmf.domain.TeamId
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The Týmy tab is a **reference** surface: search, follow, and a roster the
 * referee may correct one field of. These tests are mostly about what it
 * refuses to do.
 */
class BrowseTeamsTest {
    private fun browse(followed: FakeFollowedTeamRepository = FakeFollowedTeamRepository()) =
        BrowseTeams(TestLeague.repository(), followed)

    @Test
    fun everyBundledTeamIsListedUnderItsLeague() =
        runTest {
            val directory = browse()()

            assertEquals(1, directory.leagues.size)
            assertEquals(
                "6. liga K",
                directory.leagues
                    .single()
                    .group.name,
            )
            assertEquals(
                listOf("Kominíci", "Sp. Sumýš"),
                directory.leagues
                    .single()
                    .teams
                    .map { it.team.name },
            )
            assertFalse(directory.searching)
        }

    @Test
    fun searchingWithoutDiacriticsStillFindsTheTeam() =
        runTest {
            // The case this exists for. Every second name in the league
            // carries a diacritic, and a phone keyboard puts them behind a
            // long press -- so a search that demands them is a search a
            // referee will decide is broken.
            val found = browse()("sumys").leagues.flatMap { it.teams }

            assertEquals(listOf("Sp. Sumýš"), found.map { it.team.name })
        }

    @Test
    fun searchAlsoMatchesTheSlugTheSeedFilesUse() =
        runTest {
            val found = browse()("sp-sumys").leagues.flatMap { it.teams }

            assertEquals(listOf("Sp. Sumýš"), found.map { it.team.name })
        }

    @Test
    fun aQueryThatMatchesNothingSaysSoRatherThanShowingEverything() =
        runTest {
            val directory = browse()("Slavia")

            assertTrue(directory.searching)
            assertTrue(directory.isEmpty)
        }

    @Test
    fun followedTeamsAreTheirOwnList() =
        runTest {
            val directory = browse(FakeFollowedTeamRepository(setOf(Fixtures.awayTeamId)))()

            assertEquals(listOf("Sp. Sumýš"), directory.followed.map { it.team.name })
            // And still under its league: the followed list is a shortcut,
            // not a move.
            assertEquals(
                2,
                directory.leagues
                    .single()
                    .teams.size,
            )
            assertTrue(
                directory.leagues
                    .single()
                    .teams
                    .single { it.followed }
                    .followed,
            )
        }

    @Test
    fun theQueryFiltersTheFollowedListToo() =
        runTest {
            // A search field that leaves a section unfiltered looks broken:
            // the referee typed a name and a team that does not match is
            // still on screen.
            val followed = FakeFollowedTeamRepository(setOf(Fixtures.homeTeamId, Fixtures.awayTeamId))
            val directory = browse(followed)("sumys")

            assertEquals(listOf("Sp. Sumýš"), directory.followed.map { it.team.name })
        }

    @Test
    fun eachTeamCarriesItsSquadSize() =
        runTest {
            // An empty squad is the most likely shape of bad reference data,
            // and a referee who can see it before the match can raise it
            // before the match.
            val cards =
                browse()()
                    .leagues
                    .single()
                    .teams
                    .associate { it.team.name to it.squadSize }

            assertEquals(TestLeague.homeSquad.size, cards.getValue("Kominíci"))
            assertEquals(TestLeague.awaySquad.size, cards.getValue("Sp. Sumýš"))
        }
}

class FoldForSearchTest {
    @Test
    fun theTwoRowsOfTheFoldingTableLineUp() {
        // The table is two aligned strings so that a wrong pairing is
        // visible. This is what makes "visible" mean "checked".
        assertEquals(ACCENTED.length, PLAIN.length)
        assertEquals(ACCENTED.length, ACCENTED.toSet().size, "a letter is listed twice")
    }

    @Test
    fun czechAndSlovakDiacriticsAreStripped() {
        assertEquals("scrz krk", "Ščřž KRK".foldForSearch())
        assertEquals("zluty", "Žlutý".foldForSearch())
        assertEquals("dabel", "Ďábel".foldForSearch())
        assertEquals("uply", "Úplý".foldForSearch())
        assertEquals("fc podoli", "FC Podolí".foldForSearch())
    }

    @Test
    fun anythingNotInTheTablePassesThrough() {
        // Cyrillic is not folded, and must not be mangled: a Ukrainian
        // referee searching in their own alphabet gets an exact match rather
        // than nothing. Anything unlisted degrades to an exact match, which
        // is the failure mode to have.
        assertEquals("команди", "Команди".foldForSearch())
        assertEquals("ac stromovka", "AC Stromovka".foldForSearch())
    }

    @Test
    fun surroundingSpaceIsIgnored() {
        // The referee typed into a field on a phone. There will be a space.
        assertEquals("fk letna", "  FK Letná ".foldForSearch())
    }
}

class LoadTeamRosterTest {
    private fun loader(
        followed: FakeFollowedTeamRepository = FakeFollowedTeamRepository(),
        overrides: FakeJerseyOverrideRepository = FakeJerseyOverrideRepository(),
    ) = LoadTeamRoster(
        JerseyOverridingLeagueRepository(TestLeague.repository(), overrides),
        followed,
        overrides,
    )

    @Test
    fun theRosterIsOrderedByNameAndNotByNumber() =
        runTest {
            // The numbers are the thing being edited, and a list that
            // reorders itself while the referee types into it is hostile.
            val roster = assertNotNull(loader()(Fixtures.homeTeamId))

            assertEquals(listOf("Kříž", "Novák", "Poupě"), roster.rows.map { it.player.name.surname.value })
        }

    @Test
    fun bothKitSetsAreThereInOrder() =
        runTest {
            val roster = assertNotNull(loader()(Fixtures.homeTeamId))

            // Verbatim from PSMF, and never derived from the colours.
            assertEquals(listOf("modrá", "bílo-modrá"), roster.kits.map { it.label })
        }

    @Test
    fun aCorrectedNumberIsTheOneShownAndIsMarkedAsCorrected() =
        runTest {
            val overrides = FakeJerseyOverrideRepository(mapOf(PlayerId("novak") to JerseyNumber(1)))
            val roster = assertNotNull(loader(overrides = overrides)(Fixtures.homeTeamId))

            val novak = roster.rows.single { it.player.id == PlayerId("novak") }
            assertEquals(JerseyNumber(1), novak.jerseyNumber)
            assertTrue(novak.corrected)

            // Everyone else keeps the league's number and is not marked.
            val poupe = roster.rows.single { it.player.id == PlayerId("poupe") }
            assertEquals(JerseyNumber(11), poupe.jerseyNumber)
            assertFalse(poupe.corrected)
        }

    @Test
    fun theRpNumberIsAbsentBecauseTheLeagueHasNotIssuedAny() =
        runTest {
            // Not an oversight, and the screen says so rather than showing a
            // column of dashes: PSMF have not supplied RP numbers, which is
            // the one roster dependency public data cannot meet.
            val roster = assertNotNull(loader()(Fixtures.homeTeamId))

            assertTrue(roster.rows.all { it.rpNumber == null })
        }

    @Test
    fun anUnknownTeamIsNullRatherThanAnEmptyRoster() =
        runTest {
            // An empty roster would read as "this team has no players",
            // which is a different and much more alarming statement.
            assertNull(loader()(TeamId("no-such-team")))
        }

    @Test
    fun followingIsReflectedOnTheRoster() =
        runTest {
            val followed = FakeFollowedTeamRepository(setOf(Fixtures.homeTeamId))
            val roster = assertNotNull(loader(followed)(Fixtures.homeTeamId))

            assertTrue(roster.followed)
        }
}

class JerseyOverridingLeagueRepositoryTest {
    @Test
    fun theCorrectedNumberReachesTheLineupWithoutTheLineupKnowingWhy() =
        runTest {
            // The lineup screen pre-fills from `defaultJerseyNumber` and has
            // never heard of the override table. That is the point of the
            // decorator: one seam, in the Koin module.
            val overrides = FakeJerseyOverrideRepository(mapOf(PlayerId("novak") to JerseyNumber(7)))
            val league = JerseyOverridingLeagueRepository(TestLeague.repository(), overrides)

            val loaded = assertNotNull(league.fixture(Fixtures.fixtureId))
            val novak = loaded.leagueGroup.players.single { it.id == PlayerId("novak") }

            assertEquals(JerseyNumber(7), novak.defaultJerseyNumber)
        }

    @Test
    fun withNoCorrectionsTheSeedDataIsPassedThroughUntouched() =
        runTest {
            val plain = TestLeague.repository()
            val decorated = JerseyOverridingLeagueRepository(plain, FakeJerseyOverrideRepository())

            assertEquals(plain.groups(), decorated.groups())
        }

    @Test
    fun aCorrectionForSomeoneElseLeavesAPlayerAlone() =
        runTest {
            val overrides = FakeJerseyOverrideRepository(mapOf(PlayerId("poupe") to JerseyNumber(2)))
            val league = JerseyOverridingLeagueRepository(TestLeague.repository(), overrides)

            val players =
                league
                    .groups()
                    .single()
                    .players
                    .associate { it.id to it.defaultJerseyNumber }

            assertEquals(JerseyNumber(2), players.getValue(PlayerId("poupe")))
            assertEquals(JerseyNumber(9), players.getValue(PlayerId("novak")))
        }
}
