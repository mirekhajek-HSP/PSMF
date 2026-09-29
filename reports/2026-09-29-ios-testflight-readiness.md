# iOS TestFlight readiness, and an export that works

**Session date:** 2026-09-29
**Machine:** MacBook Pro 15-inch 2018, Intel i7, macOS 15.7.9 · Xcode 26.3 (17C529) · iOS SDK 26.2 · JDK 17
**Repository:** `~/Documents/PSMFApp_Miro/PSMF`, branch `ios/testflight-readiness`
**Brief:** `prompts/09-ios-testflight-readiness.md`

**Could a TestFlight build be uploaded today if the icon, the team role and
a signing identity existed? Yes, as far as anything short of an upload can
show.** The Release app links unsigned, its privacy manifest is in the
bundle and matches the binary's symbols, and `Info.plist` declares export
compliance, a single version source and iPad orientations. Apple's own
upload validation needs a signed archive, so the first upload is the final
check. A build from `main` would upload but could not export a report. The
export is on `ios/export`, waiting for the Android check.

**Outcome:** four gates met · Parts 1–3 on `main` · Part 4 on `ios/export`,
not merged until Android is rebuilt on Windows · nothing has run on a device

### What remains

**The owner's** (none is code):

1. **The app icon.** App Store Connect rejects a build without the
   1024 × 1024 icon. `AppIcon` is deliberately empty.
2. **A role on the company's Apple Developer team** that can upload
   (App Manager or Admin; Developer can run on a device but cannot upload).
3. **Signing with that team**: Xcode's automatic signing, the company team
   selected, **never a personal team** (`docs/DECISIONS.md`, 2026-09-29).
4. **The app record in App Store Connect**, created with bundle ID
   `cz.hspinovace.psmf`. An upload needs somewhere to land.
5. **An iPhone**, for the checklist at the end of this report.
6. **The app name**, which will be shorter; both platforms change together.
7. Later, not for TestFlight: the **App Store privacy label** (PSMF's A10,
   A26, A28), and store screenshots, including iPad.

**The code's:**

1. **Rebuild Android on Windows, then merge `ios/export`**:
   `./gradlew build`, `:shared:allTests`, `detekt`, and on a phone: save,
   *back out of the folder picker* (it now says "cancelled"), send.
2. **Run the "First run on an iPhone" checklist**, which is every unverified
   item in Part 4.
3. **Run `iosApp/scripts/check-required-reason-apis.sh` on the Release build
   before every upload**, or make it a CI step.
4. **One version source for both platforms**: proposed in Part 3, not built.

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

---

## 4 · Part 4 — an export that works on iOS

Before this part, both halves were stubs that returned "unavailable": a
referee on iOS could record a whole match and then not get the ZoU out.
Both are now implemented and link into the app. **None of it has run**;
see *Unverified*, below.

> **⚠ SHARED AND ANDROID CODE CHANGED. The Android build must be checked on
> the Windows machine before `ios/export` is merged.** This Mac has no
> Android SDK: the Android files were edited by hand and have not been
> compiled. At the owner's choice, Part 4 sits on the `ios/export` branch
> until that check passes.

### The interface change, asked first

`ReportSender.send` and `ReportSaver.save` returned `Boolean`, and the screen
chose its message from that. Fine for Android. On iOS it would have said *"there
is no mail app on this device"* after a cancelled draft, and *"an email to
psmf@psmf.cz is open"* when the address could not be filled in. Asked before
changing anything; **the owner chose a richer result**:

| | Outcomes | Android reports | iOS reports |
|---|---|---|---|
| `SendOutcome` | `DraftOpened`, `HandedToMail`, `DraftSaved`, `SharedWithoutRecipient`, `Cancelled`, `NoMailApp`, `Failed` | `DraftOpened` or `NoMailApp`, exactly as before (a chooser reports nothing back) | all but `DraftOpened` and `NoMailApp` |
| `SaveOutcome` | `Saved`, `Cancelled`, `Failed` | all three. **Backing out of the folder picker now says "cancelled"** where it used to say "failed or cancelled" | all three |

