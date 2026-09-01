# The shell rework and the Týmy tab

**Session date:** 2026-08-31
**Repository:** `~/dev/psmf-app` (WSL Ubuntu)
**Commits:** 5 — `bd73f42` … `9c3c980`
**Outcome:** all four gates met, tree clean, the demo now opens into four tabs instead of an eight-screen wizard

---

## 1 · Status at a glance

| | Before (previous report) | After |
|---|---|---|
| Shell | linear 8-screen wizard | **4 tabs** — Zápasy / Zápis / Týmy / Nastavení, each with its own back stack |
| Language | follows the device, no in-app picker | **picker** — Czech (default), English, Ukrainian, via `LocalAppLocale` |
| Visual identity | Material defaults | **PSMF palette**, sampled from psmf.cz; Oswald + Noto Sans for Cyrillic |
| Teams | did not exist | **Týmy tab** — search, follow, browse by league, roster with jersey-only editing |
| Fixture list | flat | **filterable** by league and by team, followed teams surfaced first in the picker |
| Export | send only | **send or save to device**, same three files, byte-identical either way |
| SQLDelight schema | version 2, no migration path | **versioned to 3**, migrations tested, `verifyMigrations` wired into `check` |
| Shared tests (JVM) | 315 | **364** |
| Shared tests (Android host) | 294 | **329** |
| composeApp UI tests | 82 | **151** |

The prompt that opened this session was explicit that Phase 0 (migrations) had to land before any screen changed, and that nothing later could be batched with it. It was done first, alone, and everything after built on top of a schema that could already be migrated instead of only ever freshly installed.

---

## 2 · Phase 0 — migrations become possible, not just correct

The previous report closed with a known gap: two phases had already added columns with no migration path, and a clean install was the only way to update. That gap could not survive this session — Phase 3 was always going to add tables.

`Schema.version` now reads from the highest `.sqm` file plus one, rather than being hand-set. Each migration ships with a recorded verification database (`databases/<n>.db`) and `verifyMigrations` runs as part of `check`, so a schema change that isn't paired with a working migration fails the build, not a future install.

### Gate 0 against its criteria

| Criterion | |
|---|---|
| A database created at version 2 migrates cleanly to the current version | ✅ |
| `Schema.version` derives from the `.sqm` chain, not a hardcoded constant | ✅ |
| `verifyMigrations` runs in `check` | ✅ |
| Committed, alone, before any shell or UI change | ✅ `bd73f42` |

---

## 3 · Phase 1 — four tabs, four back stacks

The eight screens were not rewritten — the prompt was explicit that they were right and the frame around them was not. What changed is what contains them: `Zápasy` and `Zápis` are the same fixture list and live console as before, now reachable as tabs instead of wizard stops, each keeping its own navigation history so switching tabs and coming back doesn't lose a referee's place. `Zápis` carries a badge while a report is in progress, so a referee mid-match and looking at fixtures still sees where they are.

`Týmy` existed as a placeholder tab in this phase — deliberately, since Phase 3 was where it became real — and `Nastavení` is the renamed, relocated settings screen.

### Gate 1 against its criteria

| Criterion | |
|---|---|
| Four tabs, each with an independent back stack | ✅ |
| Zápasy and Zápis are the pre-existing screens, unmodified in substance | ✅ |
| The in-progress badge appears on Zápis only while a report is open | ✅ |
| Committed | ✅ `6655b3d` |

---

## 4 · Phase 2 — language and PSMF's identity

The language picker follows JetBrains' `LocalAppLocale` pattern — a `CompositionLocal` set at the app root, read everywhere text is produced. Czech is the default, matching the fact that the report itself is always Czech regardless of what the app displays. The palette was sampled from psmf.cz directly rather than guessed; Oswald carries headings, Noto Sans carries body text because it has Cyrillic coverage Oswald does not, which matters the moment the language picker is set to Ukrainian.

### What a device found

**The Ukrainian tab label wrapped.** `"Налаштування"` (Settings) broke after 11 of its 12 letters at the bottom-bar's default 14sp — invisible to any test asserting `assertIsDisplayed`, which confirms a node is in the tree, not that it fits on one line. Fixed by dropping to Material's own `labelMedium` size (12sp) and adding horizontal padding to keep the longest label off the screen edge.

The regression test for this could not simply assert visibility — a rewritten `everyUkrainianTabLabelFitsOnOneLineOnAPhone` test wraps the tab row in a fixed 411dp-wide `Phone { }` container and reads `SemanticsActions.GetTextLayoutResult` to count lines directly. Canary-verified: reverting the size fix reproduces the exact wrap.

### Gate 2 against its criteria

| Criterion | |
|---|---|
| Czech, English and Ukrainian all render, including Cyrillic | ✅ on device |
| Every tab label fits on one line on a phone-sized screen | ✅ found broken, fixed, regression test added |
| Palette sampled from psmf.cz, not approximated | ✅ |
| Committed | ✅ `04fa92f` |

