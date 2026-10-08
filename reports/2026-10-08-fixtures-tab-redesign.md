# Zápasy for the real league — build 0.2.1 (3)

**Session date:** 2026-10-08
**Brief:** `prompts/12-fixtures-tab-redesign.md`
**Repository:** `~/dev/psmf-app` (WSL Ubuntu), branch `main`
**Where it ran:** the Claude desktop app on the Windows host, driving the
`psmf-sandbox` image through the long-lived `psmf-work` container
(`docker exec … ./gradlew`); the Android emulator (*Medium_Phone*, Android
16, x86_64) on the Windows host for measurements and renders. Commits made
from WSL with the owner's git identity. Nothing pushed.
**Outcome:** all three gates met · 3 commits · debug APK 0.2.1 (3),
70,047,639 bytes — **47,639 bytes over 70 MB** · 454 · 401 · 210 · 23 tests ·
on the emulator, not yet on a phone.

> **What a referee sees on opening Zápasy now:** four plain boxes —
> *Všechny ligy*, *Všechna hřiště*, *Nadcházející* and a team field — and
> under them today's matches, headed *Dnes · čtvrtek 8. 10.*, each row
> naming its group; the boxes scroll away with the list, a yellow line and a
> button bring them back, and no tap on the tab blanks the screen any more.

---

## 1 · Status at a glance

| | Before (`3caf13d`, 0.2.0) | After |
|---|---|---|
| First screen on a phone | the filter: 2 league chips, then 43 pitch chips; no match visible, filter not scrollable | **4 select boxes, then today's matches**: 3 full rows on a 360 × 800 dp phone, at least 1 on 360 × 640 (tested) |
| A filter change | whole screen swapped to *Načítání dat ligy…* and back | **list and filter stay**; the new list swaps in when ready; a spinner only after 300 ms |
| Typing a team | each keystroke swapped the field out with the screen: focus and keyboard lost | **field keeps focus and keyboard**; one list per 250 ms pause, not per letter |
| League / group | two chip rows | **selects**: *Všechny ligy* → *6. liga*; then *Celá 6. liga* → *6. liga A … L*; a new league clears the group |
| Pitch | 43 code chips, all of `venues.json` | **select, code and name**, only pitches with a match in the chosen league and group (38 at most); clears when out of scope |
| Dates | none: all 792 fixtures, September first | **Nadcházející** by default · Dnes · Odehrané (newest first) · Všechny termíny; a started, unconfirmed report is never hidden |
| List shape | group heading, then rounds | **one group: rounds; several: days** (*Dnes · čtvrtek 8. 10.*), each row with its group code |
| Filter position | pinned above the list | **first item of the list**; a line at the top says what it is set to once it has gone, and a button brings it back |
| A report started from the list | came back without its badge until the filter changed | **rebuilds when a report changes state** |
| Version | 0.2.0 (2) | **0.2.1 (3)**, Android and the Xcode project |
| Debug APK | 69,948,495 bytes | **70,047,639** (+99,144): **over 70 MB by 47,639 bytes** |
| Tests (shared JVM · Android host · composeApp · importer) | 436 · 383 · 194 · 23 | Gate 1: 436 · 383 · 198 · 23 · Gate 2 and 3: **454 · 401 · 210 · 23** |

---

## 2 · Baseline

`./gradlew build :shared:allTests detekt` at `3caf13d`: **BUILD SUCCESSFUL**.
The four suites were then forced to run (`--rerun-tasks`) and counted from
their JUnit XML:

| Suite | Tests | Failures |
|---|---|---|
| `:shared:jvmTest` | 436 | 0 |
| `:shared:testAndroidHostTest` | 383 | 0 |
| `:composeApp:jvmTest` | 194 | 0 |
| `:league-import:jvmTest` | 23 | 0 |

The same as `prompts/10`'s report ends on.

---

## 3 · Gate 1 — no loading flash (`9093129`)

### The measurements

