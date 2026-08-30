# PSMF Match Report — Agent Instructions

Kotlin Multiplatform app (Android + iOS) that replaces the handwritten *Zápis o
utkání* for Hanspaulská liga. Referees record a match pitch-side; the app produces
the same report the paper form does.

**Read first:** `docs/TECH_STACK.md` for the stack and its constraints,
`docs/LEAGUE_APP_ANALYSIS.md` for the domain. The analysis is the authority on
what the product must do — do not infer requirements, look them up.

**This is not golblok.** golblok is a separate live product. Some habits from it
are wrong here and are called out below.

---

## Commands

Run from the repo root. Android and shared code build in the container; **iOS
builds only on the Mac.**

```
./gradlew build                          # everything buildable on this host
./gradlew :shared:allTests               # shared tests, all targets
./gradlew :shared:jvmTest                # shared tests, JVM only — fastest loop
./gradlew :androidApp:assembleDebug      # Android APK
./gradlew detekt                         # static analysis, must stay green
./gradlew :shared:iosSimulatorArm64Test  # macOS only
```

Single test class: `./gradlew :shared:jvmTest --tests "cz.hspinovace.psmf.SomeTest"`

## Layout

```
shared/       domain + data. No UI, no Compose imports.
                commonMain/  — the default home for everything
                androidMain/ — only what genuinely differs
                iosMain/     — only what genuinely differs
composeApp/   Compose Multiplatform UI + ViewModels. Multiplatform.
androidApp/   Android entry point ONLY — MainActivity, PsmfApplication,
                the manifest, launcher resources. Uses src/main/, not
                androidMain/. Put nothing here that could live in
                composeApp; AGP 9 forces the split, it is not a design.
iosApp/       Xcode wrapper. Touched rarely; changes here need the Mac.
docs/         TECH_STACK.md, LEAGUE_APP_ANALYSIS.md
```

Prefer `commonMain` always. Dropping into `androidMain`/`iosMain` is a decision that
needs a reason — say what it is.

## Architecture

- UI observes state from ViewModels. No logic in composables.
- ViewModels hold `StateFlow<UiState>`, expose `onEvent()`, and delegate to
  UseCases. No business logic in a ViewModel.
- Data sources sit behind interfaces. Always — this is what makes fakes possible.
- **No platform types in `shared/commonMain`.** No `Context`, no `UIViewController`,
  no `java.*`. If you need one, it belongs behind an `expect`/`actual` or an
  interface implemented per platform.
- DI is **Koin**. Hilt is Android-only and cannot be used here.
- Serialization is **kotlinx.serialization**. Never hand-write JSON parsing.

## Testing

- Shared code: `kotlin.test` (`kotlin.test.Test`, `assertEquals`, …), Turbine for
  Flows, `kotlinx-coroutines-test` for dispatchers.
- **Mocking in shared code: hand-written fakes.** MockK is JVM-only and must not
  appear in `commonMain` tests. Repositories are behind interfaces precisely so
  fakes are cheap — write a `FakeXRepository`, not a mock.
- Android-target tests are permitted in principle, but **they do not run yet**:
  the AGP 9 KMP library plugin builds no host-test compilation unless
  `withHostTestBuilder { }` is added to that module. A test dropped into
  `androidUnitTest` today is silently ignored — no task, no compile, no
  warning. Add the builder first, verify it fails when it should, then write
  the test. See `docs/BUILD_MATRIX.md`.
- Every UseCase gets tests. Every domain rule in TECH_STACK.md §4 gets a test that
  would fail if the rule were violated.

## Domain rules that are easy to get wrong

Full list and sources in `docs/TECH_STACK.md` §4. The ones that bite:

- **Minute is not an `Int`.** `30´+` and `60´+` are valid. Model it as a type.
- **A red card records straight vs. second yellow.** Suspension maths depends on it.
- **Every card carries a mandatory free-text reason.**
- **A goal may have no scorer.**
- **An RP number and a fallback identification are two different things.** An
  `RpNumber` is issued by PSMF, immutable, and **never user-editable** — there is
  no UI path to typing one, and `Player.addedAtThePitch()` takes no RP parameter.
  A date of birth or birth number is what a person enters instead. At least one
  of the three must be present or the player cannot be built.
- **What was written in the `Číslo RP` column is a per-match fact.** It lives on
  the appearance as `reportedIdentification`, is non-null, and is **stored, never
  derived at export time** — a player registered later must not retroactively
  change an old report.
- **A team owns two kit sets and the lineup records which was worn.** `label` is
  verbatim from PSMF and authoritative for the report; `colours` is for the app
  only. Neither is derivable from the other.
- **The match clock never pauses.** 2 × 30 gross time, continuous. There is no
  pause, stop, resume or adjust operation and there must not be one. The power
  play is the only timer with a lifecycle: ten minutes, not shortened by a goal.
- **The app must never claim a player is eligible.** It may warn that one might
  not be. Absence of a warning is not clearance — fielding an ineligible player
  is a technical forfeit, and an app that displayed "clear" would have caused it.
  There is deliberately no `isEligible` and nothing that renders as a green tick.
- **Jersey number lives on the appearance, not the player.**
- **"No cards" is an explicit affirmation**, not an empty list.
- **The report is versioned, never locked.** Amendments are allowed; the delta is
  part of the output.
- **The referee is the only recorder.** Never design a flow where two parties record
  independently and must be reconciled.

## Things golblok does that are wrong here

Listed because they will otherwise be copied from a familiar codebase:

| golblok | Here |
|---|---|
| Hilt | Koin — Hilt is Android-only |
| Manual `org.json` | kotlinx.serialization |
| JUnit 5 + MockK everywhere | `kotlin.test` + fakes in shared code |
| Foreground service running the clock | Store the kickoff timestamp, derive elapsed time. **iOS cannot run a background timer at all** |
| Clock pauses for injuries and breaks | **PSMF has no stoppage.** The clock runs continuously; the referee adds time instead |
| One kit colour per team | A team owns **two** kit sets and picks one per match |
| Assists, substitutions | Neither appears on the ZoU. Do not build them |
| User-configurable match rules | The league sets them. A referee changing the half length is a defect |
| Local team creation | Teams are league entities |
| 1,721-line screen files | Split before it happens, not after |

## Working agreements

- Czech-first localisation from the first screen. No hardcoded user-facing strings.
- League data is **data, not code**. Adding a group is a file plus one index line;
  see `composeApp/src/commonMain/composeResources/files/leagues/README.md`, which
  also carries the rule that seed UUIDs are never regenerated.
- `detekt` must stay green. The baseline is empty and should stay that way.
- Version catalog for every dependency. No hardcoded coordinates in build files.
- No secrets, keystores or `.env` in the repo. Release signing happens outside the
  agent workspace.
- The container cannot push. Commit freely; pushing is the human's job.
- Do not add a backend, a database or an auth system. None is decided yet — see
  TECH_STACK.md §5. If a task seems to need one, say so rather than choosing one.

## End of task

Update `docs/TECH_STACK.md` when a decision in §5 Open gets settled, or when a new
library or platform constraint is introduced. Keep §2 Decided describing only what
actually exists or is genuinely committed — never aspiration.
