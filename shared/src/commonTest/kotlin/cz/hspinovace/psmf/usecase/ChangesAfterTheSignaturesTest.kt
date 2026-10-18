package cz.hspinovace.psmf.usecase

import cz.hspinovace.psmf.domain.Confirmation
import cz.hspinovace.psmf.domain.ConfirmingParty
import cz.hspinovace.psmf.domain.Fixtures
import cz.hspinovace.psmf.domain.JerseyNumber
import cz.hspinovace.psmf.domain.LineupConfirmation
import cz.hspinovace.psmf.domain.Match
import cz.hspinovace.psmf.domain.MatchId
import cz.hspinovace.psmf.domain.MatchStatus
import cz.hspinovace.psmf.domain.Minute
import cz.hspinovace.psmf.domain.Official
import cz.hspinovace.psmf.domain.PersonName
import cz.hspinovace.psmf.domain.RefereeAssignment
import cz.hspinovace.psmf.domain.TeamSide
import cz.hspinovace.psmf.domain.afterAChangeFrom
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

private val KICKOFF = Instant.parse("2026-08-31T19:00:00Z")
private val SIGNED_AT = Instant.parse("2026-08-31T20:05:00Z")

/**
 * What the captains and the referee sign is **the report as it stood**
 * (prompt 13, part 4). A change to anything they attest -- the header, the
 * lineups, the goals, the cards (the "no cards" affirmation included), the
 * whistles -- takes the signatures back, because they no longer say what is
 * written. The assessment, the commentary and the team notes are the referee's
 * to finish until export (A6) and touch nothing.
 */
class ChangesAfterTheSignaturesTest {
    private val signedBy =
        ConfirmingParty.entries.map {
            Confirmation(it, SIGNED_AT, PersonName.of("Vlk Jiří"))
        }

    /** A finished report with all three signatures: CONFIRMED. */
    private fun signed(): Match =
        Match(MatchId("m1"), Fixtures.fixtureId, Fixtures.groupId)
            .copy(
                status = MatchStatus.CONFIRMED,
                officials = Fixtures.officials,
                homeLineup = Fixtures.homeLineup,
                awayLineup = Fixtures.awayLineup,
                kickoffAt = KICKOFF,
                confirmations = signedBy,
            )

    private val yellow =
        CardDraft(
            side = TeamSide.AWAY,
            appearance = Fixtures.bacaAppearance.id,
            reason = "podražení",
            minute = MinuteDraft("20"),
        )

    @Test
    fun aCardAfterTheFinalConfirmationsWithdrawsThem() =
        runTest {
            val matches = FakeMatchRepository(listOf(signed()))

            val after = assertNotNull(LogCard(matches)(signed(), yellow, KICKOFF))

            assertEquals(emptyList(), after.confirmations, "the signatures outlived the card")
            // And it is no longer a confirmed report: the recap asks again.
            assertEquals(MatchStatus.FINISHED, after.status)
            assertEquals(after, matches.load(MatchId("m1")))
        }

    @Test
    fun aGoalAfterTheFinalConfirmationsWithdrawsThem() =
        runTest {
            val matches = FakeMatchRepository(listOf(signed()))

            val after = LogGoal(matches)(signed(), TeamSide.HOME, null, Minute.Played(10))

            assertEquals(1, after.goals.size)
            assertEquals(emptyList(), after.confirmations)
            assertEquals(MatchStatus.FINISHED, after.status)
        }

    @Test
    fun takingBackAGoalWithdrawsThemToo() =
        runTest {
            val withAGoal =
                LogGoal(FakeMatchRepository(listOf(signed())))(signed(), TeamSide.HOME, null, Minute.Played(10))
                    .copy(confirmations = signedBy, status = MatchStatus.CONFIRMED)
            val matches = FakeMatchRepository(listOf(withAGoal))

            val after = UndoLastEvent(matches)(withAGoal)

            assertEquals(0, after.goals.size)
            assertEquals(emptyList(), after.confirmations)
        }

    @Test
    fun aWhistleAfterTheFinalConfirmationsWithdrawsThem() =
        runTest {
            val matches = FakeMatchRepository(listOf(signed()))

            val after = EndPeriod(matches)(signed(), KICKOFF + 30.minutes)

            assertEquals(1, after.periodBreaks.size)
            assertEquals(emptyList(), after.confirmations)
        }

    @Test
    fun theNoCardsAffirmationIsACardsChangeAndWithdrawsThemBothWays() =
        runTest {
            val unaccounted = signed().copy(status = MatchStatus.FINISHED, cards = null)
            val matches = FakeMatchRepository(listOf(unaccounted))

            val affirmed = AffirmNoCards(matches)(unaccounted)
            assertEquals(emptyList(), affirmed.confirmations)

            val signedAgain = affirmed.copy(confirmations = signedBy, status = MatchStatus.CONFIRMED)
            val withdrawn = WithdrawNoCards(matches)(signedAgain)
            assertEquals(null, withdrawn.cards)
            assertEquals(emptyList(), withdrawn.confirmations)
        }

    @Test
    fun aRefusedChangeWithdrawsNothing() =
        runTest {
            val matches = FakeMatchRepository(listOf(signed()))

            // 60´+ is not a minute a goal can have: refused, and the report is
            // exactly as it was, signatures and all.
            val after = LogGoal(matches)(signed(), TeamSide.HOME, null, Minute.AfterFinalWhistle)

            assertEquals(signed(), after)
            assertEquals(0, matches.saves)
        }

