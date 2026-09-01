# The demo screens

**Session date:** 2026-08-31
**Repository:** `~/dev/psmf-app` (WSL Ubuntu)
**Commits:** 5 — `bd1348b` … `eb772a4`
**Outcome:** all five gates met, tree clean, one match goes from the fixture list to three exported files

---

## 1 · Status at a glance

| | Before | After |
|---|---|---|
| Screens | 0 | **8** |
| Shared tests (JVM) | 155 | **315** |
| Shared tests (Android host) | 141 | **294** |
| composeApp UI tests | 0 | **82** |
| Kotlin in `shared/commonMain` | ~2,900 | **5,650** |
| Kotlin in `composeApp/commonMain` | ~150 | **5,084** |
| detekt baseline | empty | **still empty** |
| Runs on a device | never | **yes, emulator only** |
| Produces a ZoU | no | **TXT, CSV and JSON** |

91 files changed, +13,735 / −97 across the five commits.

The demo is showable. A referee can open the app, pick a fixture, fill in the
header, mark absentees, run a match with a clock that never stops, assess both
teams, collect three confirmations, and hand PSMF three files by email — none of
which existed at the start of the session.

---

## 2 · Phase 0 — the two corrections before any UI

Both were flagged in the previous report and both had to land before a screen
existed, because retrofitting either would have meant rewriting screens.

### 2.1 The kit label is snapshotted, not referenced

`Lineup` stored *which* kit was worn as a reference. Renaming a team's kit would
therefore have changed what an old report says under `Barva dresů` — the exact
opposite of what `reportedIdentification` already does, and both fields print on
the same form.

The lineup now stores the label verbatim alongside the reference. The reference
survives because the UI still needs to know which kit is selected; the report
never asks for it and reads only the snapshot. Two details make the rule
structural rather than merely obeyed:

- `Lineup.wearing(side, teamId, appearances, kit: Kit)` takes one whole `Kit`, so
  the id and the label cannot be set inconsistently.
- `kitLabelHasDriftedFrom(team)` makes a later rename *visible* instead of silent.

Proved the way `reportedIdentification` is proved: rename a kit after a match is
stored, and assert the stored report has not moved — in the domain and again
through a database round trip.

### 2.2 The Android test trap had two halves, each silent on its own

The previous report identified one half. There were two.

1. The KMP library plugin builds **no** Android host-test compilation unless
   `withHostTestBuilder { sourceSetTreeName = "test" }` is declared. Without it,
   every Android-target test is skipped rather than failed.
2. The source set is **`androidHostTest`**, not `androidUnitTest`.

A planted `fail()` in `androidUnitTest` still came back green with the builder
correctly declared — the second half caught on the way to fixing the first. Moved
to `androidHostTest`, watched it fail (`failures="1"` in the XML), then replaced
with `AndroidHostTestCanaryTest`, which carries the recipe for re-proving the trap
in its own comments.

`CLAUDE.md`, `docs/BUILD_MATRIX.md`, `docs/TECH_STACK.md` **and** the detekt
source list all named the wrong directory. All four corrected. Leaving them would
have kept instructing every future agent to write tests that never run.

One consequence worth knowing: **detekt 1.23.8 predates the source-set name**, so
its default test-code excludes do not match `androidHostTest` and files there are
linted as production code. Fixed by naming the constants rather than overriding
the excludes — an override would have replaced detekt's exclude lists for
`commonTest` too.

### Gate 0 against its criteria

| Criterion | |
|---|---|
| Kit label snapshotted, and the report reads the snapshot | ✅ |
| Proved by renaming a kit after a match exists | ✅ domain and database |
| `withHostTestBuilder` declared | ✅ |
| A planted failing Android test actually fails | ✅ watched failing, then made a canary |
| `:shared:allTests` green, detekt green, committed | ✅ |

---

## 3 · The architecture the eight screens share

Decided once, in Phase 1, and unchanged since.

