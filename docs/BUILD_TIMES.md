# Build times

Reference figures for the container sandbox, so that a future slowdown is
visible rather than merely suspected. Measured 2026-08-29 on the Phase 1
scaffold (two modules, ~15 Kotlin files — expect these to grow).

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
