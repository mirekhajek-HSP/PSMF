# Build report — Phase 1 (scaffold) and Phase 2 (domain foundation)

**Date:** 2026-08-29
**Scope:** the two-phase prompt in `prompts/` — environment and scaffold, then
domain model and seed data
**Outcome:** both phases complete, both gates met, 15 commits, clean tree
**Next:** the six demo screens

The code does **not** live in this repository. It is at
`~/dev/psmf-app` inside WSL Ubuntu — from Windows,
`\\wsl$\Ubuntu\home\dev\psmf-app`. This repository holds the analysis,
the scope decisions and the prompts; that one holds the app.

---

## 1. Status at a glance

| | |
|---|---|
| Gradle modules | `shared`, `composeApp`, plus an `iosApp` Xcode wrapper |
| Tracked files | 121 · 39 Kotlin files · 10 test files |
| Tests | **84** on the JVM target; 70 are common and also run on Android debug + release (224 executions per `allTests`) |
| detekt | green, **baseline empty** (0 suppressions) |
| ktlint | green |
| Android app | builds, installs, launches; verified in Czech, English and Ukrainian |
| iOS | framework targets declared; **never compiled** — that needs a Mac |
| Agent tooling | 14 skills, 3 hooks (all verified firing), committed permissions |
| Git remote | none, local only, as instructed |

---

## 2. What exists

### `shared/` — domain and data, no UI

```
domain/     Minute, PersonName, PlayerIdentifier, Primitives, Catalogue,
            Lineup, MatchEvent, Officials, Assessment, Match, ReportProblem
data/seed/  SeedFileReader, SeedDtos, SeedLeagueCatalog
data/match/ MatchRepository, SqlDelightMatchRepository
data/db/    DatabaseDriverFactory (expect/actual: Android, iOS, JVM)
export/     ZouLabels — fixed Czech strings for the generated report
```

Targets: `androidTarget`, `jvm` (the fast test loop), `iosArm64`,
`iosSimulatorArm64`.

### `composeApp/` — UI

One placeholder screen. **None of the six demo screens exist yet**, which
is deliberate: Phase 2 was explicitly "no screens". Also holds the
Compose-resource seed reader and the Koin platform module.

### Seed data — `composeApp/src/commonMain/composeResources/files/leagues/`

`index.json` and `6k.json`. 12 teams, 144 players, 66 fixtures across 11
rounds, every pairing exactly once, kickoffs on the 19:00–20:45 quarter
hours. **Placeholder data, to be replaced with real psmf.cz data.** No RP
numbers, since those are the one thing not obtainable from public sources.

### Documentation written during the work

| File | What it records |
|---|---|
| `docs/BUILD_MATRIX.md` | Why six toolchain versions are pinned below latest, and what would unlock them |
| `docs/BUILD_TIMES.md` | Reference timings, the Stop-hook cost, and the APK-size explanation |
| `.claude/README.md` | Every skill installed and why, what was left out, all three hooks |
| `iosApp/README.md` | What the first macOS session must check |
| `docs/TECH_STACK.md` | Updated: status, new platform constraints, settled §5 items |

---

## 3. Gate 1 — environment and scaffold

All criteria met.

| Criterion | Result |
|---|---|
| `./gradlew build` | PASS |
| `:composeApp:assembleDebug` produces an APK | PASS |
| `./gradlew detekt` green with an empty baseline | PASS |
| App launches and shows something | PASS — Czech, English, Ukrainian |
| Skills installed and listed | 14 |
| Both hooks verified firing | Yes, in real sessions |
| Build times recorded | Yes |
| Everything committed | Yes |

Two extra checks, both because the prompt called them out:

- **A clean clone builds with no setup at all.** `gradlew` lands as 100755
  with LF, `gradlew.bat` as CRLF, and no `local.properties` is needed.
  This is the specific failure golblok once shipped.
- **The Gradle cache volume is `psmf_psmf-gradle-cache`**, distinct from
  golblok's, so the two sandboxes cannot collide.

### The toolchain fight, and what it settled

The single most expensive discovery of Phase 1, found by bisection and not
documented anywhere upstream:

> **Since AGP 9.0, `com.android.application` refuses to apply alongside
> `org.jetbrains.kotlin.multiplatform`** — which is exactly how a Compose
> Multiplatform app is laid out.

Everything else follows from that one fact:

