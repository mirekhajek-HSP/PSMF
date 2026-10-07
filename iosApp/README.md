# iosApp

The Xcode wrapper. The entire UI is Compose Multiplatform; these files only
hand the Compose view controller to SwiftUI.

## Xcode has built it, unsigned, and it has never run

It was generated on Linux. On 2026-09-29 Xcode 26.3 built it on the Mac, Debug
and Release, with `CODE_SIGNING_ALLOWED=NO`. There was no migration prompt and
no regeneration, and the Swift is unchanged. It has not been opened in the IDE
yet, and nothing has run it.

**One change was needed: `OTHER_LDFLAGS = -lsqlite3`** on the app target.
SQLDelight's native driver (SQLiter) declares `-lsqlite3` in its cinterop, but
Kotlin/Native keeps linker options only for *dynamic* frameworks and
executables. `ComposeApp` is static, so the app has to link SQLite itself.
Without the flag, the link fails with about 30 undefined `_sqlite3_*` symbols.
Do not remove it.

`ComposeApp` itself is **not** listed in *Link Binary With Libraries*; Swift
auto-links it through `import ComposeApp`. That works; leave it alone unless
it stops working.

The Kotlin side: `composeApp` declares `iosArm64` and `iosSimulatorArm64`
framework targets named `ComposeApp`, and
`MainViewControllerKt.mainViewController()` is what `ContentView.swift`
calls.

## What the first macOS session should check

1. Open `iosApp/iosApp.xcodeproj` and let Xcode migrate it if it offers.
2. Confirm the **Compile Kotlin Framework** build phase runs before
   *Compile Sources*. It shells out to
   `./gradlew :composeApp:embedAndSignAppleFrameworkForXcode`.
3. Confirm `FRAMEWORK_SEARCH_PATHS` resolves to
   `composeApp/build/xcode-frameworks/$(CONFIGURATION)/$(SDK_NAME)`.
4. **Sign with the COMPANY's Apple Developer team — never a free personal
   team.** Signing registers the bundle ID to whichever team signs first, and
   an ID claimed by a free personal team can never be registered by the
   company: it cannot be seen or deleted from a free account, and freeing it
   takes Apple support. `cz.hspinovace.psmf` is permanent, so this is not
   recoverable by renaming. If a personal team is ever unavoidable, change the
   bundle ID to a throwaway one for that run and **do not commit the change**.
   **No signing identity, profile or team ID is committed here, and none
   should be.**
5. Then run it on a **physical iPhone over a cable**. There is no simulator
   for this project on the Intel Mac: see below.

The build that needs no phone and no signing identity, and which is the
compile proof for the whole app:

```
xcodebuild -project iosApp/iosApp.xcodeproj -scheme iosApp \
  -destination 'generic/platform=iOS' CODE_SIGNING_ALLOWED=NO build
```

`iosX64` is deliberately not a target — Compose Multiplatform no longer
publishes an `ios_x64` variant. The simulator target is
`iosSimulatorArm64`, which needs an Apple Silicon Mac. See
`docs/BUILD_MATRIX.md`.

## Privacy manifest

`iosApp/iosApp/PrivacyInfo.xcprivacy` is in the **app** target's *Copy Bundle
Resources*, not the framework's: `ComposeApp` is statically linked, so the app
is what Apple scans. It declares **what the linked release binary actually
imports**: user defaults (`CA92.1`) and file timestamp (`C617.1`). No tracking,
no tracking domains, no collected data types.

**Before every upload**, on the release build:

```
iosApp/scripts/check-required-reason-apis.sh <DerivedData>/Build/Products/Release-iphoneos/iosApp.app
```

It fails if a required-reason symbol is present and undeclared, or declared
and absent. Which symbols survive depends on dead-stripping, so a Compose bump
or a new screen can change the answer. `docs/DECISIONS.md` (2026-09-29) says
why the manifest is not the one JetBrains' table suggests.

## Export

`composeApp/src/iosMain/.../ui/export/`: `IosReportSender` (the Mail
composer if Apple Mail has an account, otherwise the share sheet with the
address copied) and `IosReportSaver` (a folder picked once, remembered as a
bookmark). The decisions behind both are in `commonMain`'s
`ExportRouting.kt`, tested on the JVM. **None of it has run yet.** The
device checklist is at the end of
`reports/2026-09-29-ios-testflight-readiness.md`.

The share sheet on iPad is a popover and **needs an anchor**, or UIKit
throws. `IosReportSender` sets one; keep it if that code moves.

## Bundle identifier

`PRODUCT_BUNDLE_IDENTIFIER` is **`cz.hspinovace.psmf`**, matching the Android
`applicationId`. **Settled 2026-09-29** — see `docs/DECISIONS.md`. It becomes
permanent the moment it is registered in App Store Connect; do not change it.

## Localisation

`Info.plist` declares `CFBundleDevelopmentRegion` as `cs` and
`CFBundleLocalizations` as `cs`, `en`, `uk`. UI strings themselves come
from Compose resources in `composeApp`, not from iOS `.strings` files.

The UI uses **bundled** Oswald and Noto Sans, as Compose resources, on both
platforms, not San Francisco. Both carry Cyrillic (verified against the font
files by `BundledFontTest`). So on iOS, a fallback face on Ukrainian text means
the resource path failed, not the font. That has not been checked on a device
yet.
