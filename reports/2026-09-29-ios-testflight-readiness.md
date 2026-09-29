# iOS TestFlight readiness, and an export that works

**Session date:** 2026-09-29
**Machine:** MacBook Pro 15-inch 2018, Intel i7, macOS 15.7.9 · Xcode 26.3 (17C529) · iOS SDK 26.2 · JDK 17
**Repository:** `~/Documents/PSMFApp_Miro/PSMF`, branch `ios/testflight-readiness`
**Brief:** `prompts/09-ios-testflight-readiness.md`

> **In progress: Parts 1–3 done and merged to `main`; Part 4, the iOS export,
> not started.** The opening answer and the "First run on an iPhone"
> checklist are written when Part 4 is done. Merged early at the owner's
> request, who wants work on `main` rather than on long-lived branches.

---

## 1 · Part 1 — finishing the compile proof

Part 2 last time proved a debug, *static* framework, which resolves no system
symbols. This part links the whole app.

### The Xcode project: built as generated, one flag added

```
xcodebuild -project iosApp/iosApp.xcodeproj -scheme iosApp \
  -destination 'generic/platform=iOS' CODE_SIGNING_ALLOWED=NO build
```

**Xcode 26.3 accepted the Linux-generated project as it was.** No migration
prompt, no parse error and no regeneration; the Swift is untouched. The
automatic scheme worked, and the *Compile Kotlin Framework* phase ran before
*Sources*, as `iosApp/README.md` asked. `FRAMEWORK_SEARCH_PATHS` resolves to
`composeApp/build/xcode-frameworks/$(CONFIGURATION)/$(SDK_NAME)`. Xcode wrote
nothing back to the project file.

**One failure, predicted before the build:**

| | |
|---|---|
| What | `Undefined symbols for architecture arm64`: 30 symbols, every one `_sqlite3_*`, all referenced from `libco.touchlab:sqliter-driver-cinterop-sqlite3-cache.a.o` inside `ComposeApp` |
| Why | SQLiter's cinterop declares `-lsqlite3`, but Kotlin/Native applies linker options only to dynamic frameworks and executables. `ComposeApp` is static (`isStatic = true`), so the flag is dropped and the app must link SQLite itself. Part 2's `shared` test executable linked `libsqlite3` for exactly this reason: it is an executable. |
| Fix | `OTHER_LDFLAGS = ("$(inherited)", "-lsqlite3")` on the app target, Debug and Release. **`iosApp` only.** |

Everything else resolved first time: UIKit, Foundation, Metal, CoreText,
CoreGraphics, QuartzCore, Skia's C++ runtime, and `ComposeApp` itself, which
is not listed in *Link Binary With Libraries* and is auto-linked by Swift's
`import ComposeApp`.

Warnings, recorded so they are not rediscovered as news:
- *All interface orientations must be supported unless the app requires full
  screen.* The target includes iPad (`1,2`) and declares three orientations.
  An upload issue; taken up in Part 3.
- *Run script build phase 'Compile Kotlin Framework' will be run during every
  build because it does not specify any outputs.* Correct and harmless: Gradle
  does its own up-to-date checking.
- `ld: Could not find or use auto-linked framework 'UIUtilities'` and a
  `SwiftUICore` "not an allowed client" note, first build only. Xcode 26 noise
  from auto-linking; the link succeeded.

### Checked in the built `.app`, not assumed

**Debug** (`Debug-iphoneos/iosApp.app`, 66 MB). With `ENABLE_PREVIEWS = YES`,
Xcode puts the real code in `iosApp.debug.dylib` and makes `iosApp` a stub, so
the linkage check is on the dylib: Mach-O arm64, linking
`/usr/lib/libsqlite3.dylib`, `UIKit`, `Foundation`, `Metal`, `CoreText` and the
rest.

**Release** (`Release-iphoneos/iosApp.app`, 46 MB): no debug dylib, and
`iosApp` is a 46 MB arm64 executable linking `libsqlite3`, `UIKit` and
`Foundation`. That is a plain `build`, so it is not stripped yet; an Archive
strips it.

