package cz.hspinovace.psmf.usecase

import cz.hspinovace.psmf.domain.Fixtures
import cz.hspinovace.psmf.domain.JerseyNumber
import cz.hspinovace.psmf.domain.Match
import cz.hspinovace.psmf.domain.MatchId
import cz.hspinovace.psmf.domain.PlayerOrigin
import cz.hspinovace.psmf.domain.TeamSide
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

private fun match() = Match(MatchId("m1"), Fixtures.fixtureId, Fixtures.groupId)

/**
 * Building the editable lineup, and — the half that bites — rebuilding it
 * from what was saved.
 *
 * A stored `Lineup` holds only the players who turned up, so absence on
 * the way back in is "not in the saved lineup". Reading that backwards
 * would mark a full squad absent the second time the screen opened.
 */
class BuildLineupEntryTest {
    private var minted = 0

    private fun build(added: FakeAddedPlayerRepository = FakeAddedPlayerRepository()) =
        BuildLineupEntry(TestLeague.repository(), added) { "id-${++minted}" }

    @Test
    fun aFreshMatchStartsWithEverybodyPresent() =
        runTest {
            val entry = assertNotNull(build()(match()))

            assertEquals(TestLeague.homeSquad.size, entry.home.present.size)
            assertEquals(0, entry.home.absentCount)
            assertEquals(0, entry.away.absentCount)
        }

    @Test
    fun jerseyNumbersComeFromSeedData() =
        runTest {
            val entry = assertNotNull(build()(match()))

            val first = TestLeague.homeSquad.first()
            val member = entry.home.members.single { it.player.id == first.id }
            assertEquals(first.defaultJerseyNumber, member.jerseyNumber)
        }

    @Test
    fun theKitDefaultsToTheTeamsPrimary() =
        runTest {
            val entry = assertNotNull(build()(match()))

            assertEquals(Fixtures.homeTeam.primaryKit.id, entry.home.kitId)
            assertEquals(Fixtures.awayTeam.primaryKit.id, entry.away.kitId)
        }

    @Test
    fun reopeningARecordedLineupMarksTheMissingPlayersAbsent() =
        runTest {
            // THE TRAP. Read this backwards and a full squad comes back
            // marked absent on the second visit to the screen.
            val absentee = TestLeague.homeSquad.first()
            val saved =
                assertNotNull(
                    build()(match())!!
                        .home
                        .withMember(absentee.id) { it.copy(absent = true) }
                        .toLineup(),
                )
            val recorded = match().copy(homeLineup = saved)

            val entry = assertNotNull(build()(recorded))

            assertTrue(
                entry.home.members
                    .single { it.player.id == absentee.id }
                    .absent,
            )
            assertEquals(TestLeague.homeSquad.size - 1, entry.home.present.size)
            // The other team was never recorded, so it is still all present.
            assertEquals(0, entry.away.absentCount)
        }

    @Test
    fun anAppearanceKeepsItsIdAcrossReopening() =
        runTest {
            // Goals and cards are attributed to an appearance id. Minting a
            // new one on reload would orphan every event of the first half.
            val first = assertNotNull(build()(match()))
            val saved = assertNotNull(first.home.toLineup())
            val recorded = match().copy(homeLineup = saved)

            val second = assertNotNull(build()(recorded))

            val before = first.home.members.associate { it.player.id to it.appearanceId }
            val after = second.home.members.associate { it.player.id to it.appearanceId }
            assertEquals(before, after)
        }

    @Test
    fun editedJerseyNumbersSurviveReopening() =
        runTest {
            val player = TestLeague.homeSquad.first()
            val saved =
                assertNotNull(
                    build()(match())!!
                        .home
                        .withMember(player.id) { it.copy(jerseyNumber = JerseyNumber(77)) }
                        .toLineup(),
                )

            val entry = assertNotNull(build()(match().copy(homeLineup = saved)))

            assertEquals(
                JerseyNumber(77),
                entry.home.members
                    .single { it.player.id == player.id }
                    .jerseyNumber,
            )
        }

