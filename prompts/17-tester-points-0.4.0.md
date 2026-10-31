# Prompt — 0.4.0: what the testers found in 0.3.1 — the Zápis tab, discarding a report, corrections in the console

**Where:** the WSL container, as before · **Model:** the owner's choice
**Follows:** `prompts/15` (0.3.1, Android) and `prompts/16` (0.3.1, iOS build 5,
which is what the points were found on).
**Builds:** 0.4.0 (6), both platforms. A minor version, not a patch: the Zápis
tab gets a screen, and the console gets corrections it never had.

**Written for one fresh session per gate**, as 15 was. To run gate N, paste:

> Run Gate N of `prompts/17-tester-points-0.4.0.md` in this fresh session.
> Read its section "Every gate" first.

The decisions behind this prompt: `docs/DECISIONS.md`, 2026-10-31.

---

## What the testers reported (0.3.1 (5), iPhone 15 Pro)

| # | Reported (tester's words, shortened) | Verdict | Gate |
|---|---|---|---|
| 1 | *Nelze zrušit rozepsaný zápis.* | A defect: a report exists from the first tap on its fixture, and nothing can remove it | 2 |
| 2 | *Pokud mám zápis rozepsaný, neměl by se zobrazovat v záložce Zápis? Stejně tak možná i dohrané zápasy.* | Agreed | 1 |
| 3 | *Sestavy: v rámci potvrzení kapitánem by měly probíhat kontroly na validní vyplnění za daný tým.* | Agreed | 3 |
| 4 | *Zápis utkání: tlačítko Vzít zpět není ohraničené.* | Agreed: its own decision's reversal condition | 4 |
| 5 | *Chybí editace střelce nebo žluté/červené karty, dá se pouze přes Vzít zpět.* | Agreed: a product requirement (analysis §2.12) the demo deferred | 5, 6 |
| 6 | *Odeslání: tlačítko pro zkopírování do schránky.* | Agreed | 4 |

**The answers, 2026-10-31.** The tester: point 1 means deleting a report
started by mistake, not recording a match that was not played; the Zápis tab
should list signed reports as well as unfinished ones; correcting a card's
player, reason and minute is enough, a wrong colour being removed and
recorded again; the copy is pasted into an email or WhatsApp, and plain text
is enough. Point 3 stands as written here; the tester may add to it later.
The owner: **a signed report can be discarded too**; corrections as this
prompt has them; 0.4.0 (6); run by hand, gate by gate, without the runner.

---