`ListFixtures` against **all of league 6** (the shipped files, 792
fixtures), warm, 200 runs each on the JVM and 40 on the emulator. Both
harnesses were temporary and are not committed: a JVM test reading the
shipped files (against prompt 10's rule, which is why it went), and a
timing loop in `FixturesViewModel.init` on the emulator, logged through
`System.out`.

| Filter | JVM median (worst) | Emulator median (p95, worst) |
|---|---|---|
| none | 0.10 ms (0.66) | 2.8 ms (4.5, 5.9) |
| league 6 | 0.10 ms (0.44) | 1.9 ms (2.5, 2.6) |
| group 6K | 0.01 ms (0.06) | 0.9 ms (1.4, 1.5) |
| pitch ZAK (busiest, 77 fixtures) | 0.02 ms (2.05) | 1.0 ms (1.6, 1.8) |
| pitch STOD | 0.02 ms (0.20) | 0.9 ms (1.3, 1.5) |
| team "a" | 0.23 ms (0.70) | 5.5 ms (8.1, 10.9) |
| team "krabice" | 0.14 ms (0.31) | 4.1 ms (5.5, 8.0) |

**The first load**, which parses all twelve files: **115–126 ms** on the
emulator over three cold starts (timed inside `load()`), 133 ms on the JVM
with the JIT cold and 11–13 ms warm. **A filter tap on 0.2.0**, timed in
`load()` from the tap to the list: 3, 4, 4, 28, 43 and 140 ms — the large
ones are the main thread drawing *Načítání* and then the screen again, not
the filtering.

The emulator is x86_64 on a Ryzen 9 9900X. A mid-range phone will be
slower, plausibly several times; nothing measured is near a visible wait
except possibly the cold start.

### What was built

- **`FixturesUiState` is one shape** — the filter, the list, whether a wait
  is showing, a failure — instead of Loading / Ready / Failed. The filter
  is drawn from the state's own filter, set at the keystroke; the list
  catches up.
- **The team field waits 250 ms** after the last keystroke before the list
  follows. Choices apply at once.
- **The list is built on `Dispatchers.Default`.** A filter change does not
  need it; the first load does, or the spinner over it would not spin.
- **A wait over 300 ms** veils the content under a centred spinner
  (`ui/common/WaitVeil.kt`): **blurred where `RenderEffect.isSupported()`**
  — iOS, Android 12+ — and **dimmed below that**. Compose answers the
  question in common code, so no platform code and nothing in `iosMain`.
  It fades in and out over 150 ms.
- **The first load** shows the same spinner, after the same 300 ms, over an
  empty frame. The *Načítání dat ligy…* text survives only as the spinner's
  screen-reader description.

**Why 300 ms.** It sits above every wait measured, with room for a phone
several times slower than the emulator, and below the point where an
unchanged screen reads as a tap that did not register. A spinner for a
shorter wait would itself be the flash this replaced. **In practice it will
almost never show**: if anywhere, on a slow phone's cold start. Built
anyway, as briefed.

### The test that fails on HEAD

`FixturesNoFlashTest`, written first against `3caf13d`'s API and run there.
It holds the summaries read open (`GatedMatchRepository`) so the screen is
seen at the moment the old one went blank. Both tests **failed by
assertion**:

```
typingInTheTeamFieldKeepsTheFieldTheKeyboardAndTheList  FAILED
  Failed: assertDoesNotExist. Reason: Did not expect any node but found '1' node
  that satisfies: (Text … contains 'Načítání dat ligy…')
choosingAPitchKeepsTheListUntilTheNewOneIsReadyThenSwapsIt  FAILED
  (the same)
```

With the loading assertion taken out, the focus assertion fails on its own:
`Expected exactly '1' node but could not find any node that satisfies:
(SetText is defined)` — mid-change, the team field does not exist. Two more
tests prove the 250 ms pause (three quick keystrokes, one list) and the
threshold (no spinner at half of it, a spinner past it, the list still
under it).

### Other screens

Checked every ViewModel for a filter or search that swaps the screen to a
loading state. **None does.** Týmy's search keeps its list while the query
changes (`loading` is only true before the first load); every other
`loading` flag — header, lineup, console, assessment, recap, export, roster
— covers only that screen's first load. Nothing else fixed.

### Gate 1 against its criteria

| Criterion | |
|---|---|
| The measurements | ✅ JVM and emulator, above |
| A test that fails on HEAD: no Loading state, team field still focused | ✅ both failure messages above |
| Green; commit | ✅ 436 · 383 · 198 · 23, detekt green, `9093129` |

---

## 4 · Gate 2 — the filter (`af59a7c`)

### What was built, outcome by outcome

1. **Select boxes, cascading.** Material 3's exposed dropdown, no new
   dependency. League first; a chosen league reveals *Skupina* under it,
   *Celá 6. liga* then *6. liga A … L*; a different league clears the
   group. Then pitch, dates, team. A closed select shows what is chosen;
   every option is a full-width row at least 56 dp tall, the chosen one
   ticked and yellow.
2. **Pitches by name, in scope.** `ARIT  Aritma`. Offered: pitches with a
   fixture in the chosen league and group (`FilterOptions.venuesInScope`);
   38 of the 43 in `venues.json` have one at all. Changing league or group
   clears a pitch the new choice does not play at
   (`FilterOptions.normalise`, applied by the selects and again by the
   ViewModel).
3. **Dates**, default *Nadcházející* (today onwards) · *Dnes* · *Odehrané*
   (before today) · *Všechny termíny*. "Today" is `ListFixtures`'s injected
   `Clock` in the device's time zone. **A started, unconfirmed report is
   never hidden by the dates**; league, pitch and team still apply to it.
4. **The shape follows the scope.** Rows from one group: by round. From
   several: by day, today headed *Dnes · čtvrtek 8. 10.*, others
   *Pátek 9. 10.*, each row with its group code (`6K`) above the pitch.
   *Odehrané* newest first, in both shapes.
5. **The filter scrolls away.** It is the list's first item. Once it has
   gone entirely, a 56 dp button (↑, *Zpět na filtr*) appears bottom right,
   inside the tab's area and so clear of the tab bar; the list ends with
   88 dp of room so it never covers the last row. Tapping it scrolls back to
   the filter.
6. **Kept.** The filter lives in the ViewModel and survives a report and
   back. *Zrušit filtr* (shown whenever the filter is not the default)
   resets everything, dates back to *Nadcházející*. An empty result says
   *Filtru neodpovídá žádný zápas.* under the filter.

Strings in cs, en and uk: 18 new, `fixtures_filter_any` gone. League,
group, team and pitch names are data and are not translated —
*Уся 6. liga*.

`FixtureFilterRow`'s KDoc now argues the reverse of what it did, and says
which half of the chip argument survived.

### The two judgement calls

**One specific match day: not added.** 83 match days this season, 53 still
to come. A select of 53 dates is a scroll, not a choice. *Dnes* covers match
day; *Nadcházející* across groups is already a list of days, each headed
with a weekday and date; and a pitch narrows the upcoming list to a median
of 10 rows (48 at the busiest, ZAK). If wanted, the cheap way is a fifth
dates option, *Den…*, opening Material 3's own date picker with only days
that have a match in scope selectable.

**Can the referee tell the list is filtered once the filter has gone? Yes:
a slim line at the top** — brand yellow, one line, ellipsised — saying the
dates always, then league or group, pitch and team: *Nadcházející · 6. liga
K · ZAK*. Information, not a control; the button is the way back. The
button alone could only say that *a* filter exists, not which.

**Does the filter come back on a short scroll up? No.** It is four or five
fields, about a third of a phone's list; sliding it over the rows being
scrolled back to fights the list, and a second floating copy of a text
field and four menus is where focus and keyboard bugs live. The line and
the button say and do what it would.

All in `docs/DECISIONS.md`, 2026-10-08, with the smaller calls.

### Also in this gate

- **A stale badge, fixed.** The list was built once per filter. A report
  started from it came back without its badge until the filter changed —
  the "reads once" shape `docs/TODO.md` already names. The ViewModel now
  rebuilds on `ListFixtures.reportsChanged()`, which fires only when a
  report's state changes, not on every save. Seen on the emulator:
  *Розпочато* on the row on return, no filter touched.
- **A group chosen without its league gets it**, which kept the existing
  `FixtureFilter(groupId = …)` tests meaningful.
- **The menu is drawn on `surface`.** The renders showed it in Material's
  lavender: the theme sets no `surfaceContainer`. Fixed on the menu; the
  theme gap is logged in `docs/TODO.md`.

### Tests

**Never the shipped files.** `LeagueSix` builds league 6's size in each
test module — 12 groups × 12 teams, 11 rounds, 792 fixtures, 43 pitches
(5 per group, 5 never used), today a Thursday in round 6, fixtures
yesterday and today. Two copies, `shared/commonTest` and
`composeApp/jvmTest`, because the second cannot see the first.

| The prompt asked to prove | Where |
|---|---|
| A phone, default filter, a row in view | `FixturesFilterFlowTest.onASmallPhoneTheTabOpensWithAMatchInView` and `…InUkrainianToo`: the whole shell at 360 × 592 dp (640 less status and navigation bars); the first row's bounds inside the list's |
| The cascade | `FixturesFilterFlowTest.theCascadeAsARefereeDrivesIt` through the real ViewModel; `FixtureFilterRowTest` (group only after a league, league clears group and pitch); `FixtureListAtLeagueScaleTest.changingTheLeague…`, `changingTheGroupKeeps…` |
| Pitch options only in scope | `FixtureFilterRowTest.thePitchSelectOffersOnlyThePitchesInScopeByCodeAndName`; `FixtureListAtLeagueScaleTest.aGroupIsOfferedOnly…`, `withNoLeagueChosen…` |
| Nadcházející hides yesterday's fixture, not yesterday's unconfirmed report | `FixtureListAtLeagueScaleTest.upcomingHides…` (SETUP, IN_PROGRESS and FINISHED kept; CONFIRMED and untouched hidden), `theDateNeverHides…ButLeaguePitchAndTeamStillDo`; `FixturesFilterFlowTest.upcomingHides…` on screen |
| One group: rounds; several: days with group codes | `FixtureListAtLeagueScaleTest.oneGroupInView…`, `severalGroupsInView…`; `FixturesFilterFlowTest.severalGroupsAreHeadedByDay…` |
| The button appears only once the filter is out of view | `FixturesFilterFlowTest.theButtonBackToTheFilterAppearsOnlyOnceTheFilterIsOutOfView` (not after a short scroll; yes past it; gone after the tap) |

Also: options at least 56 dp and full width
(`everyOptionIsAFullWidthRowBigEnoughForAColdThumb`), the button never
covering the last row, the summary line's text, the filter surviving the
screen leaving the composition, the badge, the empty message, Ukrainian
labels with Czech data, and `reportsChanged` firing on a state change and
not on a save.

**Canaries.** Each behaviour was broken in turn and its test run:

| Broken | Test |
|---|---|
| filter taller than the phone | fails |
| no rebuild on a report change | fails |
| button always shown | fails |
| no room under the last row | fails |
| cascade keeps the pitch | fails |
| pitches not scoped | fails |
| dates hide an unconfirmed report | fails (also in `shared`) |
| one heading shape for everything | fails |
| clearing empties the dates too | fails |

**Assertions of the old rule, changed:** `FixtureFilterRowTest` rewritten
for selects (`theChipsStayCompleteWhileAFilterIsInForce` is reversed in
spirit: pitches are now scoped on purpose); `ListFixturesTest` and
`FixtureFilterTest` moved to sections, pinned to a date before their
fixtures, pitches ordered by code, and `theOptionsCarryEveryPitchInTheLeague`
became *…EveryPitchThatHasAMatch*; `PlaceholderMatchAfterTheUpgradeTest`
asks for every date, so it does not quietly test less once the shipped
fixtures are in the past.

### The renders, looked at

`reports/2026-10-08-fixtures-tab-renders.png`: the emulator at **360 dp**
(density forced to 480), real league data, today 8 October. Czech: the
tab as it opens, the pitch select open, the list scrolled with the line and
the button. Ukrainian: the pitch select open, *6. liga C* by round with
*Скинути фільтр*, scrolled with *Майбутні · 6. liga C*.

What looking found: the lavender menu (fixed); *Налаштування* breaking
mid-word in the **tab bar** at 360 dp (pre-existing, logged); the button
covering a mid-list row's pitch code while scrolling (what a floating
button does; never the last row). Ukrainian's longest new strings
(*Скинути фільтр*, *Сьогодні · четвер 8. 10.*) fit on one line.