**Screens are plain functions of immutable state.** Every screen composable takes
a state value and a callback and holds no logic, which means every one of them is
testable without Koin, without a database and without a device. That is what the
82 UI tests run against.

**State lives in ViewModels as `StateFlow`.** Use cases live in `shared` and are
the only things that touch repositories.

**Navigation is hand-rolled** — a `Destination` sealed interface and an
`AppNavigator` back stack held as a Koin `single`. No navigation library. The demo
is a wizard with eight stops; the version matrix is worth more than the
convenience.

**`composeApp` gained a `jvm()` target.** Not to ship a desktop app: its targets
were Android and iOS, so a Compose UI test could only have run on a device or a
Mac, neither of which the build container can reach. `runComposeUiTest` now runs
in the sandbox on every commit.

**The action row is its own composable, taking the mirror as a parameter.**
Left-handed mode is deferred, not cancelled, and retrofitting it later would have
been surgery.

---

## 4 · Screens 1 and 2 — the fixture list and the match header

Fixtures is a flat list from seed data, grouped by round and ordered by kickoff.
Tapping a row starts a report *or* picks up the one already under way — one
gesture for both, because a referee whose phone died mid-match taps the row they
tapped the first time. The report is created and saved **before** the first field
is filled in, which is what makes every later screen survivable: they all write
through to a row that already exists.

Match header is ZoU page 1. Pitch, date, time and group come from the fixture and
are read-only. The referee supplies both officials, the R flag per official, and
the delegating team — the field that decides who gets fined, given its own block
and its own explanation.

This was also the first time anything ran on a device, which was the point of the
phase: the Compose-resource seed path had only ever been tested against an
in-memory map.

### Gate 1 against its criteria

| Criterion | |
|---|---|
| Both screens run on a device | ⚠️ emulator, API 36 — no physical device reachable |
| Seed data loads from Compose resources **on device** | ✅ the path that had never run |
| Czech, English and Ukrainian all render | ✅ |
| Cyrillic glyphs render | ✅ |
| A smoke test per screen | ✅ |
| Committed | ✅ `97cdc97` |

---

## 5 · Screen 3 — the lineup

The screen carrying the most decisions.

**Absentees, not attendees.** The squad is already known, so the referee marks who
did *not* turn up — two taps for two absentees instead of writing ten names. An
absent row is struck through and loses its jersey field; the count above the list
reads `Přítomno 10 z 12`. The tap target is the name, not a checkbox: twelve
ticked boxes say "check each of these", which is the job the inversion exists to
avoid.

**Jersey numbers default from seed and are corrected by exception.** Two players
sharing one is shown rather than crashed — goals are attributed by number, so the
lineup type refuses to hold a duplicate and nothing is written through until it is
fixed.

**Identification is displayed and never editable.** The RP number is shown because
it is what goes on the report, and there is nowhere on the screen to type one. A
"no card" control appears only where saying so would change anything — in the
shipped data, nobody. Date of birth is the documented fallback.

**Adding a player takes surname, first name and date of birth, and nothing else.**
The request type, the use case and `Player.addedAtThePitch` all have *no
parameter* for an RP number, and the new `added_player_record` table has no
`rp_number` column. The rule is structural, not a validation. Dates parse as
`18.5.1992` or `18051992` and are echoed back in full, because a typo here becomes
six digits in the `Číslo RP` column.

**The suspension badge warns and never clears.** Even yellow totals get
`2 ŽK k 5. 10. 2026 — může mít stop`, carrying the date the count was true. Odd
totals get nothing, and a standing note says the absence of a warning does not
mean everything is in order. There is no `isEligible`, no green tick, and no
problem a card count can produce.

**Captain confirmation is a tap**, picking who is confirming from the players
actually present, with a deputy flag. The attestation is quoted from the form: it
is the captain's claim, not the app's.

