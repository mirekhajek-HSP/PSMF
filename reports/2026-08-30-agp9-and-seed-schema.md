# AGP 9 migration and the seed schema revision

**Session date:** 2026-08-30
**Repository:** `~/dev/psmf-app` (WSL Ubuntu)
**Commits:** 9 — `bb80eb3` … `9d74f87`
**Outcome:** both gates met, tree clean, no screens built

---

## 1 · Status at a glance

| | Before | After |
|---|---|---|
| Gradle modules | 2 (`shared`, `composeApp`) | **3** (+ `androidApp`) |
| AGP | 8.13.2 | **9.3.2** |
| Gradle | 9.5.1 | **9.7.1** |
| Kotlin | 2.2.20 | **2.4.10** |
| Compose Multiplatform | 1.11.1 | **1.12.0** |
| lifecycle (JetBrains port) | 2.10.0 | **2.11.0** |
| compileSdk / targetSdk | 36 | **37** |
| Shared tests | 84 | **155** |
| detekt baseline | empty | **still empty** |
| Cold build | 136 s | **112 s** |
| `:shared:jvmTest` forced | 13 s | **4 s** |

Everything is on its latest stable release. `./gradlew build`,
`:androidApp:assembleDebug`, `detekt`, `ktlintCheck` and `:shared:allTests`
all pass.

---

## 2 · Part A — the AGP 9 migration

### What the previous session got wrong

`docs/BUILD_MATRIX.md` recorded six versions as locked together until *"AGP
becomes compatible with the KMP plugin"*, and treated that as upstream work
somebody else had to do.

**The symptom was diagnosed correctly. The conclusion was wrong.** Since AGP
9, a module applying `org.jetbrains.kotlin.multiplatform` cannot also apply
`com.android.application` or `com.android.library` — that part is real and
was bisected properly. But AGP 9 was already compatible; it required a
**module restructure**, which both JetBrains and Google document. It was also
never optional: the legacy path is removed entirely in AGP 10.

**Four of the six versions were pinned by nothing but that misreading.**

The correction is written into `BUILD_MATRIX.md` rather than quietly
deleted, because the old reasoning read convincingly and would otherwise be
re-derived by the next person to look.

### The one constraint that is real

Compose Multiplatform 1.12.0 requires `compileSdk` ≥ 37. Verified by
building at 36, which fails with an explicit AAR-metadata error naming every
`androidx.compose` artifact. Everything else is simply latest.

That `compileSdk` can now move again matters beyond tidiness: **the Play
Store targetSdk floor rises every year**, so a project that cannot raise its
targetSdk has a compliance deadline rather than a preference.

### The Kotlin pin was collateral damage

The old matrix blamed Compose: *"built against 2.2.20 and its tooling cannot
read Kotlin 2.4 metadata."* Not reproducible. Kotlin was bumped to 2.4.10 on
its own — **before** Compose moved off 1.11.1 — and built clean. Whatever
produced that metadata error belonged to the AGP 8 configuration.

### The new module layout

```
androidApp/    com.android.application         entry point ONLY
composeApp/    KMP + com.android.kotlin.multiplatform.library
shared/        KMP + com.android.kotlin.multiplatform.library
iosApp/        Xcode wrapper, unchanged
```

`MainActivity`, `PsmfApplication`, the manifest and the launcher resources
moved to `:androidApp`. `AppModule.android.kt` deliberately **stayed** in
`:composeApp` — it is the `actual` for an `expect` in `commonMain` and has to
sit beside it.

`:androidApp` does not apply the Kotlin Android plugin: AGP 9 has Kotlin
support built in and the old plugin is incompatible with the new DSL.

### Gate A against its criteria

| Criterion | |
|---|---|
| `./gradlew build` passes on AGP 9 | ✅ |
| `:androidApp:assembleDebug` produces an APK | ✅ (task moved from `:composeApp`) |
| `:shared:allTests` green, detekt green, baseline empty | ✅ |
| `BUILD_MATRIX.md` rewritten | ✅ |
| `iosApp` still configured | ✅ **needed no change** — see below |
| Build times re-measured | ✅ |
| App launches in cs / en / uk | ⚠️ **verified, but not by launching** |

