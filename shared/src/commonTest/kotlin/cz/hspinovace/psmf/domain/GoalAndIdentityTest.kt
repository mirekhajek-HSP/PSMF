package cz.hspinovace.psmf.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * RULE: **a goal may have no scorer.**
 *
 * The worked example in analysis section 2.5 reads
 * `5´ Poupě 0:1`, `11´ Novák 1:1`, **`13´ — 2:1`**, `29´ Pořízek 2:2`,
 * `45´ Kulík 3:2`, `58´ Lovec 4:2`. The third row has a time and a
 * resulting score and no scorer at all.
 */
class GoalTest {

    @Test
    fun aGoalCanBeRecordedWithNoScorer() {
        val unattributed = GoalEvent(
            minute = Minute.Played(13),
            side = TeamSide.HOME,
            scorer = null,
            scoreAfter = Score(2, 1),
        )

        assertNull(unattributed.scorer)
        assertEquals("2:1", unattributed.scoreAfter.asWrittenOnReport)
    }

    @Test
    fun theWholeWorkedExampleScorelineCanBeRepresented() {
        val goals = listOf(
            GoalEvent(Minute.Played(5), TeamSide.AWAY, Fixtures.bacaAppearance.id, Score(0, 1)),
            GoalEvent(Minute.Played(11), TeamSide.HOME, Fixtures.poupeAppearance.id, Score(1, 1)),
            GoalEvent(Minute.Played(13), TeamSide.HOME, null, Score(2, 1)),
            GoalEvent(Minute.Played(29), TeamSide.AWAY, Fixtures.bacaAppearance.id, Score(2, 2)),
            GoalEvent(Minute.Played(45), TeamSide.HOME, Fixtures.houzevAppearance.id, Score(3, 2)),
            GoalEvent(Minute.Played(58), TeamSide.HOME, Fixtures.poupeAppearance.id, Score(4, 2)),
        )

        val match = Fixtures.matchInSetup().copy(goals = goals)

        assertEquals(Score(4, 2), match.scoreFromGoals())
        assertEquals(1, goals.count { it.scorer == null })
    }

    @Test
    fun theRunningScoreDerivedFromGoalsMatchesTheRecordedOne() {
        val goals = listOf(
            GoalEvent(Minute.Played(5), TeamSide.HOME, null, Score(1, 0)),
            GoalEvent(Minute.Played(9), TeamSide.AWAY, null, Score(1, 1)),
        )
        assertEquals(Score(1, 1), Fixtures.matchInSetup().copy(goals = goals).scoreFromGoals())
    }

    @Test
    fun aNegativeScoreIsRejected() {
        assertFailsWith<IllegalArgumentException> { Score(-1, 0) }
    }
}

/**
 * RULE: **the player identifier is one field plus a discriminator.**
 *
 * The ZoU has a single `Číslo RP` column holding either a registration
 * number or, for a player without their card, a date of birth. The worked
 * example contains `33 | 990121 | Hlok Petr` — six digits among five-digit
 * RP numbers (analysis section 2.5).
 */
class PlayerIdentifierTest {

    @Test
    fun oneValueCarriesItsOwnKind() {
        val rp = PlayerIdentifier("59001", PlayerIdentifierType.RP)
        val dateOfBirth = PlayerIdentifier("990121", PlayerIdentifierType.DATE_OF_BIRTH)

        // Same column, same field, different meaning. Two nullable fields
        // would allow both to be set at once, which the paper cannot express.
        assertEquals("59001", rp.asWrittenOnReport)
        assertEquals("990121", dateOfBirth.asWrittenOnReport)
        assertTrue(rp.type != dateOfBirth.type)
    }

    @Test
    fun theThreeKindsAreExactlyThoseTheFormAndRegulationsAllow() {
        assertEquals(
            listOf(
                PlayerIdentifierType.RP,
                PlayerIdentifierType.DATE_OF_BIRTH,
                PlayerIdentifierType.BIRTH_NUMBER,
            ),
            PlayerIdentifierType.entries.toList(),
        )
    }

    @Test
    fun aBlankIdentifierIsRejectedBecauseNullMeansNotRecorded() {
        assertFailsWith<IllegalArgumentException> { PlayerIdentifier("", PlayerIdentifierType.RP) }
        assertFailsWith<IllegalArgumentException> { PlayerIdentifier("  ", PlayerIdentifierType.RP) }
    }

    @Test
    fun anIdentifierIsOptionalBecauseNoRpNumbersExistYet() {
        // The one roster dependency that cannot be met from public data.
        assertNull(Fixtures.player("p1", "Novak", "Jan", 7).identifier)
    }
}

/**
 * RULE: **player names are Latin only.**
 *
 * PSMF's records are Latin. The app UI is available in Ukrainian, but that
 * is interface text: a Cyrillic surname on a generated ZoU is a name PSMF
 * cannot match against its own card cabinet.
 */
class PersonNameTest {

    @Test
    fun czechDiacriticsAreLatinAndAreAccepted() {
        listOf("Pořízek", "Žák", "Křížová", "Ďurica", "Ňuňo", "Šťastný")
            .forEach { assertNotNull(PersonName.orNull(it), "$it should be accepted") }
    }

    @Test
    fun cyrillicIsRejectedEvenThoughTheUiSupportsUkrainian() {
        listOf("Коваль", "Олександр", "Kovalенко")
            .forEach { assertNull(PersonName.orNull(it), "$it should be rejected") }
    }

    @Test
    fun ordinaryNamePunctuationSurvives() {
        assertNotNull(PersonName.orNull("O'Brien"))
        assertNotNull(PersonName.orNull("Lepis A."))
        assertNotNull(PersonName.orNull("Novak-Svoboda"))
    }

    @Test
    fun blankAndPunctuationOnlyNamesAreRejected() {
        assertNull(PersonName.orNull(""))
        assertNull(PersonName.orNull("   "))
        assertNull(PersonName.orNull("--"))
    }

    @Test
    fun whitespaceIsNormalisedSoTheReportIsTidy() {
        assertEquals("Novak Jan", PersonName.of("  Novak   Jan ").value)
    }

    @Test
    fun theReportWritesSurnameFirst() {
        // `Příjmení a jméno`.
        val name = PlayerName(PersonName.of("Bača"), PersonName.of("Petr"))
        assertEquals("Bača Petr", name.asWrittenOnReport)
    }
}
