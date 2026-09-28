# iOS Parts 2 and 3 — it compiles; running it waits for an iPhone

**Session date:** 2026-09-28 (briefed as `prompts/08`, dated 2026-09-01; the
filename keeps the brief's date so the two sit together)
**Machine:** MacBook Pro 15-inch 2018, Intel i7, macOS 15.7.9
**Repository:** `~/Documents/PSMFApp_Miro/PSMF`, branch `ios/part-2-compile`
**Outcome:** Gate 2 met with **zero source changes** · Gate 3 blocked on hardware

---

## Part 1's verdict: (b), confirmed

Part 1 answered **(b)**: *it compiles and can be submitted, but iOS UI work
needs an Apple Silicon Mac or a physical iPhone.* That was provisional, because
compilation had not been attempted.

**It compiles.** Every line of `shared` and `composeApp`, including the five
`iosMain` files, went through Kotlin/Native for `iosArm64` on the first
attempt, and nothing in the repository had to change. The shared module goes
further than compiling: it links into a full arm64 iOS executable against
UIKit, Foundation and the SDK's `libsqlite3`.

**It is not (c).** Nothing in the chain is broken. There is one new clock to
add to the two Part 1 found, and it is recorded below: the Kotlin/Native
compiler now calls an Intel Mac host deprecated.

One qualification on "it compiles", stated rather than hidden: `ComposeApp`
is a **static** framework, so its link does not resolve symbols against the
system. The Compose UI half is therefore proven only as far as a complete,
correct static archive. The app-level link that closes that gap is the first
thing Part 3 does, and it does not need a phone (see §7).

---

## 1 · Status at a glance

| | Before | After |
|---|---|---|
| Xcode | none, Command Line Tools only | **26.3 (17C529)**, active developer directory set, first launch done |
| iOS SDK | none | **iPhoneOS 26.2** |
| JDK | 15 and 13 | **Temurin 17.0.20.1**, picked up by Gradle as launcher *and* daemon |
| `linkDebugFrameworkIosArm64` | never attempted | ✅ **first attempt**, 14 min 25 s cold |
| `:shared` test executable, iosArm64 | never attempted | ✅ linked: Mach-O arm64, links `libsqlite3`, UIKit, Foundation |
| `:shared:jvmTest` | 388 | **388**, 0 failures, on the Mac |
| `:composeApp:jvmTest` | 162 | **162**, 0 failures, on the Mac |
| Files changed to make iOS compile | — | **none** |
| Running on a device | never | **blocked: no iPhone connected** |

---

## 2 · Part 0 — prerequisites

All four failed at the start of the session. Part 1 had left the Mac as it
found it, which is correct; installs are the owner's to make.

| # | Check | At start | At end |
|---|---|---|---|
| 1 | `xcodebuild -version` | ❌ no Xcode anywhere | ✅ Xcode 26.3, build 17C529 |
| 2 | `xcrun --sdk iphoneos --show-sdk-version` | ❌ `SDK "iphoneos" cannot be located` | ✅ 26.2 |
| 3 | `java -version` | ❌ 15 | ✅ 17.0.20.1 (Temurin) |
| 4 | `./gradlew -version` | not run, blocked by 3 | ✅ Gradle 9.7.1, launcher JVM and daemon JVM both 17 |

**Three snags on the way, none of them the one the brief predicted:**

- **Xcode was unpacked into `~/Downloads`, not `/Applications`.** So
  `sudo xcode-select -s /Applications/Xcode.app` pointed at nothing. The quick
  way to confirm an Xcode wherever it sits, without sudo and without changing
  anything: `DEVELOPER_DIR=<path>/Contents/Developer xcodebuild -version`.
  After `mv` into `/Applications`, the brief's three trap commands worked as
  written.
- **Homebrew was too old to install anything.** 4.2.16 (April 2024), with a
  shallow `homebrew-core` clone last updated in May 2020. It crashed parsing
  today's cask API (`undefined method 'first' for nil`) and refused to update
  because of the shallow clone. The fix was `brew untap homebrew/core` (the
  API makes the tap unnecessary), then `brew update`, then
  `brew install --cask temurin@17`.
- **Homebrew no longer supports Intel Macs**, as of September 2026. It still
  installed this cask, but it builds no bottles for this configuration any
  more. That is a third party independently ending Intel support on the same
  timeline as Apple.

**Also worth knowing before the next session on this Mac:** the git status a
session sees at startup belongs to a repository rooted at `/Users/kosara`
(remote `MargovynaMorkovyna/20240420test`), not to this project. The project
is `PSMFApp_Miro/PSMF/`, with its own `.git`. Any git command run from the
parent folder goes to the wrong repository.

---

## 3 · Part 2 — does the code compile for iOS?

```
./gradlew :composeApp:linkDebugFrameworkIosArm64      BUILD SUCCESSFUL in 14m 25s
```

**No failures to work through: there were none.** The first contact with a
real toolchain compiled `:shared` and `:composeApp` for `iosArm64` and linked
the framework on the first run. That is the finding, and the list of what did
*not* break is worth as much as a list of what did:

| Expected to break | What happened | Why |
|---|---|---|
| `LocalAppLocale.ios.kt`: `NSLocale.currentLocale.languageCode` as `String?` against `actual val current: String` | ✅ compiled unchanged | `NSLocale.h` in the iOS 26.2 SDK sits inside `NS_HEADER_AUDIT_BEGIN(nullability, sendability)` and `languageCode` is not marked `nullable`, so Kotlin/Native imports it as `String`. The mismatch would exist only against an unaudited header. |
| The same file's positional `setObject(listOf(value), LANGUAGES_KEY)` | ✅ compiled unchanged | `-setObject:(nullable id)value forKey:(NSString *)defaultName` imports as `setObject(value: Any?, forKey: String)`, so a positional call is valid. |
| `ReportSaver.ios.kt`, iOS `ReportSender` stubs | ✅ compiled | Left as stubs, as instructed. |
| Compose resources on Kotlin/Native | ✅ the resource-collector tasks ran for iosArm64 | Only the *generation* is proven. The fonts and JSON are packaged into the app by `embedAndSignAppleFrameworkForXcode`, not into the framework, so they are first exercised in Part 3. |
| SQLDelight `NativeSqliteDriver` | ✅ resolved, and **linked** | `shared/build/bin/iosArm64/debugTest/test.kexe` is a Mach-O arm64 executable, and `otool -L` shows `/usr/lib/libsqlite3.dylib`. |

**The output, checked rather than trusted:**
`composeApp/build/bin/iosArm64/debugFramework/ComposeApp.framework`, 374 MB
(debug, static), `lipo`: arm64 only, `MinimumOSVersion` 15.0, and `ComposeApp.h`
exports `+ (UIViewController *)mainViewController`, which is what
`ContentView.swift` calls.

### The test binaries

Compiled, as the brief allowed, as a further check on the code. Not run:
`iosArm64` has no test executor, and none was added.

| | Result |
|---|---|
| `:shared:linkDebugTestIosArm64` | ✅ a full executable link, the strongest evidence this session has |
| `:composeApp:linkDebugTestIosArm64` | `NO-SOURCE`: composeApp's tests live in `jvmTest`, so there is nothing to link |

### Gate 2 against its criteria

| Criterion | |
|---|---|
| `linkDebugFrameworkIosArm64` succeeds | ✅ first attempt |
| Every failure and its fix, listed | ✅ none occurred; the five predicted ones are accounted for above |
| Anything outside `iosMain` changed? | ✅ **nothing changed anywhere**, `iosMain` included |
| Android build and `:shared:allTests` still green | ⚠️ **see below**: true by construction, not re-run on this host |
| Committed and pushed on a branch | ✅ `ios/part-2-compile` (docs and this report only) |

**On the Android criterion, plainly.** This Mac has no Android SDK and no
Docker, so neither `:androidApp:assembleDebug` nor `:shared:allTests` can run
here. `allTests` fails in one second at configuration with
`SDK location not found`, before any test starts. What *could* run on the
Mac did: `:shared:jvmTest` (388) and `:composeApp:jvmTest` (162), all green.
The Android criterion exists to catch an iOS fix that breaks Android, and no
fix was made: the branch changes no source or build file, so the Android build
is byte-for-byte the one at `b9eab23`. A container run on the Windows machine
would confirm it in a minute if a receipt is wanted.

---

## 4 · Part 3 — can it run?

**Blocked on hardware.** `xcrun devicectl list devices` reports *No devices
found*, and no iPhone shows on USB. Per the brief, the session stopped after
Gate 2.

Nothing in Part 3 was attempted. The Xcode project has still never been
opened, and none of the four device checks (Cyrillic through the bundled
fonts, the `AppleLanguages` picker, the database surviving a kill, and the
stub exports failing gracefully) was reachable.

### Gate 3 against its criteria

| Criterion | |
|---|---|
| A screenshot of it running, or a plain statement of what blocked it | ⛔ **no iPhone connected**, the only blocker. Every software prerequisite is now in place. |
| Whichever of the four checks were reachable | none were; all four need the app on a device |

---

## 5 · Decisions taken, and by whom

| Decision | Who | Note |
|---|---|---|
| Part 1's verdict (b) confirmed; not (c) | This session | On the evidence in §3; the static-framework qualification stated with it |
| Xcode 26.3 Universal and Temurin 17, installed | Project owner | Both installs, and every sudo step, done by the owner in the terminal |
| Homebrew repaired by untapping `homebrew/core`, not unshallowing it | Project owner, on this session's advice | The tap is unnecessary under the API; unshallowing is a multi-gigabyte fetch to keep something unused |
| The Android criterion accepted as true by construction rather than installing an Android SDK on the Mac | This session | The branch touches no source; installing an SDK only to re-prove an unchanged build is tooling the Mac does not otherwise need |
| Stopped after Gate 2 | Per the brief | No iPhone to hand |

---

## 6 · Findings worth not rediscovering

**`:shared:iosSimulatorArm64Test` on an Intel Mac is `SKIPPED` under
`BUILD SUCCESSFUL`.** It compiles and links the simulator test binary, then
runs nothing and still reports success. This is the same family as the
Android host-test trap in `BUILD_MATRIX.md`, a green build that ran no check,
and `CLAUDE.md` listed the command as *"macOS only"*. It is **Apple-Silicon
only**; corrected in `CLAUDE.md` in this commit.

**The Kotlin/Native compiler warns that an Intel Mac host is deprecated.**
Kotlin 2.4.10 prints *"The current host platform 'macos_x64' is deprecated and
will be removed in a future Kotlin release."* JetBrains' own target-support
page still lists Intel macOS as a host that builds final binaries for any
target, with no removal version. So there is no date, but there is a direction,
and it is the same one as Apple's and Homebrew's. It matters because Kotlin
bumps are not optional forever, and the day a Kotlin release drops this host,
iOS compilation on this Mac stops however long Apple's submission floor holds.
**That is a third clock alongside Part 1's two, and possibly the nearest.**

**Nullability in the ObjC interop follows the SDK header, not intuition.**
`languageCode` is `String` here because `NSLocale.h` is nullability-audited.
Before predicting a `String?`, read the header:
`$(xcrun --sdk iphoneos --show-sdk-path)/System/Library/Frameworks/Foundation.framework/Headers/`.

**A static framework link proves less than it looks.** `isStatic = true`
produces an `ar` archive, and system symbols are resolved only when an app or
executable links it. The test executable is what proves `:shared`; for
`:composeApp`, only the app build does.

**How long it takes on this Mac, cold:** 14 min 25 s for the first framework
link, including a 1.6 GB Kotlin/Native toolchain download into `~/.konan`.
Warm incremental runs: about 1 to 1.5 minutes.

**`materialIconsExtended` is now deprecated upstream as well**: *"pinned to
version 1.7.3 and will not receive updates."* That is a second reason for the
TODO item that already wants it dropped for size.

---

## 7 · Open items

**Part 3 needs an iPhone connected by cable.** Nothing else blocks it.

**It can start without the phone.** The app-level link, which is the missing
half of the Compose proof, and the question of whether the Linux-generated
`iosApp.xcodeproj` works at all can both be answered by an unsigned device
build:

```
xcodebuild -project iosApp/iosApp.xcodeproj -scheme iosApp \
  -destination 'generic/platform=iOS' CODE_SIGNING_ALLOWED=NO build
```

That runs the *Compile Kotlin Framework* phase (`embedAndSignAppleFrameworkForXcode`),
links the whole app against UIKit, and copies the Compose resources into the
bundle. It needs no signing identity, so nothing about the team or profile
gets near the repository. It was not run this session, because the brief
said to stop after Gate 2.

**The Android criterion has not been independently re-run** (see §3). It is
true by construction.

**The Kotlin/Native host deprecation has no date.** Watch the "What's new"
page of each Kotlin release before bumping `kotlin` in
`libs.versions.toml`, and bump it only with a Mac run in the loop.

**`iosApp/README.md` said the simulator was the next step and that no bundled
font was expected.** Both were wrong: there is no simulator on this Mac, and
Oswald and Noto Sans are bundled. Corrected in this commit.

---

## 8 · Commits

| | |
|---|---|
| `ios/part-2-compile` | This report; `CLAUDE.md`, `iosApp/README.md`, `docs/DECISIONS.md`, `docs/TODO.md`, `docs/TECH_STACK.md`, `prompts/README.md` and `reports/README.md` brought in line with it. **No source or build file changed.** |

---

## 9 · Next session

1. **Plug in an iPhone** and run Part 3 from `prompts/08` as written.
2. Or, before that, run the unsigned `xcodebuild` in §7. It is the cheapest
   way to learn whether the Xcode project needs regenerating, and it finishes
   the compile proof.
