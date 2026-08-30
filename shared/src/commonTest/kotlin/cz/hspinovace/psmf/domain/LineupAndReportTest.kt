package cz.hspinovace.psmf.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * RULE: **the jersey number belongs to the appearance, not the player.**
 *
 * Numbers change between matches (analysis section 3.6). The form even
 * carries a referee rating `Č` for whether a team's shirts are properly
 * numbered at all, which is only worth asking because numbering is loose.
 */
class JerseyNumberOwnershipTest {
    @Test
    fun onePlayerCanWearDifferentNumbersInDifferentMatches() {
        val player = Fixtures.player("baca-petr", "Bača", "Petr", number = 13)

        val lastWeek = Fixtures.appearance("app-1", player.id.value, number = 13)
        val thisWeek = Fixtures.appearance("app-2", player.id.value, number = 7)

        // Same player, same record, two different numbers on the day. The
        // number lives on the appearance, so nothing about the player had
        // to change to say so.
        assertEquals(player.id, PlayerId(lastWeek.playerId.value))
        assertEquals(player.id, PlayerId(thisWeek.playerId.value))
        assertNotEquals(lastWeek.jerseyNumber, thisWeek.jerseyNumber)

        // The player record keeps only a default to pre-fill from.
        assertEquals(JerseyNumber(13), player.defaultJerseyNumber)
    }

    @Test
    fun whatWasWrittenInTheRpColumnAlsoBelongsToTheAppearance() {
        // A player may turn up without their card, so what goes in the
        // `Číslo RP` column is decided at the pitch, per match -- and it is
        // stored rather than derived, so an old report does not change if
        // the player is registered later.
        val withCard =
            Fixtures.appearance(
                "app-1",
                "p-1",
                13,
                reportedIdentification = ReportedIdentification("59001", IdentificationSource.RP),
            )
        val withoutCard =
            Fixtures.appearance(
                "app-2",
                "p-1",
                13,
                reportedIdentification =
                    ReportedIdentification("990121", IdentificationSource.DATE_OF_BIRTH),
            )

        assertNotEquals(
            withCard.reportedIdentification.source,
            withoutCard.reportedIdentification.source,
        )
    }

    @Test
    fun twoPlayersCannotShareANumberInOneLineup() {
        // Goals and cards are attributed by number, so a duplicate makes
        // the report ambiguous.
        assertFailsWith<IllegalArgumentException> {
            Fixtures.lineup(
                TeamSide.HOME,
                Fixtures.appearance("a", "p1", 9),
                Fixtures.appearance("b", "p2", 9),
            )
        }
    }

    @Test
    fun aPlayerCannotAppearTwiceInOneLineup() {
        assertFailsWith<IllegalArgumentException> {
            Fixtures.lineup(
                TeamSide.HOME,
                Fixtures.appearance("a", "p1", 9),
                Fixtures.appearance("b", "p1", 10),
            )
        }
    }

    @Test
    fun aPlayerWithoutANumberIsAllowed() {
        // Not every carded person has a number, and a lineup may be part-filled.
        val lineup =
            Fixtures.lineup(
                TeamSide.HOME,
                Fixtures.appearance("a", "p1", null),
                Fixtures.appearance("b", "p2", null),
            )
        assertEquals(2, lineup.appearances.size)
    }

    @Test
    fun jerseyNumbersOutsideTheFormsTwoDigitColumnAreRejected() {
        assertFailsWith<IllegalArgumentException> { JerseyNumber(100) }
        assertFailsWith<IllegalArgumentException> { JerseyNumber(-1) }
    }
}

/**
 * RULE: **the referee is the only recorder.**
 *
 * Captains confirm what the referee wrote. A design in which two parties
 * record independently and must be reconciled invents a problem the paper
 * process does not have (analysis section 6).
 */
class SingleRecorderTest {
    @Test
    fun confirmingChangesNothingExceptTheConfirmations() {
        val recorded =
            Fixtures.matchInSetup().copy(
                goals = listOf(GoalEvent(Minute.Played(5), TeamSide.HOME, null, Score(1, 0))),
                cards = CardsSection.NoneIssued,
                assessment = Assessment(commentary = "Bez pozoruhodných událostí."),
                result = MatchResult(halfTime = Score(1, 0), fullTime = Score(1, 0)),
            )

        val afterCaptain = recorded.confirmedBy(Fixtures.confirmation(ConfirmingParty.HOME_CAPTAIN))

        // A captain contributes acknowledgement, never content. If someone
        // ever gives Confirmation a payload, this comparison starts failing.
        assertEquals(recorded.goals, afterCaptain.goals)
        assertEquals(recorded.cards, afterCaptain.cards)
        assertEquals(recorded.assessment, afterCaptain.assessment)
        assertEquals(recorded.result, afterCaptain.result)
        assertEquals(recorded, afterCaptain.copy(confirmations = recorded.confirmations))
    }

    @Test
    fun eachPartyConfirmsAtMostOnceAndReconfirmingReplaces() {
        val once = Fixtures.matchInSetup().confirmedBy(Fixtures.confirmation(ConfirmingParty.HOME_CAPTAIN))
        val twice = once.confirmedBy(Fixtures.confirmation(ConfirmingParty.HOME_CAPTAIN, asDeputy = true))

        assertEquals(1, twice.confirmations.size)
        assertTrue(twice.confirmations.single().asDeputy)
    }

    @Test
    fun captaincyCanBeDelegatedToADeputy() {
        // The worked example shows a deputy signing as `Lepiš (zást.)`.
        val deputy = Fixtures.confirmation(ConfirmingParty.AWAY_CAPTAIN, asDeputy = true)
        assertTrue(deputy.asDeputy)
        assertEquals("Lepis", deputy.confirmedBy.value)
    }

