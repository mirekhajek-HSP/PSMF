package cz.hspinovace.psmf.data.match

import cz.hspinovace.psmf.data.db.DatabaseDriverFactory
import cz.hspinovace.psmf.data.player.SqlDelightAddedPlayerRepository
import cz.hspinovace.psmf.data.player.SqlDelightRememberedCardNumberRepository
import cz.hspinovace.psmf.data.player.SqlDelightRememberedDateOfBirthRepository
import cz.hspinovace.psmf.data.team.SqlDelightFollowedTeamRepository
import cz.hspinovace.psmf.data.team.SqlDelightJerseyOverrideRepository
import cz.hspinovace.psmf.db.PsmfDatabase
import cz.hspinovace.psmf.domain.CardReason
import cz.hspinovace.psmf.domain.CardSubject
import cz.hspinovace.psmf.domain.CardsSection
import cz.hspinovace.psmf.domain.ConfirmingParty
import cz.hspinovace.psmf.domain.Dismissal
import cz.hspinovace.psmf.domain.FixtureId
import cz.hspinovace.psmf.domain.Fixtures
import cz.hspinovace.psmf.domain.GoalEvent
import cz.hspinovace.psmf.domain.JerseyNumber
import cz.hspinovace.psmf.domain.Match
import cz.hspinovace.psmf.domain.MatchId
import cz.hspinovace.psmf.domain.MatchStatus
import cz.hspinovace.psmf.domain.Minute
import cz.hspinovace.psmf.domain.PersonName
import cz.hspinovace.psmf.domain.Player
import cz.hspinovace.psmf.domain.PlayerId
import cz.hspinovace.psmf.domain.PlayerName
import cz.hspinovace.psmf.domain.PowerPlay
import cz.hspinovace.psmf.domain.RedCard
import cz.hspinovace.psmf.domain.Score
import cz.hspinovace.psmf.domain.TeamSide
import cz.hspinovace.psmf.domain.YellowCard
import cz.hspinovace.psmf.usecase.DiscardReport
import cz.hspinovace.psmf.usecase.TestLeague
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import java.io.File
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.time.Instant

/**
 * **Discarding a report removes all of it, and nothing else** (prompt 17,
 * Gate 2, the tester's point 1).
 *
 * On the real SQLite driver, as `MatchPersistenceTest` is: the report's rows,
 * the lineups being built for it and the players added for it at the pitch
 * live in eight tables, and a fake would only prove that a map lost a key.
 * Each step is a separate session against the same file, so nothing is read
 * back from a cache.
 */
class DiscardReportPersistenceTest {
    private val databaseFile: File = File.createTempFile("psmf-discard-", ".db").also { it.delete() }

    @AfterTest
    fun cleanUp() {
        databaseFile.delete()
    }

    private class Session(
        val database: PsmfDatabase,
    ) {
        val matches = SqlDelightMatchRepository(database)
        val drafts = SqlDelightLineupDraftRepository(database)
        val added = SqlDelightAddedPlayerRepository(database)
        val cardNumbers = SqlDelightRememberedCardNumberRepository(database)
        val datesOfBirth = SqlDelightRememberedDateOfBirthRepository(database)
        val jerseys = SqlDelightJerseyOverrideRepository(database)
        val followed = SqlDelightFollowedTeamRepository(database)
        val discard = DiscardReport(matches, drafts)
    }

    /** A separate app run against the same database file. */
    private suspend fun <T> session(block: suspend (Session) -> T): T {
        val driver = DatabaseDriverFactory("jdbc:sqlite:${databaseFile.absolutePath}").create()
        try {
            return block(Session(PsmfDatabase(driver)))
        } finally {
            driver.close()
        }
    }

    private val baca = CardSubject.Player(Fixtures.bacaAppearance.id)

    /** A match under way with a row in every one of the report's tables. */
    private fun report(
        id: String,
        fixtureId: FixtureId,
    ): Match =
        Fixtures.matchInSetup().copy(
            id = MatchId(id),
            fixtureId = fixtureId,
            status = MatchStatus.IN_PROGRESS,
            kickoffAt = Fixtures.kickoffAt,
            goals = listOf(GoalEvent(Minute.Played(5), TeamSide.AWAY, Fixtures.bacaAppearance.id, Score(0, 1))),
            cards =
                CardsSection.Issued(
                    listOf(
                        YellowCard(Minute.Played(20), TeamSide.AWAY, baca, CardReason("podražení")),
                        RedCard(Minute.Played(25), TeamSide.AWAY, baca, CardReason("2. ŽK"), Dismissal.SECOND_YELLOW),
                    ),
                ),
            powerPlays =
                listOf(PowerPlay(TeamSide.AWAY, Instant.parse("2026-08-31T19:25:00Z"), Minute.Played(25))),
            confirmations = listOf(Fixtures.confirmation(ConfirmingParty.HOME_CAPTAIN)),
        )

