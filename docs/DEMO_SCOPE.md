# Demo scope

**Goal:** show PSMF that a match can be recorded on a phone and the report
delivered digitally, with no paper. Nothing more.

Derived from the reuse audit in `LEAGUE_APP_ANALYSIS.md` §7 and the ZoU field spec
in §2.5. This file only adds what the analysis deliberately left open: the screen
set, the cut line, and the resolution of the §5 tensions.

---

## Shell — four tabs around one wizard

The first build shipped as an eight-stop linear wizard, and on a device it reads as
one long form: back, forward, back, forward. The screens were right; the frame
around them was not.

| Tab | Czech | Holds |
|---|---|---|
| 1 | **Zápasy** | Fixtures across every bundled group, filtered by league and team |
| 2 | **Zápis** | The report — the wizard, unchanged, badged while one is in progress |
| 3 | **Týmy** | Followed teams, search, roster |
| 4 | **Nastavení** | Language, theme, and the league's rules as information |

golblok already has exactly this — Home, Match, Teams, Settings — which is where
the expectation came from and is a good reason to keep it.

**The report stays linear inside its tab.** A ZoU has a required order, and a
referee outdoors in the rain should not be choosing where to go next. The tabs
exist so that everything *else* is reachable without abandoning the report.

Three things follow:

- **Each tab keeps its own back stack.** `AppNavigator` becomes a map of stacks.
  Still hand-rolled — a navigation library buys convenience the demo does not need
  and costs a version-matrix constraint `BUILD_MATRIX.md` works to avoid.
- **A match in progress is visible from every tab**, as a badge on *Zápis*. Without
  it a referee who opens Settings mid-half has no way back. This reverses the
  earlier "no ongoing-match hero" clause deliberately.
- **Leaving the console mid-match is safe by construction.** Elapsed time is *now
  minus kickoff*, one stored instant. Nothing ticks, so nothing drifts.

---

## Screens

Ten in the full product. **Nine in the demo**, arranged in four tabs.

| # | Screen | golblok origin | In demo | Notes |
|---|---|---|---|---|
| 1 | **Fixtures** | HomeScreen | ✅ | Its own tab. Matches across all groups, filterable by league and team. An in-progress match is reachable from any tab — see *Shell*. |
| 2 | **Match header** | MatchSetupScreen | ✅ | *Different content, not a modification.* Pick a fixture, then referee + assistant, licensed-hire flag (`R`), and the **delegating team** — which is who gets fined for a bad report. |
| 3 | **Lineup** | — *new* | ✅ | Per team: who is present, jersey numbers, kit worn, identification. Captain confirms. **Absences live here and nowhere else.** |
| 3a | **Týmy** | TeamsScreen | ✅ | Its own tab. Search, follow, roster. Jersey numbers editable; nothing else. |
| 4 | **Live console** | MatchScreen | ✅ | The one genuinely reusable interaction model. |
| 5 | **Assessment** | — *new* | ✅ | NH / Čd / Č / B per team, plus the mandatory commentary. |
| 6 | **Recap & confirm** | MatchRecapScreen | ✅ | What the captains sign against. |
| 7 | **Export** | Share action | ✅ | JSON + CSV + formatted text, out via email intent **and saved to the device**. |
| 8 | Settings | SettingsScreen | ✅ | Its own tab. Language and theme are real settings; league rules are information. |
| 9 | Amend a finished match | MatchEditScreen | ❌ later | Required by the product (§2.12), not by the demo. |

Screens 5 and 7 are the ones that make the demo *true*. Without the assessment
block the ZoU is incomplete, and an incomplete ZoU does not demonstrate that paper
can be replaced — it demonstrates the opposite.

---

## Screen by screen

### 1 · Fixtures — the *Zápasy* tab
Seed data for every bundled group. No scraper, no backend, no network. Tap a
fixture to start or to resume one already under way.

Nine divisions is roughly 900 teams, so a flat list does not survive real data.
Filter by league and by team.

**Fixtures stay in round and kickoff order** — the order on the paper schedule, and
what makes the round headings work as navigation. "Followed teams first" applies to
the **team picker**, not to the fixture rows: at nine divisions the picker is
otherwise a nine-hundred-row scroll.

### 2 · Match header
ZoU page 1. Pitch, date, time, league group come from the fixture. The referee
enters: main referee, assistant, `R` flag if a licensed hire, and the delegating
team — a separate field from the two playing teams, and easy to miss.

### 3 · Lineup
**The design decision that shapes everything.** §5.1's proposal: the squad is
already known, so the task inverts from "write ten names" to "mark who is absent"
— three to five taps, done on the referee's own phone with the captain beside them,
nothing changing hands.

Per player: present/absent, jersey number, identification.

- Jersey number defaults to last known and is corrected by exception. In the demo
  there is no history, so it defaults from seed data.
- **Kit selection per team.** A team owns two kit sets and picks one per match, so
  that two teams do not play in similar colours. Defaults to the primary. This is
  what fills `Barva dresů` on the ZoU, which is why the form asks for it at the
  match rather than holding it against the team.