| | Pinned | Latest then | Why it cannot move |
|---|---|---|---|
| Gradle | 9.5.1 | 9.7.1 | AGP 8.x uses a Gradle internal API removed in 9.6 |
| AGP | 8.13.2 | 9.3.2 | AGP 9 is incompatible with the KMP plugin (above) |
| Kotlin | 2.2.20 | 2.4.10 | Compose Multiplatform is built against 2.2.20 |
| Compose Multiplatform | 1.11.1 | 1.12.0 | 1.12 needs compileSdk 37 and AGP 9.1+ |
| lifecycle (JetBrains) | 2.10.0 | 2.11.0 | Same compileSdk 37 requirement |
| compileSdk / targetSdk | 36 | 37 | Ceiling of AGP 8.13.2 |

**These six move together or not at all.** The unlock is AGP becoming
compatible with the KMP plugin. Full evidence in `docs/BUILD_MATRIX.md`.

Two smaller findings from the same fight:

- **`iosX64` is no longer a viable target.** Neither Compose nor the
  lifecycle port publishes an `ios_x64` variant. That is the *Intel*
  simulator, obsolete since Apple Silicon; declaring it fails dependency
  resolution outright. Targets are `iosArm64` and `iosSimulatorArm64`.
- One forced dependency resolution (`org.jetbrains:annotations` to 23.0.0),
  needed only when SQLDelight and the Compose compiler plugin are both on
  the classpath. Bisected precisely and documented where it is written.

### Build times

| Scenario | Time |
|---|---|
| Cold — fresh copy, empty Gradle home, no daemon | 136 s |
| Clean recompile, build cache off | 26–27 s |
| Warm `build`, nothing changed | 1–4 s |
| `:shared:jvmTest`, forced rerun | 13 s |
| `:shared:jvmTest`, up to date | 0 s |
| `detekt`, forced rerun | 3 s |

One trap fixed along the way: `buildToolsVersion` was defaulting to a
package the container image does not ship, so `sdkmanager` re-downloaded
it **on every container run** (the writable layer is discarded by
`compose run --rm`). Roughly 15 s per build, and completely silent.

---

## 4. Gate 2 — domain model and seed data

All criteria met.

| Criterion | Result |
|---|---|
| `./gradlew :shared:allTests` | PASS |
| Each domain rule covered by a test that fails if violated | **Verified by mutation** |
| Seed loading works, add-a-group test passes | PASS |
| In-progress match survives a kill | PASS |
| Stop hook running, runtime measured | ~1 s typical |
| detekt still green, everything committed | PASS |

### The rules, and how each is enforced

Every one comes from `docs/LEAGUE_APP_ANALYSIS.md`; sections cited in the
code.

| Rule | How it is made hard to get wrong |
|---|---|
| **Minute is not an `Int`** | Sealed type. `30´+` sorts after minute 30 and before 31; `60´+` sorts last even past added time. Uses the form's own U+00B4 accent, not an apostrophe. |
| **Red records straight vs `2. ŽK`** | Separate `Dismissal` value on `RedCard`. Suspension arithmetic depends on it: two yellows in one match count zero toward a ban. |
| **Every card carries a reason** | `CardReason` rejects blank at construction, so a card without one cannot be built. |
| **A goal may have no scorer** | `scorer` is nullable. The worked example contains `13´ — 2:1`. |
| **Identifier is one field + discriminator** | `PlayerIdentifier(value, type)` with `RP` / `DATE_OF_BIRTH` / `BIRTH_NUMBER`. Two nullable fields would allow both at once, which the paper cannot express. |
| **Names are Latin only** | `PersonName` accepts Czech diacritics, rejects Cyrillic. UI may be Ukrainian; name *data* may not. |
| **Jersey number is per-appearance** | It lives on `Appearance`; `Player` keeps only a default to pre-fill. |
| **"No cards" is an affirmation** | `CardsSection` has three states and `Issued` cannot be empty — so "not filled in" can never become "the boxes were struck through". |
| **The referee is the only recorder** | `Confirmation` carries no match content, and confirming provably changes nothing else. |
| **Assessment block** | `NH` by jersey number, `Čd`, `Č`, `B` — the last two start `null`, because defaulting them to "yes" would quietly waive fines. |

**One detail the analysis rewards reading closely:** the worked example
contains `30´+ Lepiš A. - nesp. chování` — a card with a name and **no
jersey number**, shown to a deputy captain. Modelling the card subject as
a mandatory lineup reference would have made that unrecordable, so
`CardSubject` allows a named person.

**Beyond the brief:** a report-readiness check catches a recorded score
that disagrees with the recorded goals. Nobody catches that today until
the transcription crew does, a week later.

### The tests were checked, not assumed

Ten rules were deliberately broken and the suite re-run. **All ten were
caught:**

