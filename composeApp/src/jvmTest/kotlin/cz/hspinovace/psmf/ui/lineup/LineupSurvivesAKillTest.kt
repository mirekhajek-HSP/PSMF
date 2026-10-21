package cz.hspinovace.psmf.ui.lineup

import cz.hspinovace.psmf.data.match.LineupDraft
import cz.hspinovace.psmf.data.match.LineupDraftRepository
import cz.hspinovace.psmf.data.match.MatchRepository
import cz.hspinovace.psmf.data.seed.LeagueGroup
import cz.hspinovace.psmf.domain.JerseyNumber
import cz.hspinovace.psmf.domain.Kit
import cz.hspinovace.psmf.domain.KitId
import cz.hspinovace.psmf.domain.Match
import cz.hspinovace.psmf.domain.MatchId
import cz.hspinovace.psmf.domain.PersonName
import cz.hspinovace.psmf.domain.Player
import cz.hspinovace.psmf.domain.PlayerId
import cz.hspinovace.psmf.domain.PlayerName
import cz.hspinovace.psmf.domain.ReportProblem
import cz.hspinovace.psmf.domain.Team
import cz.hspinovace.psmf.domain.TeamId
import cz.hspinovace.psmf.domain.TeamSide
import cz.hspinovace.psmf.domain.reportProblems
import cz.hspinovace.psmf.ui.UiTestData
import cz.hspinovace.psmf.ui.fixtures.GatedMatchRepository
import cz.hspinovace.psmf.ui.fixtures.InMemoryLeague
import cz.hspinovace.psmf.usecase.AddPlayerAtThePitch
import cz.hspinovace.psmf.usecase.AddPlayerToLineup
import cz.hspinovace.psmf.usecase.BuildLineupEntry
import cz.hspinovace.psmf.usecase.LineupProblem
import cz.hspinovace.psmf.usecase.NewId
import cz.hspinovace.psmf.usecase.NewPlayerRequest
import cz.hspinovace.psmf.usecase.RememberCardNumber
import cz.hspinovace.psmf.usecase.RememberDateOfBirth
import cz.hspinovace.psmf.usecase.RestoreLineupDraft
import cz.hspinovace.psmf.usecase.SaveLineup
import cz.hspinovace.psmf.usecase.SaveLineupDraft
import cz.hspinovace.psmf.usecase.SquadMemberEntry
import cz.hspinovace.psmf.usecase.TeamLineupEntry
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/**
 * A lineup in progress, and the app killed (prompt 15, Gate 3).
 *
 * In 0.3.0 a team was written down only once it was whole -- somebody present,
 * a captain, a goalkeeper and a `Číslo RP` in every row -- so marks made
 * before that lived in the view model alone, and a process that died took
 * them. These tests open the lineup, work on it, **never touch that view model
 * again** and open a new one over what the repositories hold. That is a kill:
 * the view model and everything it kept in memory are gone, the database is
 * not.
 *
 * Two kinds of test. Those about what comes back are the defect. Those about
 * what is *not* written are the non-negotiable: a lineup that is not whole is
 * never taken for one, so the console, the export and the signatures read the
 * report exactly as before.
 */
@OptIn(ExperimentalCoroutinesApi::class, ExperimentalTime::class)
class LineupSurvivesAKillTest {
    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private val novak = player("novak", "Novák", "Jan", 9)
    private val poupe = player("poupe", "Poupě", "Petr", 11)
    private val svoboda = player("svoboda", "Svoboda", "Karel", 7)
    private val dvorak = player("dvorak", "Dvořák", "Lukáš", 5)
    private val hruby = player("hruby", "Hrubý", "Tomáš", 4, UiTestData.awayTeamId)

    private fun player(
        ref: String,
        surname: String,
        first: String,
        number: Int,
        team: TeamId = UiTestData.homeTeamId,
    ) = Player(
        id = PlayerId(ref),
        ref = ref,
        teamId = team,
        name = PlayerName(PersonName.of(surname), PersonName.of(first)),
        rpNumber = null,
        dateOfBirth = null,
        birthNumber = null,
        defaultJerseyNumber = JerseyNumber(number),
    )

    private val blue = KitId("k1")
    private val white = KitId("k1b")

