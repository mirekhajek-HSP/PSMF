# Prompt — AGP 9 restructure and seed schema revision

**Where:** WSL container, `~/dev/psmf-app` · **Model:** Opus for Part A, Sonnet for Part B

Both must land **before the six screens**. Part A gets structurally more expensive
with every screen added; Part B changes the shape of data the screens will read.

---

```
Two pieces of work on ~/dev/psmf-app, both before any screen is built. Part A
first, gate between them. Walk me through it, verify as you go.

Read CLAUDE.md, docs/TECH_STACK.md, docs/BUILD_MATRIX.md and
docs/LEAGUE_APP_ANALYSIS.md before starting.

Environment note: Claude Code ignores permissions.allow from the project
settings.json until the workspace is trusted once in an interactive container
session. Expect Gradle prompts until that happens.

## PART A — migrate to AGP 9

### The correction

docs/BUILD_MATRIX.md records six versions as locked together until "AGP becomes
compatible with the KMP plugin". The incompatibility it found is real and was
correctly bisected: since AGP 9, a module applying org.jetbrains.kotlin.multiplatform
cannot also apply com.android.application or com.android.library.

The conclusion is wrong. AGP 9 is already compatible — it requires a MODULE
RESTRUCTURE, not upstream work. Both JetBrains and Google document it:

  https://kotlinlang.org/docs/multiplatform/multiplatform-project-agp-9-migration.html
  https://developer.android.com/build/releases/agp-9-0-0-release-notes

And it is not optional. The legacy path survives behind
android.enableLegacyVariantApi=true but is REMOVED ENTIRELY IN AGP 10, which
JetBrains dated to Q2 2026 — already passed.

### The migration

Follow the official guide, not this summary — but the shape is:

  1. Extract the Android entry point into a new androidApp module applying
     com.android.application. MainActivity and anything Android-specific moves
     there.
  2. Move composeApp/src/androidMain to androidApp/src/main.
  3. Change the shared/composeApp Android plugin from com.android.library to
     com.android.kotlin.multiplatform.library.
  4. Replace the android {} block with kotlin.androidLibrary {}, using
     androidLibrary instead of androidTarget.
  5. Remove the kotlinAndroid plugin from androidApp — built in from AGP 9.
  6. Fix namespaces so they do not collide.
  7. Bump AGP to 9.x and Gradle to 9.1.0+.

Then re-test the whole version matrix. Several pins existed only because of this
incompatibility and should now move: Kotlin, Compose Multiplatform, lifecycle,
compileSdk/targetSdk. Take them one at a time so a failure is attributable.

Do NOT bump anything without re-running the full suite. Rewrite
docs/BUILD_MATRIX.md to record what is actually pinned and why, and delete the
claims that are no longer true.

Note why this matters beyond tidiness: compileSdk/targetSdk frozen at 36 becomes a
Play Store compliance problem, because Play's targetSdk floor rises every year.

### GATE A

  - ./gradlew build passes on AGP 9
  - :androidApp:assembleDebug (or whatever it is now called) produces an APK
  - the app still launches in Czech, English and Ukrainian
  - :shared:allTests still green, detekt still green, baseline still empty
  - BUILD_MATRIX.md rewritten
  - iosApp still configured for the new module layout — it references the shared
    framework and that reference will have moved
  - build times re-measured

Report the new version matrix and the times. Do not start Part B without checking in.

## PART B — revise the seed schema

The current shape works but has four problems. Change the schema, the DTOs, the
parser, the tests and 6k.json together.

### B1 · Opaque stable IDs

Current IDs encode names: "t-kominici", "p-kominici-01". A team renames or a player
transfers — the analysis says one transfer per season is permitted — and the ID
becomes a lie. Persisted matches reference these IDs, so they must outlive the
names.

Add an opaque UUID as the real id, and keep the readable slug as a separate `ref`
field for hand-editing and debugging.

CRITICAL: UUIDs are written into the seed file and must NEVER be regenerated. When
real psmf.cz data replaces the placeholder, the importer must preserve existing
UUIDs by matching on natural key, not mint new ones. A regenerated UUID orphans
every persisted match. Write that rule into the seed README and a test.

### B2 · Player identification — TWO fields, not one polymorphic one

This replaces the current `identifier` / `identifierType` pair, and it is a DOMAIN
MODEL change, not just a schema rename.

There are two genuinely different things, and the project owner is right that
collapsing them was wrong:

  - RP NUMBER — issued by PSMF when a player registers. Immutable. Arrives from
    their database. **The user must never be able to type or edit one.**
  - FALLBACK IDENTIFICATION — date of birth, or rodné číslo pending A28. Entered
    by a person when there is no RP number to use.

So on Player:

    rpNumber: RpNumber?          // league-issued, never user-editable
    dateOfBirth: LocalDate?
    birthNumber: BirthNumber?    // exists only because A28 is unresolved

    INVARIANT: at least one of the three must be present. Enforce it at
    construction — a player who cannot be identified at all cannot be built.
    If rpNumber is present, nothing else is required.

Three situations produce a value for the ZoU's single Číslo RP column, and the
analysis §2.5 distinguishes them:

  a. registered, card present      → write the RP number
  b. registered, card NOT to hand  → write the date of birth (the form's own rule:
                                     "U hráčů, kteří nemají k dispozici svůj
                                     registrační průkaz (RP), uvedou místo čísla RP
                                     jejich datum narození")
  c. not yet registered at all     → the pending-registration case

So what was actually written is a per-match fact, not a player attribute. Store it
on Appearance:

    reportedIdentification: { value: String, source: RP | DATE_OF_BIRTH | BIRTH_NUMBER }

Store it, do not derive it at export time. A report records what was written on the
day. If a player later gains an RP number, an old report must not retroactively
change — that is the same versioning principle as §5.3.

Also add a flag distinguishing players that came from seed data from players a user
added at the pitch, so the latter can be reconciled when PSMF registers them.

The user flow this supports, and which screen 3 must allow: a player is present who
is not in the squad list. The user adds them with first name, surname and date of
birth. **No RP field is offered.**

### B3 · Kit sets — a team owns two, and the match records which was worn

Corrected from the earlier reading. A team does not have "a kit colour". It owns
**two kit sets**, and picks one per match so that two teams do not play in similar
colours. The ZoU's Barva dresů is on the lineup block and is filled at the match,
which is exactly why: it records what was actually worn that day.

Seed data — on Team:

    "kits": [
      { "id": "...", "label": "modrá",       "colours": ["modrá"] },
      { "id": "...", "label": "bílo-černá",  "colours": ["bílá", "černá"] }
    ]

Order is meaningful: first is the primary. Both fields are needed because the ZoU
takes exactly what PSMF writes, and "bílo-černá" is not mechanically derivable from
["bílá", "černá"] — the first element takes a different grammatical suffix in
Czech. So `label` is verbatim and authoritative for the report and is NEVER
derived; `colours` is for the app only, for team chips and clash hints.

Match data — the lineup for each team records which kit was worn. Selected on
screen 3 by the referee, defaulting to the primary.

Tests: every team has at least one kit; every kit label is non-blank (the report
cannot be generated without it); a lineup's kit reference resolves to one of that
team's kits.

### B4 · Venues are missing entirely

The ZoU header requires Hřiště, the pitch. Fixtures reference a venue, but there is
no venue data anywhere in the seed files.

Venue codes are LEAGUE-WIDE, not group-specific — the analysis §2.2 lists ~35
pitches across Prague with short codes (METE1, MOTO1, ZAKOS, MIK, PRA, ZAK, P1). So
they belong in their own file, not duplicated into every group:

    files/leagues/venues.json

Fixtures reference a venue by code. Add a test that every venue code referenced by
a fixture exists in venues.json — the same class of check ShippedSeedDataTest
already does for teams.

### B5 · Disciplinary record — advisory only, never authoritative

Analysis §2.6. Yellow cards accumulate within a league group per season and trigger
an automatic suspension on EVEN totals — 2nd, 4th, 6th, 8th. Two yellows in one
match contribute ZERO to that total. A yellow followed by a straight red counts as
one yellow.

Red cards are different and must NOT be computed: a red carries immediate
suspension until STDK decides, with no fixed ban.

Seed data — per player, per group, per season:

    "discipline": { "yellowsThisSeason": 3, "asOf": "2026-08-24" }

The `asOf` date is not optional. A count without a date cannot be reasoned about.

Domain: compute suspension from an even yellow total, using the counting rules
above. Test the awkward cases specifically — two yellows in one match adding zero,
and a yellow-then-straight-red adding one.

HARD CONSTRAINT, and it is the reason to keep this weak:

> The app must NEVER claim a player is eligible. It may warn that one might not be.
> Absence of a warning must not read as clearance.

Fielding an ineligible player means a TECHNICAL FORFEIT under §2.6 — the result is
voided on different terms from an ordinary one. If the app showed "clear" and the
player was banned, the app caused that. So: an advisory badge carrying its `asOf`
date, never a block, never a green tick, never the word "eligible".

### B6 · The match clock runs continuously

Analysis §2.6: "2 × 30 minutes gross time... The clock runs continuously; the
referee may add time."

golblok pauses for injuries and breaks. PSMF has NO such stop. Do not carry that
behaviour across — it is exactly the kind of thing copied from a familiar codebase
without noticing.

The only timer with a stop/start lifecycle is the power-play: 10 minutes after a
dismissal, NOT shortened by a goal, and unaffected by further dismissals. It runs
alongside a match clock that never pauses. Model it now even though the console is
a later session, since it belongs with the domain rules.

### B7 · Also rename and add

  - givenName → firstName. Note the ZoU displays "Příjmení a jméno", surname
    first — that is display order, not field naming, and does not change.
  - Add "periods": 2 alongside halfLengthMinutes at group level. 2 × 30 is
    universal in Hanspaulská liga as far as we know, but veteran and futsal
    competitions may differ, and both numbers should be data rather than constants.

### GATE B

  - schema, DTOs, parser, tests and 6k.json all consistent
  - venues.json exists and is referenced by fixtures
  - the add-a-group test still passes
  - ShippedSeedDataTest extended to catch a missing venue and a blank kit label
  - UUID stability rule documented and tested
  - suspension arithmetic tested including both awkward cases
  - power-play lifecycle modelled and tested
  - :shared:allTests green, detekt green, everything committed

## Do not

- Build any of the six screens. Next session.
- Add a backend or any network call.
- Regenerate the placeholder player names — they are throwaway, but keeping them
  stable makes the diff readable.
```