---

## 5 · Phase 3 — the Týmy tab

The largest phase, and the one the whole rework existed to make room for.

**Two kit sets are the boundary, not the roster.** A team follows or doesn't; jersey numbers can be corrected from their league default, but nothing else about the roster — not a name, not an RP number, not a card — is editable, because none of that is PSMF's to change on a device. The correction sits behind `JerseyOverridingLeagueRepository`, a decorator over the existing `LeagueRepository`: neither the seed repository nor the lineup screen needs to know an override table exists, which is what makes it safe to add without touching either.

**"Followed teams first" was read as the picker's ordering, not the fixture list's.** The scope line was genuinely ambiguous between two readings. Fixtures stay in round and kickoff order — the order printed on the paper schedule — because promoting some rows above others would break the round headings that make a long list navigable. The team picker is where followed-first earns its keep: at nine divisions it's otherwise a nine-hundred-row scroll. Flagged as a judgment call at the gate rather than assumed away.

**The tab is thin because PSMF's current data is thin, and it says so.** Search across twelve teams in one group, one league in the browse menu, an empty `Číslo RP` column because PSMF haven't issued RP numbers yet. None of that is a bug, and all of it looks like one if the screen goes quiet about why. The roster carries a sentence explaining the empty column instead of a column of blanks; the league picker stays a `DropdownMenu` deliberately, because a searchable picker designed against twelve rows would be a guess about what nine hundred rows need.

### What a device found

**Clearing a search left followed teams scrolled off-screen.** `LazyColumn` anchors its position by the key of whichever item is currently first on screen; clearing the query changes what that first item is, and the followed section — which the referee is trying to get back to — ends up above the fold. Fixed in one line (`scrollToItem(0)` when the query changes).

The interesting part is what happened trying to test it: **three independent attempts at a regression test all passed unconditionally with the fix deleted** — a phone-sized container, `performScrollTo`, and a tagged list with `performScrollToIndex` under `--rerun-tasks` to rule out caching. The JVM Compose test host does not model a lazy list's scroll-position-by-key behavior at all, so there was nothing for any of the three tests to catch. The test was **deleted rather than committed**, with a comment on the fix stating plainly that nothing in the repository covers it, and a DECISIONS.md entry generalizing the rule: *a test that cannot fail is worse than no test, because it tells the next reader something false.*

### Gate 3 against its criteria

| Criterion | |
|---|---|
| Search, follow/unfollow, browse by league, all on device | ✅ |
| Roster jersey-number editing, everything else read-only | ✅ structurally — no other field is writable |
| A jersey override never alters an already-stored report | ✅ proved in `JerseyOverrideTest` and on a live database |
| Fixture list gains league/team filters, followed teams first in the picker | ✅ |
| Schema migration (v2→3) tested at the version immediately before it | ✅ |
| Committed | ✅ `4b09dad` |

---

## 6 · Phase 4 — save beside send

The last phase, and the smallest in scope: one button, beside the existing send button, writing the same three files somewhere the referee can reopen them without the app.

**`ACTION_CREATE_DOCUMENT` was implemented literally.** It creates exactly one document per launch — there is no batch form — so saving three formats is three "Save As" dialogs in a row, chained so the referee taps through rather than restarting. The smoother alternative, `ACTION_OPEN_DOCUMENT_TREE` asked once for a folder, was not taken, because the exact constant `ACTION_CREATE_DOCUMENT` is what's written down twice in project documentation. Recorded as reversible if three dialogs proves too clunky for a referee to use twice a week.

**The saver couldn't be a Koin single the way the sender is.** `CreateDocument` returns a `Uri` through `ActivityResultRegistry`, which only a live `ComponentActivity` has — Koin here is wired from the Application context, which cannot open a picker at all. Built as `rememberReportSaver()`, a `@Composable expect`/`actual` constructed at the point of use, reusing the pattern `LocalAppLocale` already established in Phase 2 for exactly this kind of platform capability.

**"The saved bytes match the sent bytes" is now structural.** The two Android write paths (`File.writeText` for send, a SAF `OutputStream` for save) were two easy places for a diacritic or the CSV's byte-order mark to drift apart unnoticed. Both now go through one `ZouDocument.bytes()` extension in `shared`. Proved twice: `ZouDocumentBytesTest` pins the byte-level contract on the JVM — the BOM survives encoding as three bytes, `EF BB BF`, and the other two formats never gain one — where no Android write path can run at all; then the emulator sent and saved the same match and `cmp` found all six files byte-for-byte identical, first three CSV bytes `ef bb bf` on both sides.

### Gate 4 against its criteria

