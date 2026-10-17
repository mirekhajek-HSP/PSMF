package cz.hspinovace.psmf.usecase

import cz.hspinovace.psmf.domain.AppearanceId
import cz.hspinovace.psmf.domain.Fixtures
import cz.hspinovace.psmf.domain.IdentificationSource
import cz.hspinovace.psmf.domain.Match
import cz.hspinovace.psmf.domain.MatchId
import cz.hspinovace.psmf.domain.Player
import cz.hspinovace.psmf.domain.PlayerOrigin
import cz.hspinovace.psmf.domain.ReportedIdentification
import cz.hspinovace.psmf.domain.RpNumber
import cz.hspinovace.psmf.domain.TeamSide
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * DECISIONS 2026-10-07, "A league player may arrive with no
 * identification": every player scraped from psmf.cz has no RP number, no
 * date of birth and no birth number. The rule moves from the player to the
 * appearance -- such a player can be in the squad, and the referee writes
 * what the form asks for at the pitch to field them.
 *
 * What the form asks for, since DECISIONS 2026-10-17: the number from the
 * player's card, as the captain copies it on paper, and with no card the
 * date of birth, which is the form's own printed rule.
 */
class LeaguePlayerWithoutIdentificationTest {
    private var minted = 0

    /** Who psmf.cz gives us: a name, a team, and nothing to write in Číslo RP. */
    private val unidentified = Fixtures.player("bilek-ondrej", "Bílek", "Ondřej", 7, dateOfBirth = null)

    private val league =
        FakeLeagueRepository(listOf(TestLeague.group.copy(players = TestLeague.group.players + unidentified)))

    private val rememberedDates = FakeRememberedDateOfBirthRepository()
    private val rememberedNumbers = FakeRememberedCardNumberRepository()

    private fun match(id: String = "m1") = Match(MatchId(id), Fixtures.fixtureId, Fixtures.groupId)

    private suspend fun lineup(match: Match = match()) =
        BuildLineupEntry(league, FakeAddedPlayerRepository(), rememberedDates, rememberedNumbers) {
            "id-${++minted}"
        }(match)!!

    private fun TeamLineupEntry.bilek() = members.single { it.player.id == unidentified.id }

    /** The block as a referee has it with only this player in it, roles named, the way they came. */
    private fun TeamLineupEntry.onlyBilek() =
        members
            .fold(this) { team, member -> team.withPresence(member.player.id, member.player.id == unidentified.id) }
            .withCaptain(unidentified.id)
            .copy(goalkeeper = unidentified.id)

    @Test
    fun aSquadPlayerWithNoIdentificationIsOnTheListButNotYetWithAnEmptyRpColumn() =
        runTest {
            // On 8bd3c11 this player could not be built at all: the league
            // file would not load, and the referee saw no squad.
            val home = lineup().home.onlyBilek()

            assertTrue(home.bilek().present)
            assertNull(home.bilek().identification)
            // Present with nothing in Číslo RP: the block cannot be written yet.
            assertEquals(listOf(LineupProblem.NoIdentification(TeamSide.HOME, unidentified.id)), home.problems())
            assertNull(home.toLineup())
        }

    @Test
    fun typingTheNumberFromTheirCardFieldsThemWithItInTheRpColumn() =
        runTest {
            val home = lineup().home.onlyBilek()
            assertTrue(home.bilek().asksForCardNumber)

            val typed = home.withMember(unidentified.id) { it.withCardNumberTyped("59 001") }

            assertEquals(ReportedIdentification("59001", IdentificationSource.RP_TYPED), typed.bilek().identification)
            assertEquals(emptyList(), typed.problems())
            val written = assertNotNull(typed.toLineup()).appearances.single { it.playerId == unidentified.id }
            assertEquals("59001", written.reportedIdentification.value)
            // What was typed is the appearance's. The player is exactly what the league said.
            assertNull(typed.bilek().player.rpNumber)
            assertEquals(unidentified, typed.bilek().player)
        }