    @Test
    fun aPitchAddedPlayerJoinsTheSquadOfTheirOwnTeam() =
        runTest {
            val added = FakeAddedPlayerRepository()
            val player = assertNotNull(AddPlayerAtThePitch(added) { "added-1" }(match(), request()))

            val entry = assertNotNull(build(added)(match()))

            assertTrue(entry.home.members.any { it.player.id == player.id })
            assertFalse(entry.away.members.any { it.player.id == player.id })
            // Present by default, like everyone else -- they are standing there.
            assertFalse(
                entry.home.members
                    .single { it.player.id == player.id }
                    .absent,
            )
        }

    @Test
    fun aFixtureThatIsNotInTheLeagueDataBuildsNothing() =
        runTest {
            val elsewhere =
                match().copy(
                    fixtureId =
                        cz.hspinovace.psmf.domain
                            .FixtureId("nope"),
                )

            assertNull(build()(elsewhere))
        }
}

private fun request(
    firstName: String = "Petr",
    surname: String = "Hlok",
    dateOfBirth: String = "21.1.1999",
) = NewPlayerRequest(
    teamId = Fixtures.homeTeamId,
    firstName = firstName,
    surname = surname,
    dateOfBirth = dateOfBirth,
)

/**
 * RULE: **a player added at the pitch cannot acquire an RP number.**
 *
 * RP numbers are issued by PSMF. There is no parameter for one on
 * [NewPlayerRequest], none on [AddPlayerAtThePitch], and none on
 * `Player.addedAtThePitch` — so there is no path from a keyboard to an
 * `RpNumber` anywhere in the app, and this test is what keeps it that way.
 */
class AddPlayerAtThePitchTest {
    private var minted = 0

    private fun addPlayer(repository: FakeAddedPlayerRepository) =
        AddPlayerAtThePitch(repository) { "added-${++minted}" }

    @Test
    fun addsAPlayerWithANamenAndADateOfBirthAndNothingElse() =
        runTest {
            val repository = FakeAddedPlayerRepository()

            val player = assertNotNull(addPlayer(repository)(match(), request()))

            assertEquals("Hlok Petr", player.name.asWrittenOnReport)
            assertEquals(PlayerOrigin.ADDED_AT_PITCH, player.origin)
            assertNull(player.rpNumber)
            assertNull(player.birthNumber)
            // The worked example's row: 990121 among five-digit RP numbers.
            assertEquals("990121", player.identificationFor(registrationCardPresent = true)?.value)
        }

    @Test
    fun theyAreStoredAgainstTheMatchBecauseNoLeagueFileKnowsThem() =
        runTest {
            val repository = FakeAddedPlayerRepository()
            val player = assertNotNull(addPlayer(repository)(match(), request()))

            assertEquals(listOf(player), repository.forMatch(MatchId("m1")))
            assertEquals(emptyList(), repository.forMatch(MatchId("another")))
        }

    @Test
    fun anRpNumberCannotBeGivenToThemEvenByTheModel() =
        runTest {
            val repository = FakeAddedPlayerRepository()
            val player = assertNotNull(addPlayer(repository)(match(), request()))

            // The only way one ever arrives is PSMF's own reconciliation,
            // which also moves them out of ADDED_AT_PITCH.
            val reconciled =
                player.registeredWith(
                    cz.hspinovace.psmf.domain
                        .RpNumber("59123"),
                )
            assertEquals(PlayerOrigin.LEAGUE_RECORD, reconciled.origin)
        }

    @Test
    fun anIncompleteRequestAddsNobodyAndSaysWhichFieldIsWrong() =
        runTest {
            val repository = FakeAddedPlayerRepository()

            assertNull(addPlayer(repository)(match(), request(surname = "")))
            assertNull(addPlayer(repository)(match(), request(dateOfBirth = "nonsense")))
            assertEquals(emptyList(), repository.forMatch(MatchId("m1")))
        }

