# Build matrix

Why the toolchain versions are what they are. Several are **not** the
latest release, and every one of them is pinned by something concrete.

Established by bisection on 2026-08-29 while scaffolding the project. None
of it is guessable from release notes.

---

## The chain

Each link forces the next. Breaking any one of them breaks the build.

| | Pinned | Latest at the time | Why it cannot move |
|---|---|---|---|
| Gradle | **9.5.1** | 9.7.1 | AGP 8.x uses `org.gradle.api.problems.internal.InternalProblems`, a Gradle internal API removed in **9.6.0**. Gradle's own error message names 9.5 as the last workable version. |
| AGP | **8.13.2** | 9.3.2 | Since **AGP 9.0**, `com.android.application` and `com.android.library` refuse to apply alongside `org.jetbrains.kotlin.multiplatform`. That is precisely how a Compose Multiplatform app is laid out, so AGP 9 is unusable here until either AGP or the KMP plugin changes. |
| Kotlin | **2.2.20** | 2.4.10 | Compose Multiplatform is built against 2.2.20. On Kotlin 2.4.10 the Compose tooling fails reading `kotlin-stdlib` metadata: *"binary version of its metadata is 2.4.0, expected version is 2.2.0"*. |
| Compose Multiplatform | **1.11.1** | 1.12.0 | 1.12.0 pulls `androidx.compose:*:1.12.0`, whose AAR metadata demands **compileSdk 37 and AGP 9.1+**. AGP 9 is ruled out above, and AGP 8.13.2 caps compileSdk at 36. |
| `org.jetbrains.androidx.lifecycle` | **2.10.0** | 2.11.0 | 2.11.0 has the same compileSdk 37 requirement, so it follows Compose down. |
| compileSdk / targetSdk | **36** | 37 | Ceiling of AGP 8.13.2. |

**Consequence: these six move together or not at all.** Bumping Compose
alone, or Kotlin alone, will fail. The unlock is AGP 9 becoming compatible
with the KMP plugin — watch
<https://developer.android.com/kotlin/multiplatform/plugin>. When that
happens the whole column can move up at once.

---

## Two separate workarounds

### `org.jetbrains:annotations` forced to 23.0.0

In `build.gradle.kts`. Gradle pins `annotations` to 13.0 on the buildscript
classpath to match its embedded Kotlin. The Android plugin drags in 23.0.0
via `ddmlib` and `layoutlib-api`, and resolution fails with *"Pinned to the
embedded Kotlin"*.

Bisected precisely:

| Plugins on the classpath | Result |
|---|---|
| AGP alone | resolves |
| AGP + KMP + Compose Multiplatform | resolves |
| AGP + KMP + SQLDelight | resolves |
| AGP + KMP + SQLDelight + Compose **compiler** plugin | **fails** |

SQLDelight 2.1.0 also avoids it, but pinning a dependency two minor
versions back for a reason nobody will remember later is worse than one
forced version with a comment saying why. Nothing needs `annotations` 13.0
at runtime; 23.0.0 is backwards compatible.

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
configuration-cache compatible. Worth revisiting when detekt 2 ships.

---

## How to re-test after a bump

```bash
./gradlew build && ./gradlew detekt && ./gradlew :shared:allTests
```

If dependency resolution fails with *"Pinned to the embedded Kotlin"* or
*"No matching variant"*, the chain above has been broken somewhere. Bisect
by cutting the root `plugins {}` block down and adding entries back one at
a time; that is what found all of this.