Pitch-added players get their own table, scoped to the match. They exist in no
league file, so an appearance pointing at one would otherwise resolve to nobody on
reopening.

### Gate 2 against its criteria

| Criterion | |
|---|---|
| Absent marking, jersey per appearance, kit snapshot, RP read-only | ✅ on device |
| Adding a player works | ✅ |
| A pitch-added player cannot acquire an RP number | ✅ no parameter, no column |
| Suspension badge advisory only, carries `asOf`, never says "eligible" | ✅ |
| Captain confirms per team, a tap not a signature | ✅ |
| UI tests for absent-marking and add-player | ✅ 17 tests |
| Committed | ✅ `cf34e91` |

---

## 6 · Screen 4 — the live console

Page 2 of the ZoU, recorded as it happens.

**THE CLOCK NEVER PAUSES.** There is no pause, stop, resume or adjust control on
the screen or behind it, and a test asserts their absence — golblok pauses, and
that is the habit most likely to come across. The whole clock is one stored
instant: elapsed time is *now minus kickoff*, so it cannot drift, is not killed
with the process, and needs no background timer, which iOS cannot run anyway. A
one-second loop in the route decides how often the screen redraws and nothing
else.

**Goals are one tap on a player row**, because that is the moment attention is
scarcest, and a separate one-tap button records a goal with no scorer — the worked
example in the analysis has one, and demanding a scorer would make the app unable
to record a match the paper handles. The running score is recomputed over the
whole list on every change, so undoing a goal leaves a correct score rather than a
stale one.

**Cards go through a form**, because the form is what the block requires: time,
name, reason, and for a red whether it was straight or a second yellow. The reason
is mandatory and the sheet quotes the form's own warning about vague ones. The
minute pre-fills from the clock and offers `30´+` and `60´+`, which are ordinary
values here and which no integer holds. A card can be shown to somebody with no
jersey number, as the worked example shows one to a deputy captain. A player
already booked in this match is warned *before* the second yellow, not after.

**A dismissal starts a ten-minute power play** for that side, counting down beside
the score. Not shortened by a goal, and a second dismissal starts a second
independent period — which is why a power play stores only the instant it began.
Sent-off players keep their row and lose their buttons: hiding them would lose the
reason they are unavailable.

**Undo takes back the last event** of the merged timeline, and a dismissal takes
its power play with it. Undo, not editing — amending a finished report is screen 9
and is out of the demo.

### Gate 3 against its criteria

| Criterion | |
|---|---|
| A full match end to end on a device | ✅ emulator |
| Kill the app mid-match and resume with everything intact | ✅ force-stop, one tap back in, clock and power play correct to the second |
| UI tests for a goal, a card, and a dismissal starting a power play | ✅ 16 tests |
| Committed | ✅ `6a6083a` |

---

## 7 · Screens 5–8 — assessment, recap, export and settings

The end of the demo, and the part actually being sold. Analysis §1 is blunt about
it: the value to PSMF is not a better referee experience, it is the elimination of
a week of transcription. The output file demonstrates that, not the app, so most
of the effort here sits behind the export.

### One report value, three renderings

`BuildZouReport` resolves everything — names, jersey numbers, kit labels, the
winner, the word for "no cards" — and hands one `ZouReport` to the text, CSV and
JSON formatters. They cannot disagree with each other. The **recap screen renders
that same value**, which makes analysis §5.5 — *whatever is not on this screen is
not being checked* — true by construction rather than by diligence.

### The report is always Czech

Every label in it is a fixed string in `ZouLabels` and `ZouWords`, never a
localised resource. Put them in `strings.xml` and the first referee to set their
phone to English produces an English ZoU that PSMF has to retype. The screen
*around* the document translates, and says so in the language being read.

Two details decide whether the spreadsheet opens at all in a Czech Excel:

- **Semicolons, not commas.** A comma-separated file opens as one column per row.
- **A UTF-8 byte-order mark.** Without it Excel reads CP1250 and every `ě`, `š`
  and `ř` arrives as mojibake.

