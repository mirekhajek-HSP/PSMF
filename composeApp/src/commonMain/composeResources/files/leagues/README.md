# League seed data

Everything the app knows about teams, players, fixtures and pitches. It is
**data, not code**: adding a group means dropping a file here and adding one
line to `index.json`. No Kotlin changes, ever. `SeedLeagueCatalogTest`
exists to keep that true.

```
index.json    the only filename the app knows
venues.json   pitch codes, LEAGUE-WIDE
6k.json       one group: teams, squads, fixtures
```

---

## THE ONE RULE THAT MATTERS

> ### A UUID in these files is permanent. Never regenerate one.

Every team, player and fixture carries an opaque `id`. **A match report
saved on a referee's phone stores those UUIDs.** So:

> **A regenerated UUID orphans every persisted match that referenced it.**

When real psmf.cz data replaces this placeholder set, the importer must
**preserve existing ids by matching on the natural key** and mint new ids
only for genuinely new entities. Regenerating the file from scratch is what
a scraper does by default, and it is the failure this rule guards against.

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

Player refs are **not team-scoped** — `ruzicka-radek`, never
`kominici-01`. The analysis permits one transfer per season, and a
team-scoped ref would change on transfer, mint a new UUID, and orphan every
match the player already appeared in.

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
defaults to.

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

**At least one must be present.** A player who cannot be identified at all
cannot be put on a report, and the model refuses to build one.

- `rpNumber` is **issued by PSMF** and immutable. It arrives from their
  database. **It must never be typed by a user** — not in the app, and not
  into this file except from real PSMF data. All 144 placeholder players
  have `null`, because RP numbers are the one roster dependency that cannot
  be met from public data (analysis §2.9, blocked on A2).
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

### `venues.json` — league-wide

Pitch codes are shared across the whole league; the analysis lists roughly
35 across Prague (§2.2). They are **not** duplicated into group files,
which would guarantee they drift.

Only the codes the analysis actually names are here. `name` is omitted
rather than invented — PSMF publishes the codes, and the long names are not
in the analysis.

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

## The placeholder data

`6k.json` is invented. The team names, player names, dates of birth and
yellow-card counts are all made up; the *shape* is real, and the fixture
list matches what analysis §2.3 says a group looks like — 12 teams, 11
rounds, 66 matches, kickoffs 19:00–20:45 on 15-minute steps.

Player names are kept stable across edits on purpose: they are throwaway,
but a stable set keeps diffs readable.
