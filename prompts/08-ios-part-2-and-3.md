# Prompt — iOS Parts 2 and 3: does the code compile, and can it run?

**Where:** the MacBook Pro 2018 (Intel) · **Model:** Opus
**Continues:** `prompts/07-ios-toolchain-proof.md`, whose Part 1 passed. Do not
re-run Part 1 — the machine question is answered and recorded.

---

## What Part 1 established

| | |
|---|---|
| Verdict | **(b)** — this Mac can develop **and** submit today |
| macOS | 15.7.9 Sequoia, at its permanent ceiling |
| Xcode reachable | **26.3**, Universal, runs on Intel. Nothing above it ever. |
| App Store floor | Xcode 26 / iOS 26 SDK — **satisfied** |
| Runway | Roughly **April 2027**, when the floor likely moves to a Xcode this Mac cannot run |
| Chip | Intel → **physical iPhone only**, no simulator for this project |

## Also fixed since Part 1

That session was told to read `docs/DECISIONS.md`, found it missing, and rightly
declined to finalise its verdict. **It exists now** — along with `docs/TODO.md`,
`docs/QUESTIONS.md`, `prompts/` and `reports/`. All the planning documents moved
into the repository on 2026-09-01 precisely because of what that session found.

## Before starting: disk

Xcode 26 wants the `.xip` **and** its expansion at the same time, and installs to
roughly 20–40 GB. Part 1 measured **60 GB free**. That is enough but not
comfortable — delete the `.xip` the moment expansion succeeds, and expect
Kotlin/Native to pull down another ~2 GB of compiler toolchain on first build.

---

