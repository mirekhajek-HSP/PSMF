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
| 1 | **Zápasy** | Fixtures across every bundled group, filtered by league, group, pitch, date and team |
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
| 1 | **Fixtures** | HomeScreen | ✅ | Its own tab. Matches across all groups, filterable by league, group, pitch, date and team. An in-progress match is reachable from any tab — see *Shell*. |
| 2 | **Match header** | MatchSetupScreen | ✅ | *Different content, not a modification.* Pick a fixture, then referee + assistant, licensed-hire flag (`R`), and the **delegating team** — which is who gets fined for a bad report. |
| 3 | **Lineup** | — *new* | ✅ | Per team: who came (marked, nobody is present until tapped), the captain and goalkeeper, jersey numbers, kit worn, Číslo RP. Captain confirms before kickoff. **Who came is recorded here and nowhere else.** |
| 3a | **Týmy** | TeamsScreen | ✅ | Its own tab. Search, follow, roster. Jersey numbers editable; nothing else. |
| 4 | **Live console** | MatchScreen | ✅ | The one genuinely reusable interaction model. |
| 5 | **Assessment** | — *new* | ✅ | NH / Čd / Č / B per team, an optional note per team, plus the mandatory commentary. |
| 6 | **Recap & confirm** | MatchRecapScreen | ✅ | What the captains sign against: the result from the goals, three taps. |
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

League 6 alone is 12 groups, 144 teams, 792 fixtures and 43 pitches, so a flat
list does not survive real data. **Select boxes** for league, then group, then
pitch (only pitches with a match in that league and group, by code and name),
then dates (*Nadcházející* by default); a **text field** for the team. The
filter is the list's first item and scrolls away with it; a button brings it
back, and a line at the top says what the list is narrowed to (DECISIONS
2026-10-08).

**One group in view: round and kickoff order** — the order on the paper
schedule, and what makes the round headings work as navigation. **Several
groups: by day**, each row naming its group (`6K`). *Odehrané* lists the
newest first. A report started and not confirmed is never hidden by the
dates.

### 2 · Match header
ZoU page 1. Pitch, date, time, league group come from the fixture. The referee
enters: main referee, assistant, `R` flag if a licensed hire, and the delegating
team — a separate field from the two playing teams, and easy to miss.

### 3 · Lineup
**The design decision that shapes everything.** §5.1's proposal: the squad is
already known, so the task inverts from "write ten names" to a few taps, done on
the referee's own phone with the captain beside them, nothing changing hands. It
was first built as "mark who is absent". The first iPhone test (2026-10-17) turned
it round: **mark who came**, as the paper form's captain does.

Per player: present or not, jersey number, Číslo RP.

- **Mark who came.** Nobody is present until tapped. A player who has not come is
  **one line**, the mark and the name, a touch target tall. A tap marks the player
  present and **opens their row downwards** (jersey, Číslo RP, *Nemá průkaz*); a tap
  again takes it back and closes it. **The name that was tapped does not move**; the
  rows below it do, as in any list. What was typed into a row comes back if the
  player is marked again on the same visit. The count of those marked is pinned at
  the top. (0.3.0 gave every unmarked player the height of an open row, so that
  nothing below ever moved; twenty players became four screens of blank, and the
  testers said so. `DECISIONS.md`, 2026-10-21.)
- **A lineup in progress is kept, and is never a lineup.** Marks, the captain and
  the goalkeeper, jersey numbers, card numbers and dates (half typed too),
  *Nemá průkaz*, the kit and a player added at the pitch are written to a draft as
  they are made, so a phone call or the system reclaiming the app does not take a
  squad of twenty. A team is still a lineup only once it is whole: until then the
  console has nobody on it, and the recap and the export list the lineup as missing,
  exactly as before.
- **One captain and one goalkeeper per team**, both among those present, possibly
  the same person; both are needed to continue. The report lists the captain
  first, then the goalkeeper, then the rest in jersey order, and says in all three
  formats who is which.
- Jersey number defaults to last known and is corrected by exception. In the demo
  there is no history, so it defaults from seed data.
