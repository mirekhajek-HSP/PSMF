# Prompt — iOS build 4: 0.3.0 to TestFlight, and the upgrade over build 3

**Where:** the MacBook Pro 2018 (Intel) · **Model:** Opus
**Follows:** `prompts/13`, built and verified on Windows (`09c3fae`). Expect
**0.3.0 (4)**.
**Also owed:** the end of `prompts/11`. Its report in git stops at Gate 1,
although build 3 went up to TestFlight and the tester used it.

---

## What this is for

0.3.0 is the tester's eighteen points from build 3, and it changes the
report flow end to end:
- **Lineups:** mark who came, a captain and a goalkeeper, and the number from
  the card.
- **Console:** a goal at its own minute, a whistle taken back, mm:ss.
- **Recap:** the result from the goals, and three signatures by tap.
- **Everywhere:** the keyboard goes away, and Zpět is on every step.

Everything is in common code, and **none of it has run on an iPhone**. Two
things in it are iPhone questions above all:
- **The keyboard.** The iOS number pad has no return key, so a tap outside or
  a scroll is the only way to dismiss it.
- **The way back.** iOS has no system back button.

It also carries **migration 6 → 7**, the second migration ever to run on
iOS, so this session runs it **as an upgrade over build 3**, with real
reports in it.

---

```
Finish the record of build 3, then build 0.3.0 (4) for iOS, upload it to
TestFlight, and run it as an upgrade over build 3 on an iPhone. Five parts.
STOP AND REPORT after each.

Work on main. Push after each gate.

## Read first

  CLAUDE.md
  reports/2026-10-11-ios-build-3.md   build 3's report, stopped at Gate 1
  reports/2026-10-17-report-flow-after-iphone-test.md
                     what 0.3.0 changed. §8 is the Android checklist,
                     §9 the iPhone one. Part 4 runs them.
  prompts/11-ios-build-2.md   the mechanics of the last build; this prompt
                              repeats only what changed
  docs/DECISIONS.md   2026-09-29 (signing: the company team, never a
                      personal one) and the four 2026-10-17 entries

## The trap on this Mac, still

  git rev-parse --show-toplevel    must end in PSMFApp_Miro/PSMF
  git remote -v                    must show mirekhajek-HSP/PSMF

## PART 0 — finish build 3's report

reports/2026-10-11-ios-build-3.md has Gates 0 and 1. Parts 2 to 4 of
prompts/11 ran (the build was uploaded and tested) but nothing of them is
in git. Pull first; it should fast-forward.

Fill in what is known. Ask the owner for anything you cannot find on this
Mac yourself (the Organizer, App Store Connect, the TestFlight app):
  - the upload: the build number App Store Connect shows, and when
  - THE ANSWER THAT REPORT EXISTS FOR: did schema 4 → 6 survive the upgrade
    over build 1 on a real iPhone? Did the old match survive, and did
    anything crash?
  - the three checklists of prompts/11 Part 4, as far as they were run.
    A step nobody recorded is NOT RECORDED, never PASS.
  - what the tester reported. Those eighteen points are in
    reports/2026-10-17 §2; link them rather than copy them.

Write the opening sentence prompts/11 asked for, and mark the report as
finished.

### GATE 0
  - build 3's report finished, committed and pushed

## PART 1 — pull, versions, signing

  - main at 09c3fae or later. MARKETING_VERSION 0.3.0 and
    CURRENT_PROJECT_VERSION 4, Debug and Release, matching Android's
    versionName and versionCode. If not, STOP.
  - The team ID still comes from the git-ignored
    iosApp/Configuration/Local.xcconfig (prompt 11, c860e87). After a signed
    build, `git status` shows nothing under iosApp/.

### GATE 1
  - versions confirmed; git status clean after signing

## PART 2 — build, and the checks that guard an upload

  - ./gradlew :composeApp:linkDebugFrameworkIosArm64, then the app,
    unsigned, Debug and Release. This is the first iOS compile of
    everything prompts/13 changed. Report anything that broke, and do not
    fix it in common code (see "Do not").
  - iosApp/scripts/check-required-reason-apis.sh on the Release build.
    Nothing in prompts/13 should add a required-reason symbol. If one
    appears, STOP: that is a finding, not something to declare away.
  - League files in the bundle, byte-identical to the repository's, as last
    time (they did not change).
  - Times against build 3's.

### GATE 2
  - builds green, check green, resources present; push

## PART 3 — archive, upload, and the upgrade over build 3

  - Archive Release with the COMPANY team, validate, and upload with
    **TestFlight Internal Only**, as build 3 was. Keep the archive in the
    Organizer.

THE ORDER MATTERS. Before updating, the iPhone must have build 3 from
TestFlight with THREE reports recorded in it. This is §8 step 0 of the
2026-10-17 report:
  (a) in progress: kicked off, a goal and a card, not ended. Write down how
      many players are present in each lineup, and the goal's minute.
  (b) finished and not signed
  (c) CONFIRMED: all three signatures. Note its result and the names under
      the signatures.
If the phone does not have them, record them now in build 3. Then update
through the TestFlight app. Do NOT delete the app first: that wipes the
database, and the test with it.

Then §8 A1 to A7 on this iPhone, with what was seen at each. If it crashes,
get the crash log (TestFlight → the build → Crashes, or the Organizer's
Crashes tab) before anything else.

### GATE 3
  - uploaded, with the build number App Store Connect shows
  - the upgrade, A1 to A7: pass or fail, with what was seen
  - push

## PART 4 — the iPhone checklist, step by step

  1. §9, I1 to I11, all of them. These are the steps only an iPhone can
     show: the number pad with no return key, the way back with no system
     button, the edge swipe, the keyboard over the lineup's lower rows, the
     clock after a lock, Dynamic Type, the time zone, export and Ukrainian.
  2. §8 S1 to S6: the signatures.
  3. §8 B, at least 1, 2, 5, 6, 11, 12, 13 and 15. Step 13 needs over
     thirty minutes of play, and the clock past 30:00 has never been seen
     on any phone. The rest of B as time allows; a step not done is
     NOT RUN.

Two things the Windows review wants seen on a real screen. Record them even
where the step passes:
  - The lineup's unmarked players are tall blank rows, about 160 dp each.
    Does a squad of 20 feel like too much scrolling?
  - A lineup is saved only once it is complete. Mark three players, kill the
    app, and reopen. Are the marks gone? On Windows they are expected to be;
    record what the iPhone does.

Every step gets PASS, FAIL or NOT RUN, with what was seen. "Sort of worked"
is a FAIL with a note.

### GATE 4
  - every step recorded; push

## Do not

  - Commit a team ID, a signing identity or a profile, in any file.
  - Select a personal team, for any reason. Do not pick the team in Xcode's
    Signing menu: it writes DEVELOPMENT_TEAM back into project.pbxproj.
  - Change the bundle ID or the version numbers.
  - Change commonMain or shared. This Mac cannot build Android, so a fix
    there would ship to Android uncompiled. Report the defect with steps to
    reproduce; it is fixed on Windows.
  - Bump anything in gradle/libs.versions.toml.
  - Update this iPhone to iOS 27. Xcode 26.3 is this Mac's ceiling.

## Report

reports/<date>-ios-build-4.md, committed and pushed. Open with one sentence:
did schema 6 → 7 survive the upgrade over build 3, with all three reports
meaning what they meant? Then each gate against its criteria, the checklist
steps in full, every defect with steps to reproduce, and what is still
unverified.
```