    /** The home team owns two kits, so that choosing the second is something to lose. */
    private val homeTeam =
        Team(
            UiTestData.homeTeamId,
            "kominici",
            UiTestData.groupId,
            "Kominíci",
            listOf(Kit(blue, "modrá"), Kit(white, "bílá")),
        )

    private val group =
        LeagueGroup(
            season = UiTestData.season,
            group = UiTestData.group,
            teams = listOf(homeTeam, UiTestData.awayTeam),
            players = listOf(novak, poupe, svoboda, dvorak, hruby),
            fixtures = listOf(UiTestData.fixture),
            venues = listOf(UiTestData.venue),
        )

    private val league = InMemoryLeague(listOf(group))
    private val added = InMemoryAddedPlayers()
    private val rememberedDates = InMemoryRememberedDates()
    private val rememberedNumbers = InMemoryRememberedNumbers()
    private val drafts = InMemoryLineupDrafts()
    private val matchId = MatchId("m1")
    private val untouched = Match(matchId, UiTestData.fixtureId, UiTestData.groupId)
    private val matches = GatedMatchRepository(listOf(untouched))
    private var counter = 0

    private val captainTapped = Instant.parse("2026-08-31T18:55:00Z")

    /**
     * A view model over what the device holds. Called again after the work, it
     * is the lineup reopened by a fresh process: only the repositories carry
     * over.
     */
    private fun openTheLineup(
        onDevice: MatchRepository = matches,
        draftsOnDevice: LineupDraftRepository = drafts,
    ): LineupViewModel {
        val newId = NewId { "id-${counter++}" }
        val save = SaveLineup(onDevice)
        return LineupViewModel(
            matchId = matchId,
            matches = onDevice,
            buildLineupEntry = BuildLineupEntry(league, added, rememberedDates, rememberedNumbers, newId),
            restoreLineupDraft = RestoreLineupDraft(draftsOnDevice),
            saveLineup = save,
            saveLineupDraft = SaveLineupDraft(draftsOnDevice),
            addPlayerToLineup = AddPlayerToLineup(AddPlayerAtThePitch(added, newId), save, newId),
            rememberDateOfBirth = RememberDateOfBirth(rememberedDates),
            rememberCardNumber = RememberCardNumber(rememberedNumbers),
            clock =
                object : Clock {
                    override fun now(): Instant = captainTapped
                },
        )
    }

    private fun LineupViewModel.home(): TeamLineupEntry = assertNotNull(state.value.entry).home

    private fun TeamLineupEntry.member(player: Player): SquadMemberEntry = members.single { it.player.id == player.id }

    private fun TeamLineupEntry.presentIds(): Set<PlayerId> = present.map { it.player.id }.toSet()

    private fun LineupViewModel.mark(vararg players: Player) =
        players.forEach {
            onEvent(LineupEvent.PresenceToggled(it.id))
        }

    // ---------------------------------------------------------------------
    // What comes back
    // ---------------------------------------------------------------------

    @Test
    fun threePlayersMarkedAndACardNumberTypedAreThereWhenTheLineupIsReopened() =
        runTest {
            val first = openTheLineup()
            first.mark(novak, poupe, svoboda)
            first.onEvent(LineupEvent.CardNumberTyped(novak.id, "59001"))

            val home = openTheLineup().home()

            assertEquals(setOf(novak.id, poupe.id, svoboda.id), home.presentIds(), "the marks did not survive")
            assertEquals("59001", home.member(novak).cardNumberTyped)
        }

    @Test
    fun theCaptainTheGoalkeeperTheKitAndTheCaptainsTapAreThereToo() =
        runTest {
            val first = openTheLineup()
            first.mark(novak, poupe, svoboda)
            first.onEvent(LineupEvent.CaptainChosen(novak.id))
            first.onEvent(LineupEvent.GoalkeeperChosen(poupe.id))
            first.onEvent(LineupEvent.KitSelected(white))
            first.onEvent(LineupEvent.CaptainConfirms)

            val home = openTheLineup().home()

            assertEquals(novak.id, home.captain, "the captain was lost")
            assertEquals(poupe.id, home.goalkeeper, "the goalkeeper was lost")
            assertEquals(white, home.kitId, "the kit that was chosen was lost")
            val tap = assertNotNull(home.confirmation, "the captain's tap was lost")
            assertEquals(captainTapped, tap.at, "the tap keeps the time it was given")
            assertEquals("Novák Jan", tap.by.value)
        }

