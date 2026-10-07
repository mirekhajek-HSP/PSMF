# Prompt — Red cards and the clock, real league data, and build 0.2.0

**Where:** the WSL container · **Model:** Opus
**Follows:** the merge of `ios/export` into `main` (`152f637`), which is
built and green: 388 shared JVM, 349 shared Android-host, 182 composeApp UI
tests.

---

## What this is for

Three things before the next build goes to testers:

1. **Fix what a code review found in the match logic**, mostly red cards. A
   tester reported problems with red cards without details, and the review
   found enough to explain that several times over.
2. **Replace the invented league data with the real league 6** from
   psmf.cz, all twelve groups. That reverses a decision, and
   `docs/DECISIONS.md` (2026-10-07) says why and on what terms.
3. **Ship it as 0.2.0 (2)**, with the TEST icon on Android too.

All decided in `docs/DECISIONS.md`, the four entries dated 2026-10-07.
**Read them first.** They are the spec. This prompt is the order of work.

---

```
Fix the card and clock defects, let the league carry players with no
identification, import league 6 from psmf.cz, and build 0.2.0 (2). Four
parts. STOP AND REPORT after each; one commit per gate, on main.

## Read first

  CLAUDE.md
  docs/DECISIONS.md          the four 2026-10-07 entries ARE the spec;
                             also 2026-08-31 "A half-time exists"
  composeApp/src/commonMain/composeResources/files/leagues/README.md
                             the id rule; read it before writing an importer
  shared/src/commonMain/sqldelight/README.md
                             before any schema change
  prompts/README.md          "name the outcome, not the API"

## Before anything: the baseline

./gradlew build :shared:allTests detekt must be green at HEAD before you
change anything. Record the three test counts. If it is not green, STOP.

## Known, and not for this session

  - BackHandler and TabRow deprecation warnings
  - materialIconsExtended costing 31 MB
  - Minutes count from 0 (a goal at 0:40 is 0´), and an event at 62´ before
    the whistle is 62´, not 60´+. Both are open questions for a referee.
    Leave them.
  - The Version.xcconfig proposal from the TestFlight report. Bump numbers
    by hand this time.

## PART 1 — cards and the clock

Five defects, all in DECISIONS 2026-10-07 "The card and clock review". For
EACH: first a test that fails on HEAD and says in its name what the
referee would have seen, then the fix. Report the failing run and the passing
run.

1. A SECOND YELLOW SENDS THE PLAYER OFF, AS ONE ACTION.
   Outcome: the referee cards a booked player once. The app records the
   second yellow AND a red of kind 2. ŽK at the same minute, the row shows
   the player off, the power play starts, and one Undo takes both back.
   It must be impossible to end up with a player holding two yellows and
   no dismissal. The season count for that match is 0 (yellowsAccumulatedBy
   already says so for yellow+yellow+red; keep that true).
   2. ŽK as a red option is offered only for a player who already has a
   yellow in this match; for anyone else a red is straight.
   Cards to a NamedPerson: no automatic second yellow. Leave them as they are.
   YOUR CALL, REPORTED BACK: how the card sheet tells the referee, before
   they save, that this yellow is a dismissal. Today it is a red hint line
   that changes nothing.

2. THE REPORT SAYS 2. ŽK FROM THE STORED KIND, NEVER FROM FREE TEXT.
   Outcome: a second-yellow red reads 2. ŽK in TXT, CSV and JSON whatever
   the referee typed, with their reason kept beside it if they gave one. A
   straight red never reads 2. ŽK because of text left over from switching.
   JSON gets the kind as a field. Still always Czech, still one encoder
   (ZouDocument.bytes()).

3. THE POWER PLAY IS TEN MINUTES OF PLAY.
   Outcome: the interval does not count, so a red at 25´ with a long
   half-time still leaves the side short at the start of the second half. A
   red during the interval starts counting when play resumes. A red after
   the final whistle (60´+, or the match FINISHED) starts no power play. A
   red to a NamedPerson starts none. Nothing is shown counting down after
   the final whistle.
   YOUR CALL, REPORTED BACK: when the referee types a minute earlier than
   the clock (logging late), whether the ten minutes run from that minute.
   If mapping a typed minute back onto played time across a half-time is not
   clean, run from the moment of saving and say so.

4. THE SECOND PERIOD STARTS AT 30´.
   Outcome: period k shows (k−1) × halfLengthMinutes at its kickoff,
   whatever added time the previous period had. Consequences you must also
   handle: 30´+ sorts before the second half's 30´ in the timeline, the
   log and the report; the half-time score counts exactly period one's
   goals. MatchClockTest and ConsoleEntryTest assert the OLD behaviour on
   purpose ("continues the same sixty minutes"). Change those assertions,
   and say which ones in the report.

5. UNDO TAKES BACK THE LAST THING RECORDED.
   Outcome: by recording order, not by minute. A card at 20´ then a goal at
   20´: Undo removes the goal. A card typed at 22´ after a goal at 25´:
   Undo removes the card. A second yellow (item 1) undoes as one action.
   This almost certainly needs the recording order persisted, which is a
   schema change: follow the migration README exactly. Record the old
   schema BEFORE editing a .sq file, and say what an existing match gets
   for its old rows.

And the stale text: CLAUDE.md ("The match clock never pauses... There is
no pause, stop, resume or adjust operation"), the header of MatchClock.kt and
the comment on Match.kickoffAt still describe a clock with nothing but a
kickoff. Since half-time that is false. Restate them as: no stoppage during
play, a recorded interval between periods. Keep "no pause/stop/resume
control during play" exactly as strong as it is.

### GATE 1
  - each of the five: the failing test, the fix, the passing run
  - the two judgement calls, answered
  - the migration, if any: version, what old rows get
  - build, allTests, detekt green; counts against the baseline
  - commit

## PART 2 — a league player with no identification

DECISIONS 2026-10-07, "A league player may arrive with no identification".
Every scraped player will have no RP number, no date of birth and no birth
number. Today Player refuses to be built like that, and a lineup with such a
player shows "Chybí údaj do sloupce Číslo RP" with no way to fix it.

Outcome:
  - Player can be a LEAGUE_RECORD with none of the three. ADDED_AT_PITCH
    keeps requiring a date of birth. An RP number is still never typeable.
  - In the lineup, the referee gives such a player a date of birth WITHOUT
    LEAVING THE SCREEN where they mark them present. The YYMMDD rendering
    is the app's job, not the referee's. Reuse DateOfBirthEntry.
  - That date is remembered on the device for that player and offered next
    time, like a jersey-number override. Editable, never required to match.
  - Appearance.reportedIdentification stays non-null. The lineup still
    cannot be confirmed with a present player whose Číslo RP is empty.
  - The Týmy tab's "PSMF čísla RP zatím nedodala" note stays true; check it
    still reads right when no player has a date of birth either.

### GATE 2
  - a test that fails on HEAD: a squad player with no identification
    cannot be fielded at all
  - what the referee sees, step by step
  - schema change, if any, with its migration
  - green; commit

## PART 3 — league 6 from psmf.cz

DECISIONS 2026-10-07, "Scrape psmf.cz after all". Read its conditions; they
are requirements.

### What is on the site (checked 2026-10-07)

  Season     /souteze/2026-hanspaulska-liga-podzim/
  Groups     .../6-a/ to .../6-l/                    12 groups
  Teams      .../6-k/tymy/<slug>/                    ~12 per group
  Kits       .../6-k/dresy/          "Krabice  pistáciovo-černá"
                                     "Améby AFC A  černá, bílá"
                                     comma = a second kit; some have one
  Pitches    /hriste/                code, name, address for every pitch
  robots.txt Disallow: /cms/ only

A team page has: every fixture of the team (date, time, pitch code, home,
away, round, result if played); "Statistiky" — every player who has
appeared, SURNAME FIRST ("Bělohlávek Jan  3  0"); and every played match in
detail — lineups (FIRST NAME FIRST, goalkeeper before the dash), goals and
cards with minutes ("47. Lubomír Makovský"), cards as
<span class="component__table-card is-yellow|is-red">, and the half-time
score. No RP numbers, dates of birth or jersey numbers anywhere.

### What to build

AN IMPORTER, NOT A ONE-OFF. It will run again after more rounds, and that
run must not orphan matches recorded against this one.

  - A JVM-only Gradle module under tools/, depending on :shared so that it
    writes through the app's own seed DTOs and keeps ids with SeedIdentity.
    jsoup for parsing is APPROVED, catalogued in libs.versions.toml, in
    this module only. Never in shared, composeApp or androidApp. The app
    stays without network.
  - Polite: sequential, ≥ 1 s between requests, a user-agent naming the
    project, raw HTML cached in a git-ignored directory so development
    re-runs do not hit the site. Report how many requests a full run makes.
  - Parser tests run against a few saved pages committed as fixtures, never
    against the live site.
  - Output: one file per group (6a.json … 6l.json), index.json, venues.json
    with names and addresses from /hriste/. Group ids 6a…6l, names "6. liga
    A", report codes 6A…, 2 × 30. The README's id rule holds: the importer
    reads existing files and keeps every id whose ref it has seen.
  - Squad = the Statistiky table. Cards and lineups are matched to it by
    name; anything that does not match is REPORTED, not guessed.
  - Discipline: yellows per player from the played matches, with the
    README's rules (two in one match count zero), asOf = the date the data
    was fetched. Reds are not counted. Say in the report how many players
    carry a count and whether the site shows anything for a second yellow.
  - Player refs are not team-scoped (README). Two players with the same
    name in league 6 will collide; choose a rule, write it in the README,
    and list the collisions found.
  - Kits: label verbatim from /dresy/; colours best-effort for the app's
    chips. A team with one kit has one.

### The invented data retires

6k.json is made up (README, "The placeholder data"). Real 6-K replaces it.

  - TESTS MUST NOT DEPEND ON SCRAPED DATA, which changes every run. Any
    test that reads the bundled files for Kominíci, ruzicka-radek and the
    rest moves to a test fixture holding the old placeholder set. Find
    them all; say how many.
  - A match saved against a placeholder team must not crash any screen
    after the upgrade. Neither may a followed team or a jersey override
    that points at an id that no longer exists. Prove the first with a test
    at least. Say what the referee sees for such a match.

### GATE 3
  - groups, teams, players, fixtures, pitches: counts per group. Each group
    should have 66 fixtures if it has 12 teams. Explain any that do not.
  - name-matching failures, ref collisions, kit-parsing oddities: listed
  - the request count and the run time
  - the app loads all twelve groups through the real catalogue (a test)
  - green; commit, including the generated files

## PART 4 — the icon, the version, the build

  - ANDROID ICON: the iOS TEST icon,
    iosApp/iosApp/Assets.xcassets/AppIcon.appiconset/AppIcon-test-1024.png,
    as an adaptive launcher icon. Android masks to a circle or squircle,
    so "ZoU" and the TEST band must survive a circular mask. Render the
    masked result and look at it before you commit. Today there is no
    launcher icon at all.
  - VERSION 0.2.0, build 2, both platforms: versionName / versionCode in
    androidApp/build.gradle.kts, and MARKETING_VERSION /
    CURRENT_PROJECT_VERSION in iosApp.xcodeproj/project.pbxproj (Debug AND
    Release). Change those four values and nothing else in the Xcode
    project. Never DEVELOPMENT_TEAM.
  - ./gradlew :androidApp:assembleDebug. Report its size against 70 MB.

### GATE 4
  - the masked icon, looked at
  - version numbers, both platforms
  - the APK path and size
  - green; commit

## Do not

  - Add network access to the app, or a backend.
  - Add any dependency to shared, composeApp or androidApp.
  - Bump anything in gradle/libs.versions.toml except adding jsoup. Kotlin
    bumps need the Intel Mac in the loop now.
  - Touch iosMain or iosApp beyond the two version numbers. This machine
    cannot build iOS.
  - Regenerate a seed UUID.
  - Add anything that reads as "eligible", or a green tick.
  - Add a pause, stop or resume control during play.
  - Write a second report encoder.
  - Push. The container cannot, by design.

## Report

reports/2026-10-07-cards-clock-and-league-6.md, committed. Open with one
sentence: what will a referee notice is different in 0.2.0? Then each gate
against its criteria, decisions taken and by whom, and findings worth not
rediscovering.

End with a section "On the phone", a numbered checklist for the owner and
the tester on Android, each step with the expected result and what failure
looks like. It must cover: a second yellow (one tap sends off, one undo
restores); 2. ŽK in the saved CSV; a red before half-time still short after
it; a red after the final whistle starting nothing; the second half
starting at 30´; undo of same-minute events; fielding a real league player
by entering a date of birth, and it being offered next time; real teams,
kits and pitches in Zápasy and Týmy; a match from 0.1.0 surviving the
upgrade; the export's new "cancelled" message when backing out of the
folder picker, and send, both from the ios/export merge; the TEST icon on
the home screen.
```
