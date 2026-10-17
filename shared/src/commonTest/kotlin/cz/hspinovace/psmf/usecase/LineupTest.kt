package cz.hspinovace.psmf.usecase

import cz.hspinovace.psmf.data.player.AddedPlayerRepository
import cz.hspinovace.psmf.domain.AppearanceId
import cz.hspinovace.psmf.domain.DisciplinaryRecord
import cz.hspinovace.psmf.domain.Fixtures
import cz.hspinovace.psmf.domain.IdentificationSource
import cz.hspinovace.psmf.domain.JerseyNumber
import cz.hspinovace.psmf.domain.LineupConfirmation
import cz.hspinovace.psmf.domain.MatchId
import cz.hspinovace.psmf.domain.PersonName
import cz.hspinovace.psmf.domain.Player
import cz.hspinovace.psmf.domain.PlayerId
import cz.hspinovace.psmf.domain.PlayerOrigin
import cz.hspinovace.psmf.domain.RpNumber
import cz.hspinovace.psmf.domain.TeamSide
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

/** In-memory stand-in for the pitch-added player store. */
class FakeAddedPlayerRepository(
    initial: List<Pair<MatchId, Player>> = emptyList(),
) : AddedPlayerRepository {
    private val stored = initial.toMutableList()

    override suspend fun add(
        matchId: MatchId,
        player: Player,
    ) {
        stored += matchId to player
    }

    override suspend fun forMatch(matchId: MatchId): List<Player> =
        stored.filter { it.first == matchId }.map { it.second }

    override suspend fun remove(playerId: PlayerId) {
        stored.removeAll { it.second.id == playerId }
    }
}

private fun entry(
    player: Player,
    present: Boolean = true,
    jersey: Int? = player.defaultJerseyNumber?.value,
    cardPresent: Boolean = true,
    cardNumber: String = "",
) = SquadMemberEntry(
    player = player,
    appearanceId = AppearanceId("app-${player.ref}"),
    present = present,
    jerseyNumber = JerseyNumber.orNull(jersey),
    registrationCardPresent = cardPresent,
).withCardNumberTyped(cardNumber)

private fun team(members: List<SquadMemberEntry>) =
    TeamLineupEntry(
        side = TeamSide.HOME,
        team = Fixtures.homeTeam,
        members = members,
        kitId = Fixtures.homePrimaryKit.id,
    )

/**
 * RULE: **the referee marks who CAME.**
 *
 * DECISIONS 2026-10-17, reversing 2026-08-29. The squad from psmf.cz is
 * everyone who has played this season and only grows, so marking the
 * absentees meant a forgotten tap put a no-show on the report, where it
 * counts as an appearance. Nobody is present until they are tapped.
 */
class LineupEntryTest {
    private val novak = Fixtures.player("novak", "Novák", "Jan", 9)
    private val poupe = Fixtures.player("poupe", "Poupě", "Petr", 11)
    private val baca = Fixtures.player("baca", "Bača", "Tomáš", 13)

    private fun squad() =
        team(
            listOf(
                entry(novak, cardNumber = "59001"),
                entry(poupe, cardNumber = "59002"),
                entry(baca, cardNumber = "59003"),
            ),
        )

    /** Everybody came, and the two roles are named: the shape the rest of these tests start from. */
    private fun finished() = squad().withCaptain(novak.id).copy(goalkeeper = baca.id)

    @Test
    fun nobodyIsPresentUntilSomebodyIsMarked() {
        val nobody = team(squad().members.map { it.copy(present = false) })

        assertEquals(0, nobody.present.size)
        // A team that turned nobody up did not play. That is a forfeit for
        // PSMF to record, not a lineup the referee can write.
        assertEquals(listOf(LineupProblem.NobodyPresent(TeamSide.HOME)), nobody.problems())
        assertNull(nobody.toLineup())
    }

    @Test
    fun markingSomebodyBringsThemIntoTheLineup() {
        val squad = team(squad().members.map { it.copy(present = false) }).withPresence(poupe.id, true)

        assertEquals(listOf(poupe.id), squad.present.map { it.player.id })
    }

    @Test
    fun takingTheMarkBackTakesThemOutAgain() {
        val squad = finished().withPresence(poupe.id, false)

        assertEquals(2, squad.present.size)
        assertFalse(squad.toLineup()!!.appearances.any { it.playerId == poupe.id })
    }