Both are tested, and both were checked in the bytes of a file pulled off the
device — which is how the second one turned out to be broken. See §8.

### The assessment

`Č` and `B` start unanswered — neither `Ano` nor `Ne` selected — under a note
saying an empty answer is not a yes. Both feed straight into fines, so a default
would quietly waive one and nobody would ever see it happen. Waiting time defaults
to zero, because zero is the normal case and is different from unassessed. The
commentary is mandatory and stays editable until export, pending **A6**.

### The recap

Asks for the half-time score and pre-fills only the final one. The clock never
stops, so added time makes minute 31 as likely to be first half as second — only
the referee knows where the break fell. A full-time score below the half-time one
is refused rather than exported as a contradiction.

**An empty card list is not "no cards".** The paper form has those boxes struck
through, which makes "none" an affirmation somebody made, so the referee makes it.
Without it a clean match could not be sent at all, because the console only ever
adds cards.

One captain per team confirms, plus the referee, each with a deputy flag.
Re-confirming moves the timestamp rather than adding a second signature: the
captain confirming the lineup before kickoff and the report at the end is one
party doing one thing twice.

### The export

Refuses when something mandatory is missing and **names each thing on its own
line** rather than counting them. The fine for an incomplete report lands on the
delegating team, so this is the one place in the demo where refusing is worth
doing. The send control is *absent*, not disabled — verified in the view
hierarchy, not just visually.

Sending opens a mail draft with the three files attached and does **not** send.
The referee presses send in their own mail app, which keeps the last word with the
person whose name is on the report and means the app needs no account and no
credential of any kind.

The filename folds Czech diacritics by hand — `java.text.Normalizer` is JVM-only —
giving `zapis_6K_2026-08-31_Kominici_United-Smichov.csv`.

### Settings

Read-only about the rules, settable about the theme. **Language follows the device
rather than offering its own picker.** This diverges from the table in
`DEMO_SCOPE.md` and is the smaller surprise: a referee who has set their phone to
Ukrainian has already answered the question.

### Gate 4 against its criteria

| Criterion | |
|---|---|
| A complete match produces a complete ZoU in all three formats | ✅ all three previewed on device, all three files written, chooser offered them to a mail client as "Sharing 3 files" |
| The export is Czech with the app set to English and to Ukrainian | ✅ interface translated, Cyrillic and all; document unchanged |
| Report-readiness blocks export when something mandatory is missing | ✅ eight named problems, no send control in the hierarchy, no files written |
| Committed | ✅ `eb772a4` |

---

## 8 · Seven defects that only a device found

Each has a regression test now. This is the section that justifies the "on a
device" clause in every gate.

| # | Phase | What the tests could not see |
|---|---|---|
| 1 | 1 | **Both licensed-hire switches read `Placený rozhodčí (R)`** — side by side under two name fields, saying nothing about which official each belonged to. |
| 2 | 1 | **The delegating-team chips offered all twelve teams**, including the two playing, directly under a note saying it is neither of them. Tapping one would have landed a fine on the wrong club. |
| 3 | 2 | **A player added after absences were marked came back marked absent.** Absence is derived — "not in the saved lineup" — which cannot distinguish somebody who did not turn up from somebody added later. Every unit test had added a player to a match with no lineup saved yet: the one case where the ambiguity cannot arise. |
| 4 | 3 | **The away team's console was empty.** The lineup screen wrote through on every edit, and a team with nobody absent and every number already right is never edited — so its block was never written at all. |
| 5 | 3 | **Resuming a match in progress landed on the header screen.** All the data survived, and the referee was still put two screens away from it and made to press Continue twice. |
| 6 | 4 | **The attachments were deleted before the mail app could read them.** Written to `cacheDir`; on an emulator with a full disk, `DeviceStorageMonitorService` emptied it seconds after the chooser opened. A report the referee believes they have sent, arriving with nothing on it. |
| 7 | 4 | **The CSV shipped without its byte-order mark** — and the test went green throughout. See below; it is the most instructive of the seven. |

