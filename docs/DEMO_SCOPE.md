# Demo scope

**Goal:** show PSMF that a match can be recorded on a phone and the report
delivered digitally, with no paper. Nothing more.

Derived from the reuse audit in `LEAGUE_APP_ANALYSIS.md` §7 and the ZoU field spec
in §2.5. This file only adds what the analysis deliberately left open: the screen
set, the cut line, and the resolution of the §5 tensions.

---

## Screens

Nine in the full product. **Six in the demo.**

| # | Screen | golblok origin | In demo | Notes |
|---|---|---|---|---|
| 1 | **Fixtures** | HomeScreen | ✅ stub | A flat list of matches to pick from. Not a dashboard, no history, no ongoing-match hero. |
| 2 | **Match header** | MatchSetupScreen | ✅ | *Different content, not a modification.* Pick a fixture, then referee + assistant, licensed-hire flag (`R`), and the **delegating team** — which is who gets fined for a bad report. |
| 3 | **Lineup** | — *new* | ✅ | Per team: who is present, jersey numbers, identifier. Captain confirms. See §5.1 below. |
| 4 | **Live console** | MatchScreen | ✅ | The one genuinely reusable interaction model. |
| 5 | **Assessment** | — *new* | ✅ | NH / Čd / Č / B per team, plus the mandatory commentary. |
| 6 | **Recap & confirm** | MatchRecapScreen | ✅ | What the captains sign against. |
| 7 | **Export** | Share action | ✅ | JSON + CSV + formatted text, out via email intent. |
| 8 | Settings | SettingsScreen | ➖ stub | Read-only. Shows league rules; edits nothing. |
| 9 | Amend a finished match | MatchEditScreen | ❌ later | Required by the product (§2.12), not by the demo. |

Screens 5 and 7 are the ones that make the demo *true*. Without the assessment
block the ZoU is incomplete, and an incomplete ZoU does not demonstrate that paper
can be replaced — it demonstrates the opposite.

---

## Screen by screen

### 1 · Fixtures
Hardcoded seed data for the demo — one or two groups, teams and players in local
memory. No scraper, no backend, no network. Tap a fixture to start.

### 2 · Match header
ZoU page 1. Pitch, date, time, league group come from the fixture. The referee
enters: main referee, assistant, `R` flag if a licensed hire, and the delegating
team — a separate field from the two playing teams, and easy to miss.

### 3 · Lineup
**The design decision that shapes everything.** §5.1's proposal: the squad is
already known, so the task inverts from "write ten names" to "mark who is absent"
— three to five taps, done on the referee's own phone with the captain beside them,
nothing changing hands.

Per player: present/absent, jersey number, and what goes in the `Číslo RP`
column. Per team: which kit was worn.

- Jersey number defaults to last known and is corrected by exception. In the demo
  there is no history, so it defaults from seed data.
- **The RP number is never editable and never offered as an input.** It is issued
  by PSMF. What the referee can change is the *fallback* — the date of birth
  written in the RP column when a player has no card with them, which is the
  form's own printed rule. The value written is recorded per match, not taken
  from the player record at export time.
- **A player who is present but not in the squad list can be added**, with first
  name, surname and date of birth. **No RP field is offered.** They are flagged so
  PSMF can reconcile them once the player is registered.
- **Kit choice per team**, from the two sets the team owns, defaulting to the
  primary. It is on this screen because it is on this block of the paper form:
  the report records what was actually worn that day.
- Player name, surname and card history are **read-only**. A referee inventing a
  player is a data-integrity failure.
- Where a player's yellow total is even, an **advisory badge** carrying its `asOf`
  date. Never a block, never a green tick, and never the word "eligible" — see
  the note on discipline below.

Captain confirms per team. A tap, not a signature — parity with a pen mark nobody
verifies (§3.2, pending A7).

### 4 · Live console
Reworked from golblok. What changes:

- **Goals:** time, jersey number, scorer, running score. **A goal may have no
  scorer** — the worked example contains one.
- **Cards:** mandatory free-text reason on every card. Red must record straight
  versus second yellow (`2. ŽK`).
- **Minute is not an integer** — `30´+` at half-time, `60´+` after the whistle
  before signing.
- **No substitutions. No assists.** Neither appears on the ZoU.
- **No user-configurable rules.** 2 × 30, 5+1, venue from the fixture.
- Keep: undo, tap-to-log against a tabbed player list, crash recovery, sent-off
  players visually disabled, left-handed mirroring.
- **Timer:** store the kickoff timestamp and derive elapsed time. Nothing ticks in
  the background — iOS cannot run a background timer at all.

### 5 · Assessment
No counterpart in golblok, and mandatory on the form.

Per team: `NH` best player by jersey number · `Čd` waiting time in minutes if the
team was not ready at kickoff · `Č` shirts properly numbered, yes/no · `B` uniform
kit colour, yes/no. `Č` and `B` feed directly into fines.

Then the commentary — free prose, ~400 characters in the worked example.

**This is the hardest usability problem in the product** and A6 decides how hard:
if the commentary must be complete before the captains sign, it has to be typed at
the pitch in the dark. Until A6 lands, build it editable at any point up to export.

