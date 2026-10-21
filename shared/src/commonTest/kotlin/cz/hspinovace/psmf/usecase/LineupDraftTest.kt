package cz.hspinovace.psmf.usecase

import cz.hspinovace.psmf.data.match.DraftedMember
import cz.hspinovace.psmf.data.match.LineupDraft
import cz.hspinovace.psmf.domain.Fixtures
import cz.hspinovace.psmf.domain.JerseyNumber
import cz.hspinovace.psmf.domain.KitId
import cz.hspinovace.psmf.domain.Match
import cz.hspinovace.psmf.domain.MatchId
import cz.hspinovace.psmf.domain.PlayerId
import cz.hspinovace.psmf.domain.TeamSide
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * A lineup in progress, kept and put back (prompt 15, Gate 3).
 *
 * The persistence itself is `LineupDraftPersistenceTest`, over a real
 * database. These are about what a draft means: what it puts back over the
 * lineup built from the report, what it refuses to, and that it is never a
 * lineup.
 */
class LineupDraftTest {
    private val matchId = MatchId("m1")
    private val match = Match(matchId, Fixtures.fixtureId, Fixtures.groupId)
    private var minted = 0
    private val drafts = FakeLineupDraftRepository()

    private suspend fun built(): LineupEntry =
        assertNotNull(
            BuildLineupEntry(
                TestLeague.repository(),
                FakeAddedPlayerRepository(),
                FakeRememberedDateOfBirthRepository(),
                FakeRememberedCardNumberRepository(),
            ) { "id-${++minted}" }(match),
        )

    private suspend fun reopened(entry: LineupEntry): LineupEntry = RestoreLineupDraft(drafts)(matchId, entry)

    private suspend fun keep(team: TeamLineupEntry) = SaveLineupDraft(drafts)(matchId, team)

    @Test
    fun aTeamWithNoDraftIsAsItWasBuilt() =
        runTest {
            val entry = built()

            assertEquals(entry, reopened(entry))
        }

    @Test
    fun everythingDoneToATeamComesBackOverTheLineupBuiltFromTheReport() =
        runTest {
            val entry = built()
            val edited = entry.home.halfDone(wearing = Fixtures.homeAlternateKit.id)
            keep(edited)

            val home = reopened(entry).home

            // Equal in every field: marks, roles, numbers, text half typed,
            // what the text wrote, the kit and the captain's tap.
            assertEquals(edited, home)
        }

    @Test
    fun theOtherTeamIsNotTouched() =
        runTest {
            val entry = built()
            keep(entry.home.halfDone())

            assertEquals(entry.away, reopened(entry).away)
        }

    @Test
    fun theLatestDraftOfATeamIsTheOneThatComesBack() =
        runTest {
            val entry = built()
            keep(entry.home.halfDone())
            val later =
                entry.home.halfDone().withPresence(
                    entry.home.members[2]
                        .player.id,
                    true,
                )
            keep(later)

            assertEquals(later, reopened(entry).home)
        }

    @Test
    fun aTeamThatIsNotWholeComesBackNotWholeWithItsProblemsAndIsNoLineup() =
        runTest {
            val entry = built()
            val edited = entry.home.halfDone()
            // The third player is not marked and the first two have an answer
            // in the Číslo RP column: what is missing is the only thing that is.
            val missing = edited.copy(goalkeeper = null)
            keep(missing)

            val home = reopened(entry).home

            assertEquals(listOf<LineupProblem>(LineupProblem.NoGoalkeeper(TeamSide.HOME)), home.problems())
            assertNull(home.toLineup(), "a draft with a problem became a lineup")
        }

    @Test
    fun savingTheLineupOfATeamThatIsNotWholeWritesNothing() =
        runTest {
            val matches = FakeMatchRepository()
            val entry = built()
            val missing = entry.home.halfDone().copy(goalkeeper = null)
            keep(missing)

            val restored = reopened(entry).home
            val written = SaveLineup(matches)(match, restored)

            assertEquals(match, written, "the report changed")
            assertNull(written.homeLineup)
            assertNull(matches.load(matchId), "something was saved")
        }

