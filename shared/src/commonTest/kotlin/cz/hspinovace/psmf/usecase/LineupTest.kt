package cz.hspinovace.psmf.usecase

import cz.hspinovace.psmf.data.player.AddedPlayerRepository
import cz.hspinovace.psmf.domain.AppearanceId
import cz.hspinovace.psmf.domain.DisciplinaryRecord
import cz.hspinovace.psmf.domain.Fixtures
import cz.hspinovace.psmf.domain.JerseyNumber
import cz.hspinovace.psmf.domain.MatchId
import cz.hspinovace.psmf.domain.Player
import cz.hspinovace.psmf.domain.PlayerId
import cz.hspinovace.psmf.domain.PlayerOrigin
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
    absent: Boolean = false,
    jersey: Int? = player.defaultJerseyNumber?.value,
    cardPresent: Boolean = true,
) = SquadMemberEntry(
    player = player,
    appearanceId = AppearanceId("app-${player.ref}"),
    absent = absent,
    jerseyNumber = JerseyNumber.orNull(jersey),
    registrationCardPresent = cardPresent,
)

private fun team(members: List<SquadMemberEntry>) =
    TeamLineupEntry(
        side = TeamSide.HOME,
        team = Fixtures.homeTeam,
        members = members,
        kitId = Fixtures.homePrimaryKit.id,
    )

/**
 * RULE: **the referee marks who is ABSENT.**
 *
 * The squad is already known and most of it turns up, so the task inverts
 * from writing ten names to three to five taps (analysis section 5.1).
 * Everybody starts present; that default is the whole design.
 */
class LineupEntryTest {
    private val novak = Fixtures.player("novak", "Novák", "Jan", 9)
    private val poupe = Fixtures.player("poupe", "Poupě", "Petr", 11)
    private val baca = Fixtures.player("baca", "Bača", "Tomáš", 13)

    private fun squad() = team(listOf(entry(novak), entry(poupe), entry(baca)))

    @Test
    fun everybodyIsPresentUntilSomebodyIsMarkedAbsent() {
        val squad = squad()

        assertEquals(3, squad.present.size)
        assertEquals(0, squad.absentCount)
        assertTrue(squad.problems().isEmpty())
    }

    @Test
    fun markingSomebodyAbsentTakesThemOutOfTheLineup() {
        val squad = squad().withMember(poupe.id) { it.copy(absent = true) }

        assertEquals(2, squad.present.size)
        assertEquals(1, squad.absentCount)
        assertFalse(squad.toLineup()!!.appearances.any { it.playerId == poupe.id })
    }

    @Test
    fun aTeamWithNobodyPresentIsNotALineup() {
        // A team that turned nobody up did not play. That is a forfeit for
        // PSMF to record, not a lineup the referee can write.
        val nobody = team(squad().members.map { it.copy(absent = true) })

        assertEquals(listOf(LineupProblem.NobodyPresent(TeamSide.HOME)), nobody.problems())
        assertNull(nobody.toLineup())
    }

    @Test
    fun jerseyNumbersDefaultFromSeedDataAndAreCorrectedByException() {
        val squad = squad().withMember(novak.id) { it.copy(jerseyNumber = JerseyNumber(7)) }

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
        val clash = squad().withMember(poupe.id) { it.copy(jerseyNumber = JerseyNumber(9)) }

        assertEquals(setOf(JerseyNumber(9)), clash.duplicateJerseyNumbers)
        assertEquals(
            listOf(LineupProblem.DuplicateJerseyNumber(TeamSide.HOME, JerseyNumber(9))),
            clash.problems(),
        )
        assertNull(clash.toLineup())
    }

    @Test
    fun anAbsentPlayerCannotClashWithAPresentOne() {
        // The number belongs to the appearance, and an absent player has no
        // appearance. Counting them would flag a clash that does not exist.
        val clash =
            squad()
                .withMember(poupe.id) { it.copy(jerseyNumber = JerseyNumber(9), absent = true) }

        assertTrue(clash.duplicateJerseyNumbers.isEmpty())
        assertNotNull(clash.toLineup())
    }

    @Test
    fun theKitLabelIsSnapshottedWhenTheLineupIsBuilt() {
        val lineup = assertNotNull(squad().copy(kitId = Fixtures.homeAlternateKit.id).toLineup())

        assertEquals(Fixtures.homeAlternateKit.id, lineup.kitId)
        assertEquals("bílo-modrá", lineup.kitLabel)
    }

    @Test
    fun whatGoesInTheRpColumnIsTheDateOfBirthWhenThereIsNoRpNumber() {
        // Every player in the shipped data is in exactly this position.
        val member = entry(novak)

        assertEquals("900615", member.identification?.value)
        // Offering a "no card" toggle would be offering a control that
        // changes nothing.
        assertFalse(member.cardMakesADifference)
    }

    @Test
    fun aRegisteredPlayerWritesTheirRpNumberUntilTheySayTheyHaveNoCard() {
        val registered = Fixtures.player("kominik", "Kominík", "Jan", 5, rpNumber = RpNumber("59001"))

        assertEquals("59001", entry(registered).identification?.value)
        assertTrue(entry(registered).cardMakesADifference)
        // The form's own printed rule: date of birth instead of the number.
        assertEquals("900615", entry(registered, cardPresent = false).identification?.value)
    }

    @Test
    fun aPlayerWithNothingToWriteIsAProblemNamedByPlayer() {
        // Registered, no card to hand, no date of birth on file. Rare, and
        // the referee has to supply something -- so it is said out loud
        // rather than silently dropping the row.
        val noFallback =
            Fixtures.player("x", "Novy", "Jan", 4, dateOfBirth = null, rpNumber = RpNumber("59002"))
        val squad = team(listOf(entry(noFallback, cardPresent = false)))

        assertEquals(
            listOf(LineupProblem.NoIdentification(TeamSide.HOME, noFallback.id)),
            squad.problems(),
        )
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
        val squad = team(listOf(entry(warned)))

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
        assertTrue(team(listOf(entry(odd))).problems().isEmpty())
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
        assertEquals("980304", entry(added).identification?.value)
    }
}