    private val discarded = report("m-discarded", Fixtures.fixtureId)
    private val kept = report("m-kept", TestLeague.secondRoundEarly.id)

    private fun addedFor(match: Match) =
        Player.addedAtThePitch(
            id = PlayerId("p-added-${match.id.value}"),
            ref = "p-added-${match.id.value}",
            teamId = Fixtures.homeTeamId,
            name = PlayerName(PersonName.of("Nový"), PersonName.of("Hráč")),
            dateOfBirth = LocalDate(2001, 2, 3),
        )

    private fun draftFor(
        match: Match,
        side: TeamSide,
    ) = LineupDraft(
        matchId = match.id,
        side = side,
        kitId = if (side == TeamSide.HOME) Fixtures.homePrimaryKit.id else Fixtures.awayPrimaryKit.id,
        captain = null,
        goalkeeper = null,
        confirmation = null,
        members =
            listOf(
                DraftedMember(
                    playerId = addedFor(match).id,
                    present = true,
                    jerseyNumber = JerseyNumber(4),
                    registrationCardPresent = false,
                    cardNumberTyped = "",
                    cardNumberWritten = null,
                    dateOfBirthTyped = "3. 2. 2001",
                    dateOfBirthWritten = "010203",
                ),
            ),
    )

    private suspend fun putBothOnTheDevice() =
        session {
            listOf(discarded, kept).forEach { match ->
                it.matches.save(match)
                it.added.add(match.id, addedFor(match))
                it.drafts.save(draftFor(match, TeamSide.HOME))
            }
            it.drafts.save(draftFor(discarded, TeamSide.AWAY))
        }

    private suspend fun discardTheFirst() = session { it.discard(assertNotNull(it.discard.contentsOf(discarded.id))) }

    @Test
    fun aDiscardedReportIsGoneWithItsDraftsAndItsPitchAddedPlayersAndTheOtherIsWhole() =
        runTest {
            putBothOnTheDevice()

            discardTheFirst()

            session {
                assertEquals(listOf(kept.id), it.matches.summaries().map { summary -> summary.id })
                assertNull(it.matches.load(discarded.id))
                assertEquals(emptyList(), it.drafts.forMatch(discarded.id), "a draft outlived its report")
                assertEquals(emptyList(), it.added.forMatch(discarded.id), "a pitch-added player outlived its report")
                // And no row of it is left in any table the report is read from.
                val id = discarded.id.value
                with(it.database.matchRecordQueries) {
                    listOf(
                        selectLineups(id).executeAsList(),
                        selectAppearances(id).executeAsList(),
                        selectGoals(id).executeAsList(),
                        selectCards(id).executeAsList(),
                        selectPowerPlays(id).executeAsList(),
                        selectPeriodBreaks(id).executeAsList(),
                        selectConfirmations(id).executeAsList(),
                        selectLineupDraftMembers(id).executeAsList(),
                    ).forEachIndexed { table, rows -> assertEquals(emptyList(), rows, "rows left in table $table") }
                }
            }

            session {
                assertEquals(kept, it.matches.load(kept.id))
                assertEquals(listOf(draftFor(kept, TeamSide.HOME)), it.drafts.forMatch(kept.id))
                assertEquals(listOf(addedFor(kept)), it.added.forMatch(kept.id))
            }
        }

    @Test
    fun whatIsRememberedPerPlayerAndPerTeamStaysOnTheDevice() =
        runTest {
            // Not the report's rows: the referee typed them once so as not to
            // type them again, at this match or the next (DECISIONS
            // 2026-10-31, "0.4.0, Gate 2").
            putBothOnTheDevice()
            session {
                it.cardNumbers.remember(PlayerId("p-baca"), "59003")
                it.datesOfBirth.remember(PlayerId("p-houzev"), LocalDate(1995, 5, 5))
                it.jerseys.setDefaultJerseyNumber(PlayerId("p-poupe"), JerseyNumber(7))
                it.followed.setFollowed(Fixtures.homeTeamId, true)
            }

            discardTheFirst()

            session {
                assertEquals(mapOf(PlayerId("p-baca") to "59003"), it.cardNumbers.all())
                assertEquals(mapOf(PlayerId("p-houzev") to LocalDate(1995, 5, 5)), it.datesOfBirth.all())
                assertEquals(mapOf(PlayerId("p-poupe") to JerseyNumber(7)), it.jerseys.overrides())
                assertEquals(setOf(Fixtures.homeTeamId), it.followed.followed())
            }
        }
}
