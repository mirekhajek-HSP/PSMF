# Prompt — iOS build 5: 0.3.1 to TestFlight

**Where:** the MacBook Pro 2018 (Intel) · **Model:** the owner's choice
**Follows:** `prompts/15`, built and verified on Windows (`d4bb240`). Expect
**0.3.1 (5)**.

0.3.1 is the testers' six points from 0.3.0:
- the cursor jumping in dialogs ("arekM"), and the keyboard closing on a low
  field;
- compact lineup rows, and a lineup that survives the app being killed
  (schema 8);
- the console's buttons, and the Ukrainian Settings tab;
- the whistles in the console log;
- the kit shirts.

Plus `materialIconsExtended` gone. Every change is common code, and **none
of it has run on an iPhone**.

---

```
Build 0.3.1 (5) for iOS and upload it to TestFlight. Three parts; the third
only if the owner wants it now. STOP AND REPORT after each. Work on main;
push after each gate.

## Read first

  CLAUDE.md
  reports/2026-10-21-0.3.1-tester-fixes.md   what changed; Gate 6 for the
                                             framework sizes; "On the iPhone"
                                             at the end
  reports/2026-10-19-ios-build-4.md          the last build on this Mac
  docs/DECISIONS.md   2026-09-29 (signing: the company team, never a
                      personal one)

## The trap on this Mac, still

  git rev-parse --show-toplevel    must end in PSMFApp_Miro/PSMF
  git remote -v                    must show mirekhajek-HSP/PSMF

Run long builds under `caffeinate -i`, or on mains power: last time the Mac
slept on battery and the timings were meaningless.

## PART 1 — pull, versions, signing, build, checks

  - git pull to d4bb240 or later. MARKETING_VERSION 0.3.1 and
    CURRENT_PROJECT_VERSION 5, Debug and Release, matching Android's
    versionName and versionCode. If not, STOP.
  - The team ID comes from the git-ignored Local.xcconfig, as before. After
    a signed build, `git status` shows nothing under iosApp/.
  - ./gradlew :composeApp:linkDebugFrameworkIosArm64 and
    :composeApp:linkReleaseFrameworkIosArm64, then the app, unsigned, Debug
    and Release. This is the first iOS compile of prompt 15. Report anything
    that broke, and do not fix it in common code (see "Do not").
  - Measure what dropping materialIconsExtended did. Report the size of
    ComposeApp.framework, debug and release, against 374 MB and 144 MB
    (2026-09-29), and the archived app against build 4's. Prompt 15
    expected the debug framework much smaller and the release one about
    the same; it is UNPROVEN until this run says.
  - iosApp/scripts/check-required-reason-apis.sh on the Release build. If a
    new required-reason symbol appears, STOP.
  - League files in the bundle, byte-identical to the repository's.

### GATE 1
  - versions, signing clean, builds green, sizes, check green; push

## PART 2 — archive and upload

  - Archive Release with the COMPANY team, validate, and upload with
    **TestFlight Internal Only**. Keep the archive in the Organizer.

### GATE 2
  - uploaded, with the build number App Store Connect shows; push

## PART 3 — only if the owner wants it now: the iPhone

Ask the owner. If not now, write NOT RUN (owner's choice) and stop.

If yes: update over build 4 through TestFlight, with no delete first. Before
the update, build 4 should have a match with both lineups whole, and one
lineup half done (which 0.3.0 never saved, so it is expected to be gone).
Then the report's "On the iPhone" list, steps 1 to 8. The number pad and the
dialogs come first: typing "Marek" in Přidat hráče, and the keyboard on the
lineup's lowest row. Every step is PASS, FAIL or NOT RUN, with what was seen.

### GATE 3
  - the steps run, or NOT RUN; push

## Do not

  - Commit a team ID, a signing identity or a profile, in any file.
  - Select a personal team. Do not pick the team in Xcode's Signing menu: it
    writes DEVELOPMENT_TEAM back into project.pbxproj.
  - Change the bundle ID or the version numbers.
  - Change commonMain or shared. This Mac cannot build Android. Report a
    defect with steps to reproduce; it is fixed on Windows.
  - Bump anything in gradle/libs.versions.toml.
  - Update this iPhone to iOS 27. Xcode 26.3 is this Mac's ceiling.

## Report

reports/<date>-ios-build-5.md, committed and pushed. Open with one sentence:
did 0.3.1 build for iOS with no change, and what did dropping the icon
library do to the framework? Then each gate against its criteria.
```
