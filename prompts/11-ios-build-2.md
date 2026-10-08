# Prompt — the next iOS build to TestFlight, and the first upgrade on an iPhone

**Where:** the MacBook Pro 2018 (Intel) · **Model:** Opus
**Follows:** `prompts/10` and `prompts/12`, both on Windows. **Run this only
after `prompts/12` has landed on `main`**: the owner wants its Zápasy fix on
the iPhone too. Expect **0.2.1 (3)**. Build number 2 is skipped on iOS, which
is fine, because a build number only has to increase.

---

## What this is for

Version 0.2.x exists in the repository and has never been built for iOS.
It changes more under iOS than it looks, without touching a line of
`iosMain`:

- **Two database migrations, schema 4 → 6.** This is the first time a
  migration runs on iOS at all. Build 1 is schema 4, and so is every match on
  the testers' iPhones.
- **League 6 from psmf.cz:** 12 groups, 144 teams, 1,601 players and 792
  fixtures in the bundled resources, against one invented group before.
- **The card and clock fixes, and fielding a player by date of birth**, all
  in common code.

So this session builds it, uploads it, and runs it **as an upgrade over
build 1** on a real iPhone. That upgrade is the test that matters, and it
works only once per phone.

---

```
Build the version main carries for iOS, upload it to TestFlight, and run it as an upgrade
over build 1 on an iPhone. Four parts. STOP AND REPORT after each.

Work on main. The owner prefers it to branches. Push after each gate.

## Read first

  CLAUDE.md
  reports/2026-10-07-cards-clock-and-league-6.md   what changed; §10 "On
                                                   the phone" is the checklist
                                                   you adapt in Part 4
  reports/2026-09-29-ios-testflight-readiness.md   this Mac's last session:
                                                   §8, and "First run on an
                                                   iPhone"
  docs/DECISIONS.md   2026-09-29 (signing: the company team, never personal)
                      and the 2026-10-07 / 10-08 entries

## The trap on this Mac, still

  git rev-parse --show-toplevel    must end in PSMFApp_Miro/PSMF
  git remote -v                    must show mirekhajek-HSP/PSMF

## PART 0 — pull, without committing the team ID

The last session left project.pbxproj modified ON PURPOSE: Xcode wrote
DEVELOPMENT_TEAM into it when the company team was selected, and re-sorted
it. Today's main changes four lines of the same file (the version numbers),
so `git pull` will refuse.

  1. Note the team ID Xcode wrote (you will need it in step 4).
  2. Discard the local change to project.pbxproj. It holds nothing worth
     keeping beyond that ID.
  3. git pull. Confirm MARKETING_VERSION and CURRENT_PROJECT_VERSION match
     Android's versionName and versionCode (expected 0.2.1 and 3), Debug and
     Release. If they do not match, STOP.
  4. OUTCOME: the team ID lives in a git-ignored file, so that signing with
     the company team leaves `git status` clean under iosApp/ — today and on
     every later build. The KMP template's approach is a committed
     Config.xcconfig that optionally includes a git-ignored local one
     (#include?), set as the target's base configuration. Something
     equivalent is fine. Say which you chose.
     Verify it the only way that counts: sign with the company team, build,
     and `git status` shows nothing under iosApp/.

The team ID itself must never be committed, in any file.

### GATE 0
  - pulled; version numbers confirmed
  - signed with the company team, `git status` clean under iosApp/
  - commit and push the committed half of the xcconfig change, if any

## PART 1 — build, and the checks that guard an upload

  - ./gradlew :composeApp:linkDebugFrameworkIosArm64, then the app,
    unsigned as in prompts/09 Part 1. Report anything that broke: this is
    the first iOS compile of everything prompts/10 and prompts/12 changed.
  - iosApp/scripts/check-required-reason-apis.sh on the Release build.
    Nothing in prompts/10 or 12 should add a required-reason symbol. If one
    appears, STOP: that is a finding, not something to declare away.
  - Confirm the league files are in the bundle: index.json, venues.json
    and the twelve 6a.json … 6l.json.

### GATE 1
  - builds green, check green, resources present; times against last time

## PART 2 — archive and upload

  - Archive Release, at the version from Part 0, automatic signing, the
    COMPANY team.
  - Validate, then upload with **TestFlight Internal Only**. Build 1 went
    up as "App Store Connect", which the last report said not to repeat
    while the TEST icon stands.
  - Keep the archive in the Organizer (its dSYMs symbolicate TestFlight
    crashes) and the .ipa beside the first one.

### GATE 2
  - uploaded; the build number App Store Connect shows
  - push

## PART 3 — the upgrade, on the iPhone

Wait until Apple has processed the new build and it is offered in TestFlight.

THE ORDER MATTERS. Before updating, the phone must have build 1 installed
from TestFlight AND a match recorded in it: started, a goal, a yellow, a
red. If it has none, record one now in build 1. Without old data there is no
migration to test.

Then update to the new build through the TestFlight app. Do NOT delete the app
first; that wipes the database and the test with it.

Expected, and what failure looks like:
  - It opens. FAILURE: a crash at launch, or right after. A migration that
    fails on iOS will most likely show as a crash at the first database
    read. If it crashes, get the crash log (TestFlight → the build → Crashes,
    or the Organizer's Crashes tab) before anything else.
  - The old match was against the invented 6-K, which is gone. It stays in
    the database and is offered nowhere: no Zápis badge, no Kominíci
    anywhere, an empty followed list. FAILURE: a crash, or an invented team.
  - Zápasy shows twelve groups, 6-A to 6-L, 66 fixtures each.

### GATE 3
  - the upgrade: pass or fail, with what was seen
  - push

## PART 4 — the checklists, recorded step by step

Two lists, both owed, both on this iPhone:

  1. reports/2026-10-07-cards-clock-and-league-6.md §10 "On the phone",
     steps 5–23, adapted to iOS: send and save follow the iOS routes in
     reports/2026-09-29 §4, not Android's.
  2. reports/2026-09-29-ios-testflight-readiness.md "First run on an
     iPhone", steps 6–28, which were never recorded one by one: fonts and
     Cyrillic, the language picker surviving a kill, the database surviving
     a kill, every send and save case, the CSV starting EF BB BF.

Where they overlap, do the step once and record it under both. Every
step gets PASS, FAIL or NOT RUN with what was seen. "Sort of worked" is a
FAIL with a note.

### GATE 4
  - both lists, every step recorded
  - push

## Do not

  - Commit a team ID, a signing identity or a profile, in any file.
  - Select a personal team, for any reason.
  - Change the bundle ID or the version numbers.
  - Change commonMain or shared. This Mac cannot build Android, so a fix
    there would ship to Android uncompiled. Report the defect, with steps to
    reproduce; it is fixed on Windows.
  - Bump anything in gradle/libs.versions.toml.
  - Update this iPhone to iOS 27. Xcode 26.3 is this Mac's ceiling.

## Report

reports/<date>-ios-build-3.md, committed and pushed. Open with one
sentence: did schema 4 → 6 survive the upgrade on a real iPhone? Then each
gate against its criteria, the two checklists in full, every defect with
steps to reproduce, and what is still unverified.
```
