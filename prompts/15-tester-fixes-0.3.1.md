# Prompt — 0.3.1: what the testers found in 0.3.0, and the kits' colours

**Where:** the WSL container, as before · **Model:** Sonnet (the owner's choice)
**Follows:** `prompts/13` (0.3.0, Android) and `prompts/14` (0.3.0, iOS).
Both are with testers.
**Builds:** 0.3.1 (5), both platforms.

**Written for one fresh session per gate.** The owner clears the context
between gates; later, a runner may start each gate overnight. Each gate has
to work cold, so each one says what to read. To run gate N, paste:

> Run Gate N of `prompts/15-tester-fixes-0.3.1.md` in this fresh session.
> Read its section "Every gate" first.

The decisions behind this prompt: `docs/DECISIONS.md`, 2026-10-21.

---

## What the testers reported (0.3.0, Android and iPhone)

| # | Reported (owner's words, shortened) | Gate |
|---|---|---|
| 1 | The keyboard disappears when the referee taps a field low on the screen, one the keyboard would cover. Expected: the field scrolls up and the keyboard stays. | 1 |
| 2 | In dialogs the first letter is typed, then the cursor jumps before it: typing "Marek" gives "arekM". | 1 |
| 3 | In the lineup, unmarked players take a lot of space. Make them compact, and open the row (shirt number, Číslo RP) when the player is marked. | 2 |
| 4 | In Ukrainian, *Налаштування* does not fit its tab at the bottom of the screen. | 4 |
| 5 | The console's buttons sit to the right, below or beside each other. Lay them out deliberately. | 4 |
| 6 | Show each team's kit colours under its name on the console, and on the team tabs, so the referee need not remember which colour is which team. Not necessarily a shirt; anything that shows the colours. | 5 |

From the review of 0.3.0, and owed: a lineup in progress is lost if the app is
killed (Gate 3), the console log does not list the whistles (Gate 4), and
`materialIconsExtended` costs 31 MB for five icons (Gate 6).

---

```
Seven gates. One fresh session per gate. Each session does ONE gate, commits
it, and stops.

## Every gate

READ, IN THIS ORDER, AND NOTHING ELSE UNTIL THE GATE NEEDS IT
  1. CLAUDE.md
  2. In this prompt: "Every gate", "What is already known", and your gate.
  3. reports/2026-10-21-0.3.1-tester-fixes.md (the report so far; Gate 1
     creates it)
  4. git log --oneline -15 and git status
  5. Your gate's reading list. Read docs/DECISIONS.md by the entries named,
     never whole (it is over 2,300 lines).

IF THE TREE HAS UNCOMMITTED CHANGES WHEN YOU START, they are an earlier
attempt at this gate that was cut off. Read them (git diff). Carry on from
them if they are sound; otherwise git stash push -m "gate N, abandoned
attempt" and say so in the report. Never git reset --hard, never git clean,
never amend or rewrite an earlier commit.

ONE GATE, ONE COMMIT. The subject starts "Prompt 15, Gate N:". The gate's
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
  - the only way forward reverses an entry in docs/DECISIONS.md
  - the work needs a device, credentials, or network beyond Gradle's
To stop: commit nothing. Write the reason, and what you tried, in
.runner/STOP (create the folder; it is git-ignored). Say the same in your
last message.

NO EMULATOR, NO PHONE. Renders come from JVM tests. What only a phone can
prove goes in the report's checklist, marked UNPROVEN where it matters.

KEEP THE CONTEXT SMALL. Filter Gradle output to failures and counts; never
print a whole build log. Read long files in parts.

## What is already known (from reading 0.3.0, 2026-10-21)

  1. THE CURSOR ("arekM"). PsmfTextField takes a String, and most screens
     route it through a ViewModel's StateFlow, so the value comes back a
     frame later. Material's String text field keeps its own selection and
     fits it to the value it is given. If it recomposes once with the old
     empty value before "M" arrives, the cursor is fitted to 0 and stays
     there. Only the first character is hit, which is exactly "arekM". This
     is the likely mechanism, not a proven one. It may hit any field whose
     round trip is slow, and dialogs are where it was seen.
  2. THE LOW FIELD. KeyboardDismissal's DismissOnScroll dismisses on a
     nested scroll from NestedScrollSource.UserInput, on the belief that a
     list scrolling a focused field into view is not UserInput. When the
     keyboard opens, the list brings the focused field into view, and
     recent Compose foundation dispatches that scroll through nested scroll
     as UserInput. If so, the field asks to be seen and the dismissal reads
     it as the referee scrolling. Likely, not proven. The pointer handler
     in the same file already tells a finger that moved past touch slop
     from one that did not.
  3. THE LINEUP ROWS. SquadRow lays out an unmarked row at the full height
     of a marked one (about 160 dp), so that marking never moves the rows
     below. That was prompt 13's "rows do not jump under the thumb" taken
     further than meant (DECISIONS 2026-10-21).
  4. A LINEUP IS WRITTEN ONLY ONCE WHOLE: everyone marked has a number or
     a date, plus a captain and a goalkeeper. Until then, marks live only
     in the ViewModel.
  5. THE BUTTONS. ActionRow (ui/common/Controls.kt) is a FlowRow aligned to
     the end. Buttons keep their own widths and wrap right-aligned, which
     is the ragged look on the console. Seven screens use it.
  6. THE TAB. PsmfNavigationBar wraps labels instead of clipping them;
     "Налаштування" is one long word, so it breaks mid-word.
  7. THE KITS. Kit.colours is a list of Czech colour names, "for the app
     only" (Catalogue.kt). Lineup.kitId says which kit was worn;
     Lineup.kitLabel is the report's snapshot. League 6 uses twenty names:
     černá, bílá, modrá, červená, žlutá, zelená, oranžová, fialová, růžová,
     šedá, světle zelená, zlatá, tmavě modrá, rudá, světle modrá, hnědá,
     mentolová, pistáciová, vínová, limetková.

## GATE 1 — typing (points 1 and 2)

First, the baseline: ./gradlew build :shared:allTests detekt green at HEAD,
the four counts recorded. If not green, STOP. Create the report file, with
the table of points above and a section for this gate.

Reading list: ui/common/PsmfTextField.kt, ui/common/KeyboardDismissal.kt,
their tests (KeyboardTest), reports/2026-10-17-report-flow-after-iphone-test.md
§3 only.

OUTCOMES
  - Every character typed lands where the cursor is, in every field, in a
    dialog or not, including when the app's state comes back a frame late.
    "Marek" typed is "Marek". Fix it in PsmfTextField, so every field has it.
  - A field low on the screen, tapped, keeps the focus and the keyboard.
    The screen moves so that the field and the button under it are above
    the keyboard. Only the referee's finger dragging the content counts as
    "starting to scroll". A tap outside a field, and leaving the step,
    still dismiss as they do now.

TESTS, FIRST, FAILING ON HEAD
  - a field whose value is delivered a frame late: typing "Marek" gives
    "Marek"
  - a focused field brought into view by the list keeps the focus, and a
    finger drag on the same list still takes it away
If you cannot make one of them fail on the JVM, say exactly what you tried,
make the change that removes the suspected mechanism anyway, mark the point
UNPROVEN, and put it first in the phone checklist. Do not stop for it.
Every existing KeyboardTest still passes.

## GATE 2 — compact lineup rows (point 3)

Reading list: ui/lineup/SquadRow.kt, ui/lineup/LineupScreen.kt, their tests,
reports/2026-10-17-… §4 only, DECISIONS 2026-10-21.

OUTCOME. An unmarked player is one compact line: the mark and the name, at
least a full touch target tall. Marking opens the row downwards (shirt
number, Číslo RP, Nemá průkaz). The name that was tapped stays where it is;
the rows below move down. Unmarking closes the row. Anything typed into a
row comes back if the player is unmarked and marked again on the same visit.
A short animation is fine if a tap never waits for it.

Replace the tests that pinned the tall blank row with ones that pin what is
wanted: the tapped name does not move; an unmarked row is no taller than
its name needs; the same at font scale 1.5.

Renders, 360 dp, cs and uk: a squad of 20 with 8 marked.

## GATE 3 — a lineup in progress survives the app being killed

Reading list: ui/lineup/LineupViewModel.kt, usecase/LineupUseCases.kt,
shared/src/commonMain/sqldelight/README.md, SchemaMigrationTest,
reports/2026-10-17-… §4 "Judgement calls" only.

OUTCOME. Marks, the captain and the goalkeeper, shirt numbers, typed card
numbers and dates survive the process dying at any moment, and are there
when the lineup is reopened.
NON-NEGOTIABLE: an incomplete lineup is never treated as complete. The
console, the export and the signatures see it exactly as they do now, with
its problems listed.
A schema change is allowed if the work needs one. It becomes schema 8:
record 7.db first, in the README's sequence, then a migration test from
7.db. Say why the draft lives where it does.

TEST, FIRST, FAILING ON HEAD: mark three players, type one card number,
rebuild the ViewModel from the repository as after a kill, reopen: all of it
is there.

## GATE 4 — the console's buttons, the Ukrainian tab, whistles in the log (points 5 and 4)

Reading list: ui/common/Controls.kt, ui/console/ConsoleScreen.kt (Controls,
Body, LogRow), ui/shell/AppShell.kt (PsmfNavigationBar), their tests,
reports/2026-10-17-… §5 only.

OUTCOME, THE BUTTONS. The console's controls are laid out on purpose in
every state: before kickoff, the first half, added time, the interval, the
second half, after the final whistle, confirmed. They are aligned, evenly
sized where they sit together, and never wrap raggedly. Check at 360 and
411 dp, cs and uk, font 1.0 and 1.3. Undo stays visibly apart from the
whistles and quieter than them. The design is yours; render every state.
Look at the other six uses of ActionRow, and change them only if they show
the same fault.

OUTCOME, THE TAB. In Ukrainian, Settings fits its tab without breaking a
word, at 360 dp and font 1.0 and 1.3. How is your call: a shorter word for
the tab only (the screen title keeps Налаштування), a line break that
happens only between words, or something else. Do not shrink the text
below what the bar already uses. Nobody native has read the Ukrainian;
say so.

OUTCOME, THE LOG. The console log lists the whistles in their place
(kickoff, end of half, start of the next, final whistle), because Undo walks
back through them.

## GATE 5 — the kits' colours (point 6)

Reading list: domain/Catalogue.kt (Kit), domain/Lineup.kt, usecase/ConsoleEntry.kt
and the code that builds it, ui/console/Scoreboard.kt, ui/console/ConsoleScreen.kt
(SideTabs), ui/lineup/LineupScreen.kt (SideTabs). DECISIONS 2026-09-01 "The
card glyph is drawn, not borrowed", 2026-08-30 "Kit label must be
snapshotted", 2026-10-21.

OUTCOME. A small shirt, drawn in code, in the colours of the kit the team
wears in this match:
  - under each team's name on the console's scoreboard
  - on each team's tab on the console and in Sestavy
Two colours show as two and three as three; the pattern is your call.
White and pale kits stay visible on light and dark themes (an outline).
The colours come from a table in the app that maps each name to a colour,
covering at least the twenty names above. A name not in the table draws
nothing for that colour; a kit with no known colour draws no shirt. Never
guess a colour. Each shirt's accessible description is the kit's label.

NON-NEGOTIABLE: the report does not change. Barva dresů stays the label
snapshot, and nothing in shared/ or the export reads the colour table. No
icon library.

Tests: every one of the twenty names maps; an unknown name draws nothing;
the scoreboard and both tab rows show each side's worn kit, not its first
kit. Renders: cs and uk, light and dark, with a white kit against a
black-and-white one.

## GATE 6 — drop materialIconsExtended

Reading list: DECISIONS 2026-09-01 "Five icons must not cost 31 MB",
gradle/libs.versions.toml, composeApp/build.gradle.kts, every use of Icons.

OUTCOME. The app no longer depends on materialIconsExtended, and its icons
look the same. Copy the vectors in, with the licence notice the Material
icons carry. No dependency is added or bumped. Report the APK size before
and after. iOS cannot be built here; say what you expect for its framework.

## GATE 7 — documents, version, build

  - DEMO_SCOPE §3 (the row that opens), §4 (the controls, the kit shirt,
    the whistles in the log). The decisions you made are already in
    DECISIONS, gate by gate.
  - 0.3.1, build 5, both platforms: versionName/versionCode, and
    MARKETING_VERSION/CURRENT_PROJECT_VERSION in Debug and Release. Only
    those values in the Xcode project; never DEVELOPMENT_TEAM.
  - ./gradlew :androidApp:clean :androidApp:assembleDebug. Report the size
    against 0.3.0's 70,313,351 bytes.
  - Finish the report (below).

## Do not

  - Add or bump a dependency. Gate 6 removes one.
  - Change the seed files or the importer.
  - Touch iosMain or iosApp beyond the version numbers.
  - Change what the export writes.
  - Add network access. Push. Run the emulator or adb.

## The report

reports/2026-10-21-0.3.1-tester-fixes.md. Gate 1 opens it; each gate adds
its section. Gate 7 finishes it:
  - It opens with one sentence: what changed for a referee.
  - A table of the six points: proved on the JVM, or UNPROVEN.
  - "On the Android phone": a numbered checklist. Each step has what should
    happen and what failure looks like. Points 1 and 2 come first, on a
    real phone, in a dialog and on the lineup's lowest row. Install over
    0.3.0 without uninstalling, with a lineup half done when you update if
    Gate 3 changed the schema.
  - "On the iPhone": what only iOS can show, the number pad and the
    dialogs above all.
```