    @Test
    fun theSignaturesGivenSoFarAreWithdrawnWhenOnlySomeWereGiven() =
        runTest {
            val twoOfThree = signed().copy(status = MatchStatus.FINISHED, confirmations = signedBy.take(2))
            val matches = FakeMatchRepository(listOf(twoOfThree))

            val after = assertNotNull(LogCard(matches)(twoOfThree, yellow, KICKOFF))

            assertEquals(emptyList(), after.confirmations)
        }

    // ------------------------------------------------------------------
    // What attests, and what does not
    // ------------------------------------------------------------------

    @Test
    fun aChangeToTheHeaderOrALineupWithdrawsThem() {
        val signed = signed()

        val otherReferee =
            signed.copy(
                officials =
                    RefereeAssignment(Official(PersonName.of("Jiný Rozhodčí")), null, "Celtic THK"),
            )
        val otherLineup = signed.copy(homeLineup = Fixtures.awayLineup)

        assertEquals(emptyList(), otherReferee.afterAChangeFrom(signed).confirmations)
        assertEquals(emptyList(), otherLineup.afterAChangeFrom(signed).confirmations)
    }

    @Test
    fun theSameHeaderOrLineupWrittenAgainWithdrawsNothing() {
        val signed = signed()

        assertEquals(signedBy, signed.copy().afterAChangeFrom(signed).confirmations)
    }

    @Test
    fun theCaptainsOwnConfirmationUnderTheLineupIsASignatureNotContent() {
        val signed = signed()
        val lateConfirmation =
            signed.copy(
                homeLineup =
                    Fixtures.homeLineup.copy(
                        confirmation = LineupConfirmation(SIGNED_AT, PersonName.of("Houžev Karel")),
                    ),
            )

        assertEquals(signedBy, lateConfirmation.afterAChangeFrom(signed).confirmations)
    }

    @Test
    fun theAssessmentTheCommentaryAndTheTeamNotesStayEditableWithoutTouchingAnySignature() =
        runTest {
            val matches = FakeMatchRepository(listOf(signed()))
            val save = SaveAssessment(matches)

            val edited =
                save(
                    signed(),
                    AssessmentDraft(
                        home = TeamAssessmentDraft(bestPlayer = "9", note = "Domácí protestují proti 2. gólu."),
                        away = TeamAssessmentDraft(note = "Hosté: hráč neměl průkaz."),
                        commentary = "Nastřelená tyč ve 12. minutě.",
                    ),
                )

            assertEquals("Domácí protestují proti 2. gólu.", edited.assessment.home.note)
            assertEquals("Hosté: hráč neměl průkaz.", edited.assessment.away.note)
            assertEquals(signedBy, edited.confirmations)
            assertEquals(MatchStatus.CONFIRMED, edited.status)
        }

    @Test
    fun afterTheSignaturesAreWithdrawnTheRecapCanAskForThemAgainAndTheReportIsConfirmedAgain() =
        runTest {
            val matches = FakeMatchRepository(listOf(signed()))
            val changed = LogGoal(matches)(signed(), TeamSide.HOME, null, Minute.Played(10))
            assertEquals(MatchStatus.FINISHED, changed.status)

            var current = changed
            ConfirmingParty.entries.forEach { party ->
                current = ConfirmReport(matches)(current, party, PersonName.of("Vlk Jiří"), SIGNED_AT)
            }

            assertEquals(MatchStatus.CONFIRMED, current.status)
            assertEquals(3, current.confirmations.size)
        }

    // ------------------------------------------------------------------
    // The header and the lineups save as they are edited, and are saved through here
    // ------------------------------------------------------------------

    @Test
    fun aHeaderChangedAfterTheSignaturesWithdrawsThemAndTheSameHeaderWrittenAgainDoesNot() =
        runTest {
            val matches = FakeMatchRepository(listOf(signed()))
            val sameEntry = MatchHeaderEntry.from(signed().officials)

            val unchanged = SaveMatchHeader(matches)(signed(), sameEntry)
            assertEquals(signedBy, unchanged.confirmations)
            assertEquals(MatchStatus.CONFIRMED, unchanged.status)

            val changed = SaveMatchHeader(matches)(signed(), sameEntry.copy(refereeName = "Jiný Rozhodčí"))
            assertEquals(
                "Jiný Rozhodčí",
                changed.officials
                    ?.main
                    ?.name
                    ?.value,
            )
            assertEquals(emptyList(), changed.confirmations)
            assertEquals(MatchStatus.FINISHED, changed.status)
        }

    @Test
    fun aLineupChangedAfterTheSignaturesWithdrawsThemAndTheSameLineupWrittenAgainDoesNot() =
        runTest {
            val build =
                BuildLineupEntry(
                    TestLeague.repository(),
                    FakeAddedPlayerRepository(),
                    FakeRememberedDateOfBirthRepository(),
                    FakeRememberedCardNumberRepository(),
                ) { "id" }
            val entry = assertNotNull(build(Match(MatchId("m1"), Fixtures.fixtureId, Fixtures.groupId))).everyoneCame()
            val matches = FakeMatchRepository()
            val written = SaveLineup(matches)(Match(MatchId("m1"), Fixtures.fixtureId, Fixtures.groupId), entry)
            val signedMatch = written.copy(status = MatchStatus.CONFIRMED, confirmations = signedBy)

            val unchanged = SaveLineup(matches)(signedMatch, entry)
            assertEquals(signedBy, unchanged.confirmations)

            // A shirt number changed on one player: the lineup is not what was signed.
            val first =
                entry.home.members
                    .first()
                    .player.id
            val renumbered = entry.home.withMember(first) { it.copy(jerseyNumber = JerseyNumber(99)) }
            val changed = SaveLineup(matches)(signedMatch, renumbered)
            assertEquals(emptyList(), changed.confirmations)
            assertEquals(MatchStatus.FINISHED, changed.status)
        }
}