Minute half-time collapsing to minute 30 · blank card reason accepted ·
empty `Issued` list · Cyrillic in names · duplicate jersey numbers · goals
not persisted · unaccounted cards saved as affirmed-none · kickoff
timestamp dropped · second yellow flattened to straight red · named-person
card losing its name.

### Seed data is data, not code

Adding a group is: drop a file in, add one line to `index.json`, rebuild.
Nothing in Kotlin knows the name of any group.
`SeedLeagueCatalogTest.addingAGroupFileAndAnIndexLineMakesItAppear` is that
requirement written down as a test.

`shared` owns parsing behind a `SeedFileReader` interface; `composeApp`
supplies the bytes from Compose resources. That split keeps the whole
loader testable with no device and no platform type in `commonMain`.

`ShippedSeedDataTest` reads the **real** files rather than fakes, so a typo
in `6k.json` fails a build instead of surfacing on a phone at a pitch. It
guards the mistakes hand-editing actually produces: a fixture naming a team
that is not in the file, an identifier without its kind, a Cyrillic name.

### Persistence

Normalised SQLDelight tables — match, lineups, appearances, goals, cards,
confirmations — rather than a JSON blob in one column. It costs mapping
code and buys being able to ask which matches were left unfinished, which
is exactly what the app needs to offer to resume one.

The test that matters opens a real database file, **closes the driver**
(which is what losing the process amounts to) and opens a fresh driver on
the same bytes. It then checks the awkward values specifically, not just
that something came back.

---

## 5. Decisions taken during the work

| Decision | Who | Note |
|---|---|---|
| `minSdk` **28** | Project owner | Consistency with their other apps. Moved out of TECH_STACK §5 Open. |
| **No network calls**; email leaves by platform intent | Project owner | Ktor stays catalogued, wired to no module. |
| `applicationId` **left as `cz.hspinovace.psmf`** | Project owner | **Still not confirmed.** Flagged in both build files and the Xcode project. |
| Gradle 9.5.1 / AGP 8.13.2 / Kotlin 2.2.20 / Compose 1.11.1 | Forced by compatibility | See §3. |
| `iosX64` dropped | Forced by upstream | No `ios_x64` variant is published any more. |
| Normalised persistence over a JSON blob | Judgement | Queryability for the resume flow. |
| detekt thresholds raised, baseline kept empty | Judgement | `LongMethod` 80, `TooManyFunctions` in files 25, `ReturnCount` 4 — each with the reason in the config. Every finding was fixed in code, none suppressed. |

---

## 6. The APK-size question, answered before anyone asks

Release APK went **9.4 MB → 25.7 MB** between the two gates. It looks
exactly like bloat. It is not.

Uncompressed DEX is essentially unchanged: 24.5 MB before the domain
model, 24.8 MB after. What changed is **how it is stored**: from
**minSdk 28, AGP writes DEX into the APK uncompressed** so that ART can
memory-map it instead of extracting it at install time.

Confirmed by building one commit twice with only `androidMinSdk` differing:

```
minSdk 24   →   9.5 MB    classes.dex   4,612,947 bytes (deflated)
minSdk 28   →  25.7 MB    classes.dex  13,275,416 bytes (stored)
```

The `.apk` on disk is larger; the installed footprint and cold-start time
both improve. **The number to watch for real growth is uncompressed DEX,
not APK size.** Nothing is minified yet — `isMinifyEnabled` is false,
because release signing and shrinking happen outside the agent workspace.

---

## 7. Agent tooling

### Skills — 14, chosen rather than copied wholesale

From **chrisbanes/skills**: the router plus `compose-state-and-effects`,
`compose-performance`, `compose-component-design`,
`compose-ui-testing-patterns`, `kotlin-concurrency-and-flow`,
`kotlin-api-design`, `kotlin-control-flow`, `gradle-run`.
`compose-animations` and `compose-focus-navigation` are installed only
because four other skills link to them.

From **mmiani/kotlin-kmp-claude-agent-skills**:
`kotlin-platform-kmp-bridges` (expect/actual — the rule CLAUDE.md is
strictest about), `kotlin-project-modularization`,
`kotlin-build-kmp-gradle-governance`.

Deliberately **not** installed: the GitHub-workflow skills (no remote yet)
and the KMP skills covering navigation, deep links, adaptive UI, data
layer, state management, bugfix, feature implementation, refactor safety,
testing and review. Reasoning for every inclusion and exclusion is in
`.claude/README.md`. Worth revisiting `kotlin-testing-kmp` and
`kotlin-project-state-management` when screens arrive.

### Hooks — three, all verified firing in real sessions