Plus `ReportSender.prefillsRecipient()`, defaulting to `true`, so the screen
can warn **before** send when the address cannot be filled in. The warning is
Compose, in the referee's language, and tested on the JVM; it is not a native
alert in whatever language iOS happens to be in.

**Changed outside `iosMain`/`iosApp`, listed in full:**

| Where | What |
|---|---|
| `composeApp/commonMain` · `ReportSender.kt`, `ReportSaver.kt` | the outcome types; `prefillsRecipient()` |
| `composeApp/commonMain` · `ExportViewModel.kt` | `sendOutcome` / `saveOutcome` / `recipientPrefilled` replace four booleans |
| `composeApp/commonMain` · `ExportScreen.kt` | one message per outcome; the pre-send note |
| `composeApp/commonMain` · `App.kt` | `saveHandled(outcome)`; Settings told on `Saved` only |
| `composeApp/commonMain` · `ExportRouting.kt`, `MailComposerResult.kt` | **new**: the iOS decisions as plain functions, so they can be tested |
| `composeApp/commonMain/composeResources` · `values`, `values-en`, `values-uk` | 7 new strings; `export_save_failed` reworded, since it no longer covers cancellation |
| `composeApp/androidMain` · `AndroidReportSender.kt`, `AndroidReportSaver.kt` | mapped onto the outcomes. **No behaviour change beyond the cancel message.** Not compiled here. |
| `composeApp/jvmTest` · `ExportScreenTest.kt`, `ExportRoutingTest.kt` | +20 tests |

`shared/` is untouched. `ZouDocument`, `bytes()` and every encoder are untouched.

### Send: what the referee sees, step by step

**The route is chosen at the moment of sending**, by whether Apple Mail has
an account (`MFMailComposeViewController.canSendMail()`).

**A · Apple Mail is set up.** The mail composer:

1. Nothing extra on the export screen.
2. *Odeslat na PSMF* → the Mail composer slides up with **To: psmf@psmf.cz**,
   the subject *ZoU 6K 31.8.2026 Domácí - Hosté*, the text report as the body,
   and the three files attached.
3. Then one of:
   - **Send** → the composer closes → *Předáno aplikaci Mail k odeslání na
     psmf@psmf.cz.* It says **handed to Mail**, not delivered: Mail may hold
     it in the Outbox.
   - **Cancel → Delete Draft** → *Odeslání zrušeno. Nic nebylo odesláno.*
   - **Cancel → Save Draft** → *Uloženo jako koncept v aplikaci Mail. Zatím
     neodesláno.*
   - Mail reports a failure → *Odeslání se nezdařilo. Nic nebylo odesláno.*

**B · No Apple Mail account** (Gmail, Outlook or another app). The share
sheet:

1. **Before anything is pressed**, the export screen says, above the
   buttons: *V tomto zařízení není účet v aplikaci Mail, takže adresu nelze
   vyplnit za vás. Po stisknutí Odeslat vyberte svou poštovní aplikaci a jako
   příjemce vložte psmf@psmf.cz — adresa se zkopíruje.*
2. *Odeslat na PSMF* → **psmf@psmf.cz is copied to the clipboard** → the system
   share sheet opens, offering every app that takes files. It carries the
   subject, the text body and the three files.
3. The referee picks their mail app. **The To field is empty; no share-sheet
   API can fill it.** They paste the address and send.
4. Then:
   - Finished in the app → *Předáno vybrané aplikaci. Adresu nešlo vyplnit —
     zkontrolujte, že zápis šel na psmf@psmf.cz.* It is shown as a
     **warning**, not a confirmation: whether it reached PSMF depends on what
     was typed.
   - Share sheet dismissed → *Odeslání zrušeno. Nic nebylo odesláno.*
   - The attachments could not be written, or the sheet reports an error →
     *Odeslání se nezdařilo. Nic nebylo odesláno.*

