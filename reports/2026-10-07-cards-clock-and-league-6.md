# Cards, the clock, league 6 — build 0.2.0 (2)

**Session date:** 2026-10-07
**Brief:** `prompts/10-cards-clock-and-league-6.md`
**Repository:** `~/dev/psmf-app` (WSL Ubuntu), branch `main`
**Where it ran:** the Claude desktop app on the Windows host, driving the
`psmf-sandbox` image through a long-lived container
(`docker compose run -d --name psmf-work sandbox sleep infinity`, then
`docker exec … ./gradlew`). Same image, same Gradle cache volume as the usual
in-container session; commits made from WSL with the owner's git identity.
Nothing pushed.
**Outcome:** Gates 1 and 2 met. Gates 3–4 pending.

> **What a referee will notice in 0.2.0:** *to be written when the last gate
> closes.*

---

## 1 · Status at a glance

| | Before (`8bd3c11`) | After |
|---|---|---|
| Second yellow | saved a plain yellow; player stayed on, no power play, report showed two yellows and no red | **one action records ŽK + ČK (2. ŽK)** at the same minute, row shows *Vyloučen*, power play starts, one Undo takes both back |
| Red → 2. ŽK on the sheet | offered for anyone; for a booked player recorded yellow + red, season count 1 | offered **only for a booked player** (and for a named person); records the second yellow too, season count 0 |
| `2. ŽK` in the files | only if the free-text reason happened to say so | **from the stored kind**, TXT/CSV/JSON, reason kept beside it; JSON has `"dismissal"` |
| Power play | ten minutes of wall clock; expired during half-time; started after the final whistle; started for a named person | **ten minutes of play**, held through the interval; none after the whistle or for a named person; nothing shown counting after the whistle |
| Second-half minute | continued from the first half's added time (32´ after a 32-minute half) | **restarts at 30´** |
| `30´+` ordering | after 30´ | **after 29´, before the second half's 30´** — timeline, log, report |
| Undo | last by minute (a card typed late was safe; a same-minute goal was not) | **last recorded**, persisted |
| League player with no RP, no date of birth | could not exist: the league file would not load | **in the squad**; the referee types the date of birth **in their lineup row**, the app writes YYMMDD, the date is remembered and offered next time |
| Schema | version 4 | **version 6** (`4.sqm` three nullable columns; `5.sqm` one new table) |
| Tests (shared JVM · Android host · composeApp) | 388 · 349 · 182 | Gate 1: 415 · 374 · 187 · Gate 2: **428 · 383 · 193** |

---

## 2 · Baseline

`./gradlew build :shared:allTests detekt` at `8bd3c11`: **BUILD SUCCESSFUL**.
The first run came from Gradle's build cache, so the three test tasks were
forced to execute (`--no-build-cache` after `clean*Test`), and counted from
their JUnit XML:

| Suite | Tests | Failures |
|---|---|---|
| `:shared:jvmTest` | 388 | 0 |
| `:shared:testAndroidHostTest` | 349 | 0 |
| `:composeApp:jvmTest` | 182 | 0 |

Matches the counts `prompts/10` quotes.

---

## 3 · Gate 1 — cards and the clock

### Method

All the new tests were written first, against `8bd3c11`'s API so that they
failed by assertion rather than by not compiling, and run there: **24 failed,
1 guard passed** (the list is below, with each failure message as it came
out). Then the fixes, then the whole suite. The defect tests live together
in `shared/src/commonTest/.../usecase/CardAndClockReviewTest.kt`, one class
per defect, plus one persistence test and three UI tests where the defect is
on disk or on screen.

Two of the first draft's tests were not good enough and were fixed before
the red run was recorded: the one-undo test passed on `8bd3c11` for the
wrong reason (it never recorded the red, so undoing one yellow "worked") and
now asserts the dismissal first; and the switch-back UI test failed on input
injection, not its assertion — see *Findings*, the locale helper.

