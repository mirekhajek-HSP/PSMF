# League seed data

Everything the app knows about teams, players, fixtures and pitches. It is
**data, not code**: adding a group means dropping a file here and adding one
line to `index.json`. No Kotlin changes, ever. `SeedLeagueCatalogTest`
exists to keep that true.

```
index.json            the only filename the app knows
venues.json           pitch codes, LEAGUE-WIDE, with names and addresses
6a.json … 6l.json     league 6, one group each: teams, squads, fixtures
```

**These files are generated** from psmf.cz by `tools/league-import`
(DECISIONS 2026-10-07, "Scrape psmf.cz after all"), for internal testing.
Re-run it after more rounds; never regenerate by hand. A hand edit to a
name or a kit is overwritten by the next run; an id is never touched.

```
./gradlew :league-import:importLeague                       # fetches what is not cached
./gradlew :league-import:importLeague -PimportArgs=--offline # cache only, no requests
```

The run reads these files first and **keeps every id whose ref it has
seen**. Its own account of what it could not reconcile is
`tools/league-import/last-run.md`.

---

## THE ONE RULE THAT MATTERS

> ### A UUID in these files is permanent. Never regenerate one.

Every team, player and fixture carries an opaque `id`. **A match report
saved on a referee's phone stores those UUIDs.** So:

> **A regenerated UUID orphans every persisted match that referenced it.**

The importer **preserves existing ids by matching on the natural key** and
mints new ids only for genuinely new entities. Regenerating the files from
scratch is what a scraper does by default, and it is the failure this rule
guards against. A ref that leaves the site — a withdrawn team, a renamed
player — is **carried over unchanged and reported**, never dropped: a match
may already point at it.

The natural key is the **`ref`**. See `SeedIdentity.kt`, which is that rule
written as code, and its tests.

### So, when hand-editing

| Change | Safe? |
|---|---|
| Rename a team or a player | **Yes.** Change `name`, keep `ref` and `id`. |
| Fix a typo in a `ref` | **No.** A changed ref reads as a new entity to the importer. Fix the name instead and leave the ref stale — a stale ref is harmless, that is what it is for. |
| Move a player to another team | **Yes.** Move the block; keep their `id` and `ref`. Refs are deliberately *not* team-scoped so a transfer does not break identity. |
| Add someone | **Yes.** New `ref`, new random UUID. |
| Delete someone | Prefer not to. Their `id` may already be in a saved report. |
| Reorder anything | Yes, except `kits` — see below. |

---

## `id` and `ref`

Every entity has both, and they do different jobs.

- **`id`** — an opaque UUID. The real identity. Persisted matches reference
  it. Never regenerated, never reused for a different entity.
- **`ref`** — a readable slug, for hand-editing and debugging, and the key
  entities use to point at each other *inside* these files. It is allowed to
  go stale.

Files point at each other by `ref`, because 66 fixtures full of UUIDs would
be unmaintainable by hand. The app resolves refs to ids at load time.

Player refs are **not team-scoped** — `belohlavek-jan`, never
`krabice-01`. The analysis permits one transfer per season, and a
team-scoped ref would change on transfer, mint a new UUID, and orphan every
match the player already appeared in.

### The refs the importer writes

| Entity | Ref | Why |
|---|---|---|
| Team | PSMF's own slug, `krabice` | It is in every psmf.cz URL for the team. Unique within a group. |
| Fixture | `6k-krabice-vs-hustec` | Upcoming fixtures carry no PSMF id. A pairing meets once a half-season, so the pair is the natural key, and it survives the reschedules PSMF actually makes. |
| Player | the name, `belohlavek-jan` | Not team-scoped, league-wide; see above. |
| Kit | none; matched by label within its team | A relabelled kit gets a new id. Harmless: a lineup snapshots the label. |

### Two players with the same name

They happen. On 2026-10-07, 18 players in league 6 shared a name with
someone imported before them, and two of those names were shared *within*
one team: two Tomáš Hrubý at Sváteční mančaft, two Miroslav Láník at
Hattrick Prosek FC. **The rule:** the first in import order — groups `a` to `l`,
teams by slug, squads in *Statistiky* order — keeps the plain slug; every
later one gets `-<team slug>` appended (`kasparek-jakub-krabice`), and
`-<group>-<team slug>` if that is taken too. A re-run never re-decides: it
finds an existing player by group, team and name first and keeps the ref
they have. A transfer is recognised only when exactly one player of that
name has left the site where they were.

Cards and lineups are matched to a squad by name, and a name that fits two
rows of one team cannot be attributed: those are reported, never guessed.

---

## Fields that are easy to get wrong

### `kits` — a team owns two, and the match records which was worn

A team does not have "a kit colour". It owns two sets and picks one per
match so the two sides are not in similar colours. That is exactly why
`Barva dresů` sits on the lineup block of the ZoU and is filled in at the
match.

