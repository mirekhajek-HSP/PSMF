package cz.hspinovace.psmf.usecase

import cz.hspinovace.psmf.domain.Appearance
import cz.hspinovace.psmf.domain.AppearanceId
import cz.hspinovace.psmf.domain.Fixtures
import cz.hspinovace.psmf.domain.IdentificationSource
import cz.hspinovace.psmf.domain.JerseyNumber
import cz.hspinovace.psmf.domain.Lineup
import cz.hspinovace.psmf.domain.LineupConfirmation
import cz.hspinovace.psmf.domain.Match
import cz.hspinovace.psmf.domain.MatchId
import cz.hspinovace.psmf.domain.PersonName
import cz.hspinovace.psmf.domain.PlayerOrigin
import cz.hspinovace.psmf.domain.ReportedIdentification
import cz.hspinovace.psmf.domain.TeamSide
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

private fun match() = Match(MatchId("m1"), Fixtures.fixtureId, Fixtures.groupId)

/**
 * Building the editable lineup, and -- the half that bites -- rebuilding it
 * from what was saved.
 *
 * A stored `Lineup` holds only the players who turned up, so a player is
 * present exactly when the saved lineup has them. Reading that wrongly would
 * lose the marks the referee gave on the second visit to the screen.
 */
class BuildLineupEntryTest {
    private var minted = 0

    private fun build(
        added: FakeAddedPlayerRepository = FakeAddedPlayerRepository(),
        numbers: FakeRememberedCardNumberRepository = FakeRememberedCardNumberRepository(),
    ) = BuildLineupEntry(TestLeague.repository(), added, FakeRememberedDateOfBirthRepository(), numbers) {
        "id-${++minted}"
    }