**iosApp needed no change.** `:composeApp` still declares the iOS targets and
the framework, so `:composeApp:embedAndSignAppleFrameworkForXcode` and the
`xcode-frameworks` path are unmoved. Task confirmed present; not compiled,
which is a Mac.

**Localisation was verified from the packaged APK, not on a device.** There
is no device or emulator in the container — `adb devices` is empty and the
image ships no system image. The resource table carries `app_name` as
default `Zápis o utkání`, `(uk) Протокол матчу`, `(en) Match Report`, and
the three Compose resource bundles decode to the right Czech, English and
Ukrainian strings including the Cyrillic. **That confirms packaging, not
rendering — a device run is still owed**, since glyph rendering is exactly
what the Ukrainian check exists for.

### Build times (same host, same suite)

| Scenario | Before | After |
|---|---|---|
| **Cold**, empty `GRADLE_USER_HOME` | 136 s | **112 s** |
| Clean recompile, cache off | 26 s | 27 s |
| Warm build, nothing changed | 1 s | 1 s |
| **`:shared:jvmTest` forced** | 13 s | **4 s** |
| **`:shared:allTests` forced** | 15 s | **6 s** |
| detekt forced | 3 s | 1 s |

Nothing regressed; tests got roughly three times faster, which is the figure
the Stop hook pays. APK unchanged — uncompressed DEX is 24.8 MiB before and
after, so Compose 1.12 and lifecycle 2.11 cost nothing.

---

## 3 · Four traps the migration hides

Each cost real time. All four are now in `docs/BUILD_MATRIX.md`.

### 1. The JetBrains migration guide is out of date

It says the block is `androidLibrary {}`. As of AGP 9.3 that spelling is
deprecated in favour of `android {}` nested inside `kotlin {}`, and **the
import the guide gives does not resolve at all**. The working DSL was
recovered by `javap`-ing `KotlinMultiplatformAndroidLibraryExtension` out of
`gradle-api-9.3.2.jar`.

### 2. Android unit tests are silently dropped

The KMP library plugin creates **no** Android host-test compilation unless
`withHostTestBuilder` is declared. Proven: a test planted in
`shared/src/androidUnitTest/` calling `fail()` passed `./gradlew build` and
`:shared:allTests` — no task ran, no file was compiled, nothing warned.

This mattered because `CLAUDE.md` explicitly permitted Android-target tests.
It now carries the warning and the one-line fix. **A test that never runs is
worse than no test.**

### 3. `platforms;android-37.0`, not `platforms;android-37`

Android versions SDK platforms with a minor component now. `sdkmanager` will
not resolve the bare major; AGP still takes `compileSdk = 37` and finds it.

### 4. `buildToolsVersion` still has to be pinned

It survives onto the new extension, and it still matters — left to its
default, AGP re-downloads a different build-tools package on **every**
container run, because `docker compose run --rm` discards the writable layer.
Verified after the move: the container holds exactly `build-tools/37.0.0`
and `platforms/android-37.0` after a full build, with nothing downloaded.

### The `annotations` workaround survives

`force("org.jetbrains:annotations:23.0.0")` is **still required**. Verified
by removing it, which fails immediately — and the AGP 9 error message states
the cause outright, more clearly than the original bisection did: Gradle pins
`annotations` to 13.0 on the buildscript classpath to match its embedded
Kotlin, and AGP drags in 23.0.0 via `ddmlib`, `layoutlib-api` and
`repository`. **It was never SQLDelight-specific.**

---

## 4 · Part B — the seed schema revision

Schema, DTOs, parser, tests and `6k.json` changed together, because they are
one contract. 31 files, +5,196 / −1,597.

### B1 · Opaque stable ids

