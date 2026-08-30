package cz.hspinovace.psmf.domain

import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * RULE: **an RP number and a fallback identification are two different
 * things**, and an earlier version of this model wrongly collapsed them.
 *
 * - `rpNumber` is issued by PSMF, immutable, and **never user-editable**.
 * - `dateOfBirth` / `birthNumber` are entered by a person when there is no
 *   RP number to use.
 *
 * One is a foreign key into somebody else's system; the other is what a
 * referee writes at a pitch.
 */
class PlayerIdentificationTest {
    @Test
    fun aPlayerWhoCannotBeIdentifiedAtAllCannotBeBuilt() {
        // The invariant, enforced at construction: at least one of the three.
        val failure =
            assertFailsWith<IllegalArgumentException> {
                Player(
                    id = PlayerId("p1"),
                    ref = "novak-jan",
                    teamId = Fixtures.homeTeamId,
                    name = PlayerName(PersonName.of("Novak"), PersonName.of("Jan")),
                    rpNumber = null,
                    dateOfBirth = null,
                    birthNumber = null,
                )
            }
        assertTrue(failure.message!!.contains("cannot be identified"))
    }

    @Test
    fun anRpNumberAloneIsEnough() {
        val registered =
            Player(
                id = PlayerId("p1"),
                ref = "novak-jan",
                teamId = Fixtures.homeTeamId,
                name = PlayerName(PersonName.of("Novak"), PersonName.of("Jan")),
                rpNumber = RpNumber("59001"),
                dateOfBirth = null,
                birthNumber = null,
            )
        assertEquals(RpNumber("59001"), registered.rpNumber)
        assertNull(registered.dateOfBirth)
    }

    @Test
    fun aPlayerAddedAtThePitchIsOfferedNoRpFieldAtAll() {
        // Screen 3: someone turns up who is not in the squad list. The user
        // gives first name, surname and date of birth. There is deliberately
        // no RP parameter on this factory -- that is what makes "the user
        // must never be able to type one" true in the model and not only in
        // the UI.
        val walkUp =
            Player.addedAtThePitch(
                id = PlayerId("p-new"),
                ref = "svoboda-petr",
                teamId = Fixtures.homeTeamId,
                name = PlayerName(PersonName.of("Svoboda"), PersonName.of("Petr")),
                dateOfBirth = LocalDate(2001, 3, 4),
            )

        assertNull(walkUp.rpNumber)
        assertEquals(PlayerOrigin.ADDED_AT_PITCH, walkUp.origin)
    }

    @Test
    fun aPitchAddedPlayerCannotBeGivenAnRpNumberByTheBackDoor() {
        // copy() is the back door a data class always leaves open, so the
        // invariant is checked rather than merely intended.
        val walkUp =
            Player.addedAtThePitch(
                id = PlayerId("p-new"),
                ref = "svoboda-petr",
                teamId = Fixtures.homeTeamId,
                name = PlayerName(PersonName.of("Svoboda"), PersonName.of("Petr")),
                dateOfBirth = LocalDate(2001, 3, 4),
            )

        assertFailsWith<IllegalArgumentException> { walkUp.copy(rpNumber = RpNumber("59001")) }
    }

    @Test
    fun reconcilingAPitchAddedPlayerIsTheOneWayAnRpNumberArrives() {
        // What happens when PSMF registers them. Not a user action.
        val walkUp =
            Player.addedAtThePitch(
                id = PlayerId("p-new"),
                ref = "svoboda-petr",
                teamId = Fixtures.homeTeamId,
                name = PlayerName(PersonName.of("Svoboda"), PersonName.of("Petr")),
                dateOfBirth = LocalDate(2001, 3, 4),
            )

        val registered = walkUp.registeredWith(RpNumber("59123"))

        assertEquals(RpNumber("59123"), registered.rpNumber)
        assertEquals(PlayerOrigin.LEAGUE_RECORD, registered.origin)
        // The id survives: reports already reference it.
        assertEquals(walkUp.id, registered.id)
    }

    @Test
    fun anRpNumberCannotBeBlankBecauseNullMeansNotIssued() {
        assertFailsWith<IllegalArgumentException> { RpNumber("") }
        assertFailsWith<IllegalArgumentException> { BirthNumber("  ") }
    }
}