### Defect 7 in full, because the shape of it will recur

The constant held a *literal* U+FEFF character. Somewhere between being written
and reaching the repository the invisible character was dropped, leaving
`const val BYTE_ORDER_MARK: String = ""`.

The test could not catch it. It asserted:

```kotlin
assertTrue(csv.startsWith(ZouCsv.BYTE_ORDER_MARK))
```

which is true of **every** string once the constant is empty. The assertion and
the bug cancelled out, and the suite stayed green.

It surfaced only because the exported file was pulled off the device and read as
bytes: it began `5a c3 81` — `ZÁPIS` — with no BOM. Two changes:

- The constant is now written as the escape `"\uFEFF"`, which no editor, formatter
  or copy can silently eat.
- The test asserts the **code point**, not the constant:
  `assertEquals('\uFEFF', csv.first())`.

Two lessons, both cheap to reuse: **never put an invisible character in source**,
and **never assert a value against the constant that produces it** — that test
can only fail when the constant and the output disagree, which is not the failure
mode that matters.

---

## 9 · Findings worth not rediscovering

**`androidHostTest`, not `androidUnitTest`.** Wrong directory, silent skip. Now
guarded by a permanent canary test.

**detekt 1.23.8 does not know `androidHostTest`** and lints it as production code.
Name your constants there; do not override the excludes.

**`BackHandler` is not in `compose.ui`.** Separate artifact,
`org.jetbrains.compose.ui:ui-backhandler`, versioned with Compose Multiplatform,
and simultaneously experimental *and* deprecated — the replacement
(`NavigationEventHandler`) needs yet another dependency. Deliberately accepted;
the one deprecation warning in the build is this.

**`toSortedMap` is `java.util`** and therefore unavailable in common code. Sort
entries explicitly.

**`runComposeUiTest` v2 has no `onAllNodesWithText`.** Use
`onAllNodes(hasText(…)).onFirst()`. Off-screen content needs `performScrollTo()`,
and a screen with two scrollables needs
`onNode(hasScrollAction()).performScrollToNode(hasText(…))`.

**UI tests catch ambiguous labels for free.** Twice a test failed on an ambiguous
node and the real defect was the interface: `Rozhodčí` was both a section title
and a field label, and `Kapitán potvrzuje sestavu` was both a section title and a
button. Both renamed. A test that cannot tell two things apart is often telling
you the referee cannot either.

**There are no SQLDelight migrations.** Phase 2 and Phase 4 both added columns.
Installing over an older build needs a clean install, and will until migrations
exist.

**An emulator with a full `/data` is not slow, it is broken.** At 94% used,
`adb shell input` took *sixty seconds* per call and the framework wiped app caches
continuously. `df -h /data` is the first thing to check when device driving stops
working; a cold restart (`-no-snapshot-load`) fixed it, and app data survived.

**Per-app locale beats a framework restart.**
`adb shell cmd locale set-app-locales <pkg> --locales uk` switches one app's
language in seconds with no reboot — the practical way to check three languages on
a device.

---

## 10 · Test suite

315 in `shared` on the JVM, 294 of those also on the Android target, 82 UI tests
in `composeApp`. Zero failures. detekt and ktlint green; the baseline is still
empty, with zero suppressions.

The Android host suite is 21 short of the JVM one: `ShippedSeedDataTest`,
`MatchPersistenceTest` and `DatabaseSmokeTest` are JVM-only by design — they touch
the filesystem and the JDBC driver.

### The screens