### 1 · A second yellow sends the player off, as one action

| Test (failed on `8bd3c11`) | What it said there |
|---|---|
| `SecondYellowTest.aBookedPlayerCardedAgainIsShownSentOffWithTheirSidePlayingShort` | expected ŽK + ČK at 40´, got a lone YellowCard |
| `SecondYellowTest.oneUndoTakesTheSecondYellowAndTheDismissalBackTogether` | the second yellow did not send them off: expected 3 cards, was 2 |
| `SecondYellowTest.choosingRedAndSecondYellowForABookedPlayerAlsoCountsNothingThisSeason` | expected 2 yellows, was 1 |
| `SecondYellowTest.aRedForAPlayerWithNoYellowIsStraight` | expected STRAIGHT, was SECOND_YELLOW |
| `ConsoleScreenTest.beforeSavingTheButtonItselfSaysThisYellowSendsThePlayerOff` | no *Vyloučit (2. ŽK)* on screen |
| `ConsoleScreenTest.aRedForAPlayerWithNoYellowOffersOnlyAStraightRed` | *2. ŽK* chip present |
| guard: `SecondYellowTest.guardANamedPersonGetsNoAutomaticSecondYellow` | passed, as it should |

**Fix.** `LogCard` reads from the match whether the player already has a
yellow and no red. If so, a yellow — or a red marked 2. ŽK — records the
second `YellowCard` **and** a `RedCard(SECOND_YELLOW)`, same minute, same
reason, one shared recording number (defect 5), and the power play. No path
leaves a player on two yellows with no red: `LogCard` is the only writer of
cards, and Undo removes the pair together. `yellowsAccumulatedBy` is
unchanged and returns 0 for that player, as it already did for
yellow + yellow + red.

`CardDraft` takes the booked fact as an argument (`booked`), never as a
field: the match is the source. `offersSecondYellowKind`, `dismissalKind`
and `sendsOffForSecondYellow` derive from it; a red for an unbooked player
*is* straight without being asked. Named persons keep both kinds and no
automatic second yellow, as briefed.