**Compose resources: present, in both builds.** Their first appearance
anywhere on iOS. They sit under
`compose-resources/composeResources/cz.hspinovace.psmf.resources/`, one for
one with `composeApp/src/commonMain/composeResources/`:

| | In the bundle |
|---|---|
| Fonts | `oswald_regular.ttf`, `oswald_bold.ttf`, `noto_sans_regular.ttf`, `noto_sans_medium.ttf`, `noto_sans_bold.ttf` |
| Seed JSON | `files/leagues/index.json`, `6k.json`, `venues.json` |
| Strings | `values/`, `values-en/`, `values-uk/`, compiled to `strings.commonMain.cvr` |
| Also | `files/leagues/README.md`, the contributor note. It ships in the app, which is harmless |

That they are *in the bundle* is proven. That Compose *finds* them at run time
is not; that needs the device (checklist, end of this report).

**Also noticed in the processed `Info.plist`:** Xcode rewrote
`UIRequiredDeviceCapabilities` from `armv7` to `arm64` on its own, and
`CFBundleName` is `iosApp`, which is what the home screen would show. Both are
taken up in Part 3.

### The release framework

```
./gradlew :composeApp:linkReleaseFrameworkIosArm64      BUILD SUCCESSFUL in 12m 12s
```

| | Debug | Release |
|---|---|---|
| `ComposeApp.framework` | 374 MB | **144 MB** (−61%) |
| Link time on this Mac | 14 min 25 s, cold, 2026-09-28 | **12 min 12 s**, compile step up to date |
| App built from it | 66 MB (debug dylib) | **46 MB** executable, unstripped |

Both frameworks are static archives, so their size says little about what
ships. The 46 MB release executable is the useful number.

### Gate 1 against its criteria

| Criterion | |
|---|---|
| The app links | ✅ Debug and Release, unsigned |
| Resources present in the bundle | ✅ fonts, seed JSON and strings, both configurations |
| Release framework links | ✅ 12 min 12 s, 144 MB |
| Whether the Xcode project needed regenerating | **No**: one linker flag added, nothing regenerated |
| Committed | ✅ |

---

## 2 · Part 2 — the privacy manifest

`iosApp/iosApp/PrivacyInfo.xcprivacy`, in the **app** target's *Copy Bundle
Resources*. Not in the framework: `ComposeApp` is statically linked, so the
app bundle is what Apple scans. No plugin was used.

### Declared from the binary, not from the list

Apple's categories and symbols come from its `NSPrivacyAccessedAPIType`
reference, and the reason codes from `NSPrivacyAccessedAPITypeReasons`, both
read this session rather than recalled. They were matched against the
**linked release binary**: 844 undefined imports from `nm -u`, plus ObjC
selectors from `__objc_methname`.

| Category | The brief's list | Present in the linked app | Declared | Why that reason |
|---|---|---|---|---|
| User defaults | `CA92.1` | `_OBJC_CLASS_$_NSUserDefaults`; selectors `standardUserDefaults`, `setObject:forKey:` | **`CA92.1`** | Writes `AppleLanguages` to the app's own domain, readable by no other app |
| File timestamp | `0A2A.1` | `_stat`, `_fstat`, from Skia's `SkOSFile_posix`/`_stdio` and ICU's `umapfile` | **`C617.1`** | Files inside the app container only: the bundled resources and the match database |
| System boot time | `35F9.1` | **nothing.** No `mach_absolute_time`, no `systemUptime` | **not declared** | |
| Disk space | | nothing: no `statfs`/`statvfs`/`getattrlist*`/volume keys | not declared | |
| Active keyboards | | nothing: no `activeInputModes` | not declared | |

**Two corrections to the list, and the reasoning:**

- **`0A2A.1` is not available to an app.** Apple: *"This reason may only be
  declared by third-party SDKs."* It is for a library that wraps `stat` on its
  host app's behalf. An app reading its own container declares `C617.1`.