| Class | Tests | Guards |
|---|---|---|
| `LineupScreenTest` | 17 | absent-marking, add-player, the advisory badge |
| `ConsoleScreenTest` | 16 | no pause control exists, goals, cards, power play |
| `MatchHeaderScreenTest` | 10 | the delegating team, both R flags |
| `ExportScreenTest` | 9 | **the report stays Czech in en and uk**, readiness, three formats |
| `ComposeResourceSeedTest` | 7 | seed data loads from resources |
| `FixturesScreenTest` | 6 | rounds, ordering, status chips |
| `AppNavigatorTest` | 5 | the hand-rolled back stack |
| `RecapScreenTest` | 5 | the document itself, the no-cards affirmation, confirmations |
| `AssessmentScreenTest` | 4 | `Č` and `B` start unanswered |
| `FormatsTest` | 3 | dates, times, scores |

### The export, in `shared`

| Class | Tests | Guards |
|---|---|---|
| `BuildZouReportTest` | 10 | one value, resolved once, so the three renderings agree |
| `ZouCsvTest` | 5 | **the BOM as a code point**, semicolons, CRLF |
| `ExportZouTest` | 4 | filenames, diacritic folding, the address |
| `ZouTextTest` | 3 | the paper form's blocks, in order |
| `ZouJsonTest` | 3 | round-trippable, explicit nulls |
| `ZouLanguageTest` | 3 | every label is a fixed string, not a resource |

The remaining 41 classes are the domain and use-case suites, listed in the
previous report and extended here with `LineupEntryTest` (14),
`MatchHeaderEntryTest` (11), `ConsoleEntryTest` (8), `BuildLineupEntryTest` (8),
`DateOfBirthEntryTest` (7), `ListFixturesTest` (7), `LogCardTest` (7) and eighteen
smaller ones.

---

## 11 · Build and test times

Container sandbox, WSL ext4, warm Gradle daemon. Measured on the final commit.
The forced figures each include whatever compilation was not already done when
that task ran, so they are indicative rather than additive — `:shared:allTests`
below is not the sum of the two rows under it.

| Scenario | 2026-08-30 | Now |
|---|---|---|
| Warm build, nothing changed | 1 s | **1 s** |
| Clean recompile, build cache **off** | 27 s | **59 s** |
| Clean recompile, build cache on | — | **2 s** |
| `:shared:allTests` forced | 6 s | **10 s** |
| `:shared:jvmTest` forced | 4 s | **12 s** |
| `:shared:testAndroidHostTest` forced | — | **7 s** |
| `:composeApp:jvmTest` forced | — | **13 s** |
| detekt forced | 1 s | **2 s** |
| ktlintCheck forced | — | **6 s** |
| `:androidApp:assembleDebug` forced | — | **8 s** |

The clean recompile roughly doubled, which is what 13,700 new lines and a third
compiled target look like. Nothing here is close to a problem: the loop that
matters — edit, test, look — is still under fifteen seconds, and the warm build is
unchanged at a second.

`:composeApp:jvmTest` at 13 s is the slowest single task, because each of the 82
tests spins up a Compose composition. Worth watching if the UI suite triples.

---

## 12 · Decisions taken, and by whom

| Decision | Who | Note |
|---|---|---|
| Kit label snapshotted, not referenced | Project owner | Specification error, corrected before any UI |
| One captain per team confirms, plus the referee | Project owner | Settled mid-session; no domain change needed |
| The report is always Czech, whatever the app language | Project owner | Implemented as fixed strings, not resources — the only way it survives |
| The clock never pauses; do not copy golblok | Project owner | Asserted by a test that checks the *absence* of controls |
| Navigation hand-rolled, no library | This session | Eight-stop wizard; the version matrix is worth more |
| `composeApp` gains a `jvm()` target | This session | The only way a Compose UI test runs in the container |
| Report labels live in `ZouLabels` / `ZouWords` | This session | A localised resource would produce an English ZoU |
| One `ZouReport` value feeds all three formatters and the recap | This session | Makes §5.5 structural instead of a promise |
| Half-time score entered, not derived | This session | Follows from the clock rule: minute 31 could be either half |
| CSV is semicolon-separated with a UTF-8 BOM | This session | Both decided by Czech Excel, not by preference |
| Attachments go to `filesDir`, not `cacheDir` | This session | Watched the platform delete them from the cache |
| The send control is absent, not disabled, when incomplete | This session | A disabled button invites hunting for the reason |
| Language follows the device; no in-app picker | This session | **Diverges from the `DEMO_SCOPE.md` table** — flag for review |
| Screen 9 and report versioning stay out | Project owner | Out of the demo, restated |

