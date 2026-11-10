# Prompt — iOS build 7: 0.4.1 to TestFlight

**Where:** the MacBook Pro 2018 (Intel) · **Model:** the owner's choice
**Follows:** `prompts/17`, built and verified on Windows (`6f5c990`), and
0.4.1 (7), which adds Ultraveteránská liga 1A for the PSMF demo (DECISIONS
2026-11-10). Expect **0.4.1 (7)**. Build 5 (0.3.1) is on TestFlight, and a
tester has it on an iPhone 15 Pro. **0.4.0 (6) was never built for iOS**, so
there is no build 6; the file keeps its name.

**The demo is Thursday 12 November 2026, 18:30, at Mikulovka.** If the
iPhone is used there, build 7 has to be through TestFlight's processing and
installed before then: start early.

0.4.1 is 0.4.0's code with new league data: Ultraveteránská liga 1A (the two
18:30 matches at Mikulova on the 12th) beside league 6, both read from
psmf.cz on 10 November. 0.4.0 is the testers' six points from 0.3.1:
- the Zápis tab lists the reports on the phone, signed ones too;
- any report can be discarded from that list, after a question;
- the captain's tap under a lineup checks that team's lineup first;
- Undo has an outline;
- a goal or a card is corrected or removed by tapping it in the console's
  log;
- the report's text can be copied to the clipboard, for Mail or WhatsApp.

Everything is common code except **one iOS file**: `IosReportClipboard.kt`
in `composeApp/src/iosMain` (one line, `UIPasteboard.generalPasteboard.string
= text`) and its line in `AppModule.ios.kt`. Windows compiled it as a klib
inside `./gradlew build`; **nothing from prompt 17 has been linked or run on
iOS.**

---

