# Prompt — iOS: what TestFlight requires, and an export that works

**Where:** the MacBook Pro 2018 (Intel) · **Model:** Opus
**Follows:** `prompts/08`, whose Part 2 proved the code compiles for `iosArm64`
unchanged. Part 3 (running on an iPhone) is still blocked on hardware and is
**not** this prompt.

---

## What this is for

Everything that stands between this repository and an uploadable TestFlight build,
**except three things only the owner can supply**: the app icon (coming later), a
role on the company's Apple Developer team that can sign for distribution, and a
physical iPhone.

None of the work below needs a phone. All of it needs the Mac.

**The owner will run it on a real iPhone later**, in a separate session. So this
one writes the code, proves it builds, and hands that later session a checklist
of exactly what to verify on the device.

## Settled since the last prompt

- **Bundle ID is `cz.hspinovace.psmf`**, both platforms, permanent once
  registered. Already in the Xcode project; do not change it.
- **The app icon is deferred.** Do not create a placeholder that could be uploaded
  by mistake — leave the `AppIcon` set empty and say so in the report.

## One trap on this Mac, found last time

The home folder `/Users/kosara` is itself inside another git repository (remote
`MargovynaMorkovyna/20240420test`). Any git command run from the wrong directory
goes there. Before the first git command:

    git rev-parse --show-toplevel      must end in PSMFApp_Miro/PSMF
    git remote -v                      must show mirekhajek-HSP/PSMF

---

