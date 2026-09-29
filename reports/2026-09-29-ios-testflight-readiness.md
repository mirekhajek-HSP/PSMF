# iOS TestFlight readiness, and an export that works

**Session date:** 2026-09-29
**Machine:** MacBook Pro 15-inch 2018, Intel i7, macOS 15.7.9 · Xcode 26.3 (17C529) · iOS SDK 26.2 · JDK 17
**Repository:** `~/Documents/PSMFApp_Miro/PSMF`, branch `ios/testflight-readiness`
**Brief:** `prompts/09-ios-testflight-readiness.md`

> *In progress. The opening answer and the "First run on an iPhone" checklist
> are written when all four parts are done.*

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