- **Identification is two separate things, not one field.** The RP number is issued
  by PSMF at registration, is immutable, and **the user can never type or edit
  one**. Date of birth (or rodné číslo, pending A28) is the fallback, entered by a
  person. A player needs at least one of them to exist at all.
- **Adding a player is allowed** — a player turns up who is not yet in PSMF's
  database. First name, surname, date of birth. No RP field is offered. Mark them
  as user-added so they can be reconciled once PSMF registers them.
- **Suspension badge, advisory only.** Yellows accumulate per group per season and
  trigger a ban on even totals; two yellows in one match count zero. Shown with its
  `asOf` date. **Never blocks, never says "eligible"** — fielding an ineligible
  player is a technical forfeit, so a false clearance would be the app causing harm.
  Red-card bans are STDK's and are never computed.
- Existing player name, surname and card history are **read-only**. A referee
  editing a registered player is a data-integrity failure; adding an unregistered
  one is a documented case the paper form already handles.
- What was written in the RP column is recorded **per match**, with its source, not
  derived at export. A report states what was written on the day.

Captain confirms per team. A tap, not a signature — parity with a pen mark nobody
verifies (§3.2, pending A7).

### 3a · Týmy — the reference tab

Personal in the sense of *followed teams*, not in the sense of owning them. **Still
the referee's app**; there is no captain-facing surface here.

- **Search**, over every team in every bundled group. One field at the top.
- **Follow** a team into a personal list — *Sledované týmy*. Not "download":
  everything is already on the device, and a progress bar for a local file is a
  lie. It stays the right verb if a backend ever appears.
- **Browse by league** for anyone who would rather scroll than type.
- **Open a team** to see its roster: surname and first name, default jersey number,
  the kit sets, and the RP number where one exists. Statistics later, if ever.
- **Jersey numbers are editable here**, and only jersey numbers. A default number
  is a standing attribute of a player, which is exactly what a team screen is for.
- **Names, RP numbers and card history stay read-only.** A referee editing a
  registered player is a data-integrity failure — unchanged from screen 3.

**Absences are not here.** Absence is a fact about one match, not about a player.
Setting it in a team screen would either persist forever or need a fixture
attached, at which point it is the lineup screen with extra steps. It stays on
screen 3, where the referee is standing next to the captain.

Two mechanics this needs:

- **A jersey override table.** Seed data is a bundled resource, replaced wholesale
  on every app update, so an edit cannot live in it.
- **Nothing already written moves.** The lineup snapshots the jersey per
  appearance, so changing a default cannot alter a past report — the same rule as
  the kit label and `reportedIdentification`, and already satisfied.


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
- **The clock runs continuously *during play*.** 2 × 30 gross time, referee adds
  time at the end. golblok pauses for injuries; PSMF has no such stop (§2.6). Do
  not carry it across.
- **But a match has halves.** End-of-period and start-of-next-period are real
  actions; the interval is not part of the sixty minutes and the minute holds
  through it. A half-time interval is not a pause during play — see `DECISIONS.md`,
  which records this as a correction to how the rule was first written.
  Two consequences, both simplifications: `30´+` and `60´+` become **derivable
  from the period state** instead of remembered, and the half-time score becomes
  **the score when period one ended** instead of something typed on the recap.
- **Power-play:** 10 minutes after a dismissal before a replacement may come on —
  not shortened by a goal, unaffected by further dismissals. Its own lifecycle,
  running alongside a clock that never pauses.
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
Half-time score **pre-filled from the event log** and still editable, final score,
explicit winner. Full event list. The assessment
block. An explicit **"no cards were issued"** affirmation where applicable — the
paper form requires the boxes to be struck through, so an empty list is not the
same as "none".

§5.5: on paper a captain signs one sheet they can see in full. Here they confirm a
screen, and **whatever is not on that screen is not being checked.** Design it as a
complete document, not a summary.

### 7 · Export
JSON, CSV and formatted text — all shared code, all cheap. Out through the platform
email intent to `psmf@psmf.cz`, which is already an accepted channel.

**And saved to the device**, not only handed to mail. Files that live only in
app-private storage mean a referee can never open afterwards the work their own
name is on.

**Ask for a folder once** — `ACTION_OPEN_DOCUMENT_TREE` with a persisted
permission on Android, "Save to Files" on iOS — and write all three files into it,
silently on every later export. Naming `ACTION_CREATE_DOCUMENT` here was a
specification error: it creates one document per launch, so three formats became
three chained system dialogs. See `DECISIONS.md`.

Saved and sent bytes are identical by construction — one `ZouDocument.bytes()`
encoder, so the CSV's byte-order mark cannot drift between the two paths.

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
- **Real** suspension data — the badge is in, but its counts come from seed data.
  Deriving them for real needs A3 or a scraper over the public card detail.
- Red-card suspensions — STDK's to decide, never computed by the app
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

## Visual identity

Sampled from psmf.cz, not invented. Confirm with PSMF alongside the A-questions —
particularly whether their logo may appear in the app.

