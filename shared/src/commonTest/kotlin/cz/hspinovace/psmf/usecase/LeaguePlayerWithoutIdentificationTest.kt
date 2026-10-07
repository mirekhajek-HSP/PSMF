package cz.hspinovace.psmf.usecase

import cz.hspinovace.psmf.domain.Fixtures
import cz.hspinovace.psmf.domain.IdentificationSource
import cz.hspinovace.psmf.domain.Match
import cz.hspinovace.psmf.domain.MatchId
import cz.hspinovace.psmf.domain.Player
import cz.hspinovace.psmf.domain.PlayerOrigin
import cz.hspinovace.psmf.domain.ReportedIdentification
import cz.hspinovace.psmf.domain.TeamSide
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * DECISIONS 2026-10-07, "A league player may arrive with no
 * identification": every player scraped from psmf.cz has no RP number, no
 * date of birth and no birth number. The rule moves from the player to the
 * appearance -- such a player can be in the squad, and the referee writes
 * the date of birth at the pitch to field them.
 */
class LeaguePlayerWithoutIdentificationTest {
    private var minted = 0

    /** Who psmf.cz gives us: a name, a team, and nothing to write in Číslo RP. */
    private val unidentified = Fixtures.player("bilek-ondrej", "Bílek", "Ondřej", 7, dateOfBirth = null)

    private val league =
        FakeLeagueRepository(listOf(TestLeague.group.copy(players = TestLeague.group.players + unidentified)))

    private val remembered = FakeRememberedDateOfBirthRepository()

    private fun match(id: String = "m1") = Match(MatchId(id), Fixtures.fixtureId, Fixtures.groupId)

    private suspend fun lineup(match: Match = match()) =
        BuildLineupEntry(league, FakeAddedPlayerRepository(), remembered) { "id-${++minted}" }(match)!!

    private fun TeamLineupEntry.bilek() = members.single { it.player.id == unidentified.id }

    @Test
    fun aSquadPlayerWithNoIdentificationIsOnTheListButNotYetWithAnEmptyRpColumn() =
        runTest {
            // On 8bd3c11 this player could not be built at all: the league
            // file would not load, and the referee saw no squad.
            val home = lineup().home
            val row = home.members.single { it.player.id == unidentified.id }

            assertFalse(row.absent)
            assertNull(row.identification)
            // Present with nothing in Číslo RP: the block cannot be written yet.
            assertEquals(listOf(LineupProblem.NoIdentification(TeamSide.HOME, unidentified.id)), home.problems())
            assertNull(home.toLineup())
        }

    @Test
    fun typingTheirDateOfBirthInTheRowFieldsThemWithItInTheRpColumn() =
        runTest {
            val home = lineup().home
            assertTrue(home.bilek().needsDateOfBirth)

            // As a referee types it; the YYMMDD is the app's job.
            val typed = home.withMember(unidentified.id) { it.withDateOfBirthTyped("18.5.1992") }

            assertEquals(LocalDate(1992, 5, 18), typed.bilek().typedDateOfBirth)
            assertEquals(
                ReportedIdentification("920518", IdentificationSource.DATE_OF_BIRTH),
                typed.bilek().identification,
            )
            assertEquals(emptyList(), typed.problems())
            val written = assertNotNull(typed.toLineup()).appearances.single { it.playerId == unidentified.id }
            assertEquals("920518", written.reportedIdentification.value)
        }

    @Test
    fun halfATypedDateLeavesTheColumnEmptyRatherThanGuessing() =
        runTest {
            val home = lineup().home.withMember(unidentified.id) { it.withDateOfBirthTyped("18.5.19") }

            assertNull(home.bilek().identification)
            assertEquals(listOf(LineupProblem.NoIdentification(TeamSide.HOME, unidentified.id)), home.problems())
        }

    @Test
    fun aRememberedDateIsOfferedAtTheNextMatchAndCanBeChanged() =
        runTest {
            RememberDateOfBirth(remembered)(unidentified.id, LocalDate(1992, 5, 18))

            val next = lineup(match("m2")).home

            // Offered, in the field and in the column, without asking again.
            assertEquals("18.5.1992", next.bilek().dateOfBirthTyped)
            assertEquals("920518", next.bilek().identification?.value)
            assertEquals(emptyList(), next.problems())

            // Editable, and never required to match what was remembered.
            val corrected = next.withMember(unidentified.id) { it.withDateOfBirthTyped("18.5.1993") }
            assertEquals("930518", corrected.bilek().identification?.value)
        }

    @Test
    fun aRowAlreadyWrittenKeepsItsDateWhenTheRememberedOneChangesLater() =
        runTest {
            // Written 920518 at the first match, then corrected to 1993 at
            // a later one. Reopening the first lineup must not rewrite it.
            val typed = lineup().home.withMember(unidentified.id) { it.withDateOfBirthTyped("18.5.1992") }
            val first = match().copy(homeLineup = assertNotNull(typed.toLineup()))
            RememberDateOfBirth(remembered)(unidentified.id, LocalDate(1993, 5, 18))

            val reopened = lineup(first).home

            assertEquals("920518", reopened.bilek().identification?.value)
            assertEquals(
                "920518",
                assertNotNull(reopened.toLineup())
                    .appearances
                    .single {
                        it.playerId ==
                            unidentified.id
                    }.reportedIdentification.value,
            )
        }

    @Test
    fun aPlayerAddedAtThePitchStillNeedsADateOfBirth() {
        // The referee is the only source of one, and it is all they have.
        assertFailsWith<IllegalArgumentException> {
            Player(
                id = unidentified.id,
                ref = "pitch-1",
                teamId = unidentified.teamId,
                name = unidentified.name,
                rpNumber = null,
                dateOfBirth = null,
                birthNumber = null,
                origin = PlayerOrigin.ADDED_AT_PITCH,
            )
        }
    }

    @Test
    fun aRegisteredPlayerWithoutTheirCardIsFieldedTheSameWay() =
        runTest {
            // An RP number on file, no card to hand, no date of birth: the
            // case that used to end in "Chybí údaj" with nowhere to fix it.
            val registered =
                Fixtures.player(
                    "kominik-jan",
                    "Kominík",
                    "Jan",
                    5,
                    dateOfBirth = null,
                    rpNumber =
                        cz.hspinovace.psmf.domain
                            .RpNumber("59001"),
                )
            val entry =
                SquadMemberEntry(
                    registered,
                    cz.hspinovace.psmf.domain
                        .AppearanceId("a"),
                )

            assertTrue(entry.cardMakesADifference)
            val withoutCard = entry.copy(registrationCardPresent = false)
            assertTrue(withoutCard.needsDateOfBirth)
            assertEquals("920518", withoutCard.withDateOfBirthTyped("18051992").identification?.value)
            // With the card, the RP number, whatever was typed.
            assertEquals(
                "59001",
                withoutCard
                    .withDateOfBirthTyped("18051992")
                    .copy(registrationCardPresent = true)
                    .identification
                    ?.value,
            )
        }
}
