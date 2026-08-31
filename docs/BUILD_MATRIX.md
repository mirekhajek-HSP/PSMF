# Build matrix

Why the toolchain versions are what they are, and which of them are
actually constrained. Rewritten 2026-08-29 after migrating to AGP 9;
**most of what the first version of this document claimed was wrong**, and
the correction is recorded below rather than quietly deleted.

---

## What the previous version got wrong

It recorded six versions as locked together until *"AGP becomes compatible
with the KMP plugin"*, and treated that as upstream work somebody else had
to do.

The incompatibility it found is real and was bisected correctly: **since
AGP 9, a module applying `org.jetbrains.kotlin.multiplatform` cannot also
apply `com.android.application` or `com.android.library`.** The conclusion
drawn from it was not. AGP 9 was already compatible; it required a
**module restructure**, which both JetBrains and Google document:

- <https://kotlinlang.org/docs/multiplatform/multiplatform-project-agp-9-migration.html>
- <https://developer.android.com/build/releases/agp-9-0-0-release-notes>

It was also not optional. The legacy path survives behind
`android.enableLegacyVariantApi=true` and is **removed in AGP 10**.

Four of the six versions were pinned only by that mistake and have moved.

---

## What is pinned now

| | Version | Constrained by |
|---|---|---|
| AGP | 9.3.2 | Latest stable. 9.4.0-rc02 and 9.5.0-alpha03 exist; neither is released. |
| Gradle | 9.7.1 | Latest stable. AGP 9 requires **9.1.0 or newer** — that is the only floor. |
| Kotlin | 2.4.10 | Latest stable. |
| Compose Multiplatform | 1.12.0 | Latest stable. **Requires `compileSdk` 37 or higher** — this one is real, see below. |
| `org.jetbrains.androidx.lifecycle` | 2.11.0 | Latest stable. |
| compileSdk / targetSdk | 37 | Floor set by Compose Multiplatform 1.12.0. No ceiling now. |
| build-tools | 37.0.0 | Must match what the container image installs. |
| JVM toolchain | 17 | AGP 9 minimum, and what the image ships. |

**Only one genuine constraint survives**: Compose Multiplatform 1.12.0
demands `compileSdk` 37 or higher. Verified by building at 36, which fails
with an explicit AAR-metadata error naming every `androidx.compose`
artifact. Everything else is simply the latest release.

That `compileSdk` can move again matters beyond tidiness: **the Play Store
targetSdk floor rises every year**, so a project that cannot raise its
targetSdk has a compliance deadline, not a preference.

### The Kotlin pin was collateral damage

The old document blamed Compose: *"Compose Multiplatform is built against
2.2.20 and its tooling cannot read Kotlin 2.4 metadata."* Not reproducible.
Kotlin 2.4.10 was bumped on its own, **before** Compose moved off 1.11.1,
and built clean. Whatever produced that metadata error belonged to the
AGP 8 configuration, not to Compose.

---

## The module layout AGP 9 requires

```
androidApp/    com.android.application          Android entry point ONLY
composeApp/    KMP + com.android.kotlin.multiplatform.library
shared/        KMP + com.android.kotlin.multiplatform.library
iosApp/        Xcode wrapper, links the :composeApp framework
```

`:androidApp` holds `MainActivity`, `PsmfApplication`, the manifest and
the launcher resources. Nothing else. It does **not** apply the Kotlin
Android plugin — AGP 9 has Kotlin support built in and the old plugin is
incompatible with the new DSL.

`AppModule.android.kt` deliberately stayed in `:composeApp`: it is the
`actual` for an `expect` in `commonMain` and has to sit in that module
`androidMain`.

Namespaces must differ between modules in one APK or their R classes
collide — `cz.hspinovace.psmf` for the app, `.composeapp` and `.shared`
for the libraries. The Kotlin package is unaffected.

`iosApp` needed **no** change. It references
`:composeApp:embedAndSignAppleFrameworkForXcode` and
`composeApp/build/xcode-frameworks/`, and `:composeApp` is still the
module that declares the iOS targets and the framework.

---

## Four traps, each of which cost time

### 1. The migration guide DSL name is out of date

The JetBrains guide says:

```kotlin
kotlin {
    androidLibrary { }        // deprecated as of AGP 9.3
}
```

As of AGP 9.3 that spelling is deprecated in favour of `android { }` nested
inside `kotlin { }`, and the `import com.android.build.api.dsl.androidLibrary`
the guide gives **does not resolve at all**. The working form is:

```kotlin
kotlin {
    android {
        namespace = "..."
        buildToolsVersion = "..."
        compileSdk = 37
        minSdk = 28
        compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
        androidResources { enable = true }   // only where there are resources
    }
}
```

`namespace`, `compileSdk`, `minSdk`, `buildToolsVersion`, `androidResources`,
`packaging`, `lint`, `optimization`, `withHostTest` and `withDeviceTest` are
all on `KotlinMultiplatformAndroidLibraryExtension`. There is **no**
`targetSdk` — that belongs to the application module.

### 2. Android unit tests are silently dropped — for two separate reasons

**Resolved 2026-08-30.** The trap had two halves and each is silent on its
own, so fixing one and not the other looks identical to fixing neither.