- **System boot time is absent.** `mach_absolute_time` *is* in the
  `ComposeApp.framework` archive, but only from `libdng_sdk.dng_utils.o`
  (Adobe's DNG SDK, Skia's RAW-photo decoder), which the linker dead-strips.
  It is absent from the Debug dylib and the Release executable alike.
  JetBrains' guidance describes the library; Apple scans the linked app. The
  time sources that *are* linked (`CACurrentMediaTime`,
  `CFAbsoluteTimeGetCurrent`, `gettimeofday`, `std::chrono::steady_clock`,
  `dispatch_time`) are not on Apple's list.

Declaring boot time anyway would have been harmless to Apple's automated
check. It was left out because the brief asked for exactly what is present,
and because a manifest that claims more than the app does is a false
statement that App Review may ask about.

### Tracking and collected data

- `NSPrivacyTracking` is **false** and `NSPrivacyTrackingDomains` is **empty**.
  The app has no network access at all: no networking library is applied to
  any module (Ktor is catalogued but deliberately unused), and the import
  table has no networking symbols.
- `NSPrivacyCollectedDataTypes` is **empty.** "Collected" in Apple's sense
  means data transmitted off the device to the developer or a partner. This
  app transmits nothing. Referee, player and match data are stored in the
  app's own database on the phone. The ZoU leaves the device only when the
  referee chooses to send or save it, through their own mail app or the system
  share sheet, to a recipient they can see and change. Nothing goes to us, and
  nothing goes out in the background.
- **The App Store privacy *label* is not attempted.** It depends on PSMF's
  answers to A10, A26 and A28, which are still open.

### A check that keeps it true

What is present depends on **dead-stripping**, so a Compose bump or a new
screen that reaches Skia's image decoders could pull `mach_absolute_time` in
without any source change here. So there is a check:

```
iosApp/scripts/check-required-reason-apis.sh <.../Release-iphoneos/iosApp.app>
```

