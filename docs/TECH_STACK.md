# Tech Stack — PSMF Electronic Match Report

**Status:** Scaffold and domain built, 2026-08-29. Three Gradle modules
(`shared`, `composeApp`, `androidApp`), an iOS wrapper that has never been
compiled, and one placeholder screen. Domain model, seed loading and
SQLDelight persistence exist. **None of the six demo screens yet.**
**Source of requirements:** `docs/LEAGUE_APP_ANALYSIS.md` (business analysis, 2026-08-25).

> **Rule for this document:** it describes what *is*, plus what has been *decided*
> and not yet built — clearly separated. It must never describe an app that does
> not exist. Anything speculative belongs under **Open** or **Deferred**.
> (This is a direct lesson from golblok, whose TECH_STACK.md described a Firebase
> backend that was never written and actively misled every agent that read it.)

---

## 1. What this is

A referee-facing mobile app that replaces the handwritten *Zápis o utkání* (ZoU)
for Pražský svaz malého fotbalu / Hanspaulská liga. The referee records the match
pitch-side; the app produces the same report the paper form produces, delivered
digitally the same evening.

**Not** a fork of golblok. Green field. golblok is a separate, live product with
its own repository — reuse is at the level of interaction design and domain logic,
never code.

---

## 2. Decided

### Platforms
| | |
|---|---|
| Targets | Android and iOS, referee-facing |
| Approach | Kotlin Multiplatform + Compose Multiplatform (shared UI) |
| Team-facing surface | **None for the pilot.** Captains write lineups at the pitch and always have — see analysis §5.1 |
| Web | Not now. Revisit only if lineups move to advance submission |

**Android `minSdk` is 28** (Android 9), settled 2026-08-29 by the project
owner for consistency with their other apps. Can be lowered if the referee
population turns out to skew to older devices than expected.

Shared Compose UI rather than native SwiftUI: the UI is utilitarian data entry for
a small audience, and one implementation is worth more here than platform-idiomatic
polish. Platform code is confined to what genuinely differs.

### Language and core libraries

| Concern | Choice | Note |
|---|---|---|
| Language | Kotlin | |
| UI | Compose Multiplatform | shared across both platforms |
| Async | Coroutines + Flow | as golblok |
| DI | **Koin** | Hilt is Android-only and does not work in shared code |
| Serialization | kotlinx.serialization | replaces golblok's hand-written `org.json` |
| HTTP | Ktor client | **Versioned in the catalog, applied to no module.** Settled 2026-08-29: no network calls for now. The report leaves by platform email intent, which is not an HTTP call |
| Local storage | SQLDelight | KMP-native; offline is a hard requirement |
| ViewModels | `androidx.lifecycle` multiplatform | |

Two of these are direct corrections of golblok habits that **do not carry over**:
Hilt is Android-only, and manual `org.json` serialisation was the source of a whole
class of "forgot to add the field" bugs.

### Data

For the demo and shadow-recording phase: **no backend at all.** Fixtures, teams,
kit colours and appeared-players are public and scrapeable (analysis §2.9), and the
report goes out by email — an already-accepted channel.

When RP numbers arrive (blocked on A1/A2):

| | |
|---|---|
| Store | Supabase (Postgres) |
| Access | PostgREST over HTTPS with Ktor — **no SDK** |
| Population | Scheduled job scraping psmf.cz on an interval; the app never reads PSMF directly |
| Caching | The whole group resident on device — 12 teams, ~180 players, tens of KB |

Postgres over a document store because the domain is relational and season-scoped
(`TeamEntry`, `SquadMembership` are season-scoped joins — analysis §6). PostgREST
over an SDK because **neither Supabase nor Firebase ships a first-party
multiplatform SDK**; plain REST from shared code carries no dependency and behaves
identically on both platforms.

### Identity
**No accounts, no logins, no digital signature.** The app replaces paper; it does
not introduce an identity system. Captains confirm on the referee's device, in each
other's presence — parity with a pen mark nobody verifies.

Note the boundary: this covers *signing*. It does not automatically extend to *data
access*, and RP numbers are where the two questions separate. Revisit when A1/A2 land.

### Testing

| Scope | Framework |
|---|---|
| Shared code | `kotlin.test` |
| Flow testing | Turbine (multiplatform) |
| Coroutines | `kotlinx-coroutines-test` (multiplatform) |
| Mocking | **Hand-written fakes.** MockK is JVM-only |
| Android target only | JUnit 5 + MockK permitted |

**This is a real break from golblok.** Its JUnit 5 + MockK rules apply only to
Android-target tests here. Shared code cannot use either. Since repositories sit
behind interfaces, fakes are straightforward and arguably better than mocks; reach
for a multiplatform mocking library only if fakes become unwieldy.

### Build and quality

- Gradle with a **version catalog from day one** — golblok's catalog was half-used
  and versions drifted.
- **detekt + ktlint from the first commit.** On a green field the baseline starts
  empty, which is worth far more than retrofitting one later.