    @Test
    fun withNoCardTypingTheirDateOfBirthFieldsThemWithItInTheRpColumn() =
        runTest {
            val home =
                lineup().home.onlyBilek().withMember(
                    unidentified.id,
                ) { it.copy(registrationCardPresent = false) }
            assertTrue(home.bilek().asksForDateOfBirth)

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
    fun halfATypedNumberOrDateLeavesTheColumnEmptyRatherThanGuessing() =
        runTest {
            val home = lineup().home.onlyBilek()

            assertNull(home.withMember(unidentified.id) { it.withCardNumberTyped("590") }.bilek().identification)
            val noCard = home.withMember(unidentified.id) { it.copy(registrationCardPresent = false) }
            val halfDate = noCard.withMember(unidentified.id) { it.withDateOfBirthTyped("18.5.19") }
            assertNull(halfDate.bilek().identification)
            assertEquals(listOf(LineupProblem.NoIdentification(TeamSide.HOME, unidentified.id)), halfDate.problems())
        }

    @Test
    fun aRememberedNumberIsOfferedAtTheNextMatchAndCanBeChanged() =
        runTest {
            RememberCardNumber(rememberedNumbers)(unidentified.id, "59001")

            val next = lineup(match("m2")).home.onlyBilek()

            // Offered, in the field and in the column, without asking again.
            assertEquals("59001", next.bilek().cardNumberTyped)
            assertEquals("59001", next.bilek().identification?.value)
            assertEquals(emptyList(), next.problems())

            // Editable, and never required to match what was remembered.
            val corrected = next.withMember(unidentified.id) { it.withCardNumberTyped("59002") }
            assertEquals("59002", corrected.bilek().identification?.value)
        }

    @Test
    fun aRememberedDateIsOfferedAtTheNextMatchAndCanBeChanged() =
        runTest {
            RememberDateOfBirth(rememberedDates)(unidentified.id, LocalDate(1992, 5, 18))

            val next =
                lineup(match("m2"))
                    .home
                    .onlyBilek()
                    .withMember(unidentified.id) { it.copy(registrationCardPresent = false) }

            assertEquals("18.5.1992", next.bilek().dateOfBirthTyped)
            assertEquals("920518", next.bilek().identification?.value)
            assertEquals(emptyList(), next.problems())

            val corrected = next.withMember(unidentified.id) { it.withDateOfBirthTyped("18.5.1993") }
            assertEquals("930518", corrected.bilek().identification?.value)
        }

    @Test
    fun aRowAlreadyWrittenKeepsItsNumberWhenTheRememberedOneChangesLater() =
        runTest {
            // Written 59001 at the first match, then 59002 remembered at a
            // later one. Reopening the first lineup must not rewrite it.
            val typed = lineup().home.onlyBilek().withMember(unidentified.id) { it.withCardNumberTyped("59001") }
            val first = match().copy(homeLineup = assertNotNull(typed.toLineup()))
            RememberCardNumber(rememberedNumbers)(unidentified.id, "59002")

            val reopened = lineup(first).home

            assertEquals("59001", reopened.bilek().identification?.value)
            assertEquals("59001", reopened.bilek().cardNumberTyped)
            assertEquals(IdentificationSource.RP_TYPED, reopened.bilek().identification?.source)
        }

    @Test
    fun aRowAlreadyWrittenKeepsItsDateWhenTheRememberedOneChangesLater() =
        runTest {
            // Written 920518 at the first match, then corrected to 1993 at
            // a later one. Reopening the first lineup must not rewrite it.
            val noCard =
                lineup().home.onlyBilek().withMember(
                    unidentified.id,
                ) { it.copy(registrationCardPresent = false) }
            val typed = noCard.withMember(unidentified.id) { it.withDateOfBirthTyped("18.5.1992") }
            val first = match().copy(homeLineup = assertNotNull(typed.toLineup()))
            RememberDateOfBirth(rememberedDates)(unidentified.id, LocalDate(1993, 5, 18))

            val reopened = lineup(first).home

            assertEquals("920518", reopened.bilek().identification?.value)
            assertFalse(reopened.bilek().registrationCardPresent)
        }

    @Test
    fun aPlayerAddedAtThePitchNeedsNothingOnTheirRecordBecauseTheAppearanceWritesIt() {
        // Since 0.3.0 a player with a card has their number on the
        // appearance, so the record may have no date of birth. What it may
        // never have is an RP number.
        val added =
            Player.addedAtThePitch(
                id = unidentified.id,
                ref = "pitch-1",
                teamId = unidentified.teamId,
                name = unidentified.name,
                dateOfBirth = null,
            )

        assertEquals(PlayerOrigin.ADDED_AT_PITCH, added.origin)
        assertNull(added.dateOfBirth)
        assertNull(added.rpNumber)
    }

    @Test
    fun aRegisteredPlayerWithoutTheirCardIsFieldedTheSameWay() =
        runTest {
            // An RP number on file, no card to hand, no date of birth: the
            // case that used to end in "Chybí údaj" with nowhere to fix it.
            val registered =
                Fixtures.player("kominik-jan", "Kominík", "Jan", 5, dateOfBirth = null, rpNumber = RpNumber("59001"))
            val entry = SquadMemberEntry(registered, AppearanceId("a"))

            assertTrue(entry.showsLeagueRp)
            val withoutCard = entry.copy(registrationCardPresent = false)
            assertTrue(withoutCard.asksForDateOfBirth)
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