| Role | Value | Where it is on their site |
|---|---|---|
| Brand yellow | `#FBBA00` | The logo block, section headings, primary buttons |
| Ink | `#2B2B2B` | Body text, the dark bar |
| Black | `#000000` | The top nav strip |
| Surface | `#FFFFFF` | Content cards |
| Page | `#F2F2F2` | Behind the cards |
| Alert red | `#D60010` | Their single accent — takes the red card |

**Yellow is a surface, not a text colour.** `#FBBA00` on white fails contrast for
anything you have to read. Use it for fills, the selected tab indicator, primary
buttons and the match-in-progress badge, and put `#2B2B2B` on top of it — never
white.

Dark theme keeps the same yellow, which holds up on a dark ground, and swaps the
neutrals rather than the brand.

### Fonts, and the trap in them

PSMF use **Anton** for headings and **Barlow** for body. Both are Google Fonts
under the OFL, so bundling costs nothing. **Neither covers Cyrillic** — verified
against Google's font metadata, both are `latin`, `latin-ext`, `vietnamese`.
Whatever is bundled instead is checked against the file rather than the metadata:
a test reads the `cmap` table of each `.ttf` and asserts the Ukrainian-specific
letters are in it.
`latin-ext` handles every Czech diacritic; Ukrainian would fall back to a system
face mid-screen, which looks like a bug because it is one.

So the app uses their nearest siblings that carry all three languages:

| Role | Face | Why |
|---|---|---|
| Display | **Oswald** | The closest condensed grotesque to Anton, with `cyrillic` |
| Body | **Noto Sans** | Full `cyrillic` and `cyrillic-ext`; what golblok's `Type.kt` always meant to load |

Bundle the subsets actually needed; do not fetch fonts at runtime — the app has no
network by design and a referee on a pitch may have no signal.

### What to take from golblok, and what not to

**Take:** the type *scale* in its `Type.kt` — the sizes, weights, line heights and
letter spacing are sound. The Material icon vocabulary, which is already close to
right: `SportsSoccer`, `Group`, `Settings`, `PersonAdd`, `Undo`, `Warning`,
`Share`. The four-tab `NavigationBar`. Its spacing and card rhythm.

**Do not take:** its palette — that is a different brand, dark blue. Its
`FontFamily` declarations — the comment in `Type.kt` admits the real files were
never added and both families resolve to `FontFamily.SansSerif`. And nothing that
implies a paused clock: no `Pause`, `Stop` or `PlayArrow` icon belongs on the
console.

### Constraints that outrank taste

- **Respect the system font scale.** The referee population skews older and a
  fixed-`sp` layout that breaks at 130% is not shippable.
- **Ukrainian strings run longer than Czech.** No fixed-width labels, no
  single-line assumptions.
- **Outdoors, in the cold, one-handed.** Generous touch targets and real contrast
  beat density every time.

---

## Settings

Not a stub after all — three real settings, and one deliberate omission.

| Setting | Values | Note |
|---|---|---|
| Language | **Czech (default)**, English, **Ukrainian** | Picked *in the app*, not inherited from the device — the captain reads the referee's phone |
| Theme | Dark / Light / System | |
| Left-handed mode | — | **Deferred.** Only worth adding if the button layout makes it meaningful. Decide once screen 4 exists. |

Everything else golblok exposes — half length, number of periods, players per team,
default venue, assist and substitution toggles — is **read-only information**. The
league sets those. A referee changing the half length is a defect.

**Those rule labels translate.** They are app UI, read by the referee. Only the
ZoU itself is always Czech. Ask of any string: *does PSMF receive it, or does a
person holding the phone read it?*

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
  index.json     lists available groups
  6k.json        teams, players and fixtures for 6. liga K
```

`index.json` carries id, display name, season and filename per group; `venues.json`
holds the league-wide pitch codes, which are not group-specific. Each group file
carries teams (with their **two kit sets**, each with a verbatim label, and the
half length and period count), players (surname, first name, **RP number where one
exists**, date of birth as the documented fallback, default jersey number and a
dated yellow-card count) and fixtures (round, date, time, venue code, home, away).

Player identification is **two fields, not one polymorphic one** — an RP number is
issued by PSMF and can never be typed; date of birth is what a person enters when
there is no RP number to use. See `DECISIONS.md`, 2026-08-29.

This shape follows the entity sketch in the analysis §6, and is the reason the
domain model was built before any screen.

**All nine divisions belong here, not just 6K.** Search and follow in the Týmy tab
are pointless over twelve placeholder teams, which is what promoted the psmf.cz
scrape from queued to blocking.

## A note on what the demo actually sells

§1 of the analysis: *"The value sold to PSMF is not a better referee experience —
it is the elimination of a week of transcription."*

So the most persuasive artefact may not be the app at all — it may be **the output
file**. A formatted email is pleasant; a spreadsheet their crew can use instead of
retyping is the thing that demonstrates the claim. Worth weighting effort towards
the export accordingly, and A8 asks PSMF which form they actually want.
