# Prompt — the six demo screens

**Where:** WSL container, `~/dev/psmf-app` · **Model:** Sonnet, Opus for the live console

Five phases with gates. Expect two or three sessions — stop at any gate.

---

```
Build the demo screens. Phase 0 first — two corrections that must land before any
UI. Stop at every gate and report. Do not batch phases.

## Read first

  CLAUDE.md
  docs/DEMO_SCOPE.md            the six screens, what is in and what is cut
  docs/TECH_STACK.md            stack and platform constraints
  docs/LEAGUE_APP_ANALYSIS.md   §2.5 the ZoU field spec, §5 the design tensions
  docs/BUILD_MATRIX.md          why versions are what they are

The domain model, seed loading and persistence are done and tested — 155 tests.
Do not rebuild them. If a screen seems to need a domain change, say so before
making one.

The Compose and KMP skills in .claude/skills are installed for exactly this work.
Use them, particularly for state ownership and recomposition.

/golblok-ref is mounted READ-ONLY for interaction-design reference. Its live match
console is the one genuinely reusable idea in it. DO NOT COPY CODE — different
stack, and several of its behaviours are wrong here (it pauses its clock; PSMF's
does not).

## PHASE 0 — two corrections before any UI

### 0.1 Kit label must be snapshotted, not referenced

A specification error, flagged in the last build report and confirmed.

The lineup currently records which kit was worn as a REFERENCE. Editing a team's
kit label later would therefore change an old report's Barva dresů. That is the
opposite of what reportedIdentification does, and both fields print on the ZoU.

A report states what was written on the day. So: the lineup stores the kit LABEL
VERBATIM as a snapshot, alongside the reference kept for UI. The report reads the
snapshot and never the reference. Same principle as analysis §5.3.

Test it the way reportedIdentification is tested: change a team's kit label after
a match exists, and prove the stored report is unchanged.

### 0.2 Declare withHostTestBuilder

The KMP library plugin creates no Android host-test compilation without it, so a
test in shared/src/androidUnitTest/ calling fail() currently PASSES. CLAUDE.md
explicitly permits that path, which makes it a documented trap — the same failure
mode as a JUnit 4 test on a JUnit 5 platform: compiles, reports success, never runs.

Declare it, then prove it by planting a failing test and watching it fail.

### GATE 0
  - kit snapshot in place with its test
  - a planted failing Android unit test actually fails
  - :shared:allTests green, detekt green, committed

## Design constraints for every screen

These come from who uses this and where, not from taste.

  - CZECH FIRST. Three languages, no hardcoded user-facing strings, ever.
  - The user is a referee, outdoors, in the cold, possibly in the rain, holding
    the phone in one hand. Generous touch targets. High contrast. Respect the
    system font scale — the population skews older.
  - State lives in ViewModels as StateFlow. No logic in composables.
  - Keep the action row in its own composable with the mirror as a parameter.
    Left-handed mode is deferred, not cancelled, and retrofitting it is surgery.
  - A screen that can lose data must not exist. Persistence already survives a
    process kill; keep it that way.

## PHASE 1 — Fixtures, and Match header

Screens 1 and 2 in DEMO_SCOPE.

Fixtures is deliberately first: it is what finally exercises the Compose-resource
seed path on a real device. That path is tested on the JVM but has never run on
Android.

  Fixtures     a flat list from seed data. Not a dashboard. Tap to start.
  Match header ZoU page 1. Pitch, date, time and group come from the fixture. The
               referee enters main referee, assistant, the R flag for a licensed
               hire, and THE DELEGATING TEAM — a separate field from the two
               playing teams, easy to miss, and the party fined for a bad report.

### GATE 1
  - both screens work on a real Android device, not just an emulator
  - seed data loads from Compose resources ON DEVICE — this is the point of the phase
  - Czech, English and Ukrainian all render, INCLUDING Cyrillic glyphs
  - a smoke test per screen
  - committed

## PHASE 2 — Lineup

Screen 3. The hardest screen after the console, and the one carrying the most
decisions. DEMO_SCOPE §3 is the specification; follow it exactly.

Summary of what it must do:
  - mark who is ABSENT, not who is present — §5.1's inversion, three to five taps
  - jersey number per appearance, defaulting from seed, corrected by exception
  - kit selection per team, defaulting to primary, snapshotted per 0.1
  - identification: RP number never editable, date of birth as the fallback
  - ADD A PLAYER who is not in PSMF's database — first name, surname, date of
    birth, NO RP FIELD OFFERED, flagged as pitch-added
  - suspension badge, ADVISORY ONLY, carrying its asOf date. Never blocks. Never
    says "eligible". Never a green tick. Fielding an ineligible player is a
    technical forfeit, so a false clearance would be the app causing the harm.
  - captain confirms per team — a tap, not a signature

### GATE 2
  - all of the above, on a device
  - adding a player works and the player cannot acquire an RP number
  - UI tests for the absent-marking flow and the add-player flow
  - committed

## PHASE 3 — Live console

Screen 4. Use Opus. The most interaction-dense screen and the one worth studying
/golblok-ref for — the tabbed player list and tap-to-log model, not the code.

  - goals: time, jersey number, scorer, running score. A GOAL MAY HAVE NO SCORER.
  - cards: mandatory free-text reason. Red records straight vs 2. ŽK.
  - minute is not an Int — 30´+ and 60´+ are real values and must be enterable
  - undo of the last event
  - sent-off players visibly disabled
  - NO substitutions, NO assists — neither appears on the ZoU
  - THE CLOCK NEVER PAUSES. 2 × 30 gross, referee adds time. golblok pauses; that
    is wrong here.
  - power play: 10 minutes after a dismissal, not shortened by a goal, unaffected
    by further dismissals, running alongside a clock that does not stop
  - the clock is derived from the kickoff timestamp, not a ticking service — iOS
    cannot run a background timer at all

### GATE 3
  - a full match can be recorded end to end on a device
  - kill the app mid-match and confirm it resumes with everything intact
  - UI tests for logging a goal, a card, and a dismissal starting a power play
  - committed

## PHASE 4 — Assessment, Recap & confirm, Export

Screens 5, 6 and 7, plus the read-only Settings screen.

  Assessment  NH, Čd, Č, B per team plus the mandatory commentary. Č and B start
              null — defaulting them to "yes" would quietly waive fines. Editable
              until export, pending A6.
  Recap       half-time score, final score, explicit winner, full event list, the
              assessment block, and an explicit "no cards were issued" affirmation
              where applicable. §5.5: whatever is not on this screen is not being
              checked, so build it as a complete document, not a summary.
  Export      JSON, CSV and formatted text. Out by platform email intent to
              psmf@psmf.cz. THE REPORT IS ALWAYS CZECH regardless of app language —
              export labels are fixed strings, never localised resources.
  Settings    read-only. Language, theme. League rules shown as information, not
              settings.

Weight effort towards Export. The analysis §1 is blunt about it: the value sold to
PSMF is not a better referee experience, it is the elimination of a week of
transcription. The output file is what demonstrates that, not the app.

### GATE 4
  - a complete match produces a complete ZoU in all three formats
  - the export is Czech with the app set to English and to Ukrainian
  - report-readiness blocks export when something mandatory is missing
  - committed

## Do not

- Add a backend, a database beyond what exists, or any network call.
- Build screen 9, amending a finished match. Out of the demo.
- Build report versioning. Out of the demo.
- Copy code from /golblok-ref.
- Change the domain model without telling me first.
```