**Why a combination, and not one or the other.** The composer alone fails
every referee without Apple Mail, and many of them will be. The share sheet
alone never pre-fills the recipient, even for those who have Apple Mail.
Using the composer where it works and the share sheet where it does not,
with the gap **named before send**, is the most that iOS allows. The
clipboard copy turns "type an email address on a cold pitch" into one paste.

**On iPad** the share sheet is a popover, and UIKit throws if it has no
anchor. It is anchored at the bottom centre of the screen, where the send
button sits.

### Save: the choice, and what the referee loses

**Chosen: a folder picked once and remembered**, the same as Android.

| Candidate | Interactions | Files survive deleting the app | Verdict |
|---|---|---|---|
| Document picker in export mode | one on **every** save | yes | the three-dialogs mistake Android already made once |
| The app's Documents folder, shown in Files | none | **no** | fails the one outcome saving exists for |
| **Folder once, remembered with a bookmark** | **one, on the first save** | **yes** | Android parity; fits the existing Settings row unchanged |

**What the referee sees:**

1. The first *Uložit do zařízení* → the Files folder picker. Choose a folder
   (On My iPhone, iCloud Drive, or another provider) → *Open* → the three
   files are written → *Zápis uložen. K souborům se lze vrátit i bez
   aplikace.* Settings now offers *Změnit složku*.
2. Every later save: **no dialog**. The three files are written straight
   into that folder, **replacing** the previous ones of the same name, so the
   same match saved twice leaves three files, not six.
3. Back out of the picker → *Uložení zrušeno. Nic nebylo uloženo.*
4. A write fails → *Uložení se nezdařilo. Soubory nemusí být kompletní.* It
   stops at the first failure, because a partial save looks complete in a file
   list.
5. The folder was deleted, or its provider revoked access → the next save
   asks for a folder again.
6. Settings → *Vybrat složku* / *Změnit složku* → the picker. Backing out
   keeps the old folder.

**What the referee loses:** one interaction on the very first save, compared
with the Documents-folder route. And if the chosen folder disappears, one more
on the next save. Nothing is lost against Android.

**How it is remembered:** a bookmark to the folder, base64, in the settings
value Android keeps its tree URI in (`exportFolderUri`). It is an opaque
per-device token on both; only the platform's saver reads it. A stale
bookmark is refreshed. Writes go through `NSFileCoordinator`, which iCloud
Drive and other providers need in order to see a change from outside.

### Same bytes, always Czech

- **One encoder.** The composer attachments, the shared files and the saved
  files are all `ZouDocument.bytes()`, turned into `NSData` by one helper
  (`toNSData`, `IosPresentation.kt`). The CSV's byte-order mark and the CRLF
  endings come out the same as on Android. No second encoder exists.
- **Always Czech.** The report, the subject and the body come from the same
  `ExportViewModel` code as on Android, whose tests already pin the report to
  Czech in English and Ukrainian. Only the screen's own messages translate.

### Thin glue, logic where it can be tested

| Where | What | Tested |
|---|---|---|
| `commonMain` · `ExportRouting.kt` | composer result → outcome; share result → outcome; the save sequence (stored folder, else ask, else cancelled; stop at the first failed write) | **11 JVM tests** |
| `commonMain` · `ExportScreen.kt` | one message per outcome; the pre-send note | **9 new JVM UI tests**, plus 14 updated |
| `iosMain` · `IosReportSender.kt`, `IosReportSaver.kt`, `IosPresentation.kt` | presenting the sheets, retaining their delegates, translating callbacks, the file writes | **compiled and linked only** |

**The presenting view controller, without deprecated API:** the top of the
presentation stack in the foreground `UIWindowScene`'s `keyWindow` (iOS 15,
the app's minimum), never `UIApplication.keyWindow` or `.windows`.

**MessageUI and UniformTypeIdentifiers** are system frameworks, linked
automatically: no Gradle dependency and no Xcode flag. The **privacy
manifest check still passes** on the new binary: no new required-reason
category.

### Unverified: nothing here has run

Compiled and linked, Debug and Release. Not one line executed: there is no
simulator on this Mac, no device, and `iosArm64` tests have no executor.
Each item below becomes a step in the checklist at the end.

