# Prompt — golblok / GoalTrack maintenance

**Where:** WSL container, golblok repo · **Model:** Sonnet for mechanical items,
Opus for the `Dispatchers.IO` refactor

Separate product, separate track. Nothing here is urgent — it is a live Play Store
app on maintenance. Kept in this folder only because this is where prompts live.

---

```
Maintenance work on golblok (GoalTrack), a native Android app live on Google Play.
This is a separate product from the PSMF app — no shared code, no relationship
beyond both being mine.

## Where things are

Repo: ~/dev/golblok-app in WSL2 Ubuntu (ext4). Branch master, which is the default.
Session:  wsl -d Ubuntu --cd ~/dev/golblok-app -- docker compose run --rm sandbox

Topology, and it matters:
  WSL ext4       agent workspace. 34s builds. CANNOT push — by design.
  Windows NTFS   C:\MS\Projects\GoalTrack — release checkout ONLY, never edited.
                 Signed AAB is built here in Android Studio. Keystore lives here.
  origin         source of truth. I push from the WSL host shell, not the container.

Never run Gradle in Android Studio and the container simultaneously — they share
app/build/.

Stack: Kotlin 2.1.0, Jetpack Compose, Hilt via kapt, Clean Architecture + MVVM,
compileSdk 36 / minSdk 30, JUnit 5 + MockK + Turbine. ~8,800 lines main, 2,300
test, 112 tests passing.

## Work these in order, each as its own commit with the test suite as the gate

FIRST — the only user-facing defect:

1. Repository I/O runs on the calling thread. Dispatchers.IO appears nowhere in
   data/. MatchRepository has `init { loadFromDisk() }` running during Hilt
   construction, and saveMatch / updateMatch / deleteMatch / saveOngoingMatch are
   plain non-suspend functions calling saveToDisk() inline. During a live match
   every goal and card serialises the full log and writes it on the main thread,
   degrading as the match runs longer.
   Make writes suspend + withContext(Dispatchers.IO); move the init load into a
   lazily-started scope. MockTeamRepository has the same shape.

THEN — guardrails, because much of this code is agent-written:

2. detekt + ktlint with a COMMITTED baseline so it starts green. Freezes the 46
   `!!` operators at 46 and fails the build on 47.
3. Drop testImplementation(libs.junit). JUnit 4 is on the test classpath with
   useJUnitPlatform() and no vintage engine, so a test written with org.junit.Test
   compiles and then silently never runs. Removing it makes that a compile error.
4. PostToolUse hook rejecting any write to app/src/test/** containing
   `import org.junit.Test`.
5. Stop hook running ./gradlew :app:testDebugUnitTest — 33s for 112 tests.
6. Install chrisbanes/skills into .claude/skills/, project-scoped and committed.

THEN — the docs, which are actively wrong:

7. docs/TECH_STACK.md claims Cloud Firestore, Firebase Auth and Crashlytics. NONE
   of it exists; persistence is hand-rolled org.json plus DataStore. Rewrite it to
   describe what is actually there, with anything aspirational under an explicit
   "Not implemented" heading.
8. CLAUDE.md has rules but no commands. Add build/test invocations, the module
   layout, and the three-copy topology above — an agent that does not know it
   cannot push will waste a turn finding out.
9. memory/activeContext.md is 0 bytes and nothing writes to it. Delete it or give
   it a job. memory/progress.md is an undated changelog duplicating git — reframe
   as current state / known issues / next up / recent decisions.
10. Delete app/src/test/java/cz/hsp/footballmatch/VmReflectionTest.kt — 13 lines
    that println ViewModel method names and assert nothing.

THEN — build health:

11. Firebase: the google-services plugin and firebase-bom are applied but NO
    Firebase API is called anywhere, and google-services.json is untracked. Decide
    with me: remove it, or commit the file and document it.
12. Move every hardcoded dependency coordinate into libs.versions.toml. Four
    CameraX artifacts each repeat 1.5.3; Hilt appears twice at 2.58.
13. Compose BOM 2024.09.00 → current. Own commit, unknown blast radius, test suite
    as the gate.
14. kapt → KSP. Hilt is the only annotation processor so nothing blocks it.
15. GitHub Actions: assembleDebug + testDebugUnitTest + detekt on push.
16. kotlinOptions{} → compilerOptions{}. applicationVariants.all{} is the old
    Variant API that AGP 9 removes.
17. Delete the stale origin/main — a 2-commit stub from Feb 2026.

LATER — larger, once the above is done:

18. Split ui/MatchScreen.kt (1,721 lines, 19 composables, ~900 of them bottom-sheet
    contents) into ui/match/.
19. Replace 11 collectAsState() with collectAsStateWithLifecycle(). The
    lifecycle-runtime-compose dependency is not even declared.
20. Extract a MatchRepository interface — CLAUDE.md rule 2 requires data sources
    behind interfaces, and this one is concrete, so four ViewModels depend on a
    filesystem-touching class. Same for UserPreferencesRepository.
21. Rename MockTeamRepository — it is the PRODUCTION implementation, with
    two-phase init (nullable Context + init(context)). Constructor-inject
    @ApplicationContext and switch AppModule from @Provides to @Binds.
22. MatchViewModel extends AndroidViewModel and constructs Intents to start
    MatchTimerService. Define a MatchTimer interface in domain/, implement it over
    the service, inject it. This also lets you drop
    testOptions{unitTests.isReturnDefaultValues = true}, which currently silences
    every unstubbed Android call and hides real bugs.
23. Compose UI tests. androidTest/ contains one generated file asserting the
    package name while ui-test-junit4 sits declared and unused. Start with the
    live-match happy path and the left-handed layout toggle.
24. Replace manual org.json across three repositories with kotlinx.serialization.

## Also

I want to pilot TAUT (github.com/yurgeno/taut) on this repo — it compiles a
versioned agent workspace instead of a hand-edited CLAUDE.md. golblok is the
testbed precisely because it is low stakes. Ask me before starting; it is not part
of the list above.

## Constraints

- You cannot push. Commit freely; I push from the WSL host.
- Never touch the keystore or build a signed release. That happens on Windows.
- Play Console: I need to check the specialUse foreground service justification —
  the manifest declares specialUse with subtype match_timer and Play requires a
  written declaration. Remind me; do not attempt it yourself.
```
