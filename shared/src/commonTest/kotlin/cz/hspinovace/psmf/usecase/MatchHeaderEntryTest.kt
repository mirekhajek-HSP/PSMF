package cz.hspinovace.psmf.usecase

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * RULE: **the delegating team is not one of the teams playing, and leaving
 * it out costs somebody money.**
 *
 * The fine for a report that is incomplete, incorrect or late is charged
 * to the team that delegated the referee, not to the referee (analysis
 * section 2.5, rule 10). Catching that before export is the single most
 * useful thing the app does that paper does not.
 */
class MatchHeaderEntryTest {
    private val complete =
        MatchHeaderEntry(
            refereeName = "Jiri Vlk",
            assistantName = "Roman Liska",
            assistantLicensedHire = true,
            delegatingTeam = "Kominici",
        )

    @Test
    fun aCompleteEntryBecomesAnAssignment() {
        // Straight from the worked example: "Jiří Vlk, Roman Liška ®".
        val assignment = assertNotNull(complete.toAssignment())

        assertEquals("Jiri Vlk", assignment.main.name.value)
        assertFalse(assignment.main.licensedHire)
        assertEquals("Roman Liska", assignment.assistant?.name?.value)
        assertTrue(assignment.assistant?.licensedHire == true)
        assertEquals("Kominici", assignment.delegatingTeam)
    }

    @Test
    fun anEmptyEntryIsNotAnAssignmentAndSaysWhy() {
        val problems = MatchHeaderEntry().problems()

        assertEquals(
            listOf(HeaderProblem.RefereeNameMissing, HeaderProblem.DelegatingTeamMissing),
            problems,
        )
        assertNull(MatchHeaderEntry().toAssignment())
    }

    @Test
    fun aBlankDelegatingTeamIsTheOneProblemThatCostsMoney() {
        val entry = complete.copy(delegatingTeam = "   ")

        assertTrue(HeaderProblem.DelegatingTeamMissing in entry.problems())
        assertNull(entry.toAssignment())
    }

    @Test
    fun aDelegatingTeamIsTrimmedRatherThanTakenLiterally() {
        assertEquals("Kominici", complete.copy(delegatingTeam = "  Kominici ").toAssignment()?.delegatingTeam)
    }

    @Test
    fun anAssistantIsOptionalBecausePlentyOfMatchesHaveOneOfficial() {
        val alone = complete.copy(assistantName = "", assistantLicensedHire = false)

        assertTrue(alone.problems().isEmpty())
        assertNull(alone.toAssignment()?.assistant)
    }

    @Test
    fun aCyrillicRefereeNameIsRejectedEvenWhenTheAppIsInUkrainian() {
        // The app is readable in Ukrainian; the report is not. PSMF matches
        // names against a card cabinet in the Latin alphabet, so a Cyrillic
        // name on a ZoU is a name they cannot look up.
        val cyrillic = complete.copy(refereeName = "Юрій Вовк")

        assertEquals(listOf(HeaderProblem.RefereeNameNotLatin), cyrillic.problems())
        assertNull(cyrillic.toAssignment())
    }

    @Test
    fun czechDiacriticsAreFineBecauseTheyAreLatin() {
        val czech = complete.copy(refereeName = "Jiří Vlk", assistantName = "Roman Liška")

        assertTrue(czech.problems().isEmpty())
        assertEquals(
            "Jiří Vlk",
            czech
                .toAssignment()
                ?.main
                ?.name
                ?.value,
        )
    }

    @Test
    fun aNonLatinAssistantIsFlaggedSeparatelyFromTheReferee() {
        val entry = complete.copy(assistantName = "Юрій")

        assertEquals(listOf(HeaderProblem.AssistantNameNotLatin), entry.problems())
        assertNull(entry.toAssignment())
    }

    @Test
    fun theFormReloadsExactlyAsItWasLeft() {
        // Reopening a report must show what was typed, not a blank form:
        // this is the round trip the header screen depends on.
        val assignment = complete.toAssignment()

        assertEquals(complete, MatchHeaderEntry.from(assignment))
    }

    @Test
    fun aReportWithNoHeaderYetReloadsAsABlankForm() {
        assertEquals(MatchHeaderEntry(), MatchHeaderEntry.from(null))
    }

    @Test
    fun theRFlagSurvivesTheRoundTripOnEitherOfficial() {
        // The R mark is written next to the name of a licensed hire, and it
        // belongs to that official rather than to the match.
        val refereeHired = complete.copy(refereeLicensedHire = true, assistantLicensedHire = false)

        assertEquals(refereeHired, MatchHeaderEntry.from(refereeHired.toAssignment()))
    }
}