1. The **composer** appears with recipient, subject, body and three
   attachments.
2. Each composer ending (send, delete draft, save draft) produces its
   message.
3. With **no Mail account**: the note shows before send, the clipboard
   holds the address, the share sheet offers mail apps, and Gmail or Outlook
   receive **all three attachments** and the body. **Whether they take the
   subject is unknown.**
4. **What "completed" means per app.** An app that opens in full, rather
   than as a share extension, may report completion before anything is sent.
5. **iPad**: the share sheet opens as a popover and does not crash.
6. **Folder picker**: a folder can be chosen in On My iPhone and in iCloud
   Drive.
7. **Remembered**: after the app is killed, and after a reboot, the next save
   asks nothing.
8. **Without the app**: the three files open from the Files app; saving the
   same match again replaces them.
9. **Bytes**: the saved CSV starts `EF BB BF` and uses CRLF.
10. The **delegates' callbacks arrive** (they are retained on purpose). If one
    did not, the screen would show nothing after the sheet closed, and
    pressing again would appear to do nothing.
11. **The Android build**, and Android's export on a device: unchanged
    behaviour, except the new cancel message.

### Gate 4 against its criteria

| Criterion | |
|---|---|
| Both halves implemented; the app still links | ✅ send and save; Debug and Release app builds unsigned, manifest check green |
| What the referee sees, step by step, in every case handled | ✅ above |
| What is unverified, listed | ✅ eleven items above, each a checklist step |
| Anything outside `iosMain`/`iosApp` changed? | **⚠ YES**: common UI, strings and **Android code**. Listed above. **Android must be rebuilt on Windows before merging.** |
| JVM suites and lint on this Mac | ✅ 388 shared + 182 composeApp, 0 failures; detekt and ktlint green (Android sources included) |
| Committed | ✅ on `ios/export`, pushed; **not merged to `main`** |

---

## 5 · Decisions taken, and by whom

| Decision | Who | Note |
|---|---|---|
| Keep the Linux-generated Xcode project; add `-lsqlite3` | This session | It built as generated; only the static framework's SQLite needed linking |
| File timestamp declared `C617.1`, not `0A2A.1`; system boot time not declared | This session | Traced to the binary's symbols; `0A2A.1` is SDK-only per Apple. Recorded in `DECISIONS.md` |
| The manifest check is a script, not a build phase | This session | A phase would fail the first device build over a manifest detail. CI is the open item |
| Export compliance exempt | This session | No network and no own cryptography, checked in the binary |
| iPad stays in | **Owner** | Welcome; drop if it ever complicates things |
| App name undecided, will be shorter | **Owner** | iOS mirrors Android's `app_name` until then |
| Parts 1–3 merged to `main` before Part 4 | **Owner** | Prefers `main` to long-lived branches |
| `SendOutcome` / `SaveOutcome` replace `Boolean` | **Owner**, asked first | Changes common and Android code |
| Part 4 on a branch until Android is checked | **Owner** | This Mac cannot build Android |
| Send: the composer when Apple Mail has an account, otherwise the share sheet with the address copied and the gap named before send | This session | Neither route alone serves every referee |
| Save: a folder picked once, remembered with a bookmark | This session | Android parity; the Documents folder loses the files with the app |
| The no-recipient warning in Compose, not a native alert | This session | Translated in the referee's language, and testable on the JVM |

---

## 6 · Findings worth not rediscovering

**A static Kotlin/Native framework drops its dependencies' linker options.**
SQLiter declares `-lsqlite3` and it vanished; the app must link SQLite
itself. Any future cinterop library in `ComposeApp` will do the same: an
`Undefined symbols` error on first contact means *add its library to
`OTHER_LDFLAGS`*, not that anything is broken. System frameworks used from
Kotlin (UIKit, MessageUI, UniformTypeIdentifiers) are *not* affected; they
auto-link.

**JetBrains' privacy table describes Compose, not this app.** The linker
decides what survives. Scan the linked Release binary, and never declare
`0A2A.1` in an app.