    @Test
    fun shirtNumbersAndHalfTypedNumbersAndDatesAreThereToo() =
        runTest {
            // Half-typed, because only a whole number or date is remembered on
            // the device and offered again: these can come back from nowhere else.
            val first = openTheLineup()
            first.mark(novak, poupe, svoboda)
            first.onEvent(LineupEvent.JerseyNumberChanged(novak.id, "10"))
            first.onEvent(LineupEvent.JerseyNumberChanged(poupe.id, "12"))
            first.onEvent(LineupEvent.CardNumberTyped(svoboda.id, "59"))
            first.onEvent(LineupEvent.RegistrationCardToggled(poupe.id))
            first.onEvent(LineupEvent.DateOfBirthTyped(poupe.id, "18.5."))

            val home = openTheLineup().home()

            assertEquals(JerseyNumber(10), home.member(novak).jerseyNumber)
            assertEquals(JerseyNumber(12), home.member(poupe).jerseyNumber)
            assertEquals(JerseyNumber(7), home.member(svoboda).jerseyNumber, "an untouched number is the player's own")
            assertEquals("59", home.member(svoboda).cardNumberTyped, "a number half typed was lost")
            assertTrue(home.member(svoboda).cardNumberRejected, "half a number is still not a usable one")
            assertFalse(home.member(poupe).registrationCardPresent, "'Nemá průkaz' was lost")
            assertEquals("18.5.", home.member(poupe).dateOfBirthTyped, "a date half typed was lost")
        }

    @Test
    fun aWholeNumberAndADateAreThereAndReadAsWhatWasTyped() =
        runTest {
            val first = openTheLineup()
            first.mark(novak, poupe)
            first.onEvent(LineupEvent.CardNumberTyped(novak.id, "59001"))
            first.onEvent(LineupEvent.RegistrationCardToggled(poupe.id))
            first.onEvent(LineupEvent.DateOfBirthTyped(poupe.id, "18.5.1992"))

            val home = openTheLineup().home()

            assertEquals("59001", assertNotNull(home.member(novak).identification).value)
            assertEquals("920518", assertNotNull(home.member(poupe).identification).value)
        }

    @Test
    fun whatWasTypedForSomeoneTheMarkWasTakenBackFromIsThereToo() =
        runTest {
            val first = openTheLineup()
            first.mark(novak)
            first.onEvent(LineupEvent.CardNumberTyped(novak.id, "59"))
            first.onEvent(LineupEvent.JerseyNumberChanged(novak.id, "14"))
            first.mark(novak)

            val home = openTheLineup().home()

            assertFalse(home.member(novak).present)
            assertEquals("59", home.member(novak).cardNumberTyped, "typed text of an unmarked row was lost")
            assertEquals(JerseyNumber(14), home.member(novak).jerseyNumber)
        }

    @Test
    fun eachTeamKeepsItsOwn() =
        runTest {
            val first = openTheLineup()
            first.mark(novak)
            first.onEvent(LineupEvent.SideSelected(TeamSide.AWAY))
            first.mark(hruby)
            first.onEvent(LineupEvent.CaptainChosen(hruby.id))

            val reopened = openTheLineup()
            val entry = assertNotNull(reopened.state.value.entry)

            assertEquals(setOf(novak.id), entry.home.presentIds())
            assertNull(entry.home.captain)
            assertEquals(setOf(hruby.id), entry.away.presentIds())
            assertEquals(hruby.id, entry.away.captain)
        }

    @Test
    fun aPlayerAddedAtThePitchWhileTheLineupIsNotWholeIsStillMarked() =
        runTest {
            val first = openTheLineup()
            first.mark(novak)
            first.onEvent(LineupEvent.AddPlayerOpened)
            first.onEvent(
                LineupEvent.NewPlayerEdited(
                    NewPlayerRequest(
                        teamId = UiTestData.homeTeamId,
                        firstName = "Marek",
                        surname = "Černý",
                        cardNumber = "59123",
                        jerseyNumber = 21,
                    ),
                ),
            )
            first.onEvent(LineupEvent.NewPlayerSubmitted)

            val home = openTheLineup().home()

            val newcomer = home.members.single { it.addedAtThePitch }
            assertTrue(newcomer.present, "the player added at the pitch came back unmarked")
            assertEquals("59123", newcomer.cardNumberTyped)
            assertEquals(JerseyNumber(21), newcomer.jerseyNumber)
            assertTrue(home.member(novak).present)
        }