*Half one: no compilation.* The KMP library plugin creates **no** Android
host-test compilation unless you ask for one. Verified by planting a test
calling `fail()`: `./gradlew build` and `:shared:allTests` both passed, no
task ran, no file was compiled, and nothing warned.

*Half two: the wrong directory.* The source set is **`androidHostTest`**,
not `androidUnitTest`. Verified the same way, and this is the more
dangerous half: with the builder correctly declared, a file in
`src/androidUnitTest/` is still ignored — `testAndroidHostTest` runs, the
build goes green, and the planted failure never appears. Every version of
this document before today named the wrong directory.

What `:shared` now declares:

```kotlin
kotlin {
    android {
        withHostTestBuilder {
            // Puts the compilation under commonTest, so the shared suite
            // runs on the Android target as well as the JVM.
            sourceSetTreeName = "test"
        }.configure {
            isReturnDefaultValues = true
        }
    }
}
```

The task is `:shared:testAndroidHostTest`, reached by `:shared:allTests`.
`AndroidHostTestCanaryTest` gives the compilation a reason to exist and
carries the two-line recipe for re-proving all of this in a minute.

### 3. `platforms;android-37.0`, not `platforms;android-37`

Android now versions SDK platforms with a minor component. `sdkmanager`
will not resolve the bare major; AGP still takes `compileSdk = 37` and
finds it. The Dockerfile installs `platforms;android-37.0` and
`build-tools;37.0.0`.

### 4. `buildToolsVersion` still has to be pinned

It survives onto the new extension, and it still matters. Left to its own
default, AGP asks `sdkmanager` for a different build-tools package and
re-downloads it **on every container run**, because
`docker compose run --rm` throws the writable layer away each time. Keep
`androidBuildTools` in the catalog and the `sdkmanager` line in the
Dockerfile in step.

### 5. No `schemaOutputDirectory`, no schema task — and no warning

`generateCommonMainPsmfDatabaseSchema` is the task that records the current
schema as `<version>.db`, which is the file migration verification is
verified *against*. It is **registered only if `schemaOutputDirectory` is
set**. Leave it unset and the task does not exist, `gradlew tasks` lists no
sign that anything is missing, and
`verifyCommonMainPsmfDatabaseMigration` — which does exist either way, and
passes — is checking the migrations against nothing at all.

The same family of failure as the Android host-test trap above: a green
build that ran no check.

```kotlin
sqldelight {
    databases {
        create("PsmfDatabase") {
            packageName.set("cz.hspinovace.psmf.db")
            schemaOutputDirectory.set(file("src/commonMain/sqldelight/databases"))
            verifyMigrations.set(true)     // this is what puts it on `check`
        }
    }
}
```

Two further details worth not rediscovering: the generated `.db` is **not
stamped with its own version** — `user_version` stays 0, where a database
created on a device carries the real number — and `.sqm` files are **not
package-scoped**, unlike the `.sq` files they sit beside.

---

## Two workarounds that survived the migration

### `org.jetbrains:annotations` forced to 23.0.0

In the root `build.gradle.kts`. **Still required on AGP 9.3.2 with Gradle
9.7.1** — verified by removing it, which fails immediately:

```
Could not resolve org.jetbrains:annotations:{strictly 13.0}.
  com.android.tools.build:gradle:9.3.2 --> ddmlib:32.3.2 --> org.jetbrains:annotations:23.0.0
  Constraint path: root --> org.jetbrains:annotations:{strictly 13.0}
                   because of the following reason: Pinned to the embedded Kotlin
```

The cause is simpler than the old bisection suggested, and the newer error
message states it outright: Gradle pins `annotations` to 13.0 on the
buildscript classpath to match its own embedded Kotlin, and AGP drags in
23.0.0 through `ddmlib`, `layoutlib-api` and `repository`. It has nothing
to do with SQLDelight or the Compose compiler plugin specifically; those
were merely present when it first showed up.

Nothing needs `annotations` 13.0 at runtime and 23.0.0 is backwards
compatible.

### Unfiltered repositories

`settings.gradle.kts` uses plain `google()` rather than narrowing it with
`mavenContent { includeGroupAndSubgroups(...) }`. The narrowed form looks
tidier and interacts badly with the constraint above. Left broad
deliberately.

---

## `iosX64` is not a target

Neither Compose Multiplatform nor the JetBrains lifecycle port publishes an
`ios_x64` variant any more. That target is the **Intel** iOS simulator,
which Apple Silicon made obsolete; the device is `iosArm64` and the modern
simulator is `iosSimulatorArm64`. Declaring `iosX64()` fails dependency
resolution rather than merely being unused.

---

## Configuration cache

Off, in `gradle.properties`. detekt 1.23.x is not fully
configuration-cache compatible, and it is also the source of the one
remaining Gradle deprecation warning in the build
(`ReportingExtension.file(String)`, removed in Gradle 10). Both go away
with detekt 2; revisit then.

---

## How to re-test after a bump

```bash
./gradlew build && ./gradlew detekt ktlintCheck && ./gradlew :shared:allTests --rerun-tasks
```

Bump **one** version at a time and run the above between each, or a
failure is not attributable — that is how the four dead pins above were
told apart from the one real one.

If dependency resolution fails with *"Pinned to the embedded Kotlin"* or
*"No matching variant"*, one of the two workarounds has been disturbed.