**In Debug, Xcode moves the app's code into `iosApp.debug.dylib`**
(`ENABLE_PREVIEWS`). Inspect that, not the stub executable, or `otool` shows
almost nothing.

**Kotlin/Native interop names to know:** `writeToURL` on `NSData` and
`popoverPresentationController` on `UIViewController` are category
extensions and need their own imports. Two ObjC methods that become Kotlin
overloads with the same parameter types need `@ObjCSignatureOverride`.
UIKit delegates are weak, so hold them in a field for as long as the sheet
is up.

**On iPad, `UIActivityViewController` is a popover and throws without an
anchor.** An app that "supports iPad" by default crashes on its first share
otherwise.

**Timings on this Mac:** the Kotlin/Native release framework link takes 12
minutes. A Debug app build from warm is about a minute.

---

## 7 · Commits

| | |
|---|---|
| `54cefa9` | Link the whole app unsigned; the static framework needs `-lsqlite3` |
| `5b58bcb` | Privacy manifest from the linked binary's symbols, with a check |
| `0b8ea8a` | `Info.plist` for upload: export compliance, one version source, iPad |
| `e8be70b` | iPad kept, app name open; Parts 1–3 marked done · **merged to `main`** |
| `ios/export` | Part 4: send and save on iOS, outcome types, this report · **not merged** |

---

## First run on an iPhone

A checklist for the session that first holds an iPhone. It should need nothing
else. Do the steps in order; each says what should happen and **what a
failure looks like**, so a failure is recognised rather than explained away.

### Before you start

1. **Branch.** Check out `ios/export` if it has not yet been merged. Otherwise
   export is a stub, and steps 12–28 test nothing.
2. **Build.** `open iosApp/iosApp.xcodeproj`, choose the iPhone as the run
   destination, and use the **Debug** configuration.
3. **Signing: select the COMPANY team. Never a personal team, not even to
   silence an error.** Target *iosApp* → *Signing & Capabilities* →
   *Automatically manage signing* → Team: the company's. **Why:** the first
   team to sign `cz.hspinovace.psmf` registers it for good. A free personal
   team that does so locks the company out of its own bundle ID, and only
   Apple support can free it (`docs/DECISIONS.md`, 2026-09-29).
   **Failure looks like:** Xcode offering *"Your Name (Personal Team)"* as the
   only team. **Stop there**: the company role is not set up yet.
   **Do not commit** the team ID that Xcode writes into `project.pbxproj`;
   revert that line before any commit.
4. **Trust.** On the phone: *Settings → General → VPN & Device Management*,
   then trust the developer, if iOS asks.
5. **Run.** It should open on the tab shell. **Failure:** a crash at launch
   with `sqlite3` or `dyld` in the log means a missing library; see §6, the first finding.

### The app itself (Part 3 of `prompts/08`)

6. **Czech.** Every screen in Czech with **Oswald headings and Noto Sans
   text**. Compare with an Android screenshot. **Failure:** San Francisco
   (the rounder, iOS system face) means the bundled fonts did not resolve,
   even though Part 1 found them in the bundle.
7. **English.** Settings → language → English. Every string changes with no
   restart. **Failure:** only some strings change, or none until a relaunch.
8. **Ukrainian, including Cyrillic.** Settings → Українська. Headings in
   **Cyrillic Oswald** (narrow, condensed), body in Noto Sans. Both fonts
   carry Cyrillic (`BundledFontTest`). **Failure:** a wider, rounder Cyrillic
   face means a fallback, so the resource path failed, not the font.
9. **The picker's persistence.** Set Ukrainian, kill the app from the
   switcher, reopen. Still Ukrainian. It is `AppleLanguages` in
   `NSUserDefaults`, a different mechanism from Android's. **Failure:** back
   in Czech or in the phone's language.
10. **The database survives.** Start a match, record a goal and a card, send
    the app to the background, kill it, reopen. The match and both events
    are there. **Failure:** the match is gone or empty.
11. **The home-screen name** is *Zápis o utkání*, or its truncation. **Failure:**
    `iosApp`.

### Export: send (Part 4)