| Criterion | |
|---|---|
| Save button appears beside send, under the same readiness rule | ✅ |
| Three files saved to Downloads via the system picker | ✅ verified on device, visible afterward in the Files app |
| Saved and sent bytes are identical | ✅ `cmp`, all six files, on device |
| A cancelled or failed save is named, not silent | ✅ |
| Committed | ✅ `9c3c980` |

---

## 7 · Findings worth not rediscovering

**`assertIsDisplayed` is not a layout assertion**, twice over now — once for the Ukrainian tab label, once implicitly in the Phase 3 test-that-couldn't-fail. It confirms a node exists in the tree; it says nothing about whether it fits, wraps, or is scrolled into view.

**A lazy list's scroll-position-by-key is not reproducible on the JVM test host.** For anything that depends on how much fits on a screen or which item a list re-anchors to, the emulator is the instrument, not the test suite. Written up as a general rule in DECISIONS.md, this being its third occurrence in the project.

**`@Composable expect`/`actual` is now the established pattern for a platform capability Koin's Application-scoped DI cannot reach.** `LocalAppLocale` set the precedent in Phase 2; `rememberReportSaver()` in Phase 4 followed it rather than inventing a second idiom for the same kind of problem.

**`adb pull`'s destination path needs `cygpath -w` conversion even inside Git Bash.** `adb.exe` is a native Windows binary; Git Bash's usual automatic path conversion did not save this — the pull failed silently with "No such file or directory" against a destination directory that demonstrably existed, until the destination was wrapped in `cygpath -w` explicitly. A driving-toolkit finding, not an app defect, but worth not re-discovering the hard way.

---

## 8 · Decisions taken, and by whom

| Decision | Who | Note |
|---|---|---|
| Migrations land first, alone, before any UI change | Project owner | Stated as a hard prerequisite in the opening prompt |
| Tab label size drops to `labelMedium` (12sp) | This session | Found on device; Ukrainian wrapped at 14sp |
| Jersey overrides sit behind a repository decorator | This session | One seam, in the Koin module; seed/lineup code stays unaware |
| "Followed teams first" applies to the picker, not fixture ordering | This session | Genuinely ambiguous scope line; flagged at the gate rather than assumed |
| The ineffective scroll-position test was deleted, not committed | This session | A test that cannot fail asserts something false to the next reader |
| `ACTION_CREATE_DOCUMENT` used literally — three dialogs, not one folder picker | This session | Matches the exact API named in project documentation; reversible |
| Save and send share one `ZouDocument.bytes()` encoder | This session | Makes byte-identical output structural rather than a claim |
| `rememberReportSaver()` follows the `LocalAppLocale` expect/actual pattern | This session | Reuses an established idiom rather than inventing a second one |

---

## 9 · Open items and known gaps

Carried forward from the previous report, still open:

**No physical device, at any gate.** Every "on a device" claim in this report, as in the last one, means an Android emulator, API 36, x86_64.

**iOS has never been compiled.** `rememberReportSaver()`'s iOS `actual` is written and documented as unbuilt, following the same precedent as the existing `UnavailableReportSender`. The Mac toolchain proof remains the only thing that could force a rethink.

**The three-dialog save flow is a judgment call, not a settled answer.** Recorded in DECISIONS.md with its reversal condition: if it reads as too clunky for a referee to use twice a week, `ACTION_OPEN_DOCUMENT_TREE` is a same-sized change.

New from this session:

**The Týmy tab's team picker is a `DropdownMenu`**, correct for PSMF's current twelve teams and wrong for nine hundred. Left as a menu deliberately rather than building a searchable picker against data too thin to design it properly from.

**No test covers the Týmy list's scroll-restore-on-clear-search behavior.** By design — see §6 — but it means that fix is only as safe as the next person reading its comment.

---

## 10 · Commits

| | | |
|---|---|---|
| `bd73f42` | Add SQLDelight migrations infrastructure |
| `6655b3d` | Rework the shell into four tabs with per-tab back stacks |
| `04fa92f` | Add the language picker and PSMF's visual identity |
| `4b09dad` | Add the Týmy tab: search, follow, browse, jersey overrides |
| `9c3c980` | Save the export to the device, beside sending it |

One commit per gate, each verified on a device before the next began — the same discipline as the previous report, and what makes each device-only finding above attributable to the phase that introduced it.

---

## 11 · Next session

1. **Decide on the three-dialog save flow** against a real referee's reaction, per the reversal condition recorded in DECISIONS.md.
2. **Run the whole shell on a physical phone.** Every device-only defect in this report and the last one was found by running the thing; the emulator has never been a substitute for that.
3. **Prove the iOS toolchain on the Mac**, unchanged in priority from the previous report — it's now blocking two features (export send/save both) rather than one.
4. **Revisit the Týmy team picker** once PSMF's real team/league data exists at something closer to nine divisions, rather than the twelve teams currently shipped.

Nothing in the code blocks any of these.