```
Seven gates. One fresh session per gate. Each session does ONE gate, commits
it, and stops.

## Every gate

READ, IN THIS ORDER, AND NOTHING ELSE UNTIL THE GATE NEEDS IT
  1. CLAUDE.md
  2. In this prompt: "Every gate", "What is already known", and your gate.
  3. reports/2026-10-31-0.4.0-tester-points.md (the report so far; Gate 1
     creates it)
  4. git log --oneline -15 and git status
  5. Your gate's reading list. Read docs/DECISIONS.md by the entries named,
     never whole (it is over 2,800 lines).

IF THE TREE HAS UNCOMMITTED CHANGES WHEN YOU START, they are an earlier
attempt at this gate that was cut off. Read them (git diff). Carry on from
them if they are sound; otherwise git stash push -m "gate N, abandoned
attempt" and say so in the report. Never git reset --hard, never git clean,
never amend or rewrite an earlier commit.

ONE GATE, ONE COMMIT. The subject starts "Prompt 17, Gate N:". The gate's
section of the report is in that commit, and has:
  - what changed for a referee
  - how it was proved: the tests written first and their failure on HEAD
    (the message), and each new behaviour broken once and what caught it
  - judgement calls, each also in docs/DECISIONS.md with a "Reverses if"
  - not done
  - the four counts: shared JVM · Android host · composeApp · importer
Green before the commit: ./gradlew build :shared:allTests detekt.

NOBODY IS THERE TO ANSWER A QUESTION. Decide within this prompt and the
decisions, and record the call. Stop only for these:
  - Gate 1's baseline is not green
  - a NON-NEGOTIABLE cannot be met
  - the only way forward reverses an entry in docs/DECISIONS.md that the
    2026-10-31 entry does not already reverse
  - the work needs a device, credentials, or network beyond Gradle's
To stop: commit nothing. Write the reason, and what you tried, in
.runner/STOP (create the folder; it is git-ignored). Say the same in your
last message.

NO EMULATOR, NO PHONE. Renders come from JVM tests. What only a phone can
prove goes in the report's checklist, marked UNPROVEN where it matters.

KEEP THE CONTEXT SMALL. Filter Gradle output to failures and counts; never
print a whole build log. Read long files in parts.

NO SCHEMA CHANGE IS EXPECTED. Every gate works with the tables schema 8
already has. If one turns out to need a change, it is schema 9: record 8.db
first, in shared/src/commonMain/sqldelight/README.md's sequence, then a
migration test from 8.db.

## What is already known (from reading 0.3.1, 2026-10-31)

  1. A REPORT EXISTS FROM THE FIRST TAP. StartOrResumeMatch saves a SETUP
     match the moment a fixture is tapped, before anything is filled in, so
     that nothing ever lives only in memory. A tap on the wrong fixture
     therefore leaves a report ("Rozepsáno") for good.
  2. NOTHING CAN REMOVE ONE. MatchRepository.delete exists and clears the
     match, its rows, its lineup drafts and its pitch-added players in one
     transaction (MatchPersistenceTest.aDeletedMatchIsGoneFromEveryTable,
     LineupDraftPersistenceTest.deletingTheMatchDeletesItsDraftsAndNobodyElses).
     No use case and no screen calls it.
  3. ZÁPASY NEVER HIDES AN UNCONFIRMED REPORT by its dates
     (ListFixtures, DateWindow.shows). Point 1's stray report therefore sits
     under "Nadcházející" for ever, weeks after its date.
  4. THE ZÁPIS TAB HOLDS ONE REPORT AND LISTS NONE. AppNavigator's root for
     the tab is Destination.NoReport ("Žádný zápis"); openReport replaces
     the tab's stack with the report's steps up to its resume point;
     re-selecting the tab never pops it. The navigator does not survive the
     process, and on launch App.kt puts back (adoptReport) only a match whose
     whistle has gone (ObserveReportInProgress: IN_PROGRESS, and only while
     its fixture is still in league data). After a restart, a "Rozepsáno" or
     "Dohráno" report is reachable only through Zápasy, and the tab says
     there is no report. That is what the tester saw.
  5. THE CAPTAIN'S TAP CHECKS NOTHING. LineupScreen.CaptainSection offers
     "Potvrzuje <name>" as soon as a captain is marked, and
     LineupViewModel.confirm() records it without asking
     TeamLineupEntry.problems() -- so a captain can confirm a team with no
     goalkeeper, a shared shirt number, or a player with nothing in Číslo
     RP: a block the app itself will not write. A present player with no
     shirt number is not a problem anywhere, and stays so (see Gate 3).
  6. UNDO IS QUIET ON PURPOSE. A TextButton, secondary text, no outline, no
     fill (DECISIONS 2026-10-21, 0.3.1 Gate 4, §2), pinned by
     ConsoleControlsTest.undoHasNeitherOutlineNorFillWhereTheWhistlesHaveOne.
     That decision's "Reverses if" is "...or find it too easy to miss (then
     it is a button, and the test of the pixels changes with it)". The
     tester's point is exactly that.
  7. THE ONLY CORRECTION IS UNDO. UndoLastEvent takes back the last thing
     recorded; nothing changes an event further back. Yet the recap tells the
     referee "Oprava gólu se dělá na obrazovce Zápis" (recap_result_note). A
     wrong scorer three goals back costs three Undos and three goals typed
     again with their minutes. What exists to build on:
       - every goal and card since 0.2.0 has a `sequence`, its place in the
         recording order; older ones have none;
       - a second yellow is a YellowCard and a RedCard sharing one sequence,
         and a red's PowerPlay carries the red's sequence;
       - LogGoal and LogCard already take a minute the referee wrote, with
         the rules (not later than the clock, no goal at 60´+, a written
         red's power play placed on the clock period by period);
       - GoalSheet and CardSheet already ask for a minute;
       - goals are kept in report order by rescored();
       - a change to a signed report asks first and withdraws the three
         signatures (ConsoleViewModel: withdrawing / WithdrawalConfirmed,
         afterAChangeFrom).
     The log's rows (ConsoleScreen.EventLogRow) are not tappable.
  8. EXPORT. A preview with TXT, CSV and JSON chips, TXT selected by default
     (ZouFormat.TEXT, "what goes in the body of the email"); Save and Send
     in an EvenActionRow, offered only when the report is ready; outcomes in
     words under the preview. The app touches the clipboard once already:
     IosReportSender puts PSMF's address on UIPasteboard when the device has
     no Mail account.
  9. BASELINE. Prompt 15 was verified at 593 shared JVM · 524 Android host ·
     378 UI · 23 importer tests, detekt green, APK 38,527,991 bytes.

## GATE 1 — the Zápis tab lists the reports on this device (point 2)

First, the baseline: ./gradlew build :shared:allTests detekt green at HEAD,
the four counts recorded. If not green, STOP. Create the report file, with
the table of points above and a section for this gate.

Reading list: ui/navigation/Navigation.kt, ui/shell/NoReportScreen.kt,
ui/shell/ShellViewModel.kt, usecase/ObserveReportInProgress.kt,
usecase/StartOrResumeMatch.kt (ResumePoint), App.kt (Route, the
adoptReport effect, OpenMatch.destination), NavigationAndFormatTest,
AppShellTest, PlaceholderMatchAfterTheUpgradeTest. DECISIONS 2026-08-31
"The app gets tabs, and the report stays a wizard inside one of them",
2026-10-31.

OUTCOME. The Zápis tab's first screen lists the reports on this device,
instead of saying there are none:
  - Two groups. First what is still being worked on: Rozepsáno, Probíhá,
    Dohráno. Then Potvrzeno, the reports signed by all three. Within each,
    the latest kickoff first.
  - A row says the two teams, the group, the fixture's date and time, and
    the status in the same words and look as the badge in Zápasy.
  - Tapping a row opens the report where tapping its fixture in Zápasy
    would (the same resume point). Back from the report's first step comes
    back to this list. The report stays a wizard inside the tab.
  - A report whose fixture is no longer in league data is not listed, as
    ObserveReportInProgress already does not offer it.
  - With no report on the device, the screen says what "Žádný zápis" says
    now, with the same button to Zápasy.
  - The list follows the database while it is on screen: a report started
    from Zápasy, or moved on, is there without reopening the tab. Observe;
    do not read once on entry (the bug shape in TODO, "Known gaps").
Unchanged: the tab's badge (only a match in progress); re-selecting the tab
does not throw the referee out of the step they are on; one report open in
the tab at a time; after a kill, a match in progress is still put back so
the badge lands on the console, now with the list beneath it.

TESTS, FIRST, FAILING ON HEAD
  - a "Rozepsáno" and a "Dohráno" report in the repository, a new navigator
    and shell as after a kill: the Zápis tab lists both, and tapping each
    lands at its resume point (HEAD says "Žádný zápis")
  - Back from the match header returns to the list
  - a report whose fixture is not in league data is not listed
  - a report started from Zápasy while the list is composed appears in it
Renders: 360 dp, cs and uk: one report of each status, and the empty list.

## GATE 2 — a report can be discarded (point 1)

Reading list: data/match/MatchRepository.kt and SqlDelightMatchRepository
(delete), MatchPersistenceTest (the delete case), Gate 1's list and its
tests, ui/console/TakeBackDialog.kt (how the app asks before a change that
matters). DECISIONS 2026-10-31; 2026-10-17 "A forged report" (why a report
that disappears matters).

OUTCOME.
  - Any report in Gate 1's list can be discarded, whatever its status, by
    a control of its own on the row ("Zahodit"), never by tapping the row,
    a swipe or a long press: opening a report must never be able to remove
    one. The case it is for: a report started by tapping the wrong
    fixture, and the testers' own test reports.
  - It always asks first. The question names the match (the teams and the
    date) and says what goes, in words ("hlavička, sestavy, 3 góly, 1
    karta" or whatever this report holds), and that it cannot be undone.
    Dismissing the question, or tapping beside it, keeps the report.
  - A Potvrzeno report (owner, 2026-10-31) can be discarded too, and its
    question says more: that the report is signed by both captains and the
    referee, that it may already have been sent to PSMF, and that copies
    saved to the device's folder and mail already sent are not touched. The
    destructive answer is never the one a quick tap lands on.
  - After discarding: the report is gone from the list and from Zápasy (no
    badge on its fixture; an old fixture leaves "Nadcházející" by its dates
    again); the tab's badge goes if it was the match in progress; if it was
    the report open in the tab, the tab is back at the list, never on a
    step of a report that no longer exists. Tapping the fixture again
    starts a new, empty report.
  - What stays on the device: the card numbers and dates of birth
    remembered per player, jersey overrides, followed teams, and the files
    the export saved to the device's folder. They are not the report's
    rows. Say so in DECISIONS.
NON-NEGOTIABLE: nothing removes a report without the referee's yes to that
question, and nothing else in the app gains a way to call delete().

TESTS, FIRST, FAILING ON HEAD
  - shared: the use case removes a report, its drafts and its pitch-added
    players, on the real SQLite driver as MatchPersistenceTest does, and
    leaves every other report whole
  - UI: Zahodit, then dismiss: the report is still there; Zahodit, then yes:
    gone from the list; the report open in the tab is discarded: the tab
    shows the list
  - a Potvrzeno report's question carries the signed-and-maybe-sent words;
    an unsigned one's does not
Renders: both questions, cs and uk, 360 dp.

## GATE 3 — the captain's tap checks the team first (point 3)

Reading list: ui/lineup/LineupScreen.kt (CaptainSection, Problems),
ui/lineup/LineupViewModel.kt (confirm, commitBothLineups),
usecase/LineupEntry.kt (TeamLineupEntry.problems, withCaptain),
LineupScreenTest. DECISIONS 2026-10-17 "The lineup, as built" (the
paragraph on the captain's tap) and "The captain and the goalkeeper are
marked in the lineup".

OUTCOME.
  - "Potvrzuje <captain>" records the confirmation only when that team's
    block is whole: exactly TeamLineupEntry.problems() for that side is
    empty (somebody present, a captain, a goalkeeper, no shared shirt
    number, something in every present player's Číslo RP). Nothing new is
    checked, and nothing about eligibility, ever.
  - Otherwise nothing is recorded, and that team's problems are listed by
    the button, in the words Continue's check uses, with the rows that need
    something marked as Continue marks them. The other team's problems do
    not stop this team's captain and are not listed there.
  - Whether the button looks disabled until the block is whole is your
    call, but the referee must always be able to see why it cannot be
    confirmed yet. A greyed button with no reason is not acceptable.
Unchanged: a later change to a confirmed lineup does not withdraw the
confirmation, and a different captain does (DECISIONS 2026-10-17); a missing
confirmation stops sending, never the kickoff; a confirmation already
recorded on an incomplete lineup by an earlier build stays as it is; a
player with no shirt number is still not a problem.

TESTS, FIRST, FAILING ON HEAD
  - a captain and no goalkeeper: the tap records nothing and names the
    missing goalkeeper (HEAD records it)
  - the same with a present player who has nothing in Číslo RP
  - a whole team records as before, with its time
  - a problem in the other team does not stop this team's tap
Renders: the captain's section with problems listed, cs and uk, 360 dp.

## GATE 4 — Undo looks like a button, and the report can be copied (points 4 and 6)

Reading list: ui/console/ConsoleScreen.kt (Controls, UndoPadding),
ConsoleControlsTest, ui/export/ExportScreen.kt, ui/export/ExportViewModel.kt,
ExportScreenTest, ExportButtonsTest, iosMain IosReportSender (for reference).
DECISIONS 2026-10-21 "0.3.1, Gate 4" §1, §2 and §7; 2026-08-31 "The saved
bytes are identical to the sent ones".

OUTCOME, UNDO. Undo has an outline, so it reads as a button. The rest of
its place stays as 0.3.1 decided: a line of its own under the whistles, at
the start edge, only as wide as its word plus padding (a full-width Undo is
a target a stray thumb takes a goal back with), quieter than the whistles
(outline and text in the theme's secondary colours, never a fill and never
the whistles' colour), a full touch target. Record it as that decision's
own reversal. The pixel test changes with it: it now proves an outline is
drawn, that there is no fill, and that Undo is narrower than the whistles'
row. Renders: every console state, 360 dp, cs and uk, light and dark.

OUTCOME, COPY. On Odeslání, when the report is ready -- exactly when Save
and Send are offered, never before, because copying is one more way of
sending it -- a control copies the report to the clipboard:
  - always the text report (the TXT, ZouFormat.TEXT), whichever format the
    preview shows: the tester pastes it into an email or WhatsApp, and plain
    text is enough. Exactly the TXT's text, not a second rendering. The
    control's words say it is the text ("Zkopírovat text zápisu").
  - quieter than Send and not in Save and Send's row; where is your call
  - afterwards the outcomes say it was copied ("Text zápisu je ve
    schránce."), beside the save and send outcomes, in cs, en and uk
  - how the TXT reads once pasted is not this gate's to change (see "Do
    not"); a phone step looks at it in a mail body and in WhatsApp
  - the report holds dates of birth and card numbers: where the platform
    lets the app mark clipboard text as sensitive (Android 13 and later),
    it does. Say in the report what iOS shows when it is pasted.
  - no dependency. Prefer common code; if Compose cannot put plain text on
    the clipboard from common code on both platforms, an expect/actual is
    the reason to drop into platform code. Keep the iOS actual to the one
    function, and put :composeApp:linkDebugFrameworkIosArm64 first in the
    Mac's list: it cannot be compiled here.

TESTS, FIRST, FAILING ON HEAD
  - Undo: an outline, no fill, narrower than the whistles' row (HEAD's test
    asserts the opposite and is replaced, not deleted silently: say so)
  - copy is offered only when the report is ready; with CSV or JSON in the
    preview it still copies the TXT's text; the outcome line appears
What is on the clipboard of a real phone goes in the phone checklist.

## GATE 5 — correct or remove a goal from the log (point 5)

Reading list: usecase/MatchConsole.kt (LogGoal, UndoLastEvent,
withoutLastEvent, rescored), usecase/GoalEntry.kt (GoalDraft, GoalProblem),
ui/console/GoalSheet.kt, ui/console/ConsoleViewModel.kt (the "asks first"
path: withdrawing, WithdrawalConfirmed), ui/console/ConsoleScreen.kt
(EventLogRow), GoalWithItsOwnMinuteTest, ChangesAfterTheSignaturesTest.
DECISIONS 2026-10-18 "The console: a goal at its own minute, a whistle taken
back", 2026-10-31. docs/LEAGUE_APP_ANALYSIS.md §2.12, last bullet.

OUTCOME. A goal already in the console's log can be corrected or removed,
whenever the console is open: during the match, after the final whistle,
on a signed report.
  - Tapping a goal in the log opens the goal sheet filled in with it: the
    scorer (or none) and the minute. The scorer can be changed to any
    player of the same team in its lineup, or to none; the minute under
    the rules a written minute has now (not later than the clock, never
    60´+, refused in words and never clamped).
  - The sheet can also remove the goal, after asking. A goal given to the
    wrong team is put right that way: removed, then recorded for the
    other. The team of a goal is not changed in the sheet.
  - A corrected goal keeps its place in the recording order, so Undo takes
    back what it would have taken before. A removed goal leaves the order.
    Stav, the half-time score, the result and all three export formats
    follow at once; nobody recounts anything by hand.
  - On a signed report a correction or a removal asks first and withdraws
    the three signatures, through the path a new goal already takes.
  - A goal recorded before 0.2.0 (no sequence) is corrected the same way or
    not offered; your call, recorded.
  - The whole log row is the target, at least a touch target tall, and it
    looks tappable by more than its colour. Whistles in the log stay as
    they are: only Undo takes one back.
  - The recap's "Oprava gólu se dělá na obrazovce Zápis" is now true;
    check its wording in cs, en and uk.
NON-NEGOTIABLE: nothing edits a whistle or an instant, and the clock rule is
untouched (the test that looks for a pause or stop control passes
unchanged). The report says what the goals now are; no record of the
correction is kept, because versioning (analysis §5.3) is out of the demo.

TESTS, FIRST, FAILING ON HEAD (shared first, then UI)
  - correct the scorer of the first of three goals: the other two, the
    order and Stav are as they were
  - move a goal's minute across the half: the half-time score follows
  - a minute later than the clock is refused
  - remove the middle of three goals: Stav after it drops by one
  - Undo after a correction takes back what it would have before
  - a signed report: nothing changes until the referee says yes, and the
    signatures go after it
  - UI: tapping a goal row opens the sheet filled in (HEAD: nothing happens)

## GATE 6 — correct or remove a card from the log (point 5)

Reading list: usecase/MatchConsole.kt (CardDraft, LogCard, powerPlayFor,
isBookedWithoutDismissal, how Undo takes a card), ui/console/CardSheet.kt,
CardAndClockReviewTest, DisciplineTest, Gate 5's section of the report.
DECISIONS 2026-10-07 "The card and clock review: five defects, four of them
about red cards" and "Two calls inside the card fix", 2026-10-31.

OUTCOME. A card in the log is corrected or removed as a goal is (Gate 5),
with what a card carries:
  - The sheet changes its reason (still mandatory), its minute (the card
    sheet's own rules), and its player: to another player of the same team
    with no card in this match.
  - Its colour, and for a red straight against 2. ŽK, do not change in the
    sheet. A wrong colour is removed and recorded again; the sheet says so
    in one line.
  - A second yellow is one action -- its yellow and its red share a
    sequence -- and is corrected or removed whole. Its player does not
    change.
  - A player's first yellow cannot be removed while their 2. ŽK stands: it
    would leave a second yellow without a first. The refusal says to take
    the 2. ŽK out first.
  - A red's power play follows: a corrected minute moves its start as
    LogCard places a written minute; a removed red takes its power play
    with it.
  - Removing the last card leaves the cards block unaccounted for, not "no
    cards", as Undo does.
  - On a signed report: asks first, as Gate 5.

TESTS, FIRST, FAILING ON HEAD
  - a yellow moved to another player; a player who already has a card is
    refused
  - a red's minute corrected: its power play's start moves; a red removed:
    its power play is gone
  - a 2. ŽK removed: its yellow and its red both go
  - a first yellow under a standing 2. ŽK is refused
  - the only card removed: the block is unaccounted for, not NoneIssued
  - signed: asks first

## GATE 7 — documents, version, build

  - DEMO_SCOPE: the Shell table (Zápis holds the list of reports), §3 (the
    captain's tap checks the team), §4 (Undo with an outline; corrections
    from the log, replacing "Keep: undo"), §7 (copy). The Screens table's
    row 9 and "Out of the demo": correcting goals and cards from the console
    is in; versioning and an audit of changes (§5.3) are still out.
  - 0.4.0, build 6, both platforms: versionName/versionCode, and
    MARKETING_VERSION/CURRENT_PROJECT_VERSION in Debug and Release. Only
    those values in the Xcode project; never DEVELOPMENT_TEAM.
  - ./gradlew :androidApp:clean :androidApp:assembleDebug. Report the size
    against 0.3.1's 38,527,991 bytes.
  - TODO and prompts/README.md brought level.
  - Finish the report (below).

## Do not

  - Add or bump a dependency.
  - Change the seed files or the importer.
  - Touch iosMain or iosApp beyond the version numbers, and Gate 4's one
    clipboard actual if common code cannot do it.
  - Change what the export writes, beyond the goals and cards it now holds.
    No new fields, no change log.
  - Version reports or keep an audit trail of corrections.
  - Add network access. Push. Run the emulator or adb.

## The report

reports/2026-10-31-0.4.0-tester-points.md. Gate 1 opens it; each gate adds
its section. Gate 7 finishes it:
  - It opens with one sentence: what changed for a referee.
  - A table of the six points: proved on the JVM, or UNPROVEN.
  - "On the Android phone": a numbered checklist. Each step has what should
    happen and what failure looks like. Install over 0.3.1 without
    uninstalling, with one report of each status in it. First: the list
    after a restart, discarding a stray report (and that its fixture loses
    the badge), correcting a scorer three goals back.
  - "On the iPhone" (the tester's is an iPhone 15 Pro): the list, both
    discard questions, the goal and card sheets opened from the log, and
    the copied text pasted into a mail body and into WhatsApp: whether it
    reads as a report there, and what iOS asks when it is pasted.
```