    @Test
    fun aPlayerWhoseMarkWasTakenBackKeepsWhatWasTypedInTheirRow() {
        // So giving the mark again does not mean typing it twice.
        val again = finished().withPresence(poupe.id, false).withPresence(poupe.id, true)

        val row = again.members.single { it.player.id == poupe.id }
        assertEquals("59002", row.identification?.value)
        assertEquals(JerseyNumber(11), row.jerseyNumber)
    }

    @Test
    fun jerseyNumbersDefaultFromSeedDataAndAreCorrectedByException() {
        val squad = finished().withMember(novak.id) { it.copy(jerseyNumber = JerseyNumber(7)) }

        val numbers = squad.toLineup()!!.appearances.associate { it.playerId to it.jerseyNumber?.value }
        assertEquals(7, numbers[novak.id])
        // Untouched players keep what the seed file says.
        assertEquals(11, numbers[poupe.id])
    }

    @Test
    fun twoPlayersSharingANumberIsAProblemRatherThanACrash() {
        // Goals and cards are attributed by number, so this would make the
        // rest of the report ambiguous -- but it is also what a referee's
        // screen looks like halfway through typing the second number.
        val clash = finished().withMember(poupe.id) { it.copy(jerseyNumber = JerseyNumber(9)) }

        assertEquals(setOf(JerseyNumber(9)), clash.duplicateJerseyNumbers)
        assertEquals(
            listOf(LineupProblem.DuplicateJerseyNumber(TeamSide.HOME, JerseyNumber(9))),
            clash.problems(),
        )
        assertNull(clash.toLineup())
    }

    @Test
    fun aPlayerWhoDidNotComeCannotClashWithOneWhoDid() {
        // The number belongs to the appearance, and somebody who is not
        // present has no appearance. Counting them would flag a clash that
        // does not exist.
        val clash =
            finished().withPresence(poupe.id, false).withMember(poupe.id) { it.copy(jerseyNumber = JerseyNumber(9)) }

        assertTrue(clash.duplicateJerseyNumbers.isEmpty())
        assertNotNull(clash.toLineup())
    }

    @Test
    fun theKitLabelIsSnapshottedWhenTheLineupIsBuilt() {
        val lineup = assertNotNull(finished().copy(kitId = Fixtures.homeAlternateKit.id).toLineup())

        assertEquals(Fixtures.homeAlternateKit.id, lineup.kitId)
        assertEquals("bílo-modrá", lineup.kitLabel)
    }

    // ------------------------------------------------------------------
    // Captain and goalkeeper
    // ------------------------------------------------------------------

    @Test
    fun aCaptainAndAGoalkeeperAreBothNeededBeforeTheBlockIsWritten() {
        val none = squad()

        assertEquals(
            listOf(LineupProblem.NoCaptain(TeamSide.HOME), LineupProblem.NoGoalkeeper(TeamSide.HOME)),
            none.problems(),
        )
        assertNull(none.toLineup())
        // Each is needed on its own: naming one does not excuse the other.
        assertEquals(listOf(LineupProblem.NoGoalkeeper(TeamSide.HOME)), none.withCaptain(novak.id).problems())
    }

    @Test
    fun oneManCanBeBothAndThenIsOneRow() {
        val both = squad().withCaptain(novak.id).copy(goalkeeper = novak.id)

        assertEquals(emptyList(), both.problems())
        val lineup = assertNotNull(both.toLineup())
        assertEquals(lineup.captain, lineup.goalkeeper)
        assertEquals(3, lineup.appearances.size)
    }

    @Test
    fun theLineupNamesTheAppearancesOfTheTwoRoles() {
        val lineup = assertNotNull(finished().toLineup())

        assertEquals(AppearanceId("app-novak"), lineup.captain)
        assertEquals(AppearanceId("app-baca"), lineup.goalkeeper)
    }

    @Test
    fun aRoleIsAmongThoseWhoCameSoTheMarkBeingTakenBackVacatesIt() {
        val without = finished().withPresence(novak.id, false)

        assertNull(without.captain)
        assertEquals(baca.id, without.goalkeeper)
        assertEquals(listOf(LineupProblem.NoCaptain(TeamSide.HOME)), without.problems())
    }