    @Test
    fun theFormReportsProblemsInTheOrderItShowsTheFields() {
        assertEquals(
            listOf(NewPlayerProblem.SURNAME, NewPlayerProblem.FIRST_NAME, NewPlayerProblem.DATE_OF_BIRTH),
            NewPlayerRequest(teamId = Fixtures.homeTeamId).problems(),
        )
        assertTrue(request().problems().isEmpty())
    }

    @Test
    fun aCyrillicNameIsRefusedBecausePsmfsRecordsAreLatin() =
        runTest {
            val repository = FakeAddedPlayerRepository()

            assertNull(addPlayer(repository)(match(), request(surname = "Вовк")))
            assertTrue(NewPlayerProblem.SURNAME in request(surname = "Вовк").problems())
        }
}

/** Writing a team's block through, and refusing to write a broken one. */
class SaveLineupTest {
    private var minted = 0

    private suspend fun entryFor(match: Match) =
        assertNotNull(
            BuildLineupEntry(TestLeague.repository(), FakeAddedPlayerRepository()) { "id-${++minted}" }(match),
        )

    @Test
    fun aValidBlockIsWrittenThroughImmediately() =
        runTest {
            val matches = FakeMatchRepository(listOf(match()))
            val entry = entryFor(match())

            val updated = SaveLineup(matches)(match(), entry.home)

            assertNotNull(updated.homeLineup)
            assertEquals(updated, matches.load(MatchId("m1")))
            assertNull(updated.awayLineup)
        }

    @Test
    fun aBlockWithTwoPlayersOnOneNumberIsNotWrittenAtAll() =
        runTest {
            // There is no valid lineup to save: the type will not hold one,
            // because the goals would be ambiguous. The last good state
            // stays on disk and the screen says what is wrong.
            val matches = FakeMatchRepository(listOf(match()))
            val entry = entryFor(match())
            val clashing =
                entry.home.copy(
                    members = entry.home.members.map { it.copy(jerseyNumber = JerseyNumber(9)) },
                )

            val unchanged = SaveLineup(matches)(match(), clashing)

            assertNull(unchanged.homeLineup)
            assertEquals(0, matches.saves)
        }

    @Test
    fun theTwoTeamsAreWrittenIndependently() =
        runTest {
            val matches = FakeMatchRepository(listOf(match()))
            val entry = entryFor(match())

            val withHome = SaveLineup(matches)(match(), entry.home)
            val withBoth = SaveLineup(matches)(withHome, entry.away)

            assertEquals(TeamSide.HOME, withBoth.homeLineup?.side)
            assertEquals(TeamSide.AWAY, withBoth.awayLineup?.side)
            assertEquals(2, matches.saves)
        }
}

/**
 * RULE: **a player added at the pitch is present, whenever they are added.**
 *
 * Absence is derived from "not in the saved lineup", which cannot tell
 * somebody who did not turn up from somebody who was added after the
 * lineup was saved. Every earlier test added a player to a match with no
 * lineup recorded yet — the one case where the ambiguity cannot arise —
 * which is exactly why a device found this and they did not.
 */
class AddPlayerToLineupTest {
    private var minted = 0

    private fun useCase(
        matches: FakeMatchRepository,
        added: FakeAddedPlayerRepository,
    ): AddPlayerToLineup {
        val newId = NewId { "id-${++minted}" }
        val build = BuildLineupEntry(TestLeague.repository(), added, newId)
        return AddPlayerToLineup(AddPlayerAtThePitch(added, newId), build, SaveLineup(matches))
    }

    private suspend fun matchWithAnAbsence(matches: FakeMatchRepository): Match {
        val added = FakeAddedPlayerRepository()
        val entry =
            assertNotNull(BuildLineupEntry(TestLeague.repository(), added) { "seed-${++minted}" }(match()))
        val withAbsence =
            entry.home.withMember(TestLeague.homeSquad.first().id) { it.copy(absent = true) }
        return SaveLineup(matches)(match(), withAbsence)
    }

