# iosApp

The Xcode wrapper. The entire UI is Compose Multiplatform; these files only
hand the Compose view controller to SwiftUI.

## This has never been opened by Xcode

It was generated on Linux, where iOS cannot be built. **Treat
`iosApp.xcodeproj/project.pbxproj` as a starting point, not a verified
artefact.** If Xcode objects to it, regenerating the project from the
Kotlin Multiplatform wizard and copying the two Swift files across is a
perfectly good outcome — the Swift is the part worth keeping.

The Kotlin side *is* verified: `composeApp` declares `iosArm64` and
`iosSimulatorArm64` framework targets named `ComposeApp`, and
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

The Kotlin side has been compiled and linked for `iosArm64` on the Mac, with
no changes needed (2026-09-28, `reports/2026-09-01-ios-parts-2-and-3.md`).
This Xcode project still has not been opened. The cheapest first test of it
needs no phone and no signing identity:

```
xcodebuild -project iosApp/iosApp.xcodeproj -scheme iosApp \
  -destination 'generic/platform=iOS' CODE_SIGNING_ALLOWED=NO build
```

`iosX64` is deliberately not a target — Compose Multiplatform no longer
publishes an `ios_x64` variant. The simulator target is
`iosSimulatorArm64`, which needs an Apple Silicon Mac. See
`docs/BUILD_MATRIX.md`.

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