Old ids encoded names (`t-kominici`, `p-kominici-01`). A rename or a transfer
made them a lie, and **persisted match reports on a device reference these
ids**, so they must outlive the names.

Every team, player, kit and fixture now carries an opaque UUID `id` plus a
readable `ref`. Files point at each other **by `ref`** — 66 fixtures full of
UUIDs would be unmaintainable by hand — and the loader resolves them.

**Player refs are deliberately not team-scoped**: `ruzicka-radek`, never
`kominici-01`. The analysis permits one transfer per season, and a
team-scoped ref would change on transfer, mint a new UUID, and orphan every
match the player already appears in.

The never-regenerate rule is written in three places, on purpose:

1. the seed `README.md`, where a hand-editor will actually look;
2. `SeedIdentity.kt` — the rule as executable code, with the natural key
   defined;
3. `ShippedSeedDataTest`, anchored against the **actual shipped UUIDs**, so
   regenerating the file fails the build.

### B2 · Identification is two things, not one

The previous model had one polymorphic field with a discriminator. That was
wrong, and it is the correction that shaped most of this part.

- **`RpNumber`** is *issued by PSMF*, immutable, and **never user-editable**.
- **`dateOfBirth` / `birthNumber`** are *entered by a person* when there is
  no RP number to use.

`Player` carries all three with an invariant that at least one is present —
a player who cannot be identified cannot be built.

**`Player.addedAtThePitch()` takes no RP parameter at all.** That is what
makes "the user must never be able to type one" true in the model rather than
only in the UI, and `copy(rpNumber = …)` on a pitch-added player throws.

What was actually written in the `Číslo RP` column moved to
`Appearance.reportedIdentification` — **non-null, and stored rather than
derived at export time**. A player registered later must not retroactively
change an old report; that is the same versioning principle as §5.3, and it
has a test.

A `PlayerOrigin` flag distinguishes seed players from ones a referee added,
so the latter can be reconciled when PSMF registers them.

### B3 · Kit sets

A team owns **two** and the lineup records which was worn — which is exactly
why `Barva dresů` sits on the lineup block of the paper form.

`label` is verbatim from PSMF and **authoritative for the report**;
`colours` is app-only. Neither is derivable from the other, and the seed data
demonstrates it: `bílo-modrá` sits beside `["bílá", "modrá"]`, because the
first element takes a different grammatical suffix in Czech.

### B4 · Venues

`venues.json`, league-wide rather than duplicated per group, with every
fixture validated against it.

### B5 · Discipline — advisory only

Yellow totals per group per season with a **mandatory `asOf`**. Warning on
even totals; two yellows in one match contribute zero; a yellow then a
straight red counts as one; **red cards are not computed at all**.

> The app must never claim a player is eligible. It may warn that one might
> not be. Absence of a warning is not clearance.

There is deliberately no `isEligible`, no boolean, and nothing a screen could
render as a green tick. Fielding an ineligible player is a **technical
forfeit** — if the app displayed "clear" and the player was banned, the app
caused that.

### B6 · The clock runs continuously

`MatchClock.kt` holds both timers and states why one stops and the other
cannot. **There is no pause, stop, resume or adjust operation anywhere**, and
that is written down rather than left to be inferred — golblok pauses its
clock, and it is exactly the kind of thing copied from a familiar codebase
without anyone noticing it is wrong here.

The power play is the only timer with a lifecycle: ten minutes fixed, **not**
shortened by a goal, unaffected by further dismissals.

### B7 · Renames and additions

`givenName` → `firstName` (the ZoU's surname-first display order is a display
concern and lives in `asWrittenOnReport`). `periods` joins
`halfLengthMinutes` as group-level data rather than a constant.

### Gate B against its criteria