On the emulator also: typing *alk* in the team field kept focus and the
keyboard (`mInputShown=true`) and narrowed the list.

### Gate 2 against its criteria

| Criterion | |
|---|---|
| The two judgement calls, answered | ✅ above and in DECISIONS |
| The renders, looked at | ✅ six panels, two defects found, one fixed |
| Green; commit | ✅ 454 · 401 · 210 · 23, detekt green, `af59a7c` |

---

## 5 · Gate 3 — version and build (this commit)

`versionCode = 3`, `versionName = "0.2.1"` in `androidApp/build.gradle.kts`;
`CURRENT_PROJECT_VERSION = 3` and `MARKETING_VERSION = 0.2.1` in
`iosApp.xcodeproj/project.pbxproj`, Debug and Release — four lines, nothing
else in the project file. One build number for both platforms, so iOS goes
from build 1 straight to 3: 0.2.0 (2) was numbered there but never built.

`./gradlew :androidApp:clean :androidApp:assembleDebug`: **70,047,639
bytes**, against 0.2.0's 69,948,495: **+99,144 bytes, and over 70 MB by
47,639 bytes** (70,000,000; the brief's "52 KB under" is the same line). Not
fixed here, as briefed. Where it went, entry by entry against
`psmf-0.2.0-debug-c8bafd4.apk`: +94 KB of DEX (`classes6/9/10.dex`), +0.8 KB
of strings. The 31 MB answer is still `materialIconsExtended`
(`docs/TODO.md`).

**The upgrade, on the emulator:** `psmf-0.2.0-debug-c8bafd4.apk` installed,
then this APK over it with `adb install -r`, no uninstall: *Success*,
`versionCode=3`, `firstInstallTime` still 2026-09-01 (it has never been
uninstalled since 0.1.0), and a report started earlier still on its row as
*Rozepsáno*. No schema change in this prompt.

| Criterion | |
|---|---|
| 0.2.1 (3), both platforms, Debug and Release, no `DEVELOPMENT_TEAM` | ✅ |
| Clean debug APK, size against 0.2.0 | ✅ **crosses 70 MB**, said, not fixed |
| Green; commit | ✅ 454 · 401 · 210 · 23 with all four suites forced to run, detekt green |

---

## 6 · Decisions taken, and by whom

| Decision | By | Where |
|---|---|---|
| Select boxes, scoped pitches, dates, a filter that scrolls away, no loading screen | owner | DECISIONS 2026-10-08 |
| 300 ms threshold, blur or dim by `RenderEffect.isSupported()`, work off the main thread, 250 ms typing pause | session | DECISIONS 2026-10-08 |
| No single-day picker; a summary line; no quick return | session, asked for | DECISIONS 2026-10-08 |
| *Odehrané* = before today; shape by rows in view; group brings its league; names from data; rebuild on report changes; menu on `surface` | session | DECISIONS 2026-10-08 |

---

## 7 · Findings worth not rediscovering

1. **`runComposeUiTest` (v2) shares `Dispatchers.Main`'s scheduler.** Set
   `Dispatchers.setMain(UnconfinedTestDispatcher())` and drive time with
   `mainClock.advanceTimeBy` — a `viewModelScope` delay then runs on the
   test's clock. Calling `advanceUntilIdle()` on a scheduler of your own
   **hangs the test** (the frame clock keeps scheduling): the first attempt
   here ran ten minutes before being killed.
2. **There is no platform `Main` in `composeApp`'s `jvmTest`.** Any test
   that builds a ViewModel needs `setMain`, or `viewModelScope` throws.
   `FixturesNoFlashTest` and `FixturesFilterFlowTest` are the first tests in
   the repository to drive a real ViewModel.
3. **`getBoundsInRoot()` is clipped.** An option below a menu's visible
   area measured 48 dp tall; `fetchSemanticsNode().size` is the layout's
   own. Options below the fold need `performScrollTo()` before a click, or
   the click misses.
4. **Popup menus are reachable from JVM Compose tests**, by text, like
   anything else.
5. **`RenderEffect.isSupported()`** is the common-code answer to "can this
   device blur"; `Modifier.blur` is silently ignored below Android 12.
6. **The theme has no `surfaceContainer*`**: menus, dialogs and sheets
   draw in Material's lavender unless told otherwise.
7. **Writing a file through `\\wsl.localhost` from Windows resets its mode
   to 644.** `FixturesViewModel.kt` went 755 → 644 in `9093129`; harmless,
   and the next such file was put back before committing.
8. **Real groups play at 12–24 pitches each**, not the five the test league
   gives them, so the scoped pitch list on a phone is longer than in the
   tests.

---

## 8 · Open items and known gaps

- **Nothing in 0.2.1 has been on a phone.** Emulator only; *On the phone*,
  below.
- **The dim path (Android 9–11) has never been seen.** The emulator has
  only an Android 16 image, and no other was downloaded.
- **The debug APK is over 70 MB.**
- **The spinner has never shown** outside a test: no wait measured comes
  near 300 ms.
- **iOS 0.2.1 (3) is numbered, not built.** Prompt 11.
- **The filter does not survive the process being killed**, as before: it
  lives in the ViewModel, not in saved state.
- *Налаштування* wraps mid-word at 360 dp; the theme's missing
  `surfaceContainer`; both in `docs/TODO.md`.
- `LeagueSix` exists twice, once per test module.

---

## 9 · On the phone

For the owner and the tester, on Android, with
`builds/psmf-0.2.1-debug-<this commit>.apk` in the planning folder. Each
step: what to do, what should happen, what failure looks like. **Step 1
first**: it is the upgrade, and it works only once.

1. **Install over 0.2.0, without uninstalling.** Open the APK on the phone,
   or `adb install -r`. **Expected:** installs as an update; *Settings →
   Apps → Zápis o utkání* says 0.2.1; anything recorded under 0.2.0 is still
   there. **Failure:** *App not installed*, a package conflict, or an empty
   app — stop, do not uninstall, and report it.
2. **The first screen shows matches.** Open Zápasy. **Expected:** four boxes
   (*Všechny ligy*, *Všechna hřiště*, *Nadcházející*, *Tým*) and, below
   them without scrolling, at least one match, under *Dnes · …* if there are
   matches today. **Failure:** a wall of chips, or no match visible without
   scrolling.
3. **The cascade.** Tap *Liga*, choose *6. liga*. **Expected:** a *Skupina*
   box appears under it reading *Celá 6. liga*; tapping it lists *6. liga A*
   to *L*, each a full-width row easy to hit. Choose *6. liga K*, then *Liga*
   → *Všechny ligy*. **Expected:** *Skupina* disappears. **Failure:** the
   group box there before a league is chosen, or still reading *6. liga K*
   after the league changed.
4. **A pitch by name.** With *6. liga K* chosen, tap *Hřiště*. **Expected:**
   rows like *ARIT  Aritma*, code then name, only the pitches 6K plays at (a dozen or two,
   not 43). Choose one, then change the group to one that never plays there.
   **Expected:** *Hřiště* goes back to *Všechna hřiště*. **Failure:** codes
   without names, all 43 pitches, or a pitch kept that the new group does
   not use.
5. **Nadcházející against Odehrané.** Note the first date listed, then
   *Termín* → *Odehrané*. **Expected:** nothing from today on, yesterday's
   matches first, the latest kickoff at the top. Back to *Nadcházející*:
   today first again. **Failure:** past matches under *Nadcházející*, or
   *Odehrané* oldest first.
6. **An unconfirmed report from an earlier day stays listed.** Under
   *Odehrané*, tap a match from an earlier day: the report opens. Go back to
   Zápasy without confirming it, choose *Nadcházející*. **Expected:** that
   match is still listed, at the top, with *Rozepsáno*. **Failure:** it has
   gone, or it has no badge.
7. **Typing a team name.** Tap *Tým* and type a team's name quickly.
   **Expected:** the keyboard stays up, every letter lands, the list narrows
   a moment after you stop. **Failure:** the keyboard drops, a letter is
   lost, or the screen blanks.
8. **The filter scrolls away; the button brings it back.** Scroll down the
   list. **Expected:** the boxes scroll away; a yellow line at the top says
   what is chosen (*Nadcházející · …*), and a round yellow button with an
   arrow appears bottom right, above the tab bar. Tap it. **Expected:** back
   at the boxes, the line and button gone. Scroll to the very end:
   **expected** the last match fully visible above the button. **Failure:**
   no button, a button over the tab bar, or the last match under it.
9. **The filter is kept after a report.** Set a league, group and pitch, tap
   a match, then come back to Zápasy. **Expected:** the same choices in the
   boxes, the same list, and the match you opened now marked *Rozepsáno*.
   **Failure:** the filter reset, or no badge until you change the filter.
10. **No blank flash anywhere.** Change every box a few times and type in
    *Tým*. **Expected:** the list changes in place; nothing ever replaces
    the screen with *Načítání dat ligy…*. A spinner over a blurred list (or
    a dimmed one on Android 9–11) is acceptable only if a change is
    genuinely slow — note it if it ever appears. **Failure:** the screen
    blanks, even briefly.

---

## 10 · Commits

| # | Commit | |
|---|---|---|
| 1 | `9093129` | Zápasy: no loading screen for a filter change |
| 2 | `af59a7c` | Zápasy: select boxes, a date filter, and a filter that scrolls away |
| 3 | *this commit* | 0.2.1 (3), and the report |