```
Continue the iOS toolchain proof at Part 2. Part 1 is done and its answers are in
docs/DECISIONS.md under 2026-09-01 — do not redo it.

## Read first

  CLAUDE.md                     it now indexes everything below
  docs/DECISIONS.md             2026-09-01 entries especially; every decision
                                carries what would reverse it
  iosApp/README.md              what the scaffold session flagged about the Xcode
                                project it generated on Linux and never opened
  docs/BUILD_MATRIX.md          why each version is pinned
  docs/TECH_STACK.md

## PART 0 — prerequisites, and one trap

Verify all four before compiling anything. If any fails, STOP and say which.

  1. xcodebuild -version                  must report Xcode 26.x
  2. xcrun --sdk iphoneos --show-sdk-version
                                          must report an iOS SDK. Part 1 found
                                          NONE — only Command Line Tools with
                                          macOS SDKs — which is why linking
                                          could not even be attempted.
  3. java -version                        must be 17+. Part 1 found 15 and 13,
                                          and Gradle 9.7.1 will not start on those.
  4. ./gradlew -version                   must report JVM 17. Installing a JDK is
                                          not the same as Gradle picking it up.

THE TRAP: installing Xcode does NOT repoint the active developer directory.
`xcode-select -p` will still say /Library/Developer/CommandLineTools and every
iOS SDK lookup will keep failing with no obvious reason. Fix:

    sudo xcode-select -s /Applications/Xcode.app
    sudo xcodebuild -license accept
    xcodebuild -runFirstLaunch

## PART 2 — does the code compile for iOS?

    ./gradlew :composeApp:linkDebugFrameworkIosArm64

A real iPhone's architecture, cross-compiled. No simulator needed, which is the
whole reason this is achievable on Intel. It is the FIRST TIME the shared module,
the Compose UI and five `actual` files have been through Kotlin/Native.

**About tests: there is no way to run them here, and prompts/07 was wrong to
imply otherwise.** `iosArm64` is a device target with no test executor — the test
binary can be compiled (`linkDebugTestIosArm64`) but nothing can execute it
without device-test plumbing that does not exist in this project. Compile it if
you like as a further check on the code; do not spend time trying to run it, and
do not add that plumbing.

Work failures one at a time and SAY WHAT EACH ONE WAS. A list of what broke on
first contact with a real toolchain is worth more than a green build with the
reasons lost.

### Specific things I expect to break

Do not treat these as discoveries, and do not assume the list is complete.

  - `LocalAppLocale.ios.kt` — `NSLocale.currentLocale.languageCode` is typed
    `String?` in the Kotlin/Native ObjC interop, while `actual val current` is
    declared `String`. If that is what the SDK gives you, it is a nullability
    mismatch, not a design change.
  - The same file calls `NSUserDefaults.standardUserDefaults.setObject(...)`
    positionally. Check it against the generated interop signature.
  - `ReportSaver.ios.kt` and the iOS `ReportSender` are DELIBERATE STUBS that
    return "unavailable". They should compile. **Do not implement them** —
    `UIDocumentPickerViewController` and `UIActivityViewController` are their own
    piece of work and are not this session's.
  - Compose resources on Kotlin/Native: the bundled Oswald and Noto Sans fonts,
    and the JSON seed files, have never been resolved on this platform.
  - SQLDelight's `NativeSqliteDriver` is catalogued and wired in
    `shared/build.gradle.kts`, so it should resolve. If it does not, say so —
    that would be a real finding.

### Rules

  - DO NOT change `commonMain` or `shared` to make iOS compile WITHOUT TELLING ME
    FIRST. If common code has to move, that is a finding about the platform
    decision, not a tidy-up.
  - DO NOT bump a version in `gradle/libs.versions.toml`. `BUILD_MATRIX.md`
    records why each is where it is, and iOS is not a reason to move one alone.
  - DO NOT add a dependency to make something work. Report the need.

### GATE 2
  - `linkDebugFrameworkIosArm64` succeeds, or a precise account of why it cannot
  - every failure and its fix, listed
  - whether anything outside `iosMain` had to change, and what
  - the Android build and `:shared:allTests` STILL GREEN afterwards — an iOS fix
    that breaks Android is not a fix
  - committed and pushed on a branch

## PART 3 — can it run?

**Intel, so: a physical iPhone over a cable. There is no simulator for this
project** — Compose Multiplatform no longer publishes `ios_x64`, and
`iosSimulatorArm64` needs Apple Silicon. This is settled; do not try to work
around it.

If no iPhone is to hand, STOP after Gate 2 and say Part 3 is blocked on hardware.
That is a complete and useful answer.

A free Apple Developer account gives seven-day provisioning, which is enough.

Opening the Xcode project:

  1. `iosApp/iosApp.xcodeproj` was GENERATED ON LINUX and has never been opened.
     Let Xcode migrate it. **If it objects, regenerating from the current KMP
     template and copying the two Swift files across is a perfectly good
     outcome** — the Swift is the part worth keeping, per iosApp/README.md.
  2. Confirm the **Compile Kotlin Framework** build phase runs BEFORE
     *Compile Sources*. It shells out to
     `./gradlew :composeApp:embedAndSignAppleFrameworkForXcode`.
  3. Confirm `FRAMEWORK_SEARCH_PATHS` resolves to
     `composeApp/build/xcode-frameworks/$(CONFIGURATION)/$(SDK_NAME)`.
  4. Set a development team for signing. **NO SIGNING IDENTITY, PROVISIONING
     PROFILE OR TEAM ID IS COMMITTED HERE AND NONE SHOULD BE.**

If it runs, check what only iOS can break:

  - Czech, English and Ukrainian, **including Cyrillic**. The fonts are bundled
    Compose resources and this is their first run on Kotlin/Native. Oswald and
    Noto Sans both carry Cyrillic — verified against the files — so a fallback
    face appearing means the resource path failed, not the font.
  - The language picker. It writes `NSUserDefaults` `AppleLanguages` here, which
    is a completely different mechanism from Android's.
  - The database opens, and a match survives the app being backgrounded and
    killed.
  - Export: both saving and sending are stubs on iOS. Confirm they fail
    *gracefully* rather than crashing, and say which.

### GATE 3
  - a screenshot of it running, or a plain statement of what blocked it
  - whichever of the four checks were reachable

## The report

Write `reports/2026-09-01-ios-parts-2-and-3.md`, commit and push it. The
repository is the channel now — nothing gets copied by hand.

Same shape as the five reports already in `reports/`: status at a glance, each
gate against its criteria, decisions taken and by whom, findings worth not
rediscovering, open items, commits.

Open it by confirming or correcting Part 1's verdict of **(b)**, now that you can
see whether the code actually compiles. (b) was provisional precisely because
compilation had not been attempted.

And say plainly if the answer is (c) — something in the chain is broken and iOS
needs rethinking. That is a real possible outcome and I would rather hear it now
than in December.
```