| Criterion | |
|---|---|
| Schema, DTOs, parser, tests and `6k.json` consistent | ✅ |
| `venues.json` exists and is referenced by fixtures | ✅ |
| The add-a-group test still passes | ✅ |
| `ShippedSeedDataTest` catches a missing venue and a blank kit label | ✅ both proven to bite |
| UUID stability rule documented and tested | ✅ README + `SeedIdentity.kt` + anchored test |
| Suspension arithmetic tested, both awkward cases | ✅ |
| Power-play lifecycle modelled and tested | ✅ |
| `:shared:allTests` green, detekt green, committed | ✅ |

---

## 5 · Mutation testing

19 deliberate breakages, **19 caught**, tree clean afterwards.

| Rule broken | Caught |
|---|---|
| Importer mints a new id instead of preserving | ✅ |
| A shipped UUID is regenerated | ✅ |
| A player need not be identifiable at all | ✅ |
| A pitch-added player may carry an RP number | ✅ |
| Card absent still writes the RP number | ✅ |
| Date of birth written DDMMYY instead of YYMMDD | ✅ |
| A blank kit label is accepted | ✅ |
| The loader stops checking kit labels | ✅ |
| A team may own no kits | ✅ |
| A kit from another team resolves | ✅ |
| Fixtures not checked against `venues.json` | ✅ |
| **Two yellows in one match count as two** | ✅ |
| **A straight red accumulates like a yellow** | ✅ |
| **Suspension warns on odd totals** | ✅ |
| The warning loses its `asOf` date | ✅ |
| **The match clock pauses during a power play** | ✅ |
| The power play is five minutes, not ten | ✅ |
| A second dismissal extends the first period | ✅ |
| `periods` hardcoded to 2 | ✅ |

Each mutation was confirmed to fail the suite. **Which** test caught each one
was not captured — the run used `--quiet`, so only the exit status was
recorded.

---

## 6 · Test suite

155 tests across 21 classes, up from 84.

| Class | Tests | Guards |
|---|---|---|
| `SeedLeagueCatalogTest` | 25 | data-not-code, and every way hand-edited data goes wrong |
| `ShippedSeedDataTest` | 15 | the files that actually ship |
| `MinuteTest` | 10 | `30´+`, `60´+`, ordering |
| `KitTest` | 9 | kit sets and reference resolution |
| `SeedIdentityTest` | 8 | UUID preservation |
| `ReportedIdentificationTest` | 8 | the three situations §2.5 distinguishes |
| `CardTest` | 7 | reasons, straight vs second yellow, "none issued" |
| `SuspensionAdvisoryTest` | 7 | even totals, staleness, no clearance |
| `ZouLabelsTest` | 7 | fixed Czech export strings |
| `JerseyNumberOwnershipTest` | 6 | number belongs to the appearance |
| `MatchPersistenceTest` | 6 | survives the process being killed |
| `PersonNameTest` | 6 | Latin only |
| `PlayerIdentificationTest` | 6 | RP is never user-editable |
| `PowerPlayTest` | 6 | ten minutes, not shortened, independent |
| `YellowCardAccumulationTest` | 6 | the two awkward counting rules |
| `MatchClockTest` | 5 | the clock never pauses |
| `ReportReadinessTest` | 5 | what blocks export |
| `AssessmentTest` | 4 | `NH`, `Čd`, `Č`, `B` |
| `GoalTest` | 4 | a goal may have no scorer |
| `SingleRecorderTest` | 4 | captains confirm, never contribute |
| `DatabaseSmokeTest` | 1 | driver and schema |

---

## 7 · Decisions taken, and by whom

| Decision | Who | Note |
|---|---|---|
| Migrate to AGP 9 now | Project owner | Correctly identified that the old matrix drew the wrong conclusion |
| Identification is two fields, not one | Project owner | The correction that shaped Part B |
| A team owns two kit sets | Project owner | Corrected from an earlier reading |
| Venues are league-wide, own file | Project owner | |
| Discipline advisory, never authoritative | Project owner | Stated as a hard constraint |
| No clock pause — do not copy golblok | Project owner | |
| Fixtures reference teams by `ref`, not by UUID | This session | Hand-editability; persisted matches still store ids |
| Player `ref` not team-scoped | This session | Follows directly from the transfer rule |
| `venues.json` carries codes only, no names | This session | Names are not in the analysis and were not invented |
| Discipline `asOf` set mid-season (2026-10-05) | This session | A pre-season date would be all zeros and demonstrate nothing |
| Seed UUIDs minted from a seeded RNG | This session | Re-running the transform reproduces the file exactly |