- **Kit selection per team.** A team owns two kit sets and picks one per match, so
  that two teams do not play in similar colours. Defaults to the primary. This is
  what fills `Barva dresů` on the ZoU, which is why the form asks for it at the
  match rather than holding it against the team. The kit chosen shows as a small
  shirt on the team's tab (§4); the report carries the label only.
- **Číslo RP is what the card says.** The RP number is issued by PSMF at
  registration, is immutable, and **the user can never type or edit one**; a
  player the league has one for shows it, read-only. For a present player without
  one, the row asks for the number *from the player's card* (digits, four to
  seven). **That is not an RP number**: it is what was written in the column that
  day, it lives on the appearance, it is never written into league data, and the
  export says which of the two a row's number is. *Nemá průkaz* turns the same row
  to the date of birth, the form's printed fallback. Both are remembered on the
  device for the next match, typed once and editable. **No rodné číslo** anywhere,
  pending A28.
- **Adding a player is allowed** — a player turns up who is not yet in PSMF's
  database. First name, surname, then the number from the card or, with *Nemá
  průkaz*, the date of birth. No RP field is offered. Mark them as user-added so
  they can be reconciled once PSMF registers them.
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

The captain confirms the lineup before kickoff, per team. One tap on
*Potvrzuje* and the captain's name, by the captain marked above it: no list to
pick from and no deputy. A
tap, not a signature — parity with a pen mark nobody verifies (§3.2, pending A7).
A missing one is listed in the export and never blocks kickoff; a late one carries
its real time. It is a record of its own and is **not** the captain's final
confirmation under the finished report (§6).

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

**Who came is not here.** Presence is a fact about one match, not about a player.
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
  scorer** — the worked example contains one. The one-tap goal is written at the
  clock's minute. **A goal can also be written at its own minute** — one the
  referee forgot, during the match or after the final whistle (every goal after
  the whistle asks for its minute, as the clock has none to give). The minute is
  entered as on the card sheet, a number or `30´+`; **a minute the clock has not
  reached is refused**, in words, and no goal is written at `60´+`. The goal list,
  `Stav` and the half-time score follow the minute written; goals at one minute
  keep the order they were recorded in.
- **Cards:** mandatory free-text reason on every card. Red must record straight
  versus second yellow (`2. ŽK`).
- **Minute is not an integer** — `30´+` at half-time, `60´+` after the whistle
  before signing (for a card; a goal after the whistle is written at the minute it
  was scored).
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
- **A whistle can be taken back.** A mis-tapped kickoff, end of half, start of the
  next half or final whistle is taken back with Undo, which takes back the last
  thing recorded (a goal, a card or a whistle) and **asks first** when it is a
  whistle, because it changes the clock. Then the whistle can be pressed again.
  This is not a pause: nothing stops, resumes or adjusts anything, and nothing
  edits an instant. The clock is what the remaining instants say, and a red card's
  ten minutes of play follow it. **The log lists the whistles** — `0´ · Výkop`,
  `30´+ · Konec 1. poločasu`, `30´ · Začátek 2. poločasu`, `60´+ · Konec utkání` —
  each in its place among the goals and cards, in the quieter colour, so the
  referee can see what Undo walks back through. They stand at the form's minute,
  which is not always the order Undo takes them in.
- **The controls are laid out the same way in every state.** Under the clock, one
  row for what moves the match on, as wide as the lists below it: before the kickoff
  *Zahájit utkání* alone and yellow; in the first half *Ukončit poločas* and
  *Ukončit utkání* side by side, the same size; in the interval *Zahájit 2. poločas*
  and *Ukončit utkání*; in the second half *Ukončit utkání* alone, full width; after
  the final whistle *Pokračovat na hodnocení* alone and yellow. A label that does
  not fit wraps inside its button, at a space, and its neighbour is as tall.
  **Undo is on a line of its own under them**, at the left edge, as wide as its
  word, in the secondary grey with no outline: apart from the whistles and quieter
  than them, in the same place in every state.