    @Test
    fun aReportNeedsAllThreeConfirmations() {
        val match =
            Fixtures
                .matchInSetup()
                .confirmedBy(Fixtures.confirmation(ConfirmingParty.HOME_CAPTAIN))

        val missing = match.reportProblems().filterIsInstance<ReportProblem.MissingConfirmation>()
        assertEquals(
            setOf(ConfirmingParty.AWAY_CAPTAIN, ConfirmingParty.REFEREE),
            missing.map { it.party }.toSet(),
        )
    }
}

/**
 * RULE: the assessment block — `NH`, `Čd`, `Č`, `B` — plus the **mandatory**
 * commentary (analysis section 2.5). `Č` and `B` feed directly into fines.
 */
class AssessmentTest {
    @Test
    fun theBlockCarriesTheFoursRatingsTheFormAsksFor() {
        val assessed =
            TeamAssessment(
                bestPlayer = JerseyNumber(9),
                waitingTimeMinutes = 5,
                shirtsProperlyNumbered = false,
                uniformKitColour = false,
            )

        // NH is a jersey number, not a name — that is what the form records.
        assertEquals(JerseyNumber(9), assessed.bestPlayer)
        assertEquals(5, assessed.waitingTimeMinutes)
        assertEquals(false, assessed.shirtsProperlyNumbered)
        assertEquals(false, assessed.uniformKitColour)
        assertTrue(assessed.isComplete)
    }

    @Test
    fun anUnratedTeamIsIncompleteAndNotSilentlyTreatedAsCompliant() {
        // Defaulting Č and B to "yes" would quietly waive fines, so they
        // start null and must be answered.
        val untouched = TeamAssessment()
        assertTrue(!untouched.isComplete)
        assertEquals(0, untouched.waitingTimeMinutes)
    }

    @Test
    fun waitingTimeCannotBeNegative() {
        assertFailsWith<IllegalArgumentException> { TeamAssessment(waitingTimeMinutes = -1) }
    }

    @Test
    fun theCommentaryIsMandatoryForExportButNotWhileRecording() {
        // Settled as A6 in DEMO_SCOPE: editable until export, so a blank
        // commentary is legal mid-match and blocks only the send.
        val drafting = Fixtures.matchInSetup()
        assertTrue(ReportProblem.MissingCommentary in drafting.reportProblems())

        val written = drafting.copy(assessment = Assessment(commentary = "Nastřelená tyč ve 12. minutě."))
        assertTrue(ReportProblem.MissingCommentary !in written.reportProblems())
    }
}

/** The recap screen has to say what is missing, so problems are a list. */
class ReportReadinessTest {
    private fun completeMatch(): Match =
        Fixtures
            .matchInSetup()
            .copy(
                goals = listOf(GoalEvent(Minute.Played(5), TeamSide.HOME, null, Score(1, 0))),
                cards = CardsSection.NoneIssued,
                assessment =
                    Assessment(
                        home = TeamAssessment(shirtsProperlyNumbered = true, uniformKitColour = true),
                        away = TeamAssessment(shirtsProperlyNumbered = true, uniformKitColour = true),
                        commentary = "Utkání bez pozoruhodných událostí.",
                    ),
                result = MatchResult(halfTime = Score(1, 0), fullTime = Score(1, 0)),
            ).confirmedBy(Fixtures.confirmation(ConfirmingParty.HOME_CAPTAIN))
            .confirmedBy(Fixtures.confirmation(ConfirmingParty.AWAY_CAPTAIN))
            .confirmedBy(Fixtures.confirmation(ConfirmingParty.REFEREE))

    @Test
    fun aFullyFilledReportHasNothingOutstanding() {
        assertEquals(emptyList(), completeMatch().reportProblems())
        assertTrue(completeMatch().isReadyForExport())
    }

    @Test
    fun aRecordedScoreThatContradictsTheGoalsIsCaught() {
        // Today nobody notices until the transcription crew does, a week later.
        val wrong = completeMatch().copy(result = MatchResult(Score(1, 0), Score(3, 0)))

        val problem =
            wrong
                .reportProblems()
                .filterIsInstance<ReportProblem.ScoreDisagreesWithGoals>()
                .single()

        assertEquals(Score(3, 0), problem.recorded)
        assertEquals(Score(1, 0), problem.fromGoals)
    }

    @Test
    fun theFullTimeScoreCannotBeLowerThanTheHalfTimeScore() {
        assertFailsWith<IllegalArgumentException> {
            MatchResult(halfTime = Score(2, 0), fullTime = Score(1, 0))
        }
    }

    @Test
    fun theWinnerIsDerivedSoItCannotContradictTheScore() {
        assertEquals(TeamSide.HOME, MatchResult(Score(1, 0), Score(3, 2)).winner)
        assertEquals(TeamSide.AWAY, MatchResult(Score(0, 1), Score(2, 4)).winner)
        assertTrue(MatchResult(Score(1, 1), Score(2, 2)).isDraw)
    }

    @Test
    fun theTimelineMergesGoalsAndCardsInTheFormsOwnOrder() {
        val match =
            completeMatch().copy(
                cards =
                    CardsSection.Issued(
                        listOf(
                            YellowCard(
                                Minute.HalfTime,
                                TeamSide.AWAY,
                                CardSubject.NamedPerson(PersonName.of("Lepis A.")),
                                CardReason("nesp. chování"),
                            ),
                            YellowCard(
                                Minute.Played(20),
                                TeamSide.AWAY,
                                CardSubject.Player(Fixtures.bacaAppearance.id),
                                CardReason("podražení"),
                            ),
                        ),
                    ),
            )

        assertEquals(
            listOf(Minute.Played(5), Minute.Played(20), Minute.HalfTime),
            match.timeline().map { it.minute },
        )
    }
}