**Judgement call — how the sheet says it before saving.** The save button
stops reading *Uložit* and reads **Vyloučit (2. ŽK)**, bold, in the error
colour; the line above it, which used to say the same thing and change
nothing, now says what saving records (*"Uložení zapíše druhou ŽK i
červenou kartu (2. ŽK) a hráč bude vyloučen."*). The button is where the
thumb is. No second confirmation: one tap sends off, one Undo restores,
which is the cheaper correction at a pitch. Rendered and looked at from a
throwaway JVM Compose capture before committing (both the booked-yellow
sheet and the booked-red sheet). Recorded in `DECISIONS.md`.

### 2 · The report says 2. ŽK from the stored kind

| Test (failed on `8bd3c11`) | What it said there |
|---|---|
| `SecondYellowOnTheReportTest.aSecondYellowRedReadsTwoZkInEveryFileWhateverTheRefereeTyped` | TXT red block did not contain `2. ŽK` |
| `SecondYellowOnTheReportTest.aStraightRedSaysSoInTheJsonAndNeverReadsTwoZk` | no `"dismissal"` in the JSON |
| `ConsoleScreenTest.switchingFromSecondYellowToStraightLeavesNoTwoZkInTheReason` | reason was `2. ŽK` after switching back to straight |

**Fix.** `ZouCard` gains `dismissal: ZouDismissal?`, set by `BuildZouReport`
from `RedCard.dismissal`, serialised in Czech like every other value:
`"2. ŽK"` or `"přímá ČK"` (new `ZouLabels.Cards.STRAIGHT_RED` — the form has
no notation for a straight red, so this is the app's Czech, used only where
the kind must be named). `ZouCard.written` is what TXT and CSV print after
the name: `2. ŽK` for a second-yellow red, with the referee's reason in
brackets when it is anything other than `2. ŽK` itself; the reason verbatim
otherwise. JSON keeps `reason` exactly as typed. Still one encoder —
`ZouDocument.bytes()` is untouched.

At the source: choosing *2. ŽK* on the sheet no longer writes into the
reason, so there is nothing to leave behind when switching back.

**Residual, not fixed:** a 0.1.0 match whose straight red carries the
leftover reason `2. ŽK` still prints `2. ŽK` as that red's reason — the
report does not rewrite what a referee wrote. New sheets cannot produce it.

### 3 · The power play is ten minutes of play

| Test (failed on `8bd3c11`) | What it said there |
|---|---|
| `PowerPlayIsPlayTimeTest.aRedAtTwentyFiveStillLeavesTheSideShortWhenTheSecondHalfKicksOff` | expected 1 short, was 0 |
| `…aRedShownDuringTheIntervalStartsCountingWhenPlayResumes` | expected 1, was 0 |
| `…aRedAfterTheFinalWhistleLeavesNobodyShort` | a power play was started |
| `…aRedWrittenAtSixtyPlusLeavesNobodyShortEvenBeforeTheMatchIsEnded` | a power play was started |
| `…aRedToSomeoneNotInTheLineupLeavesNobodyShort` | a power play was started |
| `…nothingCountsDownOnTheScoreboardOnceTheMatchIsOver` | one still running after FINISHED |
| `…aRedLoggedLateRunsFromTheMinuteTheRefereeWrote` | expected 0 at 30´, was 1 |
| `…aFirstHalfRedLoggedInTheSecondHalfStillSkipsTheInterval` | expected 0, was 1 |

**Fix.** A new value, `PlayClock(kickoffAt, periodBreaks)` in
`MatchClock.kt`, is the one place play is counted; `Match.clock` and
`ConsoleEntry.clock` both build it, which also removes `ConsoleEntry`'s
private copy of the elapsed-time loop. Elapsed play can now be asked about
any instant, not only "now", which is what measuring from a start in the
past needs. `PowerPlay` keeps its stored shape — side, start instant, minute
— so **no column changed meaning**, but `remainingAt`/`isRunningAt` now take
the clock and count play since the start; `endsAt` is gone, because when a
power play ends depends on breaks that may not have happened yet. `LogCard`
starts none for a named person, none when the match is `FINISHED` or
`CONFIRMED`, none for a red written `60´+`; `powerPlaysRunningAt` returns
nothing once the whistle has gone, so the scoreboard shows nothing.

A 0.1.0 power play read back under 0.2.0 gets the new arithmetic for free:
its stored instant was the save moment, which is a valid start.

**Judgement call — a red written earlier than the clock.** The ten minutes
run **from the minute written**, at its start, placed on the clock period by
period (`PlayClock.instantOfMinute`); when the written minute is the one the
clock shows, from the moment of saving. Mapping turned out clean because the
period boundaries are recorded instants: a played minute belongs to the
period whose range holds it (capped at the latest started period, clamped to
that period's end); `30´+` maps to the end of the first period; a minute
later than the clock is clamped to now. The cost is a typo: `4` for `40`
gives a power play already over, and the only sign is its absence. Recorded
in `DECISIONS.md` with the one-line reversal.

### 4 · The second period starts at 30´

| Test (failed on `8bd3c11`) | What it said there |
|---|---|
| `SecondHalfStartsAtThirtyTest.theSecondHalfKicksOffAtThirtyWhateverTheFirstHalfAdded` | expected 30´, was 32´ |
| `…firstHalfAddedTimeComesBeforeTheSecondHalfsThirtiethMinuteInTheLog` | `[30´, 30´+]` |
| `…firstHalfAddedTimeComesBeforeTheSecondHalfsThirtiethMinuteOnTheReport` | `[31´, 30´+]` |
| `…theHalfTimeScoreIsTheFirstHalfsGoalsAndNoneFromTheSecond` | 1:1, expected 1:0 |

**Fix.** `PlayClock.minuteAt`: period *k* shows (*k*−1) × `halfLengthMinutes`
plus whole minutes into the period. `Minute.HalfTime.sortKey` moves from
61 to 59 — after 29´, before the second half's 30´ — which fixes the
timeline, the log (newest first) and `scoreAtEndOfFirstPeriod` in one
place. The timeline now breaks ties within a minute by recording order. The
report's two card blocks are sorted by minute (stably, so one minute keeps
its recording order); goals already were, by construction.

**Assertions of the old behaviour, changed:**

- `MatchClockTest` (`PeriodBreakTest`)
  `startingTheNextPeriodResumesFromWhereTheBreakBegan` → renamed
  `…ResumesPlayFromTheBreakAndTheMinuteFromThirty`. Its elapsed-play
  assertions (32 min at the restart, 57 after 25 more) are **kept** — that
  much play happened, and the power play needs it — the comment "the second
  period continues the same sixty minutes" is gone, and it now asserts the
  minute: `30´` at the restart, `55´` 25 minutes later.
- `ConsoleEntryTest.onceTheSecondPeriodIsRunningAddedTimeIsAnOrdinaryMinuteAgain`:
  **it did not actually pin the old rule** — its first half ended at
  exactly 30:00, where the old and new rules agree. Moved to a 32-minute
  first half and a 3-minute break; it now asserts `64´` where the old rule
  gives `66´`.
- `MinuteTest.halfTimeSortsAfterMinuteThirtyAndBeforeMinuteThirtyOne` →
  `halfTimeSortsAfterMinuteTwentyNineAndBeforeTheSecondHalfsThirty`.

### 5 · Undo takes back the last thing recorded

| Test (failed on `8bd3c11`) | What it said there |
|---|---|
| `UndoByRecordingOrderTest.aGoalScoredInTheSameMinuteAsACardIsWhatUndoTakesBack` | the goal is still there |
| `…aCardWrittenUpLateIsWhatUndoTakesBackEvenAfterALaterGoal` | the goal went instead of the card |
| `MatchPersistenceTest.undoAfterTheAppRestartsStillTakesBackTheLastThingRecorded` | Undo took the card, not the goal recorded after it |
| (the second-yellow undo, under 1) | |

**Fix.** Every `GoalEvent`, `YellowCard`, `RedCard` and `PowerPlay` carries
`sequence: Int?` — the number of the recording action that produced it,
from 1 within the match, `Match.nextSequence()` for the next. A second
yellow's two cards and its power play share one. `UndoLastEvent` removes
everything with the highest number. Events with no number are older than
any that have one and, among themselves, go by the timeline — exactly what
0.1.0's Undo did. Added with the fix, as a guard: undoing a goal no longer
touches an affirmed *Bez karet* (a first draft of the new Undo did — caught
reading the diff, not by a test).

### The migration

**Version 4 → 5**, `shared/src/commonMain/sqldelight/cz/hspinovace/psmf/db/4.sqm`:

```sql
ALTER TABLE goal_record ADD COLUMN sequence INTEGER;
ALTER TABLE card_record ADD COLUMN sequence INTEGER;
ALTER TABLE power_play_record ADD COLUMN sequence INTEGER;
```

Followed the README in order: `generateCommonMainPsmfDatabaseSchema` run
**before** touching a `.sq` file (it rewrote `4.db` byte-identical, sha1
`20c870bf…`, so `4.db` already described the shipped schema); `.sq` edited;
`4.sqm` written; the task run again to record `5.db`;
`verifyCommonMainPsmfDatabaseMigration` green under `check`.

**What an existing match gets for its old rows: `NULL`.** Version 4 kept
no recording order, and the column says so rather than inventing one. The
app reads `NULL` as "recorded before 0.2.0": older than anything recorded
since, ordered among themselves by the timeline. So a match carried across
the update undoes its old events as 0.1.0 would have, and its new ones by
recording order, newest first. `SchemaMigrationTest.aMatchFromZeroPointOneKeepsEveryEventAndUndoesItsOldOnesAsItAlwaysDid`
proves it from the recorded `4.db`: every row survives, Undo takes the 49´
red and its power play, a goal recorded after the update is number 1 and
is the next thing Undo takes.

`SchemaMigrationTest`'s helper had to change. It wrote "old" rows with
today's repository into an old file, which only worked while no migration
had added a column to a table the repository writes; `4.sqm` adds three.
It now writes into a scratch database at the current version and copies
across **only the columns the old table has**. Generic, so the next column
migration does not hit the same wall.

### The stale text

`CLAUDE.md`, the header of `MatchClock.kt` and the KDoc on `Match.kickoffAt`
restated as **no stoppage during play, a recorded interval between
periods**. "No pause, stop, resume or adjust operation during play … and
there must not be one" is kept word for word in strength. The same wording
went into `TECH_STACK.md` §4, `DEMO_SCOPE.md` (the power play line), the
golblok table row in `CLAUDE.md`, and the KDoc of `StartMatch`,
`StartNextPeriod` (which still said "continues the same sixty minutes") and
`ConsoleEntry.minuteAt`.

### Gate 1 against its criteria

| Criterion | |
|---|---|
| Each of the five: failing test, fix, passing run | ✅ 24 failed on `8bd3c11` (above); all pass after |
| The two judgement calls, answered | ✅ above, and `DECISIONS.md` 2026-10-07 |
| The migration: version, what old rows get | ✅ 4 → 5; `NULL`, read as pre-0.2.0 |
| build, allTests, detekt green; counts against the baseline | ✅ clean uncached run: **415 · 374 · 187** (+27 · +25 · +5) |
| Commit | ✅ one commit, this report's first version included |

Detekt's first verdict was four findings (three `ReturnCount` in the new
clock code, one `NestedBlockDepth` in the migration test helper), fixed by
restructuring, not by baseline. The baseline is still empty.