    @Test
    fun aFreshMatchStartsWithNobodyPresent() =
        runTest {
            // DECISIONS 2026-10-17: the lineup marks who came.
            val entry = assertNotNull(build()(match()))

            assertEquals(TestLeague.homeSquad.size, entry.home.members.size)
            assertEquals(0, entry.home.present.size)
            assertEquals(0, entry.away.present.size)
            assertNull(entry.home.captain)
            assertNull(entry.home.goalkeeper)
            assertNull(entry.home.confirmation)
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
    fun reopeningARecordedLineupHasExactlyThePlayersWhoWerePresent() =
        runTest {
            val came = TestLeague.homeSquad.first()
            val first = assertNotNull(build()(match()))
            val saved =
                assertNotNull(
                    first.home
                        .everyoneCame(captain = came.id, goalkeeper = came.id)
                        .withPresence(TestLeague.homeSquad.last().id, false)
                        .toLineup(),
                )

            val entry = assertNotNull(build()(match().copy(homeLineup = saved)))

            assertEquals(TestLeague.homeSquad.size - 1, entry.home.present.size)
            assertFalse(
                entry.home.members
                    .single { it.player.id == TestLeague.homeSquad.last().id }
                    .present,
            )
            // The other team was never recorded, so nobody there is marked.
            assertEquals(0, entry.away.present.size)
        }

    @Test
    fun theCaptainTheGoalkeeperAndTheConfirmationComeBackWithTheLineup() =
        runTest {
            val captain = TestLeague.homeSquad[0]
            val goalkeeper = TestLeague.homeSquad[1]
            val confirmation =
                LineupConfirmation(Instant.parse("2026-10-17T17:05:00Z"), PersonName.of(captain.name.asWrittenOnReport))
            val saved =
                assertNotNull(
                    build()(match())!!
                        .home
                        .everyoneCame(captain = captain.id, goalkeeper = goalkeeper.id)
                        .copy(confirmation = confirmation)
                        .toLineup(),
                )

            val entry = assertNotNull(build()(match().copy(homeLineup = saved)))

            assertEquals(captain.id, entry.home.captain)
            assertEquals(goalkeeper.id, entry.home.goalkeeper)
            assertEquals(confirmation, entry.home.confirmation)
        }

    @Test
    fun aLineupWrittenBeforeThisVersionHasNoCaptainAndIsNotGivenOne() =
        runTest {
            // DECISIONS 2026-10-17: older reports have no captain or goalkeeper
            // and must not be given one. Read back, the entry says so, and the
            // referee is asked for both the next time the block is saved.
            val who = TestLeague.homeSquad.first()
            val old =
                Lineup(
                    side = TeamSide.HOME,
                    teamId = Fixtures.homeTeamId,
                    appearances =
                        listOf(
                            Appearance(
                                AppearanceId("old-1"),
                                who.id,
                                JerseyNumber(9),
                                ReportedIdentification("900615", IdentificationSource.DATE_OF_BIRTH),
                            ),
                        ),
                    kitId = Fixtures.homePrimaryKit.id,
                    kitLabel = Fixtures.homePrimaryKit.label,
                )

            val entry = assertNotNull(build()(match().copy(homeLineup = old)))

            // Present: it only ever held the players who were there.
            assertEquals(listOf(who.id), entry.home.present.map { it.player.id })
            assertNull(entry.home.captain)
            assertNull(entry.home.goalkeeper)
            assertEquals(
                listOf(LineupProblem.NoCaptain(TeamSide.HOME), LineupProblem.NoGoalkeeper(TeamSide.HOME)),
                entry.home.problems(),
            )
            // And what the old row wrote stands: a date of birth, with no card.
            val row = entry.home.members.single { it.player.id == who.id }
            assertEquals("900615", row.identification?.value)
            assertFalse(row.registrationCardPresent)
        }

    @Test
    fun anAppearanceKeepsItsIdAcrossReopening() =
        runTest {
            // Goals and cards are attributed to an appearance id. Minting a
            // new one on reload would orphan every event of the first half.
            val first = assertNotNull(build()(match()))
            val saved = assertNotNull(first.home.everyoneCame().toLineup())
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
                        .everyoneCame()
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
    fun aPitchAddedPlayerJoinsTheSquadOfTheirOwnTeamButIsNotMarkedPresent() =
        runTest {
            val added = FakeAddedPlayerRepository()
            val player = assertNotNull(AddPlayerAtThePitch(added) { "added-1" }(match(), request()))

            val entry = assertNotNull(build(added)(match()))

            assertTrue(entry.home.members.any { it.player.id == player.id })
            assertFalse(entry.away.members.any { it.player.id == player.id })
            // Like everyone else: present when they are marked, and not
            // before. Adding them *to the lineup* is what marks them.
            assertFalse(
                entry.home.members
                    .single { it.player.id == player.id }
                    .present,
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
    cardNumber: String = "59123",
    noCard: Boolean = false,
    dateOfBirth: String = "21.1.1999",
) = NewPlayerRequest(
    teamId = Fixtures.homeTeamId,
    firstName = firstName,
    surname = surname,
    cardNumber = cardNumber,
    noCard = noCard,
    dateOfBirth = dateOfBirth,
)

/**
 * RULE: **a player added at the pitch cannot acquire an RP number.**
 *
 * RP numbers are issued by PSMF. There is no parameter for one on
 * [NewPlayerRequest], none on [AddPlayerAtThePitch], and none on
 * `Player.addedAtThePitch` -- so there is no path from a keyboard to an
 * `RpNumber` anywhere in the app, and this test is what keeps it that way.
 *
 * What the referee copies from the player's card is not that: it is written
 * on the appearance, as what was written that day, and the player record
 * never sees it.
 */
class AddPlayerAtThePitchTest {
    private var minted = 0

    private fun addPlayer(repository: FakeAddedPlayerRepository) =
        AddPlayerAtThePitch(repository) { "added-${++minted}" }

    @Test
    fun addsAPlayerWithANameAndNothingElseOnTheirRecordWhenTheyHaveACard() =
        runTest {
            val repository = FakeAddedPlayerRepository()

            val player = assertNotNull(addPlayer(repository)(match(), request()))

            assertEquals("Hlok Petr", player.name.asWrittenOnReport)
            assertEquals(PlayerOrigin.ADDED_AT_PITCH, player.origin)
            assertNull(player.rpNumber)
            assertNull(player.birthNumber)
            // The number from the card is the appearance's, not this record's.
            assertNull(player.dateOfBirth)
        }

    @Test
    fun withNoCardTheDateOfBirthIsKeptAsTheOnlyIdentificationTheyHave() =
        runTest {
            val repository = FakeAddedPlayerRepository()

            val player = assertNotNull(addPlayer(repository)(match(), request(noCard = true)))

            // The worked example's row: 990121 among five-digit RP numbers.
            assertEquals("990121", player.identificationFor(registrationCardPresent = false)?.value)
            assertNull(player.rpNumber)
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
            assertNull(addPlayer(repository)(match(), request(cardNumber = "12")))
            assertNull(addPlayer(repository)(match(), request(noCard = true, dateOfBirth = "nonsense")))
            assertEquals(emptyList(), repository.forMatch(MatchId("m1")))
        }

    @Test
    fun theFormReportsProblemsInTheOrderItShowsTheFields() {
        assertEquals(
            listOf(NewPlayerProblem.SURNAME, NewPlayerProblem.FIRST_NAME, NewPlayerProblem.CARD_NUMBER),
            NewPlayerRequest(teamId = Fixtures.homeTeamId).problems(),
        )
        // With no card the date of birth is what is asked for instead -- and
        // the number, which is no longer wanted, is not nagged about.
        assertEquals(
            listOf(NewPlayerProblem.SURNAME, NewPlayerProblem.FIRST_NAME, NewPlayerProblem.DATE_OF_BIRTH),
            NewPlayerRequest(teamId = Fixtures.homeTeamId, noCard = true).problems(),
        )
        assertTrue(request().problems().isEmpty())
        assertTrue(request(noCard = true).problems().isEmpty())
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
            BuildLineupEntry(
                TestLeague.repository(),
                FakeAddedPlayerRepository(),
                FakeRememberedDateOfBirthRepository(),
                FakeRememberedCardNumberRepository(),
            ) {
                "id-${++minted}"
            }(match),
        ).everyoneCame()

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
    fun aBlockWithNoCaptainOrNoGoalkeeperIsNotWrittenEither() =
        runTest {
            val matches = FakeMatchRepository(listOf(match()))
            val entry = entryFor(match())

            val noCaptain = SaveLineup(matches)(match(), entry.home.withCaptain(null))
            val noGoalkeeper = SaveLineup(matches)(match(), entry.home.copy(goalkeeper = null))

            assertNull(noCaptain.homeLineup)
            assertNull(noGoalkeeper.homeLineup)
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
 * A player is present when the saved lineup has them, and somebody who has
 * just been typed in is in no saved lineup. Every earlier test added a player
 * to a match with no lineup recorded yet -- the one case where nothing is
 * saved to disagree with -- which is why a device found this and they did not.
 *
 * And **adding works on what the screen holds**, because a block is only
 * written once it is whole: a referee who has marked eight players and not
 * named a captain has marks that are nowhere but on the screen.
 */
class AddPlayerToLineupTest {
    private var minted = 0

    private fun useCase(
        matches: FakeMatchRepository,
        added: FakeAddedPlayerRepository,
    ): AddPlayerToLineup {
        val newId = NewId { "id-${++minted}" }
        return AddPlayerToLineup(AddPlayerAtThePitch(added, newId), SaveLineup(matches), newId)
    }

    private suspend fun entryFor(added: FakeAddedPlayerRepository = FakeAddedPlayerRepository()) =
        assertNotNull(
            BuildLineupEntry(
                TestLeague.repository(),
                added,
                FakeRememberedDateOfBirthRepository(),
                FakeRememberedCardNumberRepository(),
            ) {
                "seed-${++minted}"
            }(match()),
        )

    @Test
    fun aPlayerAddedAfterTheLineupWasSavedIsPresent() =
        runTest {
            val matches = FakeMatchRepository(listOf(match()))
            val added = FakeAddedPlayerRepository()
            val team = entryFor(added).home.everyoneCame()
            val started = SaveLineup(matches)(match(), team)

            val result = assertNotNull(useCase(matches, added)(started, team, request()))

            val player = added.forMatch(MatchId("m1")).single()
            assertTrue(
                result.team.members
                    .single { it.player.id == player.id }
                    .present,
            )
            // Written through: the lineup a restart reads has them in it.
            val reopened =
                assertNotNull(
                    BuildLineupEntry(
                        TestLeague.repository(),
                        added,
                        FakeRememberedDateOfBirthRepository(),
                        FakeRememberedCardNumberRepository(),
                    ) { "x" }(result.match),
                )
            val member = reopened.home.members.single { it.player.id == player.id }
            assertTrue(member.present, "A player added at the pitch came back unmarked")
            assertTrue(member.addedAtThePitch)
        }

    @Test
    fun marksThatAreNotSavedYetSurviveAddingSomebody() =
        runTest {
            // THE REASON IT WORKS ON THE SCREEN'S ENTRY. Two players marked, no
            // captain yet, so nothing is on disk -- and adding a third must
            // not throw the two away by rebuilding from a disk that has none.
            val matches = FakeMatchRepository(listOf(match()))
            val added = FakeAddedPlayerRepository()
            val first = TestLeague.homeSquad[0]
            val second = TestLeague.homeSquad[1]
            val marked = entryFor(added).home.withPresence(first.id, true).withPresence(second.id, true)

            val result = assertNotNull(useCase(matches, added)(match(), marked, request()))

            assertEquals(3, result.team.present.size)
            assertTrue(
                result.team.members
                    .single { it.player.id == first.id }
                    .present,
            )
            assertTrue(
                result.team.members
                    .single { it.player.id == second.id }
                    .present,
            )
            // Nothing was whole, so nothing was written.
            assertNull(result.match.homeLineup)
            assertEquals(0, matches.saves)
        }

    @Test
    fun theNumberFromTheCardGoesOnTheAppearanceAndNeverOnThePlayer() =
        runTest {
            val matches = FakeMatchRepository(listOf(match()))
            val added = FakeAddedPlayerRepository()
            val team = entryFor(added).home.everyoneCame()
            val started = SaveLineup(matches)(match(), team)

            val result = assertNotNull(useCase(matches, added)(started, team, request(cardNumber = "59 123")))

            val player = added.forMatch(MatchId("m1")).single()
            assertNull(player.rpNumber)
            assertNull(player.dateOfBirth)
            val written =
                assertNotNull(result.match.homeLineup)
                    .appearances
                    .single { it.playerId == player.id }
                    .reportedIdentification
            assertEquals(ReportedIdentification("59123", IdentificationSource.RP_TYPED), written)
        }

    @Test
    fun withNoCardTheDateOfBirthIsWhatTheAppearanceWrites() =
        runTest {
            val matches = FakeMatchRepository(listOf(match()))
            val added = FakeAddedPlayerRepository()
            val team = entryFor(added).home.everyoneCame()
            val started = SaveLineup(matches)(match(), team)

            val result = assertNotNull(useCase(matches, added)(started, team, request(noCard = true)))

            val player = added.forMatch(MatchId("m1")).single()
            val written =
                assertNotNull(result.match.homeLineup)
                    .appearances
                    .single { it.playerId == player.id }
                    .reportedIdentification
            assertEquals(ReportedIdentification("990121", IdentificationSource.DATE_OF_BIRTH), written)
        }

    @Test
    fun anUnusableRequestChangesNothing() =
        runTest {
            val matches = FakeMatchRepository(listOf(match()))
            val added = FakeAddedPlayerRepository()
            val team = entryFor(added).home.everyoneCame()

            assertNull(useCase(matches, added)(match(), team, request(surname = "")))
            assertEquals(emptyList(), added.forMatch(MatchId("m1")))
        }

    @Test
    fun aRequestForTheOtherTeamIsRefused() =
        runTest {
            val matches = FakeMatchRepository(listOf(match()))
            val added = FakeAddedPlayerRepository()
            val away = entryFor(added).away

            assertNull(useCase(matches, added)(match(), away, request()))
            assertEquals(emptyList(), added.forMatch(MatchId("m1")))
        }
}

/**
 * RULE: **Continue commits both blocks, including the one nobody touched
 * since it was last written.**
 *
 * Write-through happens on edit. Without this the console opens with nobody
 * on a side whose block was never written -- which is exactly what a device
 * showed and what every earlier test missed, because each of them saved the
 * side it was asserting on.
 */
class SaveBothLineupsTest {
    private var minted = 0

    private suspend fun entryFor(match: Match) =
        assertNotNull(
            BuildLineupEntry(
                TestLeague.repository(),
                FakeAddedPlayerRepository(),
                FakeRememberedDateOfBirthRepository(),
                FakeRememberedCardNumberRepository(),
            ) {
                "id-${++minted}"
            }(match),
        )

    @Test
    fun bothBlocksAreWrittenEvenWhenNeitherWasEdited() =
        runTest {
            val matches = FakeMatchRepository(listOf(match()))
            val entry = entryFor(match()).everyoneCame()

            val updated = SaveLineup(matches)(match(), entry)

            assertNotNull(updated.homeLineup, "The home block was not written")
            assertNotNull(updated.awayLineup, "The away block was not written")
            assertEquals(updated, matches.load(MatchId("m1")))
        }

    @Test
    fun everyPlayerWhoCameIsInTheirBlock() =
        runTest {
            val matches = FakeMatchRepository(listOf(match()))
            val updated = SaveLineup(matches)(match(), entryFor(match()).everyoneCame())

            assertEquals(TestLeague.homeSquad.size, updated.homeLineup?.appearances?.size)
            assertEquals(TestLeague.awaySquad.size, updated.awayLineup?.appearances?.size)
        }

    @Test
    fun anEditOnOneSideDoesNotLoseTheOther() =
        runTest {
            val matches = FakeMatchRepository(listOf(match()))
            val entry = entryFor(match()).everyoneCame()
            val edited = entry.with(entry.home.withPresence(TestLeague.homeSquad[1].id, false))

            val updated = SaveLineup(matches)(match(), edited)

            assertEquals(TestLeague.homeSquad.size - 1, updated.homeLineup?.appearances?.size)
            assertEquals(TestLeague.awaySquad.size, updated.awayLineup?.appearances?.size)
        }

    @Test
    fun nothingMarkedWritesNothing() =
        runTest {
            val matches = FakeMatchRepository(listOf(match()))

            val updated = SaveLineup(matches)(match(), entryFor(match()))

            assertNull(updated.homeLineup)
            assertNull(updated.awayLineup)
            assertEquals(0, matches.saves)
        }
}