    @Test
    fun aLineupThatIsEditedFurtherAfterTheKillGoesOnFromWhereItWas() =
        runTest {
            val first = openTheLineup()
            first.mark(novak, poupe)

            val second = openTheLineup()
            second.mark(svoboda)
            second.onEvent(LineupEvent.CaptainChosen(novak.id))

            val home = openTheLineup().home()
            assertEquals(setOf(novak.id, poupe.id, svoboda.id), home.presentIds())
            assertEquals(novak.id, home.captain)
        }

    // ---------------------------------------------------------------------
    // What is not written: the non-negotiable
    // ---------------------------------------------------------------------

    @Test
    fun aLineupThatIsNotWholeIsNotWrittenDownAsOne() =
        runTest {
            val problemsBefore = untouched.reportProblems()
            val first = openTheLineup()
            first.mark(novak, poupe, svoboda)
            first.onEvent(LineupEvent.CardNumberTyped(novak.id, "59001"))
            first.onEvent(LineupEvent.CardNumberTyped(poupe.id, "59002"))
            first.onEvent(LineupEvent.CardNumberTyped(svoboda.id, "59003"))
            first.onEvent(LineupEvent.CaptainChosen(novak.id))
            // No goalkeeper: this is the last thing missing, and it is missing.

            openTheLineup()

            val written = assertNotNull(matches.load(matchId))
            assertNull(written.homeLineup, "an incomplete lineup was written down as the team's lineup")
            assertEquals(untouched, written, "the report changed though the lineup is not whole")
            assertEquals(problemsBefore, written.reportProblems(), "the export would list something else")
            assertTrue(ReportProblem.MissingLineup(TeamSide.HOME) in written.reportProblems())
            assertFalse(ReportProblem.LineupNotConfirmed(TeamSide.HOME) in written.reportProblems())
        }

    @Test
    fun theScreenListsWhatIsStillMissingAfterTheKill() =
        runTest {
            val first = openTheLineup()
            first.mark(novak, poupe)
            first.onEvent(LineupEvent.CardNumberTyped(novak.id, "59001"))
            first.onEvent(LineupEvent.CardNumberTyped(poupe.id, "59002"))
            first.onEvent(LineupEvent.CaptainChosen(novak.id))

            val reopened = openTheLineup()

            assertEquals(
                listOf<LineupProblem>(
                    LineupProblem.NoGoalkeeper(TeamSide.HOME),
                    LineupProblem.NobodyPresent(TeamSide.AWAY),
                ),
                reopened.state.value.problems,
                "the reopened lineup does not say what is missing",
            )
        }

    @Test
    fun aLineupThatWasWholeAndIsEditedIntoOneThatIsNotKeepsTheWholeOneForTheReport() =
        runTest {
            val first = openTheLineup()
            first.mark(novak, poupe)
            first.onEvent(LineupEvent.CardNumberTyped(novak.id, "59001"))
            first.onEvent(LineupEvent.CardNumberTyped(poupe.id, "59002"))
            first.onEvent(LineupEvent.CaptainChosen(novak.id))
            first.onEvent(LineupEvent.GoalkeeperChosen(poupe.id))
            val wholeOnDisk = assertNotNull(matches.load(matchId)?.homeLineup, "the lineup should be whole by now")

            // The captain is sent home: the screen has a problem, the report keeps
            // the last lineup that was whole (as it did in 0.3.0).
            first.mark(novak)
            assertNull(first.home().captain)

            val reopened = openTheLineup()

            assertNull(reopened.home().captain, "the screen came back as it was before the last edit")
            assertEquals(setOf(poupe.id), reopened.home().presentIds())
            assertEquals(
                wholeOnDisk,
                matches.load(matchId)?.homeLineup,
                "the report no longer has the last whole lineup",
            )
        }

    // ---------------------------------------------------------------------
    // What must go on working
    // ---------------------------------------------------------------------