```
Make the iOS app uploadable to TestFlight in everything except the icon, and make
the ZoU export actually work on iOS. Four parts, STOP AND REPORT after each.

Work on a branch: ios/testflight-readiness. Do not push to main.

DO NOT SIGN ANYTHING, AND DO NOT SELECT A TEAM IN XCODE — not even to silence a
signing error in the IDE. Selecting a team makes Xcode register the bundle ID to
it, and `cz.hspinovace.psmf` registered to the wrong team (a free personal one
especially) is gone for good: docs/DECISIONS.md, 2026-09-29. Every build in this
prompt runs with CODE_SIGNING_ALLOWED=NO. If something genuinely cannot proceed
unsigned, STOP and say what.

## Read first

  CLAUDE.md                       indexes everything below
  docs/DECISIONS.md               the 2026-09-28 and 2026-09-29 entries especially:
                                  the privacy manifest, the simulator, the bundle ID
  reports/2026-09-01-ios-parts-2-and-3.md
                                  what the last session on this Mac found
  iosApp/README.md
  prompts/README.md               "name the outcome, not the API" — read why

## PART 1 — finish the compile proof

Part 2 last time proved a DEBUG, STATIC framework. That proves less than it looks:
a static framework resolves no system symbols, so the Compose half is only proven
as an archive until an app links it. Close that gap, without signing anything:

    xcodebuild -project iosApp/iosApp.xcodeproj -scheme iosApp \
      -destination 'generic/platform=iOS' CODE_SIGNING_ALLOWED=NO build

This is the FIRST TIME `iosApp.xcodeproj` is opened by Xcode at all — it was
generated on Linux. If Xcode objects to it, regenerating from the current Kotlin
Multiplatform template and copying the two Swift files across is an acceptable
outcome; the Swift is the part worth keeping. Say which happened.

Then, from the built .app, CHECK rather than assume:

  - the Compose resources are inside the bundle — both fonts (Oswald, Noto Sans)
    and the JSON seed files. They are copied by the Compile Kotlin Framework
    phase, not by the framework, so this is their first appearance anywhere.
  - the app binary is arm64, and links UIKit, Foundation and libsqlite3.

And the release build, which has never run:

    ./gradlew :composeApp:linkReleaseFrameworkIosArm64

Kotlin/Native release builds are much slower and optimise differently. Report the
time and the framework size against the 374 MB debug one.

### GATE 1
  - the app links; resources present in the bundle; release framework links
  - whether the Xcode project needed regenerating
  - commit

## PART 2 — the privacy manifest

Uploads without the right privacy manifest are rejected (ITMS-91053).
docs/DECISIONS.md, 2026-09-29, explains why the obvious manifest is not enough:
Compose Multiplatform itself puts required-reason symbols into the binary.

Add `PrivacyInfo.xcprivacy` to the APP TARGET — not the framework. The framework
is statically linked into the app, so the app bundle is what Apple scans.
JetBrains' apple-privacy-manifests plugin is for distributing a library separately
and is NOT needed here.

What JetBrains and our own code say it needs, at minimum:

  User defaults        the language picker writes AppleLanguages      CA92.1
  File timestamp       stat / fstat, from Compose and Kotlin/Native    0A2A.1
  System boot time     mach_absolute_time, from Compose               35F9.1

DO NOT TRUST THAT LIST. Inspect the built app binary for the symbols in Apple's
required-reason API list and declare exactly what is present, with the reason that
honestly applies. Apple's check is symbol-based; ours should be too. Report what
you found against the list above — additions and absences both.

Also in the manifest:
  - tracking: false, and no tracking domains. The app has no network at all.
  - collected data types: empty. The app transmits nothing to us. The ZoU leaves
    the device only through the referee's own mail app, at their action. STATE
    THAT REASONING IN THE REPORT. Do not attempt the App Store privacy LABEL —
    that depends on PSMF's answers to A10, A26 and A28, which are still open.

### GATE 2
  - the manifest is IN THE BUILT .app BUNDLE, not just in the repository. A file
    that is not in the target's resources does nothing.
  - every declared category traced to a symbol actually present
  - commit

## PART 3 — Info.plist, for upload

  - Export compliance. The app makes no network connections and uses no encryption
    of its own, so it qualifies for the exemption. Declare it in Info.plist so App
    Store Connect stops asking on every upload. State the reasoning.
  - Version and build number. TestFlight rejects a build number it has seen
    before. Say where they come from now, and propose — do not build — how they
    should stay in step with Android's versionName / versionCode.

### GATE 3
  - commit

## PART 4 — export that works on iOS

Today both halves are deliberate stubs returning "unavailable". A referee on iOS
can record an entire match and then cannot get the ZoU out — which is the whole
point of the app. This part is the one that matters most.

READ prompts/README.md FIRST. A previous prompt named an Android API where it
meant an outcome, and got three chained system dialogs, implemented correctly.
So below are OUTCOMES. The mechanisms listed are known candidates to weigh, not
instructions.

### Send

OUTCOME: the referee lands in a draft email to psmf@psmf.cz with all three files
attached, and presses send themselves. The app sends nothing, needs no account and
holds no credential — exactly as on Android.

It must work for a referee who does NOT use Apple Mail. Many will use Gmail or
another app and will never have configured Apple Mail.

Candidates: the MessageUI mail composer (pre-fills recipient, subject and
attachments, but only when Apple Mail has an account), and the system share sheet
(works with any app, cannot pre-fill a recipient). A combination is likely right.
Choose, and say plainly what the referee sees in each case — including the case
where the recipient cannot be pre-filled.

### Save

OUTCOME: the referee can open the three files later WITHOUT THE APP, with as few
interactions as iOS allows. On Android they choose a folder once and every later
save is silent.

Candidates, each with a real cost:
  - the document picker in export mode — all three files, but one interaction
    every time
  - a folder picked once and remembered via a security-scoped bookmark — Android
    parity, more code
  - writing into the app's own Documents folder and exposing it in the Files app —
    zero interactions, but the files are deleted with the app

Choose, justify, and SAY WHAT THE REFEREE LOSES with the choice you made.

### Constraints on both

  - Same three files, same names, same bytes: everything goes through
    `ZouDocument.bytes()`, so the CSV's byte-order mark and the CRLF line endings
    are identical to Android's. Do not write a second encoder.
  - The report is ALWAYS CZECH, whatever language the app is in.
  - Fit into what exists: the `ReportSender` interface and the
    `rememberReportSaver()` expect/actual pattern. If either needs to change, TELL
    ME FIRST — they are shared with Android, and this Mac cannot build Android.
  - Presenting UIKit from Compose needs the current view controller. Find it
    without deprecated APIs.
  - A cancelled or failed send or save is NAMED to the referee, never silent.
  - MessageUI and UIKit are system frameworks, not dependencies. Adding a Gradle
    dependency is still forbidden without asking.

### The honest limit

Nothing iOS-specific can RUN on this Mac: no simulator, no device, and iosArm64
tests have no executor. This code will compile and then wait for an iPhone, a
rented Apple-Silicon Mac or CI to be exercised. So keep the UIKit glue THIN and put
anything that can be logic — file naming, choosing a path, deciding which send
route applies — where it can be tested somewhere.

### GATE 4
  - both halves implemented; the app still links (repeat the Part 1 build)
  - for each: what the referee sees, step by step, in every case you handled
  - what is unverified because nothing can run it here — listed, not implied
  - whether anything outside iosMain / iosApp changed. If it did, say so loudly:
    the Android build must be re-checked on the Windows machine before merging.
  - commit

## Do not

- Create or commit an app icon, a signing identity, a team ID or a profile.
- Select a development team in Xcode, for any reason.
- Build for Appetize or any simulator. The owner chose a real iPhone instead.
- Change the bundle ID.
- Bump anything in gradle/libs.versions.toml. The Kotlin/Native Intel-host
  deprecation makes Kotlin bumps a Mac-in-the-loop decision now.
- Add a Gradle dependency without asking.
- Change commonMain or shared without asking.
- Push to main.

## Report

`reports/2026-09-29-ios-testflight-readiness.md`, committed and pushed on the
branch. Open it with one sentence answering: **could a TestFlight build be
uploaded today if the icon, the team role and a signing identity existed?** Then
list exactly what remains, owner's items separated from code items.

End it with a section headed **"First run on an iPhone"** — a numbered checklist
the later device session can follow without reading anything else. It must cover:

  - signing: select the COMPANY team, never a personal one, and why
  - Part 3 of prompts/08: Czech, English and Ukrainian including Cyrillic through
    the bundled fonts; the language picker; the database surviving a kill
  - every item you listed as UNVERIFIED in Part 4, each as a concrete step with
    the expected result — send with Apple Mail configured, send without it,
    save, reopen the saved files without the app, and the bytes: open the saved
    CSV and confirm it starts EF BB BF
  - what a failure of each looks like, so it is recognised rather than rationalised
```
