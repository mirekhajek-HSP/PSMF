# Prompt — the tab shell, the Týmy tab, and PSMF's visual identity

**Where:** WSL container, `~/dev/psmf-app` · **Model:** Sonnet throughout; Opus for Phase 1 if the navigator fights back

Five phases with gates. Phases 0 and 1 must land in order — everything after
Phase 1 depends on the shell existing. Expect two sessions.

This is a **rework of a working demo, not a new build.** Eight screens, 315 shared
tests and 82 UI tests already exist and pass. Nothing below asks for a screen to be
rewritten; the screens were right and the frame around them was not.

---

```
Rework the demo's shell and add the Týmy tab. Phase 0 first — it is a prerequisite
for every later phase and cannot be done afterwards. Stop at every gate and report.
Do not batch phases.

## Read first

  CLAUDE.md
  docs/DEMO_SCOPE.md            the tab shell, the Týmy tab, the visual identity
  docs/DECISIONS.md             the six entries dated 2026-08-31
  docs/TECH_STACK.md
  docs/BUILD_MATRIX.md

Eight screens exist and work. 315 shared tests, 294 on the Android target, 82 UI
tests, detekt baseline empty. Do not rewrite a screen unless a phase says to.

/golblok-ref is mounted READ-ONLY. It already has the four-tab NavigationBar this
prompt asks for — Home, Match, Teams, Settings in MainActivity.kt — and a sound
type scale in ui/theme/Type.kt. STUDY BOTH. DO NOT COPY CODE: different stack,
different brand, and its clock pauses, which is wrong here.

## PHASE 0 — SQLDelight migrations

Nothing else in this prompt is safe without this, and it cannot be retrofitted:
once someone has installed a build with the new tables, the migration that should
have created them has no version to run from.

There are currently NO migrations. Two earlier phases added columns and the
standing instruction has been "clean install". The phone is now a repeated install
target and Phase 3 adds two tables.

  - Establish the schema version at what is currently shipped.
  - Write the migration mechanism and prove it: build the PREVIOUS commit's schema,
    put a completed match in it, migrate forward, and assert the match is intact
    and readable. A migration test that starts from an empty database proves
    nothing.
  - SQLDelight can verify migrations against a recorded .db schema. Turn that on.

### GATE 0
  - a database written by the previous schema opens, migrates and keeps its data
  - the verification task runs in the build, not by hand
  - :shared:allTests green, detekt green, committed

## PHASE 1 — the tab shell

The demo is an eight-stop wizard and reads as one long form: the referee only ever
presses back and forward, and cannot reach Settings or the fixture list without
abandoning the report.

Four tabs. golblok has the same four, which is where the expectation came from.

  Zápasy      the fixture list, its own tab
  Zápis       the report — THE WIZARD IS UNCHANGED, badged while one is in progress
  Týmy        empty placeholder in this phase; Phase 3 fills it
  Nastavení   settings, its own tab

  - THE REPORT STAYS LINEAR inside its tab. A ZoU has a required order and a
    referee outdoors should not be choosing where to go next. The tabs exist so
    everything ELSE is reachable, not so the wizard becomes free navigation.
  - EACH TAB KEEPS ITS OWN BACK STACK. AppNavigator becomes a map of stacks, one
    per tab, plus the selected tab. Keep it hand-rolled — a navigation library buys
    convenience the demo does not need and costs a version-matrix constraint that
    BUILD_MATRIX.md works to avoid. If you conclude otherwise, say so before doing
    it.
  - Re-selecting the tab you are already on pops that tab to its root. Switching
    tabs does not reset the tab you left.
  - System back pops the current tab's stack; at a tab root it goes to Zápasy, and
    at Zápasy it leaves the app.
  - A MATCH IN PROGRESS IS VISIBLE FROM EVERY TAB, as a badge on Zápis. Without it
    a referee who opens Settings mid-half has no way back. This deliberately
    reverses the old "no ongoing-match hero" line in DEMO_SCOPE.md.
  - Leaving the console mid-match must remain safe. It already is: elapsed time is
    now-minus-kickoff, one stored instant, nothing ticking. Add a test that proves
    switching tabs mid-match and returning shows the same clock.

AppNavigatorTest exists and will need extending, not replacing.

### GATE 1
  - four tabs, independent back stacks, the badge, the two back rules
  - a full match still goes fixture → export with the wizard unchanged
  - switching tabs mid-match and returning loses nothing and moves no clock
  - all 82 existing UI tests still pass, or each change is explained
  - committed

## PHASE 2 — the language picker, and PSMF's look

Two independent pieces. Both are shell-wide, which is why they follow Phase 1.

### 2.1 Language is picked in the app

The last session made language follow the device and flagged the divergence from
DEMO_SCOPE.md itself. Restore the picker: Czech default, English, Ukrainian.

The reason it matters is not preference. THE PHONE IS READ BY MORE THAN ONE
PERSON — the captain confirms the lineup on the referee's phone, and both captains
confirm the recap. A device-level language serves the owner only, and a Ukrainian
captain confirming on a Czech referee's phone is exactly what three languages are
for.

There is NO common Compose Multiplatform API for this. JetBrains document an
expect/actual LocalAppLocale CompositionLocal as the workaround — Android through
Configuration.setLocale, iOS through NSUserDefaults AppleLanguages:

  https://kotlinlang.org/docs/multiplatform/compose-resource-environment.html

Use the documented pattern. Do not invent one, and do not reach for
AppCompatDelegate — it restarts the activity, and the app must not restart
mid-match.

The choice persists in SettingsRepository. Default follows the device on first
run, then obeys the picker.

THE REPORT LANGUAGE IS UNTOUCHED. The ZoU is always Czech whatever the picker says
— ZouLabels and ZouWords are fixed strings, and ZouLanguageTest guards it. That
test must still pass, and add one that switches the picker to uk and asserts the
export is unchanged.

### 2.2 The visual identity

DEMO_SCOPE.md "Visual identity" has the full table. The essentials:

  #FBBA00  brand yellow   surfaces and accents ONLY
  #2B2B2B  ink            body text, dark bars, AND text on yellow
  #000000  black
  #FFFFFF  surface
  #F2F2F2  page
  #D60010  alert red      the red card

  - YELLOW IS NOT A TEXT COLOUR. #FBBA00 on white fails contrast for anything
    anyone has to read. Fills, the selected tab indicator, primary buttons, the
    in-progress badge — with #2B2B2B on top, never white.
  - Dark theme keeps the same yellow and swaps the neutrals.

Fonts, and the trap: PSMF's site uses Anton and Barlow. NEITHER HAS CYRILLIC —
both are latin, latin-ext, vietnamese, verified against Google's font metadata.
Czech diacritics are fine; Ukrainian would silently fall back to a system face
mid-screen. So:

  Display   Oswald      nearest condensed grotesque to Anton, has cyrillic
  Body      Noto Sans   full cyrillic, and what golblok's Type.kt meant to load

Both OFL. BUNDLE THEM as Compose resources with the subsets needed. Never fetch a
font at runtime — the app has no network by design and a referee on a pitch may
have no signal.

Take golblok's TYPE SCALE (sizes, weights, line heights, letter spacing — they are
sound) and its icon vocabulary. Take NOTHING from its palette, that is a different
brand. And note its Type.kt admits its own font families were never loaded and
resolve to FontFamily.SansSerif — do not inherit that.

NO PAUSE, STOP OR PLAY ICON anywhere near the console. golblok has all three.

Three constraints that outrank taste:
  - Respect the system font scale. Check at 130%. The referee population is older.
  - Ukrainian strings run longer than Czech. No fixed-width labels, no single-line
    assumptions.
  - Outdoors, cold, one hand. Touch targets and contrast beat density.

### GATE 2
  - the picker switches all three languages live, with NO restart and no lost state
  - switching language mid-match keeps the match
  - the export stays Czech with the picker on en and on uk
  - Cyrillic renders in the bundled font, not a fallback — prove it, do not assume
  - the whole app carries the palette, in light and dark
  - readable at 130% font scale
  - committed

## PHASE 3 — the Týmy tab

Personal in the sense of FOLLOWED TEAMS, not in the sense of owning them. THIS IS
STILL A SINGLE-PERSONA APP: THE REFEREE'S. There is no captain surface here.

  - a search field at the top, over every team in every bundled group
  - follow a team into a personal list — "Sledované týmy". NOT "download": it is
    all already on the device, and a progress bar for a local file is a lie.
  - browse by league for anyone who would rather scroll than type
  - open a team for its roster: surname and first name, default jersey number, the
    two kit sets, and the RP number where one exists
  - JERSEY NUMBERS ARE EDITABLE HERE, AND ONLY JERSEY NUMBERS
  - names, RP numbers and card history stay read-only, unchanged from screen 3

ABSENCES ARE NOT HERE, and this is the point most likely to be got wrong. Absence
is a fact about ONE MATCH. Putting it in a team screen would either persist it
forever or require a fixture to be attached, at which point it is the lineup
screen with extra steps. It stays on screen 3, where the referee is standing next
to the captain.

Two mechanics:

  - A JERSEY OVERRIDE TABLE. Seed data is a bundled resource replaced wholesale on
    every app update, so an edit cannot live in it. New table, migrated per Phase 0.
  - NOTHING ALREADY WRITTEN MOVES. The lineup snapshots the jersey per appearance,
    so changing a default must not touch a past report. That already holds — add
    the test that proves it, the same way the kit-label rename is proved.

Also update Zápasy in this phase: it is a flat list of one group's fixtures, and
nine divisions is ~900 teams. Filter by league and by team, followed teams first.

Note honestly in your report how thin this looks against 12 placeholder teams in
one group. The psmf.cz scrape is what makes the tab worth having and is tracked
separately.

### GATE 3
  - search, follow, browse, roster, jersey editing
  - a jersey edit does not alter a report already stored — tested
  - the followed list survives a process kill
  - Zápasy filters by league and by team
  - UI tests for search, follow, and jersey editing
  - committed

## PHASE 4 — save the export to the device

Small, and it changes how far the app can be trusted. Files currently go to
app-private storage and out through a mail intent, so a referee can never open
afterwards the work their own name is on.

Add a save step beside the send step: ACTION_CREATE_DOCUMENT on Android, the share
sheet's "Save to Files" on iOS. Same three files, same names, same content — one
BuildZouReport value, unchanged.

Sending still does not send. The referee presses send in their own mail app.

### GATE 4
  - all three files land somewhere the referee can open without the app
  - the saved bytes are identical to the sent ones — including the CSV's BOM
  - committed

## Do not

- Rewrite a working screen. The screens were right; the frame was not.
- Add a backend, a network call, or any account.
- Add absences, drafts or any team-side editing beyond jersey numbers.
- Build screen 9 or report versioning. Still out.
- Copy code from /golblok-ref.
- Change the domain model without telling me first.

## Report

The usual: what was decided and by whom, what a device found that tests could not,
anything in DEMO_SCOPE.md or DECISIONS.md this session proved wrong. Include
before/after screenshots of at least the fixture list, the console and the Týmy
tab — the whole phase is a visual one and a table of passing tests does not show
whether it worked.
```