```
Build 0.4.1 (7) for iOS and upload it to TestFlight. Part 0 finishes build
5's report first. Four parts; the last only if the owner wants it now.
STOP AND REPORT after each. Work on main; push after each gate.

## Read first

  CLAUDE.md
  reports/2026-10-31-0.4.0-tester-points.md   the opening, the table of
                                              points, Gate 4 (the clipboard),
                                              and "On the iPhone" at the end
  reports/2026-10-22-ios-build-5.md           the last build on this Mac,
                                              and the sizes to compare with
  docs/DECISIONS.md   2026-09-29 (signing: the company team, never a
                      personal one); 2026-10-31 "0.4.0, Gate 4" (why the
                      clipboard is a platform interface)

## The trap on this Mac, still

  git rev-parse --show-toplevel    must end in PSMFApp_Miro/PSMF
  git remote -v                    must show mirekhajek-HSP/PSMF

Run long builds under `caffeinate -i`, on mains power.

## PART 0 — finish build 5's report

Build 5 was uploaded by the owner from the Organizer, and testers have it,
but reports/2026-10-22-ios-build-5.md still says "to be filled in" for Gate
2 and "to be decided" for Gate 3. Ask the owner, or read App Store Connect
if you can see it:
  - Gate 2: the date it was uploaded, and the build number App Store
    Connect shows for 0.3.1.
  - Gate 3: NOT RUN as a checklist (owner's choice), and that a tester
    used build 5 on an iPhone 15 Pro and reported the six points that
    became prompt 17.
If App Store Connect shows a build number for 0.3.1 of 7 or higher, STOP:
build 7 would be refused.

### GATE 0
  - build 5's report finished; push

## PART 1 — pull, versions, signing, build, checks

  - git pull. MARKETING_VERSION 0.4.1 and CURRENT_PROJECT_VERSION 7, Debug
    and Release, matching Android's versionName and versionCode. If not,
    STOP.
  - The team ID comes from the git-ignored Local.xcconfig, as before. After
    a signed build, `git status` shows nothing under iosApp/.
  - ./gradlew :composeApp:linkDebugFrameworkIosArm64 FIRST: the first link
    of prompt 17. Then :composeApp:linkReleaseFrameworkIosArm64, then the
    app, unsigned, Debug and Release.
  - If something breaks in IosReportClipboard.kt or its line in
    AppModule.ios.kt, which only this Mac can link, fix it there with the
    smallest change, say exactly what and why, and keep the behaviour: the
    report's text on the general pasteboard. A break anywhere else
    (commonMain, shared): do not fix it; report it with the message and
    STOP. It is fixed on Windows.
  - Sizes, by `du -sm` as before: ComposeApp.framework debug and release
    against build 5's 275 MB and 150 MB, and the archived app against build
    5's 37,540 KB bundle and 35,083,520 B executable. Android grew 0.73 %;
    expect about that.
  - iosApp/scripts/check-required-reason-apis.sh on the Release build. The
    pasteboard is not a required-reason API; if a new required-reason
    symbol appears anyway, STOP.
  - League files in the bundle, byte-identical to the repository's (cmp):
    15 now, uvl1a.json added.

### GATE 1
  - versions, signing clean, builds green, sizes, check green; push

## PART 2 — archive and upload

  - Archive Release with the COMPANY team, validate, and upload with
    **TestFlight Internal Only**, as the owner did for build 5. Keep the
    archive in the Organizer.

### GATE 2
  - uploaded, with the build number App Store Connect shows; push

## PART 3 — only if the owner wants it now: the iPhone

Ask the owner. If not now, write NOT RUN (owner's choice) and stop.

If yes: update over build 5 through TestFlight, with no delete first.
Before the update, build 5 should hold one report of each status
(Rozepsáno, Probíhá, Dohráno, Potvrzeno) and one stray Rozepsáno, a fixture
tapped by mistake: 0.4.0 must list them, and discard the stray one. No
schema change since build 5 (still 8), so nothing migrates.

First, the demo's match: Zápasy, Nadcházející, Thursday 12. 11.: at 18:30
on MIKU3 Klokan club UVL A – Pražačka ultraseniors A, group UVL 1A, and on
MIKU4 SC Xaverov UVL – Tučňáci UVL. Open the MIKU3 one as far as the
lineup: both squads are there, with their kits. Then discard that report,
so the fixture is clean for Thursday.

Then "On the iPhone" at the end of prompt 17's report, steps 1 to 7, in
this order of importance: 1 (the list after a swipe-away), 6 (the copied
text pasted into Mail and into WhatsApp, and what iOS asks on paste), 3
(the goal and card sheets opened from the log, the keyboard over the
minute and the reason), 2 (both discard questions), then 4, 5 and 7.
Every step is PASS, FAIL or NOT RUN, with what was seen. A screenshot for
each FAIL, and for step 6 whatever the paste looks like.

If there is time, steps 1 to 3 of "On the iPhone" in
reports/2026-10-21-0.3.1-tester-fixes.md (typing "Marek" in Přidat hráče,
the lineup's lowest row above the keyboard): never run on an iPhone.

### GATE 3
  - the steps run, or NOT RUN; push

## Do not

  - Commit a team ID, a signing identity or a profile, in any file.
  - Select a personal team. Do not pick the team in Xcode's Signing menu: it
    writes DEVELOPMENT_TEAM back into project.pbxproj.
  - Change the bundle ID or the version numbers.
  - Change commonMain or shared. This Mac cannot build Android. Report a
    defect with steps to reproduce; it is fixed on Windows. The one
    exception is Part 1's: IosReportClipboard.kt and its Koin line.
  - Bump anything in gradle/libs.versions.toml.
  - Update this iPhone to iOS 27. Xcode 26.3 is this Mac's ceiling.

## Report

reports/<date>-ios-build-7.md, committed and pushed. Open with one sentence:
did 0.4.1 build for iOS with no change, and did the pasteboard line link?
Then each gate against its criteria, and, if Part 3 ran, a table of the
iPhone steps with PASS, FAIL or NOT RUN.
```
