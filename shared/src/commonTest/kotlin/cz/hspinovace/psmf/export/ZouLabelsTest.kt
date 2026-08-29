package cz.hspinovace.psmf.export

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The report language and the UI language are independent: the app may be
 * Czech, English or Ukrainian, but the generated ZoU is always Czech.
 *
 * These assertions are what makes a drift break loudly. If someone routes
 * an export label through a localised resource, or helpfully translates
 * one, the transcribed value stops matching the official form and this
 * test fails.
 *
 * Source for every value: docs/LEAGUE_APP_ANALYSIS.md section 2.5.
 */
class ZouLabelsTest {

    @Test
    fun headerLabelsMatchTheOfficialForm() {
        assertEquals("Hřiště", ZouLabels.Header.PITCH)
        assertEquals("Liga", ZouLabels.Header.LEAGUE)
        assertEquals("Rozhodčí", ZouLabels.Header.REFEREE)
    }

    @Test
    fun delegatingTeamIsLabelledSeparatelyFromThePlayingTeams() {
        // The team that delegated the referee is who gets fined for a bad
        // report, and is a distinct field from the two teams on the pitch.
        assertEquals("Týmy", ZouLabels.Header.DELEGATING_TEAMS)
    }

    @Test
    fun identifierColumnIsASingleColumnNamedForTheRegistrationCard() {
        // The form has one column that holds either an RP number or a date
        // of birth, so the model keeps one field plus a discriminator and
        // the label stays singular too.
        assertEquals("Číslo RP", ZouLabels.Lineup.IDENTIFIER)
    }

    @Test
    fun secondYellowIsWrittenAsTheFormWritesIt() {
        assertEquals("2. ŽK", ZouLabels.Cards.SECOND_YELLOW)
    }

    @Test
    fun assessmentAbbreviationsAreTheOnesPrintedOnTheForm() {
        assertEquals("NH", ZouLabels.Assessment.BEST_PLAYER)
        assertEquals("Čd", ZouLabels.Assessment.WAITING_TIME)
        assertEquals("Č", ZouLabels.Assessment.SHIRTS_NUMBERED)
        assertEquals("B", ZouLabels.Assessment.UNIFORM_KIT)
    }

    @Test
    fun labelsCarryCzechDiacriticsAndAreThereforeNotATranslation() {
        val czechSpecific = listOf(
            ZouLabels.Header.PITCH,
            ZouLabels.Header.REFEREE,
            ZouLabels.Lineup.KIT_COLOUR,
            ZouLabels.Result.WINNER,
            ZouLabels.Signatures.REFEREE,
        )
        val diacritics = "áčďéěíňóřšťúůýž".toSet()
        czechSpecific.forEach { label ->
            assertTrue(
                label.lowercase().any { it in diacritics },
                "Export label $label has lost its Czech diacritics; the " +
                    "ZoU must stay Czech regardless of UI language.",
            )
        }
    }

    @Test
    fun noCardsIssuedIsAnAffirmationRatherThanAnEmptyString() {
        // The paper form requires the boxes to be struck through, so an
        // empty list and a recorded none are different states.
        assertTrue(ZouLabels.Cards.NONE_ISSUED.isNotBlank())
    }
}
