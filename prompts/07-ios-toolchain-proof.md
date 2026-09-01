# Prompt — iOS toolchain proof, against the real repository

> **Part 1 executed 2026-09-01 — verdict (b), recorded in `docs/DECISIONS.md`.**
> Parts 2 and 3 continue in `08-ios-part-2-and-3.md`, which corrects one thing
> stated here: there is no way to *run* iOS tests on this setup, so do not
> follow the `iosArm64Test` line below.

**Where:** the MacBook (2018, Intel) · **Model:** Opus
**Supersedes:** `prompts/02-ios-toolchain-proof.md`, written when this project was
a bare scaffold and deliberately used a throwaway template. The question has moved
on: the app is now nine screens, 388 shared tests and **five iOS `actual`
implementations that have never been compiled by anything.**

---

## Read this before you run it

**The likely answer is already half known, and it is not good.**
`shared/build.gradle.kts` declares only `iosArm64()` and `iosSimulatorArm64()`.
`iosX64` — the Intel simulator target — is deliberately absent, because Compose
Multiplatform stopped publishing an `ios_x64` variant. `iosSimulatorArm64` needs
Apple Silicon.

**So an Intel Mac can compile for a real iPhone and cannot open the simulator at
all.** That is why the prompt below is shaped as three parts with a stop after
each: the compile proof is achievable and valuable, and the run is gated on
hardware nobody has confirmed exists.

---

## Getting the code

    git clone https://github.com/mirekhajek-HSP/PSMF.git
    cd PSMF

Default branch is `main`. Run Claude Code from that directory.

**The repository is public at the time of writing** and is being made private — if
the clone 404s, that has happened and you need to be signed in as an account with
access.

---

