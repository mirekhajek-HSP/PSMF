# Prompt — Android scaffold and domain foundation

**Where:** Windows host to begin with (it sets up WSL itself) · **Model:** Sonnet,
escalate to Opus if the KMP build fights back

Self-contained. The session copies its own files and starts the environment.
Two phases with a gate between them — it may take more than one session, and that
is fine.

---

```
Set up a new Kotlin Multiplatform project and build its domain foundation. Two
phases, and STOP at the gate between them if anything is shaky. Walk me through it,
verify each step before moving on, don't batch.

## The project

PSMF Match Report — a referee-facing Android + iOS app replacing the handwritten
Zápis o utkání (ZoU) for Hanspaulská liga, Prague. Kotlin Multiplatform + Compose
Multiplatform. Green field.

Package / applicationId: cz.hspinovace.psmf
  Permanent once published and NOT finally settled — flag it before any store
  upload, don't silently lock it in.

## PHASE 1 — environment and scaffold

### 1.1 Stage the documents

Run from the WSL host shell (NOT inside a container — containers here cannot see
/mnt/c):

    mkdir -p ~/dev/psmf-app/docs
    cd ~/dev/psmf-app
    cp /mnt/c/MS/Projects/PSMFApp/repo-files/CLAUDE.md              ./
    cp /mnt/c/MS/Projects/PSMFApp/repo-files/TECH_STACK.md          docs/
    cp /mnt/c/MS/Projects/PSMFApp/repo-files/LEAGUE_APP_ANALYSIS.md docs/
    cp /mnt/c/MS/Projects/PSMFApp/DEMO_SCOPE.md                     docs/

READ ALL FOUR before writing anything.
  CLAUDE.md              agent rules, architecture, domain gotchas
  docs/TECH_STACK.md     the stack and its constraints
  docs/DEMO_SCOPE.md     the six screens and what is in or out
  docs/LEAGUE_APP_ANALYSIS.md   728 lines. The authority on the domain. §2.5 is the
                         field-by-field ZoU spec, §6 is the entity sketch. Do not
                         infer requirements — look them up.

### 1.2 Container environment

There is a working sandbox for the sibling golblok project at ~/dev/golblok-app.
Copy and adapt rather than inventing one:

    cp ~/dev/golblok-app/Dockerfile   ~/dev/psmf-app/
    cp ~/dev/golblok-app/compose.yaml ~/dev/psmf-app/
    mkdir -p ~/dev/psmf-app/docker
    cp ~/dev/golblok-app/docker/local.properties ~/dev/psmf-app/docker/

Then adapt for this project:
  - rename the image and service so the two projects never collide
  - keep the named volume for the Gradle cache, with a DIFFERENT volume name
  - keep the docker/local.properties overlay trick — it bind-mounts a file with no
    sdk.dir so AGP falls through to ANDROID_HOME inside the container. NOTE: docker
    creates a missing bind-mount target as root, so this file must exist and be
    tracked before compose runs
  - add a READ-ONLY mount of the golblok repo at /golblok-ref (see 1.3)
  - keep the engine canary comment: `docker version` must report Engine 29.7.2, not
    Docker Desktop's 28.3.2, and Ubuntu must never become the WSL default distro

Build the image and verify you get a shell.

### 1.3 golblok as reference — READ ONLY

/golblok-ref is the live Android app this project is inspired by. Mount it
read-only and treat it that way.

USE IT FOR: interaction design of the live match console, the tabbed player list,
undo, crash recovery, the match log sheet, left-handed mirroring. That interaction
model is the genuinely reusable asset.

DO NOT COPY CODE FROM IT. Different stack and several of its patterns are actively
wrong here — Hilt is Android-only, manual org.json instead of kotlinx.serialization,
AndroidViewModel, a foreground service running the clock. CLAUDE.md has the full
table. If you find yourself pasting, stop.

### 1.4 Repository

git init, local only, no remote yet.

.gitattributes FIRST, before any other file:
    * text=auto
    *.kt text eol=lf
    gradlew text eol=lf
    *.bat text eol=crlf
and ensure gradlew lands as 100755 in the index. golblok once shipped a repo that
did not build from a clean clone; do not repeat it.

Commit early and often.

### 1.5 Project structure

    shared/       domain + data. commonMain / androidMain / iosMain.
                  NO UI, no Compose imports, no platform types in commonMain.
    composeApp/   Compose Multiplatform UI + ViewModels.
    iosApp/       Xcode wrapper. Generate it, do NOT try to build it — iOS builds
                  only on macOS and that is a separate session on a separate machine.
    docs/

### 1.6 Wire up

Version catalog (gradle/libs.versions.toml) for EVERY dependency from the first
commit. No hardcoded coordinates in build files, ever.

Per TECH_STACK.md §2: Koin (NOT Hilt — Hilt is Android-only and cannot compile into
shared code), kotlinx.serialization, Ktor client, SQLDelight, androidx.lifecycle
multiplatform ViewModels, Compose Multiplatform.

detekt + ktlint running and green with an EMPTY baseline. Green field is the one
moment this is free.

### 1.7 Localisation from the first screen

Three languages: Czech (default), English, Ukrainian. No hardcoded user-facing
strings anywhere.

Two rules that are easy to get wrong and expensive to retrofit:
  - Ukrainian needs Cyrillic glyphs — verify the font actually renders them.
  - The UI language and the REPORT language are independent. The generated ZoU is
    ALWAYS Czech regardless of app language. Export field labels are fixed Czech
    strings, NOT localised resources. Keep them in a separate, non-localised place
    so this cannot drift.

### 1.8 Agent tooling — skills, hooks, permissions

Do this BEFORE Phase 2, not after. Phase 2 is real Kotlin and KMP work and the
skills exist to improve exactly that.

SKILLS — project-scoped in .claude/skills/ so they are versioned and travel with
the repo. Deliberately a different set from golblok's; do not share one.

    git clone https://github.com/chrisbanes/skills /tmp/cb-skills
    git clone https://github.com/mmiani/kotlin-kmp-claude-agent-skills /tmp/kmp-skills

Copy the relevant skill directories into .claude/skills/ and commit them. From
chrisbanes the ones that matter here are the Compose state/effects, Compose
performance, Compose component design, Compose UI testing and Kotlin
concurrency/flow skills, plus the router skill if present. From the KMP repo take
what covers multiplatform structure and expect/actual. Read what you are copying
and tell me the final list — do not copy the whole repo blindly.

HOOKS — in .claude/settings.json, committed. Write them, then TEST EACH ONE
actually fires. A hook that silently does nothing is worse than no hook.

  1. PostToolUse on Edit|Write matching *.kt — run ktlint format on the edited
     file. ktlint is configured in 1.6 so this works immediately.

  2. PostToolUse guard on shared/src/commonTest/** — REJECT the write if the
     content contains `org.junit` or `io.mockk`. Shared tests use kotlin.test, and
     MockK is JVM-only and cannot work in common code. This is the KMP-adapted
     version of golblok's JUnit 4 guard and it catches a mistake that otherwise
     produces confusing compile errors much later.

  Not yet: a Stop hook running the test suite. There is no suite worth running
  until Phase 2. See 2.4.

PERMISSIONS — a committed .claude/settings.json allowlist for the Gradle commands
this project actually uses, so I am not prompted on every build. Keep it to a
handful of broad entries. golblok accumulated nine near-identical entries one
prompt at a time; do not recreate that.

Add .claude/settings.local.json to .gitignore — that one is per-machine.

### GATE 1 — stop here and report

  - ./gradlew build passes
  - ./gradlew :composeApp:assembleDebug produces an APK
  - ./gradlew detekt is green
  - the app launches on an Android device or emulator and shows something
  - skills installed and listed; both hooks verified as actually firing
  - clean and warm build times recorded
  - everything committed

Tell me the times, which skills you installed, and anything in TECH_STACK.md that
turned out wrong in practice. Do not start Phase 2 without checking in.

## PHASE 2 — domain model and seed data

No screens yet. This is the foundation every screen needs, and it validates the
architecture before any UI exists.

### 2.1 Domain model

Build it from the entity sketch in docs/LEAGUE_APP_ANALYSIS.md §6 and the field
spec in §2.5. Scope it to what the six demo screens need — Season, Group, Team,
Player, Fixture, Match, Lineup, Appearance, MatchEvent, Confirmation, Result.

These are the ones that get modelled wrongly by default. Every one needs a test
that would fail if the rule were broken:

  - MINUTE IS NOT AN INT. `30´+` (half-time) and `60´+` (after the whistle, before
    signing) are valid values. Model it as a type.
  - A RED CARD records straight vs. second yellow (`2. ŽK`) — suspension arithmetic
    depends on the distinction.
  - EVERY CARD carries a mandatory free-text reason.
  - A GOAL MAY HAVE NO SCORER. The worked example in §2.5 contains one.
  - PLAYER IDENTIFIER is ONE field plus a discriminator:
    identifier: String + identifierType ∈ {RP, DATE_OF_BIRTH, BIRTH_NUMBER}
    because the ZoU has a single column holding either. Names are LATIN only.
  - JERSEY NUMBER belongs to the Appearance, not the Player.
  - "NO CARDS" is an explicit affirmation, not an empty list — the paper form
    requires the boxes to be struck through.
  - THE REFEREE IS THE ONLY RECORDER. Never model two parties recording
    independently and reconciling.
  - Assessment block: NH (best player by number), Čd (waiting time), Č (shirts
    numbered y/n), B (uniform kit y/n), plus the mandatory commentary.

### 2.2 Seed data — DATA, NOT CODE

Hard requirement: adding a league group must be "drop in a file, add one line to an
index, rebuild". No Kotlin changes, ever.

    composeApp/src/commonMain/composeResources/files/leagues/
      index.json      id, display name, season, filename per group
      6k.json         6. liga K — the demo group

Each group file carries:
  - teams: name, kit colour, and the match duration for the group (2 × 30 today,
    but read it from the file so it can vary without a code change)
  - players: name, surname, identifier + identifierType, default jersey number
  - fixtures: round, date, time, venue code, home team, away team

Populate 6k.json with plausible placeholder data for now — I will replace it with
real data from psmf.cz later. Structure matters more than contents.

Load it with kotlinx.serialization. Write a test proving that adding a second group
file plus an index line makes it appear, with no code change. That test is the
requirement.

### 2.3 Persistence

A match in progress must survive the app being killed — losing a match record is
the failure that ends a pilot. SQLDelight, per TECH_STACK.md. Cover it with a test.

### 2.4 Stop hook

Now that a real test suite exists, add a Stop hook running ./gradlew
:shared:jvmTest — the fast target, not allTests. Report the runtime; if it climbs
past roughly a minute, gate it to sessions where .kt files changed rather than
running it every time.

Verify it actually fires.

### GATE 2 — report

  - ./gradlew :shared:allTests passes
  - domain rules above each covered by a test that would fail if violated
  - seed loading works and the add-a-group test passes
  - in-progress match survives a kill
  - Stop hook running and its runtime measured
  - detekt still green, everything committed

## Do not

- Build any of the six screens. That is the next session.
- Add a backend, remote database or auth. None is decided — TECH_STACK.md §5. If a
  task seems to need one, say so rather than choosing one.
- Copy code from /golblok-ref.
- Attempt an iOS build.
- Invent domain rules. They are in the analysis; look them up and cite the section.
```