It uses Apple's symbol lists and compares the binary with the bundle's
manifest. It fails if a category is **used and not declared**, or **declared
and not used**. **Proven to fail both ways** before being trusted: with
file timestamp removed from a copy's manifest it failed (`used but NOT
declared: stat fstat`, exit 1), and with boot time added it failed
(`declared but no symbol present`, exit 1).

It is a script, not a build phase. As a phase it would fail the owner's first
device build over a manifest detail. Whether to promote it to CI or to a
Release-only phase is an open item.

### Gate 2 against its criteria

| Criterion | |
|---|---|
| The manifest is in the built `.app`, not just the repository | ✅ `iosApp.app/PrivacyInfo.xcprivacy`, Debug and Release, byte-identical to the repo copy |
| Every declared category traced to a symbol actually present | ✅ both; and every *undeclared* category shown absent |
| Committed | ✅ |

---

## 3 · Part 3 — `Info.plist`, for upload

All in `iosApp/`. Verified in the processed `Info.plist` of the built Release
app, not only in the source file.

| Key | Before | After | Why |
|---|---|---|---|
| `ITSAppUsesNonExemptEncryption` | absent | **`false`** | Export compliance, below |
| `CFBundleShortVersionString` | `0.1.0`, hard-coded | **`$(MARKETING_VERSION)`** → `0.1.0` | One source, below |
| `CFBundleVersion` | `1`, hard-coded | **`$(CURRENT_PROJECT_VERSION)`** → `1` | One source, below |
| `CFBundleDisplayName` | absent, so the home screen showed **`iosApp`** | **`Zápis o utkání`**, with `en.lproj` *Match Report* and `uk.lproj` *Протокол матчу* | The same three names as Android's `app_name` |
| `UIRequiredDeviceCapabilities` | `armv7` | **`arm64`** | The binary is arm64 only. Xcode had been rewriting it silently; the source now says what is true |
| `UISupportedInterfaceOrientations~ipad` | absent | **all four** | See below |

### Export compliance: exempt, and why

`ITSAppUsesNonExemptEncryption = false` stops App Store Connect asking the
export-compliance question on every upload. The claim rests on two facts,
both checked in the linked release binary rather than assumed:

- **No network connections.** No sockets, `NSURLSession`, `CFNetwork`,
  Network framework or WebKit in the imports or linked libraries, and no
  networking library applied to any module. So there is no HTTPS and no TLS.
- **No encryption of its own.** No CommonCrypto (`CC*`), no Security framework
  (`SecKey`, `SecItem`, `kSec*`), and no OpenSSL-style symbols. The database
  is plain SQLite. `arc4random_buf` is present; it is random-number
  generation, not encryption.

What remains is iOS's own data protection, which is the operating system's
encryption, not the app's. **Reverses if** the app gains a network call, even
plain HTTPS to a backend, or any encryption of its own. That needs a fresh
answer, not this line.

### Version and build number: where they come from, and a proposal

**Now:** `MARKETING_VERSION = 0.1.0` and `CURRENT_PROJECT_VERSION = 1` in
the Xcode project's target settings, Debug and Release, and `Info.plist`
reads them. The hard-coded duplicate in `Info.plist` is gone. Android sets
`versionName = "0.1.0"` and `versionCode = 1` in `androidApp/build.gradle.kts`.
Today they agree by coincidence, and nothing keeps them agreeing.

**The constraints:** TestFlight rejects a `CFBundleVersion` it has already
seen for a version. Play rejects a `versionCode` that is not higher than every
previous one. Both want a strictly increasing number.

**Proposed, not built:**

1. **One file, `iosApp/Configuration/Version.xcconfig`:**
   ```
   MARKETING_VERSION = 0.1.0
   CURRENT_PROJECT_VERSION = 1
   ```
   The Xcode target uses it as its base configuration. Its `KEY = value` lines
   are trivially parsed by `androidApp/build.gradle.kts`, so Android's
   `versionName` and `versionCode` read the **same two lines**. There are
   no new tools and no Gradle plugin.
2. **One integer build number** for both platforms, bumped for every upload
   to either store. The simplest rule that satisfies both stores and cannot
   collide. Later, CI can set it from its run number and nobody edits it by
   hand.
3. **Marketing version bumped by hand**, deliberately, when there is something
   to call a version.

Not built, because it changes `androidApp/build.gradle.kts`, which this Mac
cannot build, and the brief said propose. Listed in `docs/TODO.md`.

### iPad: kept, which was a default rather than a decision

The Linux scaffold set `TARGETED_DEVICE_FAMILY = 1,2`: iPhone **and** iPad.
With iPad included, Apple requires all four orientations or
`UIRequiresFullScreen`, and the build warned *"All interface orientations must
be supported unless the app requires full screen"*, which becomes an upload
rejection. The fix that changes nothing else is
`UISupportedInterfaceOrientations~ipad` with all four. The iPhone keeps its
three, and the warning is gone in the rebuilt project.

**Owner, after Gate 3: iPad stays.** Welcome, and to be dropped only if it
ever complicates things. So far it has cost this one key.

### Noticed, not changed

- **`Zápis o utkání` is 14 characters**, which the home screen truncates.
  **Owner, after Gate 3: the name is not decided yet and will be shorter.**
  Until then, `CFBundleDisplayName` mirrors Android's `app_name`; change both
  together.
- **Still blocking an upload, all owner items:** the app icon (App Store
  Connect rejects a build without the 1024 × 1024 icon; `AppIcon` stays empty,
  as instructed), a distribution role on the company team, and signing.

### Gate 3 against its criteria

| Criterion | |
|---|---|
| Export compliance declared, with reasoning | ✅ `ITSAppUsesNonExemptEncryption = false`; checked against the binary |
| Where version and build come from, and a proposal for keeping them in step with Android | ✅ project settings, one source; proposal above, not built |
| Committed | ✅ |
