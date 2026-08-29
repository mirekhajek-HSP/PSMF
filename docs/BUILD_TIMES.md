# Build times

Reference figures for the container sandbox, so that a future slowdown is
visible rather than merely suspected.

Re-measured 2026-08-29 **after the AGP 9 migration**, on the same host and
the same 84-test suite, so the two columns are comparable.

Host: Windows 11, WSL2 Ubuntu, native Docker Engine 29.7.2 in-distro.

| | Before | After |
|---|---|---|
| Gradle | 9.5.1 | 9.7.1 |
| AGP | 8.13.2 | 9.3.2 |
| Kotlin | 2.2.20 | 2.4.10 |
| Compose Multiplatform | 1.11.1 | 1.12.0 |
| compileSdk / targetSdk | 36 | 37 |
| Modules | 2 | 3 (`:androidApp` split out) |

---

## Timings

| Scenario | Before | After |
|---|---|---|
| **Cold** — fresh copy, empty `GRADLE_USER_HOME`, no daemon, `build` | 136 s | **112 s** |
| Clean recompile, build cache off, `build` | 26 s | 27 s |
| Clean recompile, build cache off, `assembleDebug` | 5 s | 6 s |
| Warm `build`, nothing changed | 1 s | 1 s |
| `assembleDebug`, one Kotlin file touched | 1 s | 2 s |
| **`:shared:jvmTest`, forced rerun** | 13 s | **4 s** |
| **`:shared:allTests`, forced rerun** | 15 s | **6 s** |
| `detekt`, forced rerun | 3 s | 1 s |
| `ktlintCheck`, forced rerun | — | 5 s |
| `:shared:jvmTest`, up to date | 0 s | 0 s |

Nothing regressed. The test tasks got roughly three times faster, which is
the figure that matters most day to day because the Stop hook pays it.

Splitting `:androidApp` out did not cost a measurable amount. It adds a
module to configure and removes Android variants from two others, and
those roughly cancel.

### The Stop hook

`.claude/hooks/run-shared-tests.sh` runs `:shared:jvmTest` when a session
finishes. **Still ~1 s in the common case**, because Gradle finds the task
up to date when no Kotlin changed; it now costs 4 s rather than 13 s after
an actual edit.

Far enough under the one-minute mark that it runs unconditionally. The
hook carries a `GATE_ON_KOTLIN_CHANGES` switch, off by default: flip it to
1 if the suite ever gets slow enough that paying it every turn stops being
worth it.

The cold figure is dominated by dependency download. The clean figure with
the build cache **enabled** is only a few seconds, because Gradle restores
task outputs rather than recompiling — accurate, but not a compile
measurement, so the honest recompile number above has the cache off.

## If these get much worse

Check the canary in `compose.yaml` first:

- `docker version` must report **Engine 29.7.2**, native and in-distro. If
  it reports 28.3.2 that is Docker Desktop, and the Windows filesystem
  translation is back underneath every Gradle read.
- `wsl -l -v` — Ubuntu must **not** be the default distro.
- A lost or recreated `psmf-gradle-cache` volume sends every build back to
  the cold figure.

## One trap already hit

`buildToolsVersion` is pinned in every module to the version the image
installs. Left to its own default, AGP asked `sdkmanager` for a different
build-tools package and re-downloaded it **on every container run**,
because `docker compose run --rm` throws the writable layer away each
time. It cost roughly 15 s per build and was invisible in the build output
unless you were looking for it.

It survived the AGP 9 migration: `buildToolsVersion` is a property of the
new `kotlin { android { } }` extension too. Verified after the move — the
container still holds exactly `build-tools/37.0.0` and
`platforms/android-37.0` after a full build, with nothing downloaded.

## APK size

| Build | Before | After |
|---|---|---|
| debug | — | 33.1 MiB |
| release, unsigned | 25.7 MiB | 25.8 MiB |
| **uncompressed DEX total** | **24.8 MiB** | **24.8 MiB** |

**Compose Multiplatform 1.12 and lifecycle 2.11 did not grow the app.**
The uncompressed DEX — the number that tracks real growth — is unchanged.

### Why it is 25.8 MiB and not 9.5 MiB

This was investigated when it first appeared and is **not** a regression.
From **minSdk 28 AGP writes DEX into the APK uncompressed**, so that ART
can memory-map it directly instead of extracting it at install time.

The `.apk` file on disk is therefore larger, while the installed footprint
and cold-start time both improve. Verified by building the same commit
twice with only `androidMinSdk` changed:

```
minSdk 24   classes.dex   4,612,947 bytes in the APK (deflated)
minSdk 28   classes.dex  13,275,416 bytes in the APK (stored)
```

Worth knowing before anyone reports it as bloat. The number to watch for
real growth is the **uncompressed** DEX total, not the APK size.

Neither figure is minified: `isMinifyEnabled` is false, because release
signing and shrinking happen outside this workspace. Expect a substantial
drop whenever R8 is switched on.