| Hook | What it does | Verified how |
|---|---|---|
| `ktlint-format.sh` | `PostToolUse` — formats the Kotlin file just written, via the ktlint CLI rather than Gradle | Claude wrote unformatted code; the file on disk came back formatted |
| `guard-shared-tests.sh` | `PostToolUse` — rejects `org.junit` / `io.mockk` under `shared/src/commonTest` | Blocked, and the explanation reached the model **by name**. Tested with CLAUDE.md temporarily hidden, since CLAUDE.md alone already stops this and would have masked the test |
| `run-shared-tests.sh` | `Stop` — runs `:shared:jvmTest` when a session finishes | Broke a domain rule; a real session was stopped with the failing test named |

The Stop hook costs **~1 s** in the common case (Gradle finds the task up
to date) and 13 s after a real edit — far enough under the one-minute mark
to run unconditionally. It carries a `GATE_ON_KOTLIN_CHANGES` switch, off
by default, for when that changes. It also guards against re-entry, without
which a failing suite would leave a session unable to ever finish.

### Permissions

Three broad allow entries — `./gradlew`, `git`, `ktlint` — and one explicit
deny on `git push`. Kept broad on purpose; golblok accumulated nine
near-identical entries one prompt at a time, which is how an allowlist
stops being reviewable. The container also holds no SSH key or credential
helper, so it cannot push regardless.

---

## 8. Open items and known gaps

### Needs a decision

- **`applicationId` is still unconfirmed.** `cz.hspinovace.psmf` is in the
  Android build file *and* the Xcode project, both with comments saying it
  is not final. It becomes permanent at first publication. golblok uses
  `cz.hsp.footballmatch`.

### Known gaps, all deliberate

- **The Compose-resource read path is not exercised at runtime.** The seed
  files are packaged into the APK at the correct path and the parser is
  well tested, but nothing reads them on a device until the fixtures screen
  exists.
- **The iOS Xcode project has never been opened by Xcode.** It was
  generated on Linux. The Swift is the part worth keeping;
  `iosApp/README.md` says what the first Mac session should check.
- **Power-play (10 minutes after a dismissal) is not modelled.** It is a
  timer belonging with the live console.
- **Forfeit (*kontumace*) is not modelled.** Not needed by the six screens.
- **Report versioning / amendment is not built.** Required by the product
  (§2.12, §5.3), not by the demo.

### Environment caveat

Claude Code ignores `permissions.allow` from a project `settings.json`
until the workspace is **trusted once** in an interactive container
session. Until then every Gradle command prompts. Hooks are unaffected.

---

## 9. Commit history

All on `master`, local only, no remote.

| Commit | Subject |
|---|---|
| `1812e8d` | Add .gitattributes before any other file |
| `14046a8` | Add container sandbox adapted from golblok |
| `24a8287` | Scaffold KMP project: shared, composeApp, catalog, detekt, ktlint |
| `74c4d97` | Settle the build matrix that actually works together |
| `132c5da` | Pin buildToolsVersion; document the build matrix and timings |
| `eca684b` | Add project skills, hooks and permissions |
| `a091a0f` | Add iosApp Xcode wrapper |
| `6e837dc` | Update TECH_STACK with what the scaffold actually established |
| `5d51bcc` | Set minSdk to 28; record the Ktor and minSdk decisions |
| `6c383c6` | Add the domain model and the rules that get modelled wrongly |
| `009cddb` | Fix detekt findings without touching the baseline |
| `3f9763b` | Load league data from seed files: data, never code |
| `78927e3` | Persist an in-progress match so a kill cannot lose it |
| `9a17f07` | Add the Stop hook now that a suite exists |
| `fe20012` | Record why the APK tripled at minSdk 28 |

---

## 10. Pinned versions

```
Gradle 9.5.1          AGP 8.13.2           Kotlin 2.2.20        JDK 17
Compose MP 1.11.1     lifecycle 2.10.0     Koin 4.2.2           SQLDelight 2.3.2
coroutines 1.11.0     serialization 1.11.0 datetime 0.8.0       Turbine 1.2.1
detekt 1.23.8         ktlint 1.8.0         Ktor 3.5.2 (catalogued, unwired)
minSdk 28             targetSdk 36         compileSdk 36        buildTools 36.0.0
```

**Do not bump Gradle, AGP, Kotlin, Compose or lifecycle individually.**
Read `docs/BUILD_MATRIX.md` first.

---

## 11. Next session

Build the six demo screens: Fixtures, Match header, Lineup, Live console,
Assessment, Recap & confirm, plus Export and the read-only Settings screen.

The foundation they need is in place — domain model, seed loading,
persistence, DI, localisation and the quality gates. The first screen to
build is Fixtures, because it is also what finally exercises the
Compose-resource seed path on a real device.
