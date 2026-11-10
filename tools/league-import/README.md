# league-import

Reads **league 6** of Hanspaulská liga, and **1. liga of Ultraveteránská
liga** (for the PSMF demo), from psmf.cz into the app's bundled seed files.
Decided in `docs/DECISIONS.md`, 2026-10-07, "Scrape psmf.cz after all" — read
its conditions, they are requirements — and 2026-11-10. Internal testing only.

```
./gradlew :league-import:importLeague -PimportArgs="--competition hl6 --competition uvl1"            # a run
./gradlew :league-import:importLeague -PimportArgs="--competition hl6 --competition uvl1 --offline"  # cache only
./gradlew :league-import:jvmTest                                                                     # parsers, saved pages
```

**Name every competition the seed files hold**, in that order: `hl6`
(Hanspaulská liga 6, the default when none is named) and `uvl1`
(Ultraveteránská liga 1). The index is written from the run's groups
alone, so a run that left one out would take it out of the app, and the
importer refuses it. Competitions are in `Competition.kt`: where psmf.cz
files each, and the group id, name, report code and season the app gets
(`uvl1a`, "Ultraveteránská liga 1A", `UVL 1A`). A player only moves between
teams of one competition; a veteran is never taken for a league 6 player who
left, whatever the name.

## What it reads

| Page | For | Requests |
|---|---|---|
| `/hriste/` | every pitch: code, name, address | 1 |
| `/souteze/2026-hanspaulska-liga-podzim/6/` | the twelve group slugs | 1 |
| `…/6-k/` | the group's team slugs | 12 |
| `…/6-k/dresy/` | each team's kit labels, verbatim | 12 |
| `…/6-k/tymy/<slug>/` | fixtures, results, *Statistiky* (the squad), match details (lineups, cards) | 144 |
| `/souteze/2026-ultraveteranska-liga-podzim/1/`, then `1-a/`, its `dresy/` and its ten teams | the same, for one group | 13 |

**About 183 requests from an empty cache**; league 6 alone took 4 min 43 s
on 2026-10-07. No RP numbers, dates of birth or jersey numbers exist
anywhere on the site.

## Polite

Sequential; at least 1.2 s between requests; a user-agent naming the
project; every page cached in `cache/` (git-ignored) and never fetched
again, so a development re-run makes **zero** requests. Delete a cached page
to refresh it, or the directory to refresh everything. `cache/requests.log`
lists every request ever made. `robots.txt` disallows only `/cms/`.

## What it writes

`composeApp/src/commonMain/composeResources/files/leagues/`: `index.json`,
`venues.json`, `6a.json` … `6l.json`, through the app's own seed DTOs; and
`last-run.md` here, with counts and everything it **reported instead of
guessing** — unmatched or ambiguous names, the refs it had to give a
suffix, kit and fixture oddities, departed refs. It describes **that run**:
a re-run keeps every ref it finds, so its suffix list is empty after the
first; the 18 the first run decided are in the 2026-10-07 report.

Before writing a byte it loads its output through the app's own
`SeedLeagueCatalog`; a run that would not load writes nothing.

Every player's `discipline.asOf` is the day the **oldest page read** was
fetched — for a cached page, its file time — not the day of the run, so a
re-run from the cache does not make the yellow counts look fresher than
they are. Copy the cache with its times kept (`cp -p`), or the next run
will believe it. `-PimportArgs="--as-of 2026-10-07"` overrides it.

## The id rule

It reads the existing files first and **keeps every id whose ref it has
seen**, so a run after more rounds orphans nothing recorded against this
one. Refs, the same-name rule and the carry-over of departed refs are in
the seed README beside the files, and in `SeedFiles.kt`.

## When the site changes

`PagesTest` runs the parsers against pages saved on 2026-10-07
(`src/jvmTest/resources/psmf/`). If psmf.cz changes its markup, a run's
counts in `last-run.md` will look wrong — save the new page over the old
one, watch `PagesTest` fail, and fix the parser there.