---

## 8 · Open items and known gaps

**Deliberate, not oversights.**

- **`applicationId` is still `cz.hspinovace.psmf` and still unconfirmed.** It
  now lives in `androidApp/build.gradle.kts`. **Permanent at first
  publication** — confirm against the company convention before any store
  upload. golblok uses `cz.hsp.footballmatch`.
- **The app has not been launched since the migration.** Localisation was
  verified from inside the APK. A device run is owed.
- **Kit label vs. the versioning principle.** The lineup stores a kit
  *reference*, as specified — so editing a team's kit label later changes an
  old report's `Barva dresů`. That is the opposite of what B2 does for
  identification. Followed the specification; flagged for a decision.
- **`venues.json` holds 7 codes, not ~35** — only the ones the analysis
  actually names, all of which the fixture list uses. The rest arrive with
  real data.
- **Android unit tests do not run.** `withHostTestBuilder` is not declared.
  Documented in `CLAUDE.md` and `BUILD_MATRIX.md`; add it before writing the
  first such test.
- **No RP numbers exist**, and a test asserts so — it is the reminder to
  check the data-protection position when A2 lands.
- **`birthNumber` is modelled but unused.** Blocked on A28.
- **The seed README ships in the APK** as a ~7 KB asset, because Compose
  Resources packages everything under `files/`. Harmless, and it puts the
  UUID rule where a hand-editor will look.
- **Report versioning (§5.3) is still not built.** Amendments, deltas and the
  Monday 19:00 freeze remain unmodelled.
- **Forfeit / kontumace is still unmodelled.**
- **The Xcode project has never been opened.**
- **The container image was rebuilt** for SDK 37 — anyone else on this repo
  needs `docker compose build`.

---

## 9 · Commits

| | |
|---|---|
| `bb80eb3` | Extract androidApp so the Android entry point leaves the KMP module |
| `27ff62c` | Move to AGP 9.3.2 and the KMP library plugin |
| `22d3549` | Bump Kotlin to 2.4.10 |
| `3e3ccd7` | Raise compileSdk and targetSdk to 37 |
| `762a080` | Bump Compose Multiplatform to 1.12.0 |
| `c13a996` | Bump the lifecycle port to 2.11.0 |
| `f573942` | Rewrite the build matrix around what is actually pinned |
| `fc6bac0` | Revise the seed schema: opaque ids, split identification, kits, venues |
| `9d74f87` | Correct the documented domain rules to match the revised model |

51 files changed, +5,673 / −1,842.

Each version bump is its own commit, applied and verified one at a time, so
that a later failure is attributable. That is how the four dead pins were
told apart from the one real constraint.

---

## 10 · Documentation corrected

`CLAUDE.md`, `docs/TECH_STACK.md` and `docs/DEMO_SCOPE.md` **all** stated the
old one-field identification rule. Leaving it would have kept instructing
every future agent to reintroduce the error this session removed.

All three now also carry the rules that had no written form before: kit sets,
the continuously running clock, the power play, the one-sided suspension
advisory, and that seed UUIDs are never regenerated. `DEMO_SCOPE` screen 3
now says what it must allow — adding a player who turned up, **with no RP
field offered**.

---

## 11 · Next session

**The six demo screens**, starting with the fixture list. The domain, the
seed data and the persistence they need are all in place and tested.

Two things to do first, both small:

1. Run the app on a device and confirm Czech, English and Ukrainian render —
   particularly the Cyrillic glyphs.
2. Decide the kit-label question in §8.

Nothing else blocks screens.