```
Establish whether this Mac can build, run and eventually ship the iOS half of a
Kotlin Multiplatform app — mine, not a template. Three parts, STOP AND REPORT
after each. Do not skip ahead to make something work; the point is to find out
what does not.

## The machine

A 2018 MacBook, Intel. I do not know what macOS it runs or what Xcode it can take.
Establishing that is part one, and I want it CHECKED, not recalled.

## The project

Kotlin Multiplatform + Compose Multiplatform. The whole UI is Compose; the Xcode
project only hands a view controller to SwiftUI.

Read first, all in the repo:

  CLAUDE.md
  iosApp/README.md              what the scaffold session already knew and flagged
  docs/BUILD_MATRIX.md          why every version is pinned where it is
  docs/TECH_STACK.md
  docs/DECISIONS.md             the platform decision and what would reverse it

**Nothing on the iOS side has ever been compiled.** Expect failures. They are the
deliverable, not an obstacle to it.

Five `actual` implementations exist and are unverified — every one written on
Linux by somebody who could not build them:

  composeApp/src/iosMain/.../MainViewController.kt
  composeApp/src/iosMain/.../di/AppModule.ios.kt
  composeApp/src/iosMain/.../ui/export/ReportSaver.ios.kt
  composeApp/src/iosMain/.../ui/locale/LocalAppLocale.ios.kt
  shared/src/iosMain/.../data/db/DatabaseDriverFactory.ios.kt

`iosApp/iosApp.xcodeproj/project.pbxproj` was GENERATED ON LINUX and has never
been opened by Xcode. Its own README says to treat it as a starting point, not an
artefact — if Xcode objects, regenerating from the current KMP template and
copying the two Swift files across is a perfectly good outcome.

## PART 1 — can this machine ship at all?

Answer in order. Check each; do not answer from memory, because these change and
a stale answer is worse than none.

  1. What macOS is this Mac on, and what is the NEWEST it can run? Apple has been
     retiring Intel Macs.
  2. What Xcode version does that macOS support?
  3. What Xcode / iOS SDK does Apple CURRENTLY REQUIRE for App Store submission?
     Read Apple's own developer pages for this.
  4. DOES 2 SATISFY 3? This is the real question. If it does not, this Mac can
     develop and cannot submit, and I need that fact now rather than in December.
  5. Confirm the chip: Intel or Apple Silicon. `uname -m`.

### GATE 1
Report all five before touching the project. If 4 is a no, say so plainly and
stop — everything after this becomes a different conversation.

## PART 2 — does MY code compile for iOS?

The valuable part, and achievable on Intel.

  ./gradlew :composeApp:linkDebugFrameworkIosArm64

That is a real iPhone's architecture, cross-compiled, and it does not need a
simulator. It exercises the whole shared module, the whole Compose UI and all
five `actual` files above on Kotlin/Native for the first time.

Then:

  ./gradlew :shared:iosArm64Test        or whatever the test task is called

Work through the failures one at a time and SAY WHAT EACH ONE WAS. A list of what
broke on first contact with a real toolchain is worth more to me than a green
build with the reasons lost.

Things I already expect to be wrong, so do not treat them as surprises:

  - `ReportSaver.ios.kt` is documented as written-but-unbuilt.
  - `LocalAppLocale.ios.kt` uses NSUserDefaults `AppleLanguages` and needs
    `@OptIn(InternalComposeUiApi::class)`, per JetBrains' own guidance.
  - SQLDelight's native driver on iOS is a different artefact from the JVM one.
  - The bundled fonts (Oswald, Noto Sans) are Compose resources and have never
    been resolved on Kotlin/Native.

Do NOT change shared or common code to make iOS compile without telling me first.
If common code has to move, that is a finding about the platform decision, not a
tidy-up.

### GATE 2
  - report every failure and what fixed it
  - `linkDebugFrameworkIosArm64` succeeds, or explain precisely why it cannot
  - say whether anything in `commonMain` had to change, and what
  - commit, and push the branch

## PART 3 — can it RUN?

Two paths, and part 1 decides which.

**If this is Apple Silicon:** run on a simulator.

    ./gradlew :composeApp:linkDebugFrameworkIosSimulatorArm64

then open `iosApp/iosApp.xcodeproj` and run it.

**If this is Intel:** the simulator is not available to this project at all. The
only way to see the app is a PHYSICAL iPHONE over a cable, with a free Apple
Developer account giving seven-day provisioning. If no iPhone is to hand, STOP —
report that part 3 is blocked on hardware and do not spend time working around it.

Either way, when opening Xcode:

  1. Let it migrate the project if it offers.
  2. Confirm the **Compile Kotlin Framework** build phase runs before
     *Compile Sources*. It shells out to
     `./gradlew :composeApp:embedAndSignAppleFrameworkForXcode`.
  3. Confirm `FRAMEWORK_SEARCH_PATHS` resolves to
     `composeApp/build/xcode-frameworks/$(CONFIGURATION)/$(SDK_NAME)`.
  4. Set a development team for signing. **NO SIGNING IDENTITY IS COMMITTED HERE
     AND NONE SHOULD BE.** Do not commit one.

If it runs, check the things that only iOS can break:

  - Czech, English and Ukrainian, including Cyrillic — the fonts are bundled
    Compose resources and this is their first run on Kotlin/Native.
  - The language picker, which uses `NSUserDefaults` on this platform.
  - The database opens and a match survives being backgrounded and killed.
  - The export. Sending and saving are both stubs or unverified on iOS; find out
    which, and say so.

### GATE 3
  - a screenshot of the app running, or a plain statement of what blocked it
  - whichever of the four checks above were reachable

## What I actually need out of this

One question, answered honestly: **can this project ship on iOS, on this
hardware, in this decade?**

Three outcomes and I want to know which:

  a. Yes — it compiles, runs and can be submitted.
  b. It compiles and can be submitted, but iOS UI work needs an Apple Silicon Mac
     or a physical iPhone, because the simulator is unavailable on Intel.
  c. No — something in the chain is broken and iOS needs rethinking.

**(b) is the one I think is most likely, and it is a purchase decision, not a
defeat.** Say so if that is what you find, and say what the cheapest thing that
unblocks it is.

## Do not

- Change common or shared code to make iOS compile, without telling me first.
- Commit a signing identity, a provisioning profile or a team ID.
- Add a dependency to make something work. Report the need instead.
- Bump a version in `gradle/libs.versions.toml`. `BUILD_MATRIX.md` records why
  each one is where it is, and iOS is not a reason to move one unilaterally.
- Push to `main`. Work on a branch and push that.

## Report

Write `reports/2026-09-01-ios-toolchain-proof.md` in the repo — create the
`reports/` directory, it does not exist yet on this side. Same shape as the five
reports already in the project's planning repo, and it must answer a/b/c above in
its first paragraph.

Then COMMIT AND PUSH IT on your branch. That is now how a report gets back to me:
the repository is the channel, not a copied file.
```
