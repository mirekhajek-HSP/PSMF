package cz.hspinovace.psmf.data.match

import cz.hspinovace.psmf.data.db.DatabaseDriverFactory
import cz.hspinovace.psmf.db.PsmfDatabase
import cz.hspinovace.psmf.domain.Assessment
import cz.hspinovace.psmf.domain.CardReason
import cz.hspinovace.psmf.domain.CardSubject
import cz.hspinovace.psmf.domain.CardsSection
import cz.hspinovace.psmf.domain.ConfirmingParty
import cz.hspinovace.psmf.domain.Dismissal
import cz.hspinovace.psmf.domain.Fixtures
import cz.hspinovace.psmf.domain.GoalEvent
import cz.hspinovace.psmf.domain.Match
import cz.hspinovace.psmf.domain.MatchStatus
import cz.hspinovace.psmf.domain.Minute
import cz.hspinovace.psmf.domain.PersonName
import cz.hspinovace.psmf.domain.RedCard
import cz.hspinovace.psmf.domain.Score
import cz.hspinovace.psmf.domain.TeamAssessment
import cz.hspinovace.psmf.domain.TeamSide
import cz.hspinovace.psmf.domain.YellowCard
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
 * **Losing a match record is the failure that ends a pilot.**
 *
 * A referee cannot reconstruct twenty events from memory, and once they
 * have stopped writing on paper there is no fallback. So the interesting
 * test is not "does save work" but "does the report still exist after the
 * process that wrote it is gone".
 *
 * These tests open a real database file, close the driver — which is what
 * the app losing its process amounts to — and open a *fresh* driver
 * against the same bytes.
 */
class MatchPersistenceTest {
    private val databaseFile: File = File.createTempFile("psmf-crash-", ".db").also { it.delete() }

    @AfterTest
    fun cleanUp() {
        databaseFile.delete()
    }

    /** A separate app run against the same database file. */
    private suspend fun <T> session(block: suspend (MatchRepository) -> T): T {
        val driver = DatabaseDriverFactory("jdbc:sqlite:${databaseFile.absolutePath}").create()
        try {
            return block(SqlDelightMatchRepository(PsmfDatabase(driver)))
        } finally {
            driver.close()
        }
    }

    /** A match mid-second-half, with everything awkward in it. */
    private fun matchInProgress(): Match =
        Fixtures
            .matchInSetup()
            .copy(
                status = MatchStatus.IN_PROGRESS,
                kickoffAt = Instant.parse("2026-08-31T19:00:00Z"),
                goals =
                    listOf(
                        GoalEvent(Minute.Played(5), TeamSide.AWAY, Fixtures.bacaAppearance.id, Score(0, 1)),
                        // The goal with no scorer, from the worked example.
                        GoalEvent(Minute.Played(13), TeamSide.HOME, null, Score(1, 1)),
                    ),
                cards =
                    CardsSection.Issued(
                        listOf(
                            YellowCard(
                                Minute.Played(20),
                                TeamSide.AWAY,
                                CardSubject.Player(Fixtures.bacaAppearance.id),
                                CardReason("podražení"),
                            ),
                            // Half-time, and shown to someone with no jersey number.
                            YellowCard(
                                Minute.HalfTime,
                                TeamSide.AWAY,
                                CardSubject.NamedPerson(PersonName.of("Lepis A.")),
                                CardReason("nesp. chování"),
                            ),
                            // A second yellow, which must not come back as a straight red.
                            RedCard(
                                Minute.AfterFinalWhistle,
                                TeamSide.AWAY,
                                CardSubject.Player(Fixtures.bacaAppearance.id),
                                CardReason("2. ŽK"),
                                Dismissal.SECOND_YELLOW,
                            ),
                        ),
                    ),
                assessment =
                    Assessment(
                        home =
                            TeamAssessment(
                                bestPlayer = null,
                                waitingTimeMinutes = 5,
                                shirtsProperlyNumbered = false,
                            ),
                        away = TeamAssessment(uniformKitColour = true),
                        commentary = "Nastřelená tyč ve 12. minutě.",
                    ),
            ).confirmedBy(Fixtures.confirmation(ConfirmingParty.HOME_CAPTAIN, asDeputy = true))