- CI on GitHub Actions: Linux runner for shared + Android, **macOS runner for iOS**.
- Czech-first localisation from the first screen. Retrofitting it is far worse than
  maintaining it.

---

## 3. Platform constraints that shape the design

### The match timer cannot run in the background on iOS
golblok keeps its clock in an Android foreground service. **iOS has no equivalent** —
background execution is restricted to a fixed set of modes and a match timer is not
one of them.

The resolution works better on both platforms: **store the kickoff timestamp and
derive elapsed time on resume.** Nothing ticks in the background; the clock is a
computation, not a process. What is lost is a live-updating notification. What is
gained is a timer that cannot drift, cannot be killed, and survives a reboot.

### AGP 9 requires a three-module layout

**A module applying `org.jetbrains.kotlin.multiplatform` cannot also apply
`com.android.application` or `com.android.library`.** That is not a
temporary incompatibility, it is the shape AGP 9 expects, and the legacy
path is removed entirely in AGP 10. The Android entry point therefore lives
in its own module:

```
androidApp/    com.android.application -- MainActivity, Application, manifest, launcher res
composeApp/    KMP + com.android.kotlin.multiplatform.library -- shared Compose UI
shared/        KMP + com.android.kotlin.multiplatform.library -- domain + data
```

Migrated 2026-08-29. Everything is on its latest stable release except
where Compose Multiplatform 1.12.0 sets a `compileSdk` 37 floor, which is
now the **only** real version constraint in the project. Evidence, the four
traps the migration hides, and the two surviving workarounds:
`docs/BUILD_MATRIX.md`.

One trap deserves repeating here because it silently weakens testing: the
KMP library plugin creates **no Android host-test compilation** unless
`withHostTestBuilder` is declared. A test placed in `androidUnitTest` today
does not run and does not warn.

Also: **`iosX64` is no longer a viable target.** Neither Compose
Multiplatform nor the lifecycle port publishes an `ios_x64` variant, so the
Intel simulator is gone; the targets are `iosArm64` and
`iosSimulatorArm64`, and the simulator therefore needs an Apple Silicon Mac.

### PDF and spreadsheet generation do not belong in the app
JSON, CSV and formatted text are shared code and effectively free. PDF and `.xlsx`
have no good shared-Kotlin library, so building them in the app means writing each
twice. **Render them server-side** from a JSON payload — one implementation, no
platform code.

Worth knowing: the official ZoU is itself an `.xlsx`, so producing a spreadsheet in
their layout may be both easier than PDF and more useful to PSMF. A8 asks them.

---

## 4. Domain constraints the model must respect

From the analysis §2.5 and §6. These are the things that get modelled wrongly by
default.

- **Minute is not an integer.** `30´+` (half-time) and `60´+` (after the final
  whistle, before signing) are valid values.
- **A red card must record whether it was straight or a second yellow** (`2. ŽK`) —
  suspension arithmetic depends on the distinction.
- **Every card carries a mandatory free-text reason.**
- **A goal may have no scorer.** The worked example contains one.
- **Player identifier is one field plus a discriminator**, not several: the ZoU has
  a single column holding either a `Číslo RP` or a date of birth. Model it as
  `identifier` + `identifierType ∈ {RP, DATE_OF_BIRTH, BIRTH_NUMBER}`.
  `BIRTH_NUMBER` exists only because A28 is unresolved.
- **Jersey number belongs to the appearance, not the player** — numbers change
  between matches.
- **"No cards issued" is an explicit affirmation**, not an empty list. The paper
  form requires the boxes to be struck through.
- **The report is versioned, not locked.** Snapshot at captain confirmation, allow
  amendment, deliver both plus the delta. Freeze at Monday 19:00, which is already
  the rule on the form.
- **There is exactly one recorder** — the referee. Captains confirm what the referee
  wrote. A design where two parties record independently invents a reconciliation
  problem the paper process does not have.

---

## 5. Open

| Question | Blocks | Reference |
|---|---|---|
| `applicationId` / iOS bundle ID | Permanent at publication | **`cz.hspinovace.psmf` is in the build files and the Xcode project, and is NOT settled.** golblok uses `cz.hsp.footballmatch`; align with the company's existing convention before any store upload |
| Does the store hold RP numbers? | Whether a backend is needed at all | A1, A2 |
| `rodné číslo` vs date of birth | Data-protection weight | A28 — largest legal exposure |
| Export format | What gets built | A8 |
| Controller vs processor | Legal footing | A10, A26 |

## 6. Deferred

Not rejected — out of scope until something changes.

- Backend of any kind, until RP numbers exist.
- PDF / `.xlsx` rendering (server-side when it happens).
- Team-facing web surface.
- Device-to-device lineup handoff — golblok's QR machinery suits this job, though
  not the roster-sharing role it was built for.
- Player photographs — blocked on A9 and A27, and currently has no regulatory
  standing.
- Suspension warnings — depends on A3.
- TAUT-managed agent workspace. Being piloted on golblok first.