    @Test
    fun aPlayerAddedAfterTheLineupWasSavedIsStillPresent() =
        runTest {
            // THE BUG. Marking two absences saves a lineup; the player added
            // next was not in it, so the rebuild called them absent.
            val matches = FakeMatchRepository(listOf(match()))
            val added = FakeAddedPlayerRepository()
            val started = matchWithAnAbsence(matches)

            val updated = assertNotNull(useCase(matches, added)(started, request()))

            val player = added.forMatch(MatchId("m1")).single()
            val entry = assertNotNull(BuildLineupEntry(TestLeague.repository(), added) { "x" }(updated))
            val member = entry.home.members.single { it.player.id == player.id }
            assertFalse(member.absent, "A player added at the pitch came back marked absent")
            assertTrue(member.addedAtThePitch)
        }

    @Test
    fun theAbsenceThatWasAlreadyRecordedIsNotUndone() =
        runTest {
            val matches = FakeMatchRepository(listOf(match()))
            val added = FakeAddedPlayerRepository()
            val started = matchWithAnAbsence(matches)
            val absentee = TestLeague.homeSquad.first()

            val updated = assertNotNull(useCase(matches, added)(started, request()))

            val entry = assertNotNull(BuildLineupEntry(TestLeague.repository(), added) { "x" }(updated))
            assertTrue(
                entry.home.members
                    .single { it.player.id == absentee.id }
                    .absent,
            )
        }

    @Test
    fun anUnusableRequestChangesNothing() =
        runTest {
            val matches = FakeMatchRepository(listOf(match()))
            val added = FakeAddedPlayerRepository()
            val started = matchWithAnAbsence(matches)

            assertNull(useCase(matches, added)(started, request(surname = "")))
            assertEquals(emptyList(), added.forMatch(MatchId("m1")))
        }
}

/**
 * RULE: **Continue commits both blocks, including the one nobody touched.**
 *
 * Write-through happens on edit, and a team with nobody absent and every
 * number already right is never edited. Without this its block is never
 * written, and the console opens with nobody on that side to tap — which
 * is exactly what a device showed and what every earlier test missed,
 * because each of them saved the side it was asserting on.
 */
class SaveBothLineupsTest {
    private var minted = 0

    private suspend fun entryFor(match: Match) =
        assertNotNull(
            BuildLineupEntry(TestLeague.repository(), FakeAddedPlayerRepository()) { "id-${++minted}" }(match),
        )

    @Test
    fun bothBlocksAreWrittenEvenWhenNeitherWasEdited() =
        runTest {
            val matches = FakeMatchRepository(listOf(match()))
            val entry = entryFor(match())

            val updated = SaveLineup(matches)(match(), entry)

            assertNotNull(updated.homeLineup, "The home block was not written")
            assertNotNull(updated.awayLineup, "The away block was not written")
            assertEquals(updated, matches.load(MatchId("m1")))
        }

    @Test
    fun everyPlayerIsInTheirBlockBecauseNobodyWasMarkedAbsent() =
        runTest {
            val matches = FakeMatchRepository(listOf(match()))
            val updated = SaveLineup(matches)(match(), entryFor(match()))

            assertEquals(TestLeague.homeSquad.size, updated.homeLineup?.appearances?.size)
            assertEquals(TestLeague.awaySquad.size, updated.awayLineup?.appearances?.size)
        }

    @Test
    fun anEditOnOneSideDoesNotLoseTheOther() =
        runTest {
            val matches = FakeMatchRepository(listOf(match()))
            val entry = entryFor(match())
            val edited = entry.with(entry.home.withMember(TestLeague.homeSquad.first().id) { it.copy(absent = true) })

            val updated = SaveLineup(matches)(match(), edited)

            assertEquals(TestLeague.homeSquad.size - 1, updated.homeLineup?.appearances?.size)
            assertEquals(TestLeague.awaySquad.size, updated.awayLineup?.appearances?.size)
        }
}
