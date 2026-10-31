package cz.hspinovace.psmf.usecase

import cz.hspinovace.psmf.data.match.LineupDraft
import cz.hspinovace.psmf.domain.Assessment
import cz.hspinovace.psmf.domain.CardReason
import cz.hspinovace.psmf.domain.CardSubject
import cz.hspinovace.psmf.domain.CardsSection
import cz.hspinovace.psmf.domain.ConfirmingParty
import cz.hspinovace.psmf.domain.Dismissal
import cz.hspinovace.psmf.domain.Fixtures
import cz.hspinovace.psmf.domain.GoalEvent
import cz.hspinovace.psmf.domain.Match
import cz.hspinovace.psmf.domain.MatchId
import cz.hspinovace.psmf.domain.MatchStatus
import cz.hspinovace.psmf.domain.Minute
import cz.hspinovace.psmf.domain.RedCard
import cz.hspinovace.psmf.domain.Score
import cz.hspinovace.psmf.domain.TeamSide
import cz.hspinovace.psmf.domain.YellowCard
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Discarding a report (prompt 17, Gate 2, the tester's point 1): what the
 * question before it is told the report holds, and that the yes removes the
 * report it was asked about and no other.
 *
 * That the report's rows, its drafts and its pitch-added players all go on
 * the real database is `DiscardReportPersistenceTest`'s.
 */
class DiscardReportTest {
    private val drafts = FakeLineupDraftRepository()

    /** What a tap on the wrong fixture leaves: a report and nothing in it. */
    private val stray = Match(MatchId("stray"), Fixtures.fixtureId, Fixtures.groupId)

    private val baca = CardSubject.Player(Fixtures.bacaAppearance.id)

    /** Header, both lineups, three goals, a yellow and a second yellow, a note, two signatures. */
    private val played =
        Fixtures.matchInSetup().copy(
            id = MatchId("played"),
            status = MatchStatus.FINISHED,
            goals =
                listOf(
                    GoalEvent(Minute.Played(5), TeamSide.AWAY, Fixtures.bacaAppearance.id, Score(0, 1)),
                    GoalEvent(Minute.Played(13), TeamSide.HOME, null, Score(1, 1)),
                    GoalEvent(Minute.Played(40), TeamSide.HOME, null, Score(2, 1)),
                ),
            cards =
                CardsSection.Issued(
                    listOf(
                        YellowCard(Minute.Played(20), TeamSide.AWAY, baca, CardReason("podražení")),
                        // A second yellow is one action and two cards on the form.
                        YellowCard(Minute.Played(44), TeamSide.AWAY, baca, CardReason("2. ŽK"), 7),
                        RedCard(
                            Minute.Played(44),
                            TeamSide.AWAY,
                            baca,
                            CardReason("2. ŽK"),
                            Dismissal.SECOND_YELLOW,
                            7,
                        ),
                    ),
                ),
            assessment = Assessment(commentary = "Utkání bez potíží."),
            confirmations =
                listOf(
                    Fixtures.confirmation(ConfirmingParty.HOME_CAPTAIN),
                    Fixtures.confirmation(ConfirmingParty.AWAY_CAPTAIN),
                ),
        )

    private fun draft(
        matchId: MatchId,
        side: TeamSide,
    ) = LineupDraft(
        matchId = matchId,
        side = side,
        kitId = if (side == TeamSide.HOME) Fixtures.homePrimaryKit.id else Fixtures.awayPrimaryKit.id,
        captain = null,
        goalkeeper = null,
        confirmation = null,
        members = emptyList(),
    )

    /** What the question would be told about [match], with [others] beside it on the device. */
    private suspend fun contentsOf(
        match: Match,
        vararg others: Match,
    ): ReportContents {
        val matches = FakeMatchRepository(listOf(match, *others))
        return assertNotNull(DiscardReport(matches, drafts).contentsOf(match.id))
    }

    @Test
    fun aReportStartedByATapHoldsNothing() =
        runTest {
            val contents = contentsOf(stray)

            assertTrue(contents.isEmpty, "$contents")
            assertFalse(contents.signed)
        }

    @Test
    fun aReportSaysWhatItHolds() =
        runTest {
            val contents = contentsOf(played)

            assertEquals(
                ReportContents(
                    matchId = played.id,
                    status = MatchStatus.FINISHED,
                    header = true,
                    lineups = 2,
                    goals = 3,
                    cards = 3,
                    assessment = true,
                    signatures = 2,
                ),
                contents,
            )
            assertFalse(contents.isEmpty)
            assertFalse(contents.signed, "two signatures of three is not signed")
        }

    @Test
    fun aLineupStillBeingBuiltIsALineupThatGoes() =
        runTest {
            // The draft goes with the report, so the question counts it --
            // once per team, saved or not.
            val homeSaved = stray.copy(homeLineup = Fixtures.homeLineup)
            drafts.save(draft(stray.id, TeamSide.HOME))
            drafts.save(draft(stray.id, TeamSide.AWAY))

            val contents = contentsOf(homeSaved)

            assertEquals(2, contents.lineups)
            assertFalse(contents.isEmpty)
        }

    @Test
    fun anotherReportsDraftIsNotThisOnes() =
        runTest {
            drafts.save(draft(played.id, TeamSide.HOME))

            assertEquals(0, contentsOf(stray, played).lineups)
        }

    @Test
    fun aSignedReportIsSaidToBeSigned() =
        runTest {
            val signed =
                played.copy(
                    status = MatchStatus.CONFIRMED,
                    confirmations = played.confirmations + Fixtures.confirmation(ConfirmingParty.REFEREE),
                )

            val contents = contentsOf(signed)

            assertTrue(contents.signed)
            assertEquals(3, contents.signatures)
        }

    @Test
    fun aReportThatIsNotThereHasNothingToAskAbout() =
        runTest {
            assertNull(DiscardReport(FakeMatchRepository(listOf(played)), drafts).contentsOf(stray.id))
        }

    @Test
    fun theYesRemovesTheReportItWasAskedAboutAndNoOther() =
        runTest {
            val matches = FakeMatchRepository(listOf(stray, played))
            val discard = DiscardReport(matches, drafts)

            discard(assertNotNull(discard.contentsOf(stray.id)))

            assertNull(matches.load(stray.id))
            assertEquals(played, matches.load(played.id))
        }
}
