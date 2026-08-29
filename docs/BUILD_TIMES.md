# Build times

Reference figures for the container sandbox, so that a future slowdown is
visible rather than merely suspected. Measured 2026-08-29, first on the
Phase 1 scaffold and then again with the Phase 2 domain model, seed
loading and persistence in place (84 shared tests).

Host: Windows 11, WSL2 Ubuntu, native Docker Engine 29.7.2 in-distro.
Toolchain: Gradle 9.5.1, AGP 8.13.2, Kotlin 2.2.20, Compose 1.11.1.

| Scenario | Time |
|---|---|
| **Cold** — fresh copy, empty `GRADLE_USER_HOME`, no daemon, `build` | **136 s** |
| Clean recompile, build cache off, `build` | 27 s |
| Clean recompile, build cache off, `:composeApp:assembleDebug` | 5 s |
| Warm `build`, nothing changed | 4 s |
| Warm `:composeApp:assembleDebug`, one Kotlin file edited | 1 s |
| `:shared:jvmTest`, forced rerun | 6 s |
| `:shared:allTests`, forced rerun | 12 s |
| `detekt`, forced rerun | 2 s |

## With the Phase 2 suite (84 shared tests)

| Scenario | Time |
|---|---|
| Clean recompile, build cache off, `build` | 26 s |
| Warm `build`, nothing changed | 1 s |
| **`:shared:jvmTest`, up to date** | **0 s** |
| `:shared:jvmTest`, forced rerun | 13 s |
| `:shared:allTests`, forced rerun | 15 s |
| `detekt`, forced rerun | 3 s |

### The Stop hook

`.claude/hooks/run-shared-tests.sh` runs `:shared:jvmTest` when a session
finishes. **Measured at ~1 s in the common case**, because Gradle finds
the task up to date when no Kotlin changed; it costs the 13 s only after
an actual edit.

That is far enough under the one-minute mark that it runs unconditionally.
The hook carries a `GATE_ON_KOTLIN_CHANGES` switch, off by default: flip
it to 1 if the suite ever gets slow enough that paying it every turn
stops being worth it.

The cold figure is dominated by dependency download. The clean figure with
the build cache **enabled** is only ~3 s, because Gradle restores task
outputs rather than recompiling — accurate, but not a compile measurement,
so the honest recompile number above has the cache switched off.

## If these get much worse

Check the canary in `compose.yaml` first:

- `docker version` must report **Engine 29.7.2**, native and in-distro. If
  it reports 28.3.2 that is Docker Desktop, and the Windows filesystem
  translation is back underneath every Gradle read.
- `wsl -l -v` — Ubuntu must **not** be the default distro.
- A lost or recreated `psmf-gradle-cache` volume sends every build back to
  the cold figure.

## One trap already hit

`buildToolsVersion` is pinned in both `android { }` blocks to the version
the image installs. Left to its own default, AGP asked `sdkmanager` for a
different build-tools package and re-downloaded it **on every container
run**, because `docker compose run --rm` throws the writable layer away
each time. It cost roughly 15 s per build and was invisible in the build
output unless you were looking for it.