---

## 4 · Gate 2 — a league player with no identification

### The failing test

| Test (failed on `0da6638`) | What it said there |
|---|---|
| `LeaguePlayerWithoutIdentificationTest.aSquadPlayerWithNoIdentificationIsOnTheListButNotYetWithAnEmptyRpColumn` | `IllegalArgumentException: Bílek Ondřej has no RP number, date of birth or birth number. A player who cannot be identified at all cannot be put on a report.` |
| `LineupScreenTest.aPlayerWithNoIdentificationIsGivenADateOfBirthInTheirOwnRow` | the same exception, from building the player |

Both compile against `0da6638` and fail there for the reason the decision
names: such a player cannot be built, so a league file of them cannot
load and nobody can be fielded. The other 17 `LineupScreenTest` tests
passed in the same run. (A first draft built the player as a class property,
which made every test in the class fail; moved into a function so the red
run says which test is about this.)

### What changed

- **`Player`** may be a `LEAGUE_RECORD` with none of the three. An
  `ADDED_AT_PITCH` player still requires a date of birth (in the
  constructor and in the seed loader); `addedAtThePitch()` still takes no RP
  parameter, and nothing typed can become an `RpNumber`.
- **`SquadMemberEntry`** carries `dateOfBirthTyped` (the field's text) and
  `writtenAtThePitch` (what it puts in the column). `identification` is the
  league record's value where it has one, otherwise the date the referee
  typed. `withDateOfBirthTyped` parses with `parseDateOfBirth` — the
  existing `DateOfBirthEntry` reader the add-a-player form uses — and renders
  YYMMDD with the existing `ReportedIdentification.of(LocalDate)`. Half a
  date writes nothing. `needsDateOfBirth` is true whenever the league record
  cannot fill the column, which also covers the old dead end: an RP number
  on file, no card, no date of birth. The "bez RP" toggle is now offered for
  any player with an RP number, since without a date on file it no longer
  leads nowhere.
- **`Appearance.reportedIdentification` is still non-null**, and
  `TeamLineupEntry.problems()` is unchanged: a present player with nothing
  for Číslo RP is still `NoIdentification`, and the block is not written.
- **Remembered on the device**: new table `remembered_date_of_birth`
  (`player_id`, ISO date) beside `jersey_override`, a
  `RememberedDateOfBirthRepository`, and a `RememberDateOfBirth` use case the
  lineup calls whenever a whole, real date is typed. `BuildLineupEntry`
  pre-fills every row from it. A row already saved keeps what it wrote: if
  the remembered date has changed since (at another match), reopening the
  old lineup shows the saved YYMMDD and an empty field, never the new date.
- **The Týmy tab note.** With no RP numbers *and* no dates of birth on file
  — every psmf.cz team — "Do zápisu se pak píše datum narození" implied the
  app had one. It now reads *"PSMF čísla RP ani data narození zatím
  nedodala. Do zápisu se místo čísla RP píše datum narození — rozhodčí ho
  zadá u soupisky."* The old sentence stays for a team that does have dates
  on file.

### What the referee sees, step by step

1. **Soupiska**, a team read from psmf.cz. Each player's row reads
   *Číslo RP: —*, and directly under the name there is a **Datum narození**
   field with the hint *např. 18.5.1992 nebo 18051992*. Players with a date
   on file have no field.
2. They tap the field and type the date as they would write it,
   `18.5.1992` or `18051992`. Under the field the app echoes **18. 5. 1992**
   to check against the player; the line above changes to
   **Číslo RP: 920518**. The referee never types the six digits.
3. A player marked absent (tap the name) loses the field: nobody needs a
   date to not play.
4. *Pokračovat* with a present player still without one: the field turns
   red and the list below says **"Bílek Ondřej: chybí údaj do sloupce Číslo
   RP. Zadejte datum narození v řádku hráče."** The block is not written.
5. The next match with that player: the field already holds `18.5.1992` and
   the column `920518`. They can change it; whatever whole date they type
   becomes the one offered next time. Nothing requires it to match.
6. Reopening an earlier match's lineup shows what was written that day.

Rendered and looked at before committing (an empty row, a typed row, a
half-typed row in error, and a player with a date on file).

### Schema change

**Version 5 → 6**, `5.sqm`: `CREATE TABLE remembered_date_of_birth`. New and
empty; nothing before version 6 could have written into it. `5.db` was
re-recorded before `TeamRecord.sq` was edited (byte-identical), `6.db`
recorded after. `SchemaMigrationTest.aReportWrittenBeforeDatesOfBirthWereRememberedIsIntactAfterItsMigration`
starts from `5.db`; `RememberedDateOfBirthTest` proves the date survives a
restart, the latest one wins, and remembering one does not touch a stored
report.

### Assertions of the old rule, changed

- `PlayerIdentificationTest.aPlayerWhoCannotBeIdentifiedAtAllCannotBeBuilt`
  → `aLeaguePlayerWithNothingOnFileCanBeBuiltButWritesNothingYet`, plus
  `aPlayerAddedAtThePitchCannotBeBuiltWithoutADateOfBirth`.
- `SeedLeagueCatalogTest.aPlayerWithNoIdentificationAtAllIsReported` →
  `aLeaguePlayerWithNoIdentificationAtAllLoads`, plus
  `aPitchAddedPlayerWithNoDateOfBirthIsReported`.
- `BuildLineupEntry` takes the remembered dates as a required argument; the
  eight test call sites pass a `FakeRememberedDateOfBirthRepository`.

`ShippedSeedDataTest` still asserts every *shipped* player can be
identified — true of the invented 6-K until Part 3 replaces it.

### Gate 2 against its criteria

| Criterion | |
|---|---|
| A test that fails on HEAD: a squad player with no identification cannot be fielded at all | ✅ above, two |
| What the referee sees, step by step | ✅ above |
| Schema change, with its migration | ✅ 5 → 6, `5.sqm`, `6.db` |
| Green | ✅ clean uncached run: **428 · 383 · 193** (+13 · +9 · +6 on Gate 1) |
| Commit | ✅ one commit |

Detekt's first verdict: `LineupViewModel.onEvent` at complexity 15 and the
class at 11 functions, both from one added event. Split the row events out
(`handleRowEvent`, the same split `ConsoleViewModel` made) rather than
raising thresholds.

## 5 · Gate 3 — league 6 from psmf.cz

*Pending.*

## 6 · Gate 4 — the icon, the version, the build

*Pending.*

---

## 7 · Decisions taken, and by whom

| Decision | By |
|---|---|
| The five card-and-clock fixes and what each must do | Owner, `DECISIONS.md` 2026-10-07 |
| The save button reads *Vyloučit (2. ŽK)* for a booked player | This session, asked for by the brief; `DECISIONS.md` |
| A late-logged red runs from the minute written | This session, asked for by the brief; `DECISIONS.md` |
| Old rows get `NULL`, read as "before 0.2.0, by the timeline" | This session |
| JSON spells a straight red `přímá ČK` | This session |
| A named person keeps both red kinds (no automatic second yellow, but the referee can still record one) | This session, reading "leave them as they are" |
| A league player may arrive with none of the three; the referee writes the date of birth | Owner, `DECISIONS.md` 2026-10-07 |
| The date field sits under the name in the row, outside the tap that marks absence | This session |
| A remembered date is replaced by the latest whole date typed; clearing the field forgets nothing | This session |
| A saved row keeps its written value even when the remembered date changes later | This session |
| The "no card" toggle is offered for any player with an RP number | This session |

---

## 8 · Findings worth not rediscovering

1. **`withLanguage("cs")` in the UI tests restores the default locale when
   its block returns.** A test that clicks *after* the block recomposes in
   English, and `onNodeWithText("Přímá ČK")` finds nothing. Clicks that
   recompose belong inside the block. *Already written down* in the KDoc of
   `withLanguage` in `UiTestData.kt` ("Wrap the whole test, not just
   `setContent`") — this session rediscovered it anyway, which is the
   argument for listing it here too.
2. **A migration that adds a column breaks "write old rows with today's
   code".** `SchemaMigrationTest` now transplants only the old table's
   columns; see above.
3. **The 2026-09-01 report says the console read `0´` at the second half's
   kickoff.** At `8bd3c11` it read the first half's elapsed time (`32´`
   after a 32-minute half). Neither is the rule now: `30´`.
4. **The match log is in minute order; Undo is in recording order.** For
   everything logged live they agree. A card typed in as 22´ after a goal
   at 25´ sits *below* the goal in the log and is still what Undo takes.
   Worth a referee's opinion.
5. **A dialog can be looked at without an emulator:**
   `onNode(isDialog()).captureToImage().toAwtImage()` in a JVM Compose test,
   written to a PNG. Used here for the card sheet; not committed.
6. **The edit tool, writing through `\\wsl.localhost`, left CRLF in two
   Markdown files** (`.kt` files were unaffected). `git ls-files --eol`
   shows it; `text=auto` would have normalised it at commit, but the working
   copy would have stayed CRLF.
7. **`Minute.HALF_LENGTH` is still a constant** (DECISIONS 2026-09-01). With
   `30´+` now sorting before 30´, a 2 × 25 competition would sort its
   second half's 25´–29´ before `30´+` and count them in the half-time
   score. True before this change as well; still only a problem outside HL.

---

## 9 · Commits

| Gate | Commit |
|---|---|
| 1 | `0da6638` — Fix the five card and clock defects; schema 5 records the order things were recorded in |
| 2 | *this commit* — Let a league player arrive with no identification; schema 6 |
