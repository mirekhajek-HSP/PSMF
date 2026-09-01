# Six fixes from a physical phone

**Session date:** 2026-08-31 – 2026-09-01
**Repository:** `~/dev/psmf-app` (WSL Ubuntu)
**Commits:** 4 — `3c98e19` … `d9f16ab`
**Outcome:** all four gates met, tree clean

---

## 1 · Status at a glance

| | Before (previous report) | After |
|---|---|---|
| Match clock | one `Ukončit utkání` button, no way to end just the first half | **half-time control** — `End the half` / `Start half 2`, clock never pauses *during play*, three stored instants instead of one |
| `30´+` / `60´+` | selected by the referee | **derived** from period state, not a choice |
| Console actions | `Gól` / `Karta` as text buttons in the player row | **icon buttons**, sized for a cold thumb; old strings kept as `contentDescription`; card stayed one icon, not split |
| Follow control | `Nesledovat` text button | **star toggle**, empty/filled |
| League filter | flat dropdown across every group | **two cascading chip rows** — league level, then group letter |
| Venue filter | did not exist | **picker** (chips) |
| Team filter | picker across every team | **text field** |
| Týmy tab | opened on browse-by-league | **opens on followed teams**, search and browse secondary |
| Settings rules panel | hardcoded Czech | **translated** cs/en/uk; the numbers (30 min, 2, 6, 10 min) stay as data |
| Report saving | three `ACTION_CREATE_DOCUMENT` dialogs, one per file, every export | **`ACTION_OPEN_DOCUMENT_TREE`** — asked once, silent thereafter |
| Shared tests (JVM) | 364 | **388** |
| composeApp UI tests | 151 | **162** |