    @Test
    fun aRoleHeldByNobodyPresentIsNotAMarkedCaptain() {
        // However the entry came to say it: somebody who did not come is not
        // the captain, and the problem is named rather than papered over.
        val stale = finished().withMember(novak.id) { it.copy(present = false) }

        assertEquals(listOf(LineupProblem.NoCaptain(TeamSide.HOME)), stale.problems())
    }

    @Test
    fun aDifferentCaptainWithdrawsTheConfirmationTheLastOneGave() {
        val confirmed =
            finished().copy(
                confirmation =
                    LineupConfirmation(
                        at = Instant.parse("2026-10-17T17:00:00Z"),
                        by = PersonName.of("Novák Jan"),
                    ),
            )

        // The same captain marked again changes nothing; another one does.
        assertNotNull(confirmed.withCaptain(novak.id).confirmation)
        assertNull(confirmed.withCaptain(poupe.id).confirmation)
    }

    // ------------------------------------------------------------------
    // What goes in the Číslo RP column
    // ------------------------------------------------------------------

    @Test
    fun aPlayerWithNoLeagueRpNumberWritesTheNumberFromTheirCard() {
        // Every player in the shipped data is in exactly this position.
        val member = entry(novak, cardNumber = "59001")

        assertTrue(member.asksForCardNumber)
        assertFalse(member.showsLeagueRp)
        assertEquals("59001", member.identification?.value)
        assertEquals(IdentificationSource.RP_TYPED, member.identification?.source)
    }

    @Test
    fun theTypedNumberIsReadTheWayItIsMeantAndNothingElse() {
        val member = entry(novak)

        // As the card prints it, spaces and all.
        assertEquals("59001", member.withCardNumberTyped("59 001").identification?.value)
        // Not a number copied from a card: too short is likelier a jersey
        // number typed in the wrong box, and letters are not digits.
        assertNull(member.withCardNumberTyped("12").identification)
        assertNull(member.withCardNumberTyped("5900x").identification)
        // Longer than any number the form shows, by more than a slip of the thumb.
        assertNull(member.withCardNumberTyped("12345678").identification)
        // And said so, rather than the column quietly staying empty.
        assertTrue(member.withCardNumberTyped("12").cardNumberRejected)
        assertFalse(member.withCardNumberTyped("").cardNumberRejected)
    }

    @Test
    fun aNumberTypedFromTheCardNeverBecomesThePlayersRpNumber() {
        // NON-NEGOTIABLE (DECISIONS 2026-10-17). What a person typed is what
        // was written that day: it lives on the appearance, and the player
        // record -- which is the league's -- is exactly what it was.
        val typed = entry(novak, cardNumber = "59001")
        val lineup = assertNotNull(team(listOf(typed)).withCaptain(novak.id).copy(goalkeeper = novak.id).toLineup())

        assertNull(typed.player.rpNumber)
        assertNull(typed.player.birthNumber)
        assertEquals(novak, typed.player)
        assertEquals(
            IdentificationSource.RP_TYPED,
            lineup.appearances
                .single()
                .reportedIdentification.source,
        )
    }

    @Test
    fun aLeagueRpNumberIsShownAndNeverTypedOver() {
        val registered = Fixtures.player("kominik", "Kominík", "Jan", 5, rpNumber = RpNumber("59001"))
        // Whatever somebody typed into the row meanwhile.
        val member = entry(registered, cardNumber = "11111")

        assertTrue(member.showsLeagueRp)
        assertFalse(member.asksForCardNumber)
        assertEquals("59001", member.identification?.value)
        assertEquals(IdentificationSource.RP, member.identification?.source)
    }

    @Test
    fun withNoCardTheDateOfBirthIsWrittenWhichIsTheFormsOwnPrintedRule() {
        // On file: the league's own, shown and never typed over.
        val onFile = entry(novak, cardPresent = false)
        assertEquals("900615", onFile.identification?.value)
        assertEquals(IdentificationSource.DATE_OF_BIRTH, onFile.identification?.source)
        assertFalse(onFile.asksForDateOfBirth)

        // Not on file -- every psmf.cz player -- so the referee writes it.
        val nothing = Fixtures.player("bilek", "Bílek", "Ondřej", 7, dateOfBirth = null)
        val asked = entry(nothing, cardPresent = false)
        assertTrue(asked.asksForDateOfBirth)
        assertNull(asked.identification)
        assertEquals("920518", asked.withDateOfBirthTyped("18.5.1992").identification?.value)
    }

