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
4. Set a development team for signing. **No signing identity is committed
   here and none should be.**
5. Then: `./gradlew :composeApp:linkDebugFrameworkIosSimulatorArm64` and
   run on a simulator.

`iosX64` is deliberately not a target — Compose Multiplatform no longer
publishes an `ios_x64` variant. The simulator target is
`iosSimulatorArm64`, which needs an Apple Silicon Mac. See
`docs/BUILD_MATRIX.md`.

## Bundle identifier

`PRODUCT_BUNDLE_IDENTIFIER` is `cz.hspinovace.psmf`, matching the Android
`applicationId`. **Neither is final.** `docs/TECH_STACK.md` section 5 still
lists it as open, and it becomes permanent at first publication. Confirm it
before any App Store upload.

## Localisation

`Info.plist` declares `CFBundleDevelopmentRegion` as `cs` and
`CFBundleLocalizations` as `cs`, `en`, `uk`. UI strings themselves come
from Compose resources in `composeApp`, not from iOS `.strings` files.

Cyrillic renders on Android with the platform font and no bundled font;
that still needs confirming on iOS, though San Francisco covers Cyrillic,
so no bundled font is expected to be necessary.