---

## 13 · Open items and known gaps

**No physical device, at any gate.** Every "on a device" claim in this report
means an Android emulator, API 36, x86_64. The seed-resource path, Cyrillic
rendering, process-kill recovery and the mail intent all behaved — but a physical
phone has still never run this app, and font scaling and one-handed reach in the
cold are exactly what an emulator cannot tell you.

**iOS has never been compiled.** `UnavailableReportSender` is bound there, so the
export is a stub on iOS. The toolchain proof on the Mac is still the only thing
that could force a rethink of the platform decision.

**No SQLDelight migrations.** Two phases added columns. A clean install is
required until this is addressed, which is fine for a demo and not fine after.

**Language is read-only in Settings**, diverging from `DEMO_SCOPE.md`. Worth an
explicit yes or no before the demo is shown.

**One deprecation warning** in the build, `BackHandler`, documented above.

**The three export formats are a stand-in, not an answer.** **A8** — PDF,
spreadsheet, or both — is still unanswered, and the choice of TXT/CSV/JSON was
made to demonstrate the transcription saving, not to match a PSMF requirement
nobody has stated.

**The commentary field is still the hardest unsolved usability problem**, blocked
on **A6**. It is currently a plain multi-line field, mandatory, editable until
export. That is a placeholder for a real design.

**`venues.json` still holds 7 codes, not ~35.** Waiting on real psmf.cz data.

**Two leftover matches sit in the emulator's database** from the verification
runs — one confirmed, one abandoned mid-report. Harmless, but they are not
demo-clean.

**No CI and no remote.** Both still blocked on the repository decision in
`TODO.md`.

---

## 14 · Commits

| | | |
|---|---|---|
| `bd1348b` | Snapshot the kit label, and make Android tests actually run | 12 files, +389 |
| `97cdc97` | Add the fixture list and the match header | 33 files, +3,165 |
| `cf34e91` | Add the lineup screen | 23 files, +2,902 |
| `6a6083a` | Add the live console | 21 files, +2,853 |
| `eb772a4` | Add the assessment, recap, export and settings screens | 42 files, +4,516 |

One commit per gate, each verified on a device before the next began. That is what
made the seven device-only defects attributable to the phase that introduced them
rather than to the pile.

Two new dependencies, both catalogued with their reasons:
`org.jetbrains.compose.ui:ui-backhandler` and `androidx.core:core-ktx` (for
`FileProvider`).

---

## 15 · Next session

The demo exists. What it needs next, in order of what unblocks what:

1. **Show it to a referee.** The field research in `TODO.md` was cheap and
   high-value before a line of code existed; it is now cheap, high-value *and*
   answerable against something real. It settles A13–A19 and tests the two designs
   this session had to guess at — lineup capture without handing over the phone,
   and where the commentary actually gets written.
2. **Run it on a physical phone.** One afternoon. Font scale, one-handed reach,
   glove taps, sunlight. Every one of the seven defects above was found by running
   the thing, and an emulator is not the thing.
3. **Prove the iOS toolchain on the Mac.** Unchanged in priority, still the only
   open item that could invalidate the platform decision.
4. **Decide the language picker** and, if it stays device-driven, correct
   `DEMO_SCOPE.md` rather than leaving the two documents disagreeing.
5. **SQLDelight migrations**, before anybody installs a build over another one.

Nothing in the code blocks any of these.