12. Finish a match until the export screen says it is complete.
13. **With Apple Mail set up:** no note above the buttons. **Failure:** the
    "no Mail account" note appears anyway.
14. *Odeslat na PSMF*. The Mail composer has **To: psmf@psmf.cz**, the subject
    `ZoU <league> <d.m.yyyy> <home> - <away>`, the text report as the body,
    and **three** attachments: `.txt`, `.csv`, `.json`. **Failure:** fewer than
    three attachments, or an empty To.
15. **Send** (to a test address, if you change To) → *Předáno aplikaci Mail k
    odeslání na psmf@psmf.cz.*
16. Again, and **Cancel → Delete Draft** → *Odeslání zrušeno. Nic nebylo
    odesláno.* Then **Cancel → Save Draft** → *Uloženo jako koncept v aplikaci
    Mail. Zatím neodesláno.* **Failure, for any of 15–16:** *no* message after
    the composer closes. That means its delegate was lost, and pressing send
    again will then appear to do nothing.
17. **Without Apple Mail** (Settings → Mail → Accounts → remove it, or use a
    phone that never had it): the note shows **above the buttons before
    anything is pressed**. **Failure:** no note.
18. *Odeslat na PSMF* → the **share sheet**. Pick **Gmail** (or Outlook). The
    attachments and body are there. **Note whether the subject is**; that is
    unknown. The To field is empty: long-press it and **Paste**, and it should
    paste `psmf@psmf.cz`. **Failure:** the paste is something else, or
    attachments are missing.
19. Send from Gmail → back in the app: *Předáno vybrané aplikaci. Adresu
    nešlo vyplnit — zkontrolujte…*, as a **warning**. **Record** whether Gmail
    reported completion only after sending, or as soon as its window opened.
20. Share sheet again, dismiss it → *Odeslání zrušeno. Nic nebylo odesláno.*
21. **On an iPad, if there is one:** the share sheet opens as a popover near
    the bottom of the screen. **Failure:** a crash (an `NSGenericException`
    about `popoverPresentationController`).

### Export: save (Part 4)

22. *Uložit do zařízení* → the Files **folder** picker. Choose *On My iPhone*,
    make a folder `PSMF`, *Open* → *Zápis uložen…* **Failure:** a picker for
    *files* rather than folders, or *Uložení se nezdařilo*.
23. Save again: **no picker at all**; the same message. Then Settings shows
    *Změnit složku*. **Failure:** asked again, or Settings still says *Vybrat
    složku*.
24. Kill the app, reopen, save a third time: still no picker. Reboot the phone
    and repeat. **Failure:** the picker comes back, which means the bookmark
    did not persist.
25. **Without the app:** open the **Files** app → On My iPhone → PSMF. **Exactly
    three** files, `zapis_…txt/.csv/.json`, not six. The `.txt` opens and reads
    in Czech. **Failure:** `zapis_… 2.csv` duplicates, or no files.
26. **The bytes.** Share the saved `.csv` to a Mac (AirDrop), then
    `xxd zapis_….csv | head -1`: it must **start `efbb bf`**, and the lines
    must end `0d0a` (CRLF). Also open it in Numbers or Excel: Czech
    diacritics correct, split into columns. **Failure:** no `efbb bf` means
    the byte-order mark was lost on the way, and Excel will garble the
    diacritics.
27. Picker again (Settings → *Změnit složku*) and **Cancel** → nothing
    changes, and the next save still goes to `PSMF`. Then from the export
    screen with no folder chosen (fresh install), open the picker and
    **Cancel** → *Uložení zrušeno. Nic nebylo uloženo.*
28. **iCloud Drive:** choose an iCloud Drive folder instead, save, and check
    the files appear there, on the phone and on iCloud.com. **Failure:** saved
    locally but never on iCloud, which points at file coordination.

### Before you finish

29. Revert any team ID or signing change Xcode made to `project.pbxproj`, and
    confirm `git diff` shows none.
30. Write down, per step, pass or fail and what was seen. A step that "sort of
    worked" is a fail with a note.
