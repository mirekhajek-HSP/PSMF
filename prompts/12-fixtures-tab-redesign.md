# Prompt — Zápasy: a filter that fits the real league, and no loading flash

**Where:** the WSL container · **Model:** Opus
**Follows:** `prompts/10`. Build 0.2.0 (2) went onto the owner's phone on
2026-10-08, and **real league data made the Zápasy tab unusable.**
**Goes before:** `prompts/11` (the iOS build), so the iPhone gets this fix too.

---

## What the owner saw on the phone

1. **No matches at all.** The filter fills the whole screen: league chips,
   twelve group chips, forty-three pitch chips. It sits above the list and
   never scrolls, so the list gets no height, and the filter itself cannot be
   scrolled either.
2. **A flash on every filter tap.** Choosing a pitch blanks the screen to
   *Načítání dat ligy…* and back.

The design is decided: `docs/DECISIONS.md`, 2026-10-08, *"Zápasy: select
boxes instead of chips…"*. Read it first. This prompt is the order of work.

---

```
Redesign the Zápasy filter for the real league (select boxes, a date
filter, a filter that scrolls away, a scroll-to-top button) and remove the
loading flash. Three parts. STOP AND REPORT after each; one commit per gate,
on main.

## Read first

  CLAUDE.md
  docs/DECISIONS.md     2026-10-08 "Zápasy: select boxes…" IS THE SPEC.
                        Also 2026-08-31 "Filters are sized to their data",
                        which it reverses, and the 2026-10-07 entries
  reports/2026-10-07-cards-clock-and-league-6.md
                        what the real data looks like (12 groups, 144 teams,
                        792 fixtures, 43 pitches, kickoffs 10:00–20:45)
  prompts/README.md     "name the outcome, not the API"

Baseline first: ./gradlew build :shared:allTests detekt green at HEAD, the
four test counts recorded. If not green, STOP.

## What is already known

  - FixturesViewModel.onFilterChanged() calls load(), which sets the whole
    screen to FixturesUiState.Loading before rebuilding. That is the flash.
    The team field is part of the screen it replaces, so every keystroke
    also throws away the field being typed in.
  - League data is parsed once and cached (SeedLeagueRepository). After the
    first load, a filter change is in-memory work plus one read of match
    summaries. It should take milliseconds.
  - FixtureFilterRow sits outside the LazyColumn, deliberately: its KDoc
    says a filter that scrolls away cannot be cleared. The scroll-to-top
    button below answers that.
  - The pitch options are all 43 venues in venues.json, whether or not any
    match in view is played there.

## PART 1 — no loading flash

OUTCOME: after the first load, nothing a referee does on this tab blanks the
screen. Changing a filter keeps the filter and the current list on screen
until the new list is ready, then swaps it. Typing in the team field never
loses focus or the keyboard, and does not rebuild the list on every
keystroke.

  - MEASURE first: how long ListFixtures takes for all of league 6, with no
    filter and with each kind of filter. On the JVM at least, and on the
    emulator if you can. Report the numbers.
  - For any wait that is genuinely visible (over roughly 300 ms; you
    choose the threshold and say why), the owner's design: a spinner in the
    middle over the content, the content blurred where the platform can
    (iOS, Android 12+) and dimmed where it cannot (Android 9–11; minSdk is
    28). Never shown for a wait shorter than the threshold. If your
    measurements say it will almost never appear, build it anyway and say
    so.
  - The FIRST load (cold start, league parsing) may show it too, over an
    empty frame. Not the old full-screen text.
  - Check every other screen for the same pattern (a filter or search that
    swaps the screen to Loading). List what you found. Fix the ones that
    flash the same way, and only those.

### GATE 1
  - the measurements
  - a test that fails on HEAD: after a filter change, no Loading state and
    the team field still focused
  - green; commit

## PART 2 — the filter

All outcomes. The mechanisms are yours.

1. SELECT BOXES, CASCADING. League first ("Všechny ligy", then the levels
   the data holds). Choosing a league reveals a group select under it
   ("Celá 6. liga", then 6. liga A…L). Choosing a different league clears
   the group. Then pitch, then the team text field. A closed select shows
   what is chosen. Each option is a full-width row, big enough for a cold
   thumb.

2. PITCHES BY NAME, IN SCOPE. Each option shows code and name. Offered:
   only pitches with at least one match in the chosen league and group. If
   changing the league leaves the chosen pitch with no match, the pitch
   clears.

3. A DATE FILTER, defaulting to UPCOMING (today onwards). Options at least:
   Nadcházející · Dnes · Odehrané · Všechny termíny. "Today" is the
   device's date, from an injected clock so tests can pin it.
   NON-NEGOTIABLE: a fixture whose report is started and not CONFIRMED is
   never hidden by the date filter. The league, pitch and team filters
   still apply to it as usual.
   YOUR CALL, REPORTED BACK: whether picking one specific match day is worth
   adding, and how, given about 80 match days in a season.

4. THE LIST'S SHAPE FOLLOWS THE SCOPE. One group in view: by round, as
   today. More than one group: by day, with a heading per day, and every
   row showing its group (6K). Odehrané lists newest first. Rows keep their
   current content and the report badge.

5. THE FILTER SCROLLS AWAY with the list. Once it is out of view, a
   scroll-to-top button appears, clear of the bottom navigation and never
   covering the last row. Tapping it returns to the filter.
   YOUR CALL, REPORTED BACK: whether the referee can still tell the list is
   filtered once the filter has scrolled away, and how (a slim summary, the
   button itself, or nothing). Also whether the filter should come back on
   a short scroll up. Only if it is cheap and does not fight the list.

6. KEPT FROM TODAY: the filter survives going into a report and back
   (it lives in the ViewModel). "Zrušit filtr" resets everything except
   the date, which returns to Nadcházející. Empty results say so, with the
   filter still reachable.

Strings in cs, en and uk; the select labels translate and the data
(team and pitch names) does not.

TESTS MUST NOT READ THE SHIPPED LEAGUE FILES (prompt 10's rule). Build test
data at league-6 scale (12 groups, 40-odd pitches, dates either side of a
pinned today) and prove at least:
  - on a phone-sized window with the default filter, at least one fixture
    row is visible without scrolling, which is the defect in the owner's
    own words
  - the cascade: group appears only after a league; changing league clears
    the group and an out-of-scope pitch
  - pitch options are only those in scope
  - Nadcházející hides yesterday's fixture but not yesterday's unconfirmed
    report
  - one group: round headings; several: day headings with group codes
  - the scroll-to-top button appears only once the filter is out of view

Render it and look at it before committing: the closed filter, one select
open, and a scrolled list with the button. On a narrow phone width, in Czech
and in Ukrainian (long words).

Update FixtureFilterRow's KDoc. It argues for chips, and that argument is
now reversed.

### GATE 2
  - the two judgement calls, answered
  - the renders, looked at
  - green; commit

## PART 3 — version and build

  - 0.2.1, build 3, both platforms: versionName/versionCode, and
    MARKETING_VERSION/CURRENT_PROJECT_VERSION, Debug and Release. Those
    values only in the Xcode project; never DEVELOPMENT_TEAM. One build
    number for both platforms, so iOS skips 2. That is fine; it only has
    to increase.
  - ./gradlew :androidApp:clean :androidApp:assembleDebug; the size against
    0.2.0's 69,948,495 bytes. The debug APK sits 52 KB under 70 MB. If this
    crosses it, say so and do not fix it here.

### GATE 3
  - green; commit

## Do not

  - Add a dependency. Material 3 already has menus.
  - Change the seed files or the importer.
  - Touch iosMain or iosApp beyond the version numbers.
  - Add network access.
  - Push. The container cannot.

## Report

reports/2026-10-08-fixtures-tab-redesign.md, committed. Open with one
sentence: what does a referee see when they open Zápasy now? End with "On
the phone", a numbered checklist, each step with what should happen and what
failure looks like: the first screen shows matches; the cascade; a pitch by
name; Nadcházející against Odehrané; an unconfirmed report from an earlier
day still listed; typing a team name without losing the keyboard; scrolling
the filter away and the button bringing it back; filter kept after opening a
report and coming back; no blank flash anywhere; install over 0.2.0 without
uninstalling.
```