    @Test
    fun aWholeTeamComesBackWholeAndWritesTheSameLineupAsBefore() =
        runTest {
            val entry = built()
            val whole =
                entry.home.halfDone().withPresence(
                    entry.home.members[2]
                        .player.id,
                    true,
                )
            val finished =
                whole.withMember(
                    entry.home.members[2]
                        .player.id,
                ) { it.withCardNumberTyped("59003") }
            keep(finished)

            val restored = reopened(entry).home

            assertTrue(restored.problems().isEmpty(), "problems: ${restored.problems()}")
            assertEquals(finished.toLineup(), restored.toLineup())
        }

    @Test
    fun aPlayerTheDraftDoesNotKnowStaysAsBuilt() =
        runTest {
            val entry = built()
            val known = entry.home.members.first()
            val draft =
                LineupDraft(
                    matchId = matchId,
                    side = TeamSide.HOME,
                    kitId = entry.home.kitId,
                    captain = null,
                    goalkeeper = null,
                    confirmation = null,
                    members = listOf(drafted(known.player.id, present = true, jersey = 21)),
                )
            drafts.save(draft)

            val home = reopened(entry).home

            assertTrue(home.members.first().present)
            assertEquals(JerseyNumber(21), home.members.first().jerseyNumber)
            assertEquals(entry.home.members.drop(1), home.members.drop(1))
        }

    @Test
    fun aPlayerWhoIsNoLongerInTheSquadIsDroppedAndNotTrusted() =
        runTest {
            val entry = built()
            val gone = PlayerId("left-the-league")
            drafts.save(
                LineupDraft(
                    matchId = matchId,
                    side = TeamSide.HOME,
                    kitId = entry.home.kitId,
                    captain = gone,
                    goalkeeper = gone,
                    confirmation = null,
                    members = listOf(drafted(gone, present = true)),
                ),
            )

            val home = reopened(entry).home

            assertEquals(entry.home.members, home.members, "a player the squad does not have was added")
            assertNull(home.captain)
            assertNull(home.goalkeeper)
        }

    @Test
    fun aKitTheTeamNoLongerOwnsIsNotWorn() =
        runTest {
            val entry = built()
            drafts.save(
                LineupDraft(
                    matchId = matchId,
                    side = TeamSide.HOME,
                    kitId = KitId("repainted-in-the-spring"),
                    captain = null,
                    goalkeeper = null,
                    confirmation = null,
                    members = emptyList(),
                ),
            )

            val home = reopened(entry).home

            assertEquals(entry.home.kitId, home.kitId)
            assertNotNull(home.kit, "the team came back in a kit it does not have")
        }

    @Test
    fun aValueWrittenWithAnEmptyFieldComesBackAsWrittenAndTheFieldStaysEmpty() =
        runTest {
            // The shape a row has when it is read from a lineup already saved:
            // a date of birth written, the field empty (see BuildLineupEntry's
            // keepingWhatWasWritten). Working the value out again from the empty
            // text would erase it and leave the row without an answer.
            // A player the league has no date of birth for, as every player read
            // from psmf.cz is: only then does the referee write one.
            val built = built()
            val first =
                built.home.members
                    .first()
                    .let { it.copy(player = it.player.copy(dateOfBirth = null)) }
            val entry = built.copy(home = built.home.copy(members = listOf(first) + built.home.members.drop(1)))
            drafts.save(
                LineupDraft(
                    matchId = matchId,
                    side = TeamSide.HOME,
                    kitId = entry.home.kitId,
                    captain = null,
                    goalkeeper = null,
                    confirmation = null,
                    members =
                        listOf(
                            drafted(
                                first.player.id,
                                present = true,
                                registrationCard = false,
                                dateOfBirthTyped = "",
                                dateOfBirthWritten = "920518",
                            ),
                        ),
                ),
            )

            val row = reopened(entry).home.members.first()

            assertEquals("", row.dateOfBirthTyped)
            assertEquals("920518", assertNotNull(row.identification).value)
            assertFalse(row.registrationCardPresent)
        }

    private fun drafted(
        player: PlayerId,
        present: Boolean = false,
        jersey: Int? = null,
        registrationCard: Boolean = true,
        dateOfBirthTyped: String = "",
        dateOfBirthWritten: String? = null,
    ) = DraftedMember(
        playerId = player,
        present = present,
        jerseyNumber = JerseyNumber.orNull(jersey),
        registrationCardPresent = registrationCard,
        cardNumberTyped = "",
        cardNumberWritten = null,
        dateOfBirthTyped = dateOfBirthTyped,
        dateOfBirthWritten = dateOfBirthWritten,
    )
}