    @Test
    fun anInProgressMatchSurvivesTheProcessBeingKilled() =
        runTest {
            val original = matchInProgress()

            session { it.save(original) }
            // <- the driver is closed here. As far as SQLite is concerned the
            //    app has died: no in-memory state carries over to the next
            //    session, which builds a brand new driver and database object.
            val restored = session { it.load(original.id) }

            assertNotNull(restored, "The match was lost when the process ended")
            assertEquals(original, restored)
        }

    @Test
    fun theAwkwardValuesSurviveIntactAndNotJustTheEasyOnes() =
        runTest {
            val original = matchInProgress()
            session { it.save(original) }
            val restored = session { it.load(original.id) }!!

            // A minute that is not an integer.
            assertEquals(Minute.HalfTime, restored.cardEvents[1].minute)
            assertEquals(Minute.AfterFinalWhistle, restored.cardEvents[2].minute)

            // A goal with no scorer stays a goal with no scorer.
            assertNull(restored.goals.single { it.minute == Minute.Played(13) }.scorer)

            // A second yellow does not come back as a straight red.
            val red = restored.cardEvents.filterIsInstance<RedCard>().single()
            assertEquals(Dismissal.SECOND_YELLOW, red.dismissal)

            // A card shown to someone with no jersey number keeps their name.
            val named = restored.cardEvents[1].subject
            assertTrue(named is CardSubject.NamedPerson)
            assertEquals("Lepis A.", named.name.value)

            // Every card still carries its reason.
            assertTrue(restored.cardEvents.all { it.reason.text.isNotBlank() })

            // The deputy flag on a confirmation survives.
            assertTrue(restored.confirmations.single().asDeputy)

            // Kickoff is a stored instant, so the derived clock resumes correctly.
            assertEquals(Instant.parse("2026-08-31T19:00:00Z"), restored.kickoffAt)
        }

    @Test
    fun anUnaccountedCardsBlockDoesNotComeBackAsNoCardsIssued() =
        runTest {
            // The distinction the whole CardsSection type exists to preserve.
            // Storing it as an empty list would quietly turn "not filled in"
            // into "referee affirmed there were none".
            val notAccounted = Fixtures.matchInSetup().copy(status = MatchStatus.IN_PROGRESS, cards = null)
            session { it.save(notAccounted) }
            assertNull(session { it.load(notAccounted.id) }!!.cards)

            val affirmedNone = notAccounted.copy(cards = CardsSection.NoneIssued)
            session { it.save(affirmedNone) }
            assertEquals(CardsSection.NoneIssued, session { it.load(affirmedNone.id) }!!.cards)
        }

    @Test
    fun theAppCanFindWhatItWasInTheMiddleOfAfterRestarting() =
        runTest {
            val running = matchInProgress()
            val done =
                Fixtures.matchInSetup().copy(
                    id =
                        cz.hspinovace.psmf.domain
                            .MatchId("finished-1"),
                    status = MatchStatus.CONFIRMED,
                )

            session {
                it.save(running)
                it.save(done)
            }

            val resumable = session { it.findByStatus(MatchStatus.IN_PROGRESS) }
            assertEquals(listOf(running.id), resumable.map { it.id })
        }

    @Test
    fun savingTwiceReplacesRatherThanAccumulates() =
        runTest {
            val original = matchInProgress()
            session { it.save(original) }

            val withOneMoreGoal =
                original.copy(
                    goals = original.goals + GoalEvent(Minute.Played(58), TeamSide.HOME, null, Score(2, 1)),
                )
            session { it.save(withOneMoreGoal) }

            val restored = session { it.load(original.id) }!!
            assertEquals(3, restored.goals.size)
            assertEquals(withOneMoreGoal, restored)
        }

    @Test
    fun aDeletedMatchIsGoneFromEveryTable() =
        runTest {
            val original = matchInProgress()
            session { it.save(original) }
            session { it.delete(original.id) }

            assertNull(session { it.load(original.id) })
            assertEquals(emptyList(), session { it.findByStatus(MatchStatus.IN_PROGRESS) })
        }
}