- **The kit worn shows as a small shirt**, drawn in code, under each team's name on
  the scoreboard and before it on each team's tab (here and in §3). Two colours show
  as two slices and three as three; white and black kits carry an outline, so they
  show on the light theme and the dark one. The colours come from a table in the app
  that maps the names league 6 uses to colours. **A name not in the table draws
  nothing for that colour, and a kit with no known colour draws no shirt**: a wrong
  colour is worse than none, because the referee would trust it. The shirt's
  description is the kit's label. The console shows the kit only while the team
  still owns it under the label the lineup recorded. **The report never reads the
  colour**: `Barva dresů` stays the label as written.
- **The clock reads `mm:ss` of play**, the second half starting at `30:00`, and
  frozen at the final whistle. The minute the next event will be written with
  (`30´+` in first-half added time and in the interval, `60´+` after the whistle)
  stays visible on a line of its own. The log and the report keep the form's
  minutes: `12:40` is still `12´`.
- **Power-play:** 10 minutes **of play** after a dismissal before a replacement
  may come on — not shortened by a goal, unaffected by further dismissals, held
  through the interval, none after the final whistle. Its own lifecycle, on a
  clock with no stoppage during play.
- Keep: undo (last recorded first), tap-to-log against a tabbed player list, crash recovery, sent-off
  players visually disabled, left-handed mirroring.
- **Timer:** store the kickoff timestamp and derive elapsed time. Nothing ticks in
  the background — iOS cannot run a background timer at all.

### 5 · Assessment
No counterpart in golblok, and mandatory on the form.

Per team: `NH` best player by jersey number · `Čd` waiting time in minutes if the
team was not ready at kickoff · `Č` shirts properly numbered, yes/no · `B` uniform
kit colour, yes/no. `Č` and `B` feed directly into fines.

Then an optional **note per team**, for a complaint or a protest the captain asks
the referee to note — typed by the referee, who is the only recorder, never a
readiness problem — and the commentary, free prose, ~400 characters in the worked
example.

**This is the hardest usability problem in the product** and A6 decides how hard:
if the commentary must be complete before the captains sign, it has to be typed at
the pitch in the dark. Until A6 lands, build it editable at any point up to export.

### 6 · Recap & confirm
The half-time and final score **come from the goals** and are shown, not edited; a
line says a goal is corrected in the console, and the winner is derived from the
final score. Full event list. The assessment block, team notes included. An
explicit **"no cards were issued"** affirmation where applicable — the paper form
requires the boxes to be struck through, so an empty list is not the same as
"none". The affirmation can be taken back.

**Three taps, no typing.** The home captain, the away captain and the referee each
have one button naming who signs: the captain **marked in that team's lineup**, the
referee **from the header**. There is no name to type and no deputy. All three make
the report CONFIRMED. These are the *final* confirmations, a different record from
the one each captain gave under the lineup before kickoff (§3); the report shows
both of each captain's, each with its time (Prague time in the text and CSV).

**A change takes the signatures back.** After the final confirmations, a change to
anything the captains attest — the header, the lineups, the goals, the cards
(including the affirmation), the whistles — asks first, and withdraws all three;
the recap then asks again. The header and the lineups save as you type, so they say
so before the first edit instead. The assessment, the team notes and the commentary
stay editable until export and never touch a signature, as A6 decided.

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
| §5.1 | How does the lineup reach the referee? | **Mark the absentees**, on the referee's own phone, captain beside them. Nothing changes hands. *Turned round 2026-10-17, after the first iPhone test: mark who came (§3).* |
| A6 | Must the commentary be complete before the captains sign? | **No.** Editable until export. |
| A7 | Is a tap enough for captain confirmation? | **Yes.** |
| A15 | How often does a player arrive without their card? | **Rare** — the captain typically carries the whole set. Identifier stays editable but not prominent. *Since 2026-10-17 the row asks for the number from the card, and for the date of birth only after Nemá průkaz (§3).* |

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
there is no RP number to use. What a referee copies from a card is neither: it
belongs to the appearance (§3). See `DECISIONS.md`, 2026-08-29 and 2026-10-17.

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