### 6 · Recap & confirm
Half-time score, final score, explicit winner. Full event list. The assessment
block. An explicit **"no cards were issued"** affirmation where applicable — the
paper form requires the boxes to be struck through, so an empty list is not the
same as "none".

§5.5: on paper a captain signs one sheet they can see in full. Here they confirm a
screen, and **whatever is not on that screen is not being checked.** Design it as a
complete document, not a summary.

### 7 · Export
JSON, CSV and formatted text — all shared code, all cheap. Out through the platform
email intent to `psmf@psmf.cz`, which is already an accepted channel.

PDF and `.xlsx` are deliberately absent: no good shared-Kotlin library exists, so
building them in the app means writing each twice. Server-side later, and A8 asks
PSMF what they actually want.

---

## Out of the demo

Deferred, not rejected.

- Amending a finished match — required by the product, not by the demo
- Report versioning and audit trail (§5.3)
- Fixture ingestion from psmf.cz — seed data instead
- Any backend, any network call
- Suspension warnings (A3)
- Device-to-device lineup handoff (§5.1 alternatives)
- Player photographs (A9, A27 — currently no regulatory standing)
- Standings and statistics — PSMF's to produce, not the app's

---

## Decisions — settled 2026-08-29

| | Question | Answer |
|---|---|---|
| §5.1 | How does the lineup reach the referee? | **Mark the absentees**, on the referee's own phone, captain beside them. Nothing changes hands. |
| A6 | Must the commentary be complete before the captains sign? | **No.** Editable until export. |
| A7 | Is a tap enough for captain confirmation? | **Yes.** |
| A15 | How often does a player arrive without their card? | **Rare** — the captain typically carries the whole set. Identifier stays editable but not prominent. |

Still worth confirming against reality by watching one referee fill in a real ZoU,
but nothing is blocked on it.

---

## Settings

Not a stub after all — three real settings, and one deliberate omission.

| Setting | Values | Note |
|---|---|---|
| Language | **Czech (default)**, English, **Ukrainian** | Many foreign players in the league |
| Theme | Dark / Light / System | |
| Left-handed mode | — | **Deferred.** Only worth adding if the button layout makes it meaningful. Decide once screen 4 exists. |

Everything else golblok exposes — half length, number of periods, players per team,
default venue, assist and substitution toggles — is **read-only information**. The
league sets those. A referee changing the half length is a defect.

**Rule that follows from Ukrainian, and is easy to get wrong: the UI language and
the report language are independent.** The generated ZoU always goes to PSMF in
Czech, whatever language the referee is reading the app in. Field labels in the
export are fixed Czech strings, not localised resources.

Even though left-handed mode is deferred, keep the action row in its own composable
with the mirror as a parameter. Cheap now, structural surgery later.

Ukrainian brings two practical consequences: Cyrillic needs a font that actually
has the glyphs, and Ukrainian strings run longer than Czech — so no fixed-width
labels or single-line assumptions in the layout.

---

## Settled 2026-08-29 (second pass)

| | Decision |
|---|---|
| Report language | **Always Czech.** The app may be Czech, English or Ukrainian; the ZoU is not. Export labels are fixed strings, not localised resources. |
| Player names | **Latin only.** PSMF's records are Latin, so names are Latin throughout. Cyrillic is permitted in *app UI text*, not in name data. |
| Power-play | **In the demo.** A dismissal makes the team play short for 10 minutes (§2.6) — a second, independent countdown running alongside the match clock. |
| Match duration | **2 × 30 universal** across HL as far as known. Treat as a constant for now, but read it from the group definition so it can vary later without a code change. |
| Seed group | **6. liga K.** More groups added later as data, never as code. |
| Crash recovery | **In the demo.** Losing a match record is the failure that ends a pilot. |

### Seed data is data, not code

Adding a group must be: drop in a file, add one line to an index, rebuild. No
Kotlin changes, ever.

```
composeApp/src/commonMain/composeResources/files/leagues/
  README.md      the schema, and the rule that UUIDs are never regenerated
  index.json     lists available groups
  venues.json    pitch codes, league-wide
  6k.json        teams, players and fixtures for 6. liga K
```

`index.json` carries id, display name, season and filename per group. Each group
file carries teams (with two kit sets each), players (name, surname, RP number,
date of birth, default jersey number, and an advisory yellow-card count with its
`asOf` date) and fixtures (round, date, time, venue, home, away). Half length and
period count are group-level data rather than constants.

Every team, player and fixture carries an **opaque UUID `id`** and a readable
`ref`. Persisted match reports reference the ids, so **a regenerated id orphans
every saved report** — the rule and its consequences are in the seed README.
Files point at each other by `ref`; the app resolves them at load time.

Venue codes are league-wide rather than group-specific, so they sit in their own
file and every fixture is validated against it.

This shape follows the entity sketch in the analysis §6, and is the reason the
domain model should be built before any screen.

## A note on what the demo actually sells

§1 of the analysis: *"The value sold to PSMF is not a better referee experience —
it is the elimination of a week of transcription."*

So the most persuasive artefact may not be the app at all — it may be **the output
file**. A formatted email is pleasant; a spreadsheet their crew can use instead of
retyping is the thing that demonstrates the claim. Worth weighting effort towards
the export accordingly, and A8 asks PSMF which form they actually want.