```json
"kits": [
  { "id": "...", "label": "modrá",      "colours": ["modrá"] },
  { "id": "...", "label": "bílo-modrá", "colours": ["bílá", "modrá"] }
]
```

**Order is meaningful: the first is the primary**, and is what a lineup
defaults to. psmf.cz's `/dresy/` lists a team's kits comma-separated, and
**some teams list only one** — 55 of 144 on 2026-10-07. Such a team has one
kit here. Nothing is invented to make two.

Both fields are needed. `label` is **verbatim from PSMF and authoritative
for the report** — it is what gets written, and it is never derived.
`colours` is for the app only, for team chips and clash hints. You cannot
build one from the other: `bílo-modrá` is not mechanically obtainable from
`["bílá", "modrá"]`, because the first element takes a different
grammatical suffix in Czech.

A blank `label` fails the build. The report cannot be generated without it.

### Player identification — three fields, and only one of them is PSMF's

```json
"rpNumber": null,
"dateOfBirth": "1992-05-18",
"birthNumber": null
```

**A league player may have none of the three** (DECISIONS 2026-10-07): every
player read from psmf.cz has none. The referee then types the date of birth
in the lineup row, the app writes it YYMMDD, and remembers it on the device
for next time. Something must still be written in Číslo RP before a lineup
can be confirmed -- that rule lives on the appearance now, not here. A
player with `"origin": "ADDED_AT_PITCH"` must have a `dateOfBirth`.

- `rpNumber` is **issued by PSMF** and immutable. It arrives from their
  database. **It must never be typed by a user** — not in the app, and not
  into this file except from real PSMF data. Every player has `null`,
  because RP numbers are the one roster dependency that cannot be met from
  public data (analysis §2.9, blocked on A2).
- `dateOfBirth` is the fallback the form itself prescribes: *"U hráčů,
  kteří nemají k dispozici svůj registrační průkaz (RP), uvedou místo čísla
  RP jejich datum narození."*
- `birthNumber` exists **only** because A28 is unresolved. Leave it null.

What actually gets written in the `Číslo RP` column is a **per-match fact**
and is stored on the appearance, not here. A player who later gains an RP
number must not retroactively change an old report.

### `discipline` — advisory, never authoritative

```json
"discipline": { "yellowsThisSeason": 3, "asOf": "2026-10-05" }
```

`asOf` is **not optional**. A count without a date cannot be reasoned
about: matches played since are not in it.

> **The app must never claim a player is eligible.** It may warn that one
> might not be. Absence of a warning is not clearance.

Fielding an ineligible player is a technical forfeit. If the app showed
"clear" and the player was banned, the app caused that. Red cards are
deliberately not modelled here at all — a red carries suspension until STDK
decides, with no fixed ban, so there is nothing to count.

The importer counts yellows from psmf.cz's match details, with the app's own
rule (`yellowsAccumulatedBy`): two in one match count zero, a yellow and a
straight red count one. `asOf` is the date the data was fetched. **The
details lag the results**: on 2026-10-07, 315 matches had a result and 262
had details, so cards from the other 53 are not in any count yet. The site
marks a second yellow as two yellow cards and a red, with nothing to tell
that red from a straight one.

### `venues.json` — league-wide

Pitch codes are shared across the whole league. They are **not** duplicated
into group files, which would guarantee they drift. Every pitch psmf.cz's
`/hriste/` lists is here — 43 on 2026-10-07 — with its `name` and the first
line of its description as `address`.

A fixture referring to a code that is not in this file fails the build.

### `periods` and `halfLengthMinutes`

```json
"halfLengthMinutes": 30,
"periods": 2
```

2 × 30 everywhere in Hanspaulská liga as far as anyone knows, but veteran
and futsal competitions may differ, so both are data. **The league sets
them. A referee changing the half length is a defect**, so nothing in the
UI may edit them.

---

## The placeholder data, retired

Until 0.2.0 this directory held one invented group, `6k.json` — Kominíci,
`ruzicka-radek` and 142 other made-up players with made-up dates of birth.
Real 6-K replaced it. The set lives on, frozen, as a test fixture in
`shared/src/jvmTest/resources/placeholder-league/`, because **tests must not
depend on scraped data**, which changes every run.

Its ids are **retired, never reused**: the importer did not read it, and
mints every id fresh. A match saved against it on a phone stays in the
database untouched and is simply not offered anywhere — its fixture is not
in league data — which `PlaceholderMatchAfterTheUpgradeTest` proves.

One thing it got wrong that real data shows: kickoffs are not only 19:00 to
20:45 on weekdays. League 6 kicks off from 17:30 on weekday evenings and
from 10:00 on Sundays (133 of its 792 fixtures), still on 15-minute steps.