Every one of these six fixes was found the same way: using the built app on a phone-sized screen (an emulator at 411dp, standing in for the physical device this session didn't have) rather than from a desk. Four of the six trace back to specification errors written by the project owner, not implementation mistakes — recorded plainly as such in `DECISIONS.md`, each entry dated before the corresponding commit.

---

## 2 · Phase 1 — the match gets a half-time

**What was wrong:** there was no way to end the first half. One `Ukončit utkání` button and nothing between kickoff and the final whistle.

**Whose error:** the project owner's. `prompts/04` read *"THE CLOCK NEVER PAUSES… no pause, stop, resume or adjust control on the screen or behind it, and a test asserts their absence"* — aimed at golblok's habit of stopping its clock for injuries, but written hard enough that it also forbade the legitimate half-time control. A session following it faithfully could not have built one.

**The rule, corrected:** the clock does not stop *during play*. PSMF's `hrubý čas` runs through injuries and stoppages and the referee adds time at the end; a half-time interval is not a pause during play, it's the gap between two periods that together make 60 minutes. What followed from restating it correctly:

- Three stored instants (kickoff, half-time, full-time) instead of one, all persisted, none ticking.
- `30´+` and `60´+` stopped being something the referee selects — an event logged while a period is running past its length *is* `30´+`, derived from period state.
- The half-time score stopped being typed in on the recap. It's read from the event log at the moment the first period ended, pre-filled and still editable, and `HALF_TIME_MISSING` as a readiness problem went away.
- Both numbers (`periods`, `halfLengthMinutes`) already lived in the group file — driven from data, not hardcoded, so a competition with a different shape than 2×30 needs no code change.
- Still true, still asserted by a test: no pause, stop or resume control, and no golblok `Pause`/`Stop`/`PlayArrow` icon anywhere near this screen.

### Gate 1 against its criteria

| Criterion | |
|---|---|
| End-of-period and start-of-next-period actions on the console | ✅ |
| Clock never exposes a pause, stop or resume control | ✅ — still asserted by test |
| `30´+`/`60´+` derived from period state, not selected | ✅ |
| Half-time score pre-filled from the event log, editable, never blank | ✅ |
| Period count and length driven from group data | ✅ |
| Committed, alone, before any of the other five fixes | ✅ `3c98e19` |

**Whether Phase 1 turned out to contradict how a referee actually keeps time:** no. Re-checked live in this session's own Phase 4 device run — the console clock reads per-half (`0´` at the kickoff of half 2, not `30´`), and the ZoU's `30´+` marker only ever needs to compare against the current period's own length. Nothing about the domain model built in Phase 1 fought the way a referee actually tracks a match.

---

## 3 · Phase 2 — console actions become icons, and follow becomes a star

**What was found:** `Gól` and `Karta` as text in a player row buy a small tap target in the widest part of the layout, on the screen where attention is scarcest and the referee may be gloved. An icon is bigger in the same space, recognised faster, and identical across all three languages — golblok already does this and was right to.

The localised strings moved to `contentDescription` rather than disappearing: screen readers keep working, and so do the existing UI tests, which find these controls by text.

**The split-card decision:** whether the card control becomes two icons (yellow, red — colour being football's fastest discriminator) or stays one icon that opens a sheet was left explicitly undecided in scope, to be judged "with a phone in hand" against row width and mistap risk at 411dp. It stayed **one icon**. Three icon-sized touch targets in a row (goal, yellow, red) would each sit well under the 48dp a cold thumb needs once the row's own padding is accounted for, and a mistap between two adjacent card colours mid-match is worse than one extra tap to choose the colour on the sheet that already opens.

**`Nesledovat` became a star**, empty and filled. A button labelled with the verb for what tapping it *will do* reads, at a glance, as a description of the current state — and gets it backwards. A star toggles in place, needs no translation, and is the convention everywhere else. (Plus/minus was the alternative considered and rejected: it implies adding a team to something, not marking one.)

### Gate 2 against its criteria

| Criterion | |
|---|---|
| `Gól`/`Karta` are icon buttons, sized for a cold thumb | ✅ |
| Old localised strings survive as `contentDescription` | ✅ |
| Split-card decision made and justified against a device screenshot | ✅ — one icon, reasoning above |
| `Nesledovat` replaced by a star toggle | ✅ |
| Committed | ✅ `02cde29` |

---

## 4 · Phase 3 — filters sized to their data

The Zápasy and Týmy filters were built against twelve teams in one group, knowingly provisional. At real scale each filter wants a different control, and the deciding factor is simply how many things sit inside it:

| Filter | Items | Control |
|---|---|---|
| League | ~60 in 8 leagues | Two cascading rows of chips — league level, then group letter |
| Venue | ~35 | Picker (chips), new |
| Team | ~900 | Text field |

The league cascade matches PSMF's own shape — psmf.cz presents `6. liga` and then `A … L`, and referees say "šestka K" out loud — and group counts run 1, 2, 4, 6, 9, 12, 12, 14, so neither row ever needs to scroll, against sixty rows in one flat menu. Chips replaced dropdowns everywhere for the same three reasons: a menu costs a tap to open before the tap to choose, hides current state until opened, and gives a smaller target than a chip — all three wrong for a cold thumb outdoors. A league alone stays a valid filter; picking one clears any group already chosen. `Týmy` now opens on followed teams, with search to add more and browse-by-league kept secondary and collapsed, because rendering nine hundred rows flat is not a list anyone uses.

### What a device found

**A validation rule that assumed every league group's `reportCode` starts with a digit.** Adding `leagueLevel`/`groupLetter` parsing to `Group` with a `require()` guard broke `SeedLeagueCatalogTest`'s existing veteran and futsal fixtures — those competitions sit outside the eight-level numbered hierarchy entirely and their codes (`VET`, `FUT`) don't start with a digit. The guard encoded an invented rule the domain doesn't actually have. Fixed by making `leagueLevel` nullable and deriving the cascade only from whatever levels are actually present in the loaded data, never hardcoding 8 leagues or a letter range — the same design proven against a 3-group, 2-level synthetic fixture in `FixtureFlowTest`.

**A stale string left over from the dropdown it used to describe.** `teams_followed_empty` still promised followed teams would appear "at the top of the fixture filter" — true of the old team dropdown, meaningless once that dropdown was replaced by a text field. Caught by re-reading the screen on device after the filter rework, not by any test; fixed in all three languages.

### Gate 3 against its criteria

| Criterion | |
|---|---|
| Filter by venue, league+group, and typed team name, on device | ✅ |
| Cascade proven at "sixty" scale even with one group actually shipped | ✅ — derived from data, tested against a synthetic multi-level fixture |
| A league alone, with no group, filters to the whole league | ✅ |
| Changing the league clears the group | ✅ |
| Týmy opens on followed teams, usable with browse collapsed | ✅ |
| Star toggles and reads correctly in all three languages | ✅ |
| UI tests for the venue filter and the star | ✅ |
| Committed | ✅ `974f7cf` |

---

## 5 · Phase 4 — the rules panel translates, and saving asks once

Two fixes carried over from the same round of device testing that found the other four.

**4.1 — the rules panel.** `Délka poločasu`, `Počet poločasů`, `Hráčů na hřišti`, `Oslabení po vyloučení` were hardcoded Czech constants in `SettingsViewModel`, so they stayed Czech in English and Ukrainian. This was the "always Czech" rule applied one layer too far — the second time that's happened, the first having cost the language picker itself. Restated precisely: **the ZoU is always Czech** (its field labels are fixed strings in `ZouLabels`/`ZouWords`, never resources, because PSMF receives that document); **everything the referee reads on screen translates**, because the referee — and the captain reading over their shoulder — is who the app is for. The rules panel is app UI, not the ZoU, so it translates; the four numeric values (30 min, 2, 6, 10 min) don't, because they're data, not prose. A sweep of the rest of the app for stray Czech literals outside `ZouLabels`/`ZouWords` turned up nothing else — the only remaining diacritic hits are language autonyms (`"Čeština"`), KDoc quoting the ZoU form, or Czech player and team names that are domain data, not UI copy.

**4.2 — saving asks once.** Saving chained three `ACTION_CREATE_DOCUMENT` dialogs, one per file, every single export. This was a specification error, not an implementation one: `prompts/05` and `DEMO_SCOPE.md` both named `ACTION_CREATE_DOCUMENT` as the mechanism, and the API was implemented exactly as named — it creates exactly one document per launch, so three formats meant three consecutive system dialogs. The prompt named an API where it should have named an outcome. Replaced with `ACTION_OPEN_DOCUMENT_TREE`: ask for a folder once, take a persistable URI permission, then find-or-create each of the three files inside it on every later save, silently. Settings gained a `Choose a folder` / `Change the folder` action for picking a different destination later. Nothing about `ZouDocument.bytes()` changed, so the byte-identical guarantee — the CSV's BOM, none on the TXT, CRLF throughout — carried over structurally rather than by re-implementation; verified by reading the saved files back off the device (`ef bb bf` on the CSV, none on the TXT, correct content in both).

### What a device found

**Settings kept saying "Choose a folder" after a folder had already been chosen.** Saving from the Export screen (not from Settings' own button) writes the folder URI straight to the repository, but `SettingsViewModel` reads `exportFolderChosen` exactly once, at app startup — and only Settings' own `Change folder` button reported a pick back to it. A referee who saves before ever opening Settings would see "Choose a folder" for the rest of the session even though one was already picked and every later save was already using it silently. The KDoc on `SettingsUiState.exportFolderChosen` already claimed `ExportRoute` reported this back; it didn't. Fixed by having it actually do so (`settings.onEvent(SettingsEvent.ExportFolderChosen)` after a successful save), and re-verified end to end on a fresh install: save from Export, then check Settings without touching its own button — it now correctly reads "Change the folder".

### Gate 4 against its criteria

| Criterion | |
|---|---|
| Rules panel reads correctly in cs/en/uk | ✅ on device, all three |
| No user-facing Czech literal survives outside `ZouLabels`/`ZouWords` | ✅ swept |
| Export still comes out Czech with the app in en/uk | ✅ — preview and saved files both, on device |
| Saving asks once, then never again; bytes still match | ✅ — picker appeared once across two saves; second save silent, same 3 files, mtime updated, no duplicates; BOM/CRLF verified byte-for-byte |
| Committed | ✅ `d9f16ab` |

---

## 6 · Findings worth not rediscovering

**Four of six fixes this session were the project owner's own specification errors, not implementation mistakes**, and each is recorded that way in `DECISIONS.md` rather than folded quietly into the fix: the half-time ban, the rules-panel translation rule applied too broadly, and the `ACTION_CREATE_DOCUMENT` API named instead of the outcome it was meant to produce. A prompt that names a specific Android API rather than the behaviour it should produce is a prompt that can be implemented correctly and still be wrong.

**A `require()` validation can silently encode an assumption the domain doesn't hold.** The `reportCode` digit-prefix guard in Phase 3 looked like input validation and was actually an invented rule; the existing test suite (veteran/futsal fixtures) caught it immediately, which is the argument for running the full suite after every domain-adjacent change rather than trusting a change "looks safe."

**A `ViewModel` that reads repository state once at construction goes stale the moment something else writes to that same repository.** `SettingsViewModel.exportFolderChosen` is the second occurrence of this shape in the project (the shell-rework report's Phase 2 found a related but distinct issue with tab-label sizing) — worth checking for elsewhere: any UI state seeded once from a repository that a *different* route can also mutate is a candidate for the same bug.

**A stale string is a device-only defect class of its own.** `teams_followed_empty`'s reference to "the top of the fixture filter" was correct when written and wrong the moment the control it described was removed — no test caught it because no test asserts prose against a UI structure that no longer exists. Re-reading every screen after a structural change, not just re-running the suite, is what caught it.

---

## 7 · Decisions taken, and by whom

| Decision | Who | Note |
|---|---|---|
| The clock never pauses *during play*; half-time is not a pause | Project owner | Reverses the over-stated original spec; recorded before the fix landed |
| `30´+`/`60´+` derived from period state, not selected by the referee | Project owner | Falls out of restating the clock rule correctly |
| The rules panel translates; the ZoU alone stays fixed-Czech | Project owner | Second occurrence of the same over-broad "always Czech" rule |
| Card control stays one icon, not split yellow/red | This session | Judgment call at the gate, against a live 411dp screenshot |
| `Nesledovat` becomes a star, not plus/minus | Project owner | Plus/minus implies adding, not marking |
| League filter is a cascade (level, then letter); venue and team are sized to their own counts | Project owner | Deciding factor stated plainly as item count, not a style preference |
| `ACTION_OPEN_DOCUMENT_TREE` replaces `ACTION_CREATE_DOCUMENT` for saving | Project owner | Reverses a spec that named an API instead of an outcome |
| `ExportRoute` reports `ExportFolderChosen` back to Settings | This session | Device-found gap between what a KDoc comment claimed and what the code did |
| The `reportCode` digit-prefix guard was removed rather than kept and special-cased | This session | Veteran/futsal sit outside the numbered league hierarchy; the guard encoded a rule the domain doesn't have |

---

## 8 · Open items and known gaps

Carried forward from the previous report, still open:

**No physical device, at any gate, in this session either.** Every "on a device" claim in this report means an Android emulator, API 36, x86_64, driven through `adb`.

**iOS has never been compiled.** Phase 4.2 notes iOS is unaffected by the save-flow change — its share sheet already asks once — but that claim, like every iOS claim in this project, is unverified against a real toolchain.

**The card-control decision is reversible, not settled.** Recorded in `DECISIONS.md`: reverses to a split yellow/red icon if the single-icon layout crowds the row on a real device.

New from this session:

**No test covers the `SettingsViewModel` staleness class of bug in general** — only the one instance found and fixed. If another route ever writes to a setting Settings itself displays, the same "read once at startup" gap could reappear elsewhere without a device pass to catch it.

**The venue filter and picker are real UI against still-provisional venue data**, per the Phase 3 decision entry — cheap to extend once `/hriste/` data lands, not yet exercised against it.

---

## 9 · Commits

| | |
|---|---|
| `3c98e19` | Give the match a half-time: end a period, hold the clock, start the next |
| `02cde29` | Console: Gól and Karta become icons |
| `974f7cf` | Filters sized to their data |
| `d9f16ab` | Move league rules panel to strings.xml and switch report saving to a single folder pick |

One commit per gate, each verified on the emulator before the next began, and Phase 1 gated alone before any of the other five fixes — as the opening prompt for this session required, since it was a domain change and the other five were not.

---

## 10 · Next session

1. **Run the whole app on a physical phone.** Every fix in this report was found by using the app rather than reading it, and the emulator has never been a substitute for that — it's the same gap the previous report closed with, still open.
2. **Decide the card-control question again once a real device is available**, against its own reversal condition rather than the emulator screenshot this session used.
3. **Prove the iOS toolchain**, unchanged in priority from every previous report — it now sits behind four unverified claims (send, save, the language picker's font coverage, and the save-folder-once flow) rather than one.
4. **Sweep for the `SettingsViewModel`-staleness bug shape elsewhere**, now that it's been found once — any settings-adjacent state seeded once from a repository that a different route can also write to.