    @Test
    fun aWholeLineupIsWholeAfterAKillAndKeepsTheIdsGoalsPointAt() =
        runTest {
            val first = openTheLineup()
            first.mark(novak, poupe)
            first.onEvent(LineupEvent.CardNumberTyped(novak.id, "59001"))
            first.onEvent(LineupEvent.CardNumberTyped(poupe.id, "59002"))
            first.onEvent(LineupEvent.CaptainChosen(novak.id))
            first.onEvent(LineupEvent.GoalkeeperChosen(poupe.id))
            val saved = assertNotNull(matches.load(matchId)?.homeLineup)

            val home = openTheLineup().home()

            assertTrue(home.problems().isEmpty(), "a whole lineup came back with problems: ${home.problems()}")
            assertEquals(
                saved.appearances.associate { it.playerId to it.id },
                home.present.associate { it.player.id to it.appearanceId },
                "goals and cards point at these ids; reopening must not mint new ones",
            )
        }

    @Test
    fun nothingIsMarkedUntilSomeoneIsTapped() =
        runTest {
            val home = openTheLineup().home()

            assertEquals(emptySet(), home.presentIds())
            assertEquals(blue, home.kitId, "the team's first kit is the default")
        }

    // ---------------------------------------------------------------------
    // The order of the writes
    // ---------------------------------------------------------------------

    @Test
    fun theDraftIsWrittenBeforeTheLineupSoItIsNeverOlderThanIt() =
        runTest {
            val log = mutableListOf<String>()
            val first =
                openTheLineup(
                    onDevice = Recording(matches) { log += "report" },
                    draftsOnDevice = RecordingDrafts(drafts) { log += "draft" },
                )
            first.mark(novak, poupe)
            first.onEvent(LineupEvent.CardNumberTyped(novak.id, "59001"))
            first.onEvent(LineupEvent.CardNumberTyped(poupe.id, "59002"))
            first.onEvent(LineupEvent.CaptainChosen(novak.id))
            log.clear()

            // The edit that makes the block whole: the last thing it needs.
            first.onEvent(LineupEvent.GoalkeeperChosen(poupe.id))

            assertEquals(listOf("draft", "report"), log, "a lineup was written before the draft that goes with it")
        }

    @Test
    fun anEditThatLeavesTheLineupIncompleteWritesTheDraftAndNothingToTheReport() =
        runTest {
            val log = mutableListOf<String>()
            val first =
                openTheLineup(
                    onDevice = Recording(matches) { log += "report" },
                    draftsOnDevice = RecordingDrafts(drafts) { log += "draft" },
                )

            first.mark(novak)

            assertEquals(listOf("draft"), log)
        }

    @Test
    fun twoQuickEditsAreWrittenInTheOrderTheyWereMadeEvenWhenTheFirstWriteIsSlow() =
        runTest {
            // The writes run off the main thread in the app, so without a queue
            // the one that finishes last is what the disk keeps. Here the first
            // write is held while the second edit is made.
            val slow = CompletableDeferred<Unit>()
            var held = false
            val inner = InMemoryLineupDrafts()
            val slowFirst =
                object : LineupDraftRepository by inner {
                    override suspend fun save(draft: LineupDraft) {
                        if (!held) {
                            held = true
                            slow.await()
                        }
                        inner.save(draft)
                    }
                }
            val first = openTheLineup(draftsOnDevice = slowFirst)

            first.mark(novak)
            first.mark(poupe)
            slow.complete(Unit)

            assertEquals(
                setOf(novak.id, poupe.id),
                openTheLineup(draftsOnDevice = inner).home().presentIds(),
                "the draft on disk is the older of the two edits",
            )
        }
}

/** The report's repository, telling [onSave] each time it is written. */
private class Recording(
    private val inner: MatchRepository,
    private val onSave: () -> Unit,
) : MatchRepository by inner {
    override suspend fun save(match: Match) {
        onSave()
        inner.save(match)
    }
}

private class RecordingDrafts(
    private val inner: LineupDraftRepository,
    private val onSave: () -> Unit,
) : LineupDraftRepository by inner {
    override suspend fun save(draft: LineupDraft) {
        onSave()
        inner.save(draft)
    }
}

/** What the device holds of the drafts: it outlives every view model, as the database does. */
internal class InMemoryLineupDrafts : LineupDraftRepository {
    private val stored = mutableMapOf<Pair<MatchId, TeamSide>, LineupDraft>()

    override suspend fun save(draft: LineupDraft) {
        stored[draft.matchId to draft.side] = draft
    }

    override suspend fun forMatch(matchId: MatchId): List<LineupDraft> = stored.values.filter { it.matchId == matchId }
}