    @Test
    fun aRegisteredPlayerWritesTheirRpNumberUntilTheySayTheyHaveNoCard() {
        val registered = Fixtures.player("kominik", "Kominík", "Jan", 5, rpNumber = RpNumber("59001"))

        assertEquals("59001", entry(registered).identification?.value)
        // The form's own printed rule: date of birth instead of the number.
        assertEquals("900615", entry(registered, cardPresent = false).identification?.value)
    }

    @Test
    fun aPlayerWithNothingToWriteIsAProblemNamedByPlayer() {
        // A card, and nothing typed from it yet. The referee has to supply
        // something -- so it is said out loud rather than silently dropping
        // the row.
        val squad = team(listOf(entry(novak))).withCaptain(novak.id).copy(goalkeeper = novak.id)

        assertEquals(listOf(LineupProblem.NoIdentification(TeamSide.HOME, novak.id)), squad.problems())
        assertNull(squad.toLineup())
    }

    @Test
    fun aSuspensionWarningIsAdvisoryAndCarriesItsDate() {
        val onEven =
            Fixtures.player(
                "even",
                "Kriz",
                "Ondrej",
                18,
                discipline = DisciplinaryRecord(2, LocalDate(2026, 8, 24)),
            )

        val warning = assertNotNull(entry(onEven).suspensionWarning)
        assertEquals(2, warning.yellowsThisSeason)
        assertEquals(LocalDate(2026, 8, 24), warning.asOf)
    }

    @Test
    fun aWarningIsNeverAProblemBecauseTheAppDoesNotDecideEligibility() {
        // THE HARD CONSTRAINT. Fielding an ineligible player is a technical
        // forfeit. The app may warn; it must never block, and must never
        // let the absence of a warning read as clearance.
        val warned =
            Fixtures.player("even", "Kriz", "Ondrej", 18, discipline = DisciplinaryRecord(4, LocalDate(2026, 8, 24)))
        val squad =
            team(
                listOf(entry(warned, cardNumber = "59001")),
            ).withCaptain(warned.id).copy(goalkeeper = warned.id)

        assertNotNull(entry(warned).suspensionWarning)
        assertTrue(squad.problems().isEmpty())
        assertNotNull(squad.toLineup())
    }

    @Test
    fun aPlayerWithAnOddCountGetsNoBadgeAndThatIsNotClearance() {
        val odd =
            Fixtures.player("odd", "Ruzicka", "Radek", 27, discipline = DisciplinaryRecord(3, LocalDate(2026, 8, 24)))

        assertNull(entry(odd).suspensionWarning)
        // Nothing anywhere says this player may take the field, and there is
        // deliberately no property that could.
        val squad = team(listOf(entry(odd, cardNumber = "59001"))).withCaptain(odd.id).copy(goalkeeper = odd.id)
        assertTrue(squad.problems().isEmpty())
    }

    @Test
    fun aPitchAddedPlayerIsFlaggedSoPsmfCanReconcileThem() {
        val added =
            Player.addedAtThePitch(
                id = PlayerId("added"),
                ref = "pitch-1",
                teamId = Fixtures.homeTeamId,
                name = novak.name,
                dateOfBirth = LocalDate(1998, 3, 4),
            )

        assertTrue(entry(added).addedAtThePitch)
        assertEquals(PlayerOrigin.ADDED_AT_PITCH, added.origin)
        // With no card, the date of birth they were added with.
        assertEquals("980304", entry(added, cardPresent = false).identification?.value)
    }

    @Test
    fun aPitchAddedPlayerWithACardNeedsNoDateOfBirthOnTheirRecord() {
        val added =
            Player.addedAtThePitch(
                id = PlayerId("added"),
                ref = "pitch-1",
                teamId = Fixtures.homeTeamId,
                name = novak.name,
                dateOfBirth = null,
            )

        // The number from their card is the appearance's; the record has none.
        assertNull(added.dateOfBirth)
        assertNull(added.rpNumber)
        assertEquals("59123", entry(added, cardNumber = "59123").identification?.value)
    }
}