/**
 * RULE: **what was written in the `Číslo RP` column is a per-match fact.**
 *
 * Analysis section 2.5 distinguishes three situations, and the value is
 * *stored* rather than derived at export time: a player who later gains an
 * RP number must not retroactively change an old report. Same principle as
 * report versioning (section 5.3).
 */
class ReportedIdentificationTest {
    private val registered =
        Fixtures.player(
            ref = "hlok-petr",
            surname = "Hlok",
            first = "Petr",
            number = 33,
            dateOfBirth = Fixtures.hlokDateOfBirth,
            rpNumber = RpNumber("59001"),
        )

    @Test
    fun registeredWithTheCardPresentWritesTheRpNumber() {
        val written = registered.identificationFor(registrationCardPresent = true)
        assertEquals(ReportedIdentification("59001", IdentificationSource.RP), written)
    }

    @Test
    fun registeredWithTheCardNotToHandWritesTheDateOfBirth() {
        // The form's own printed rule: "U hráčů, kteří nemají k dispozici
        // svůj registrační průkaz (RP), uvedou místo čísla RP jejich datum
        // narození."
        val written = registered.identificationFor(registrationCardPresent = false)
        assertEquals(IdentificationSource.DATE_OF_BIRTH, written?.source)
        // The worked example row: `33 | 990121 | Hlok Petr`, six digits
        // among five-digit RP numbers.
        assertEquals("990121", written?.value)
    }

    @Test
    fun aPlayerWithNoRpNumberFallsBackWithoutBeingAskedForOne() {
        val unregistered = Fixtures.player("novak-jan", "Novak", "Jan", 7, dateOfBirth = LocalDate(2003, 12, 5))
        val written = unregistered.identificationFor(registrationCardPresent = true)
        assertEquals(ReportedIdentification("031205", IdentificationSource.DATE_OF_BIRTH), written)
    }

    @Test
    fun theSameSquadPlayerCanBeWrittenDifferentlyInTwoMatches() {
        // Card in hand one week, forgotten the next. Nothing about the
        // player record changes; the appearance carries the difference.
        val withCard = registered.identificationFor(registrationCardPresent = true)!!
        val withoutCard = registered.identificationFor(registrationCardPresent = false)!!

        assertNotEquals(withCard, withoutCard)
        assertNotEquals(withCard.source, withoutCard.source)
    }

    @Test
    fun anOldReportDoesNotChangeWhenThePlayerLaterGainsAnRpNumber() {
        // The reason this is stored and not derived.
        val before = Fixtures.player("svoboda-petr", "Svoboda", "Petr", 8, dateOfBirth = LocalDate(2001, 3, 4))
        val appearance =
            Fixtures.appearance(
                "app-1",
                before.id.value,
                8,
                reportedIdentification = before.identificationFor(registrationCardPresent = true)!!,
            )

        val afterRegistration = before.copy(rpNumber = RpNumber("59999"))

        // The player record moved on; what the report says did not.
        assertEquals(RpNumber("59999"), afterRegistration.rpNumber)
        assertEquals(IdentificationSource.DATE_OF_BIRTH, appearance.reportedIdentification.source)
        assertEquals("010304", appearance.reportedIdentification.value)
    }

    @Test
    fun theThreeSourcesAreExactlyThoseTheFormAndRegulationsAllow() {
        assertEquals(
            listOf(
                IdentificationSource.RP,
                IdentificationSource.DATE_OF_BIRTH,
                IdentificationSource.BIRTH_NUMBER,
            ),
            IdentificationSource.entries.toList(),
        )
    }

    @Test
    fun aBlankValueIsRejectedBecauseEveryRowMustIdentifyItsPlayer() {
        assertFailsWith<IllegalArgumentException> {
            ReportedIdentification("", IdentificationSource.RP)
        }
    }

    @Test
    fun aDateOfBirthIsWrittenInTheFormsOwnSixDigitOrder() {
        // YYMMDD, the order a rodné číslo starts with.
        assertEquals("990121", LocalDate(1999, 1, 21).asWrittenInTheRpColumn())
        assertEquals("000101", LocalDate(2000, 1, 1).asWrittenInTheRpColumn())
        assertEquals("751231", LocalDate(1975, 12, 31).asWrittenInTheRpColumn())
    }
}
