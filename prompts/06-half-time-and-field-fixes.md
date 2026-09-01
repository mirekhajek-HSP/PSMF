# Prompt — half-time, and five fixes from the first phone

**Where:** WSL container, `~/dev/psmf-app` · **Model:** Opus for Phase 1, Sonnet after

Four phases. Phase 1 is a domain change and is most of the work; the rest is UI.
One session, probably.

**Everything here came from the app being installed on a real phone and used.**
Nothing was found by a test. That is the point.

---

```
Six fixes, found by using the app on a physical phone for the first time. Phase 1
is a domain change and needs a gate of its own. Stop at every gate and report.

## Read first

  CLAUDE.md
  docs/DECISIONS.md             the five entries dated 2026-08-31, newest first
  docs/DEMO_SCOPE.md            §4 the live console, Settings, the Zápasy/Týmy tabs
  docs/LEAGUE_APP_ANALYSIS.md   §2.5 the ZoU field spec, §2.6 the match rules

The app works. Nine screens, four tabs, 364 shared tests, 151 UI tests, migrations
in place. Nothing below asks for a rewrite.

/golblok-ref is mounted READ-ONLY. Phases 2 is golblok being right and worth
studying — its console icons and their sizing. STILL DO NOT COPY CODE, and still
nothing that implies a stoppable clock.

## PHASE 1 — a match has halves

### The correction

There is no way to end the first half. One `Ukončit utkání` button, nothing
between kickoff and the final whistle.

This is a SPECIFICATION ERROR IN prompts/04, which said "THE CLOCK NEVER PAUSES …
no pause, stop, resume or adjust control on the screen or behind it, and a test
asserts their absence". That was aimed at golblok stopping its clock for injuries.
Written that hard it also forbade the legitimate control, and a session following
it faithfully could not have built one.

**The rule stated correctly:** the clock does not stop DURING PLAY. PSMF's `hrubý
čas` runs through injuries and stoppages and the referee adds time at the end.
**A half-time interval is not a pause during play.** 2 × 30 is two periods with a
break between them, and the break is not part of the sixty minutes.

### What to build

  - END OF PERIOD and START OF NEXT PERIOD actions on the console.
  - Instants, not a ticking clock. Today there is one stored `kickoffAt` and
    elapsed is *now minus kickoff*. Now there are more — period start and period
    end — all persisted, all derived from. NOTHING TICKS. iOS cannot run a
    background timer and the process can die at any moment.
  - During the interval the displayed match minute HOLDS at where the period
    ended. It is not counting; the break is not part of the match.
  - DRIVE IT FROM `periods`, already in the group file beside
    `halfLengthMinutes`. Two is universal in Hanspaulská liga; veteran and futsal
    competitions may differ. Do not hardcode two.

### Two things this makes simpler, and both are deletions

  - `30´+` STOPS BEING SOMETHING THE REFEREE SELECTS. An event logged while the
    first period is running past its 30th minute *is* `30´+` — derivable from the
    period state instead of remembered under pressure. Same for `60´+`. Keep the
    manual override for correcting an event after the fact; stop requiring it.
  - THE HALF-TIME SCORE STOPS BEING TYPED IN. It is the score at the moment the
    first period ended. Pre-fill the recap from the event log, leave it editable
    because a goal can be logged late, but never blank. `HALF_TIME_MISSING` as a
    readiness problem goes away.

### What must stay true

NO PAUSE, STOP, RESUME OR ADJUST CONTROL, and no golblok `Pause` / `Stop` /
`PlayArrow` icon anywhere near this screen. The existing test asserting their
absence stays; extend it rather than weaken it, so that "end of period" cannot be
mistaken for permission to add a pause button later.

### GATE 1
  - a full 2 × 30 match runs end to end on a device, with a half-time interval
  - the minute holds during the interval and resumes correctly after it
  - an event logged past the 30th minute of period one is `30´+` WITHOUT the
    referee choosing it
  - the recap's half-time score arrives pre-filled and correct
  - kill the app during the interval and resume with everything intact
  - the no-pause-control test still passes, extended
  - schema migrated, `verifyMigrations` green
  - :shared:allTests green, detekt green, committed

## PHASE 2 — the console's actions become icons

`Gól` and `Karta` are text in each player row. On the one screen where attention
is scarcest, outdoors, possibly gloved, text buys a small tap target in the widest
part of the layout. golblok uses icons and is right.

  - icons, sized for a cold thumb, not text
  - KEEP THE LOCALISED STRING AS `contentDescription`. Screen readers keep
    working, and so do the existing UI tests, which find these nodes by text.
  - whether the card control SPLITS into a yellow icon and a red icon — colour
    being the fastest discriminator football has, and it saves a step in the sheet
    — or stays one icon, IS YOURS TO DECIDE WITH THE PHONE IN HAND. Judge it on
    row width at 411dp and on mistap risk. Say which you chose and why.
  - the card sheet is unchanged: reason mandatory, straight vs `2. ŽK` for a red.

### GATE 2
  - logging a goal and a card is faster and the targets are bigger, on a device
  - every icon has a localised `contentDescription`
  - the existing console UI tests pass, adjusted only where they must be
  - committed

## PHASE 3 — filters sized to their data

Both tabs were built against twelve teams in one group and are knowingly
provisional. The deciding factor for each control is how many things are in it.

  League   ~60 in 8 leagues   TWO ROWS OF CHIPS, cascading — see below
  Venue    ~35                PICKER — new
  Team     ~900               TEXT FIELD — a picker at nine hundred rows is a
                              scroll, not a control

### The league filter is a cascade, and it is chips rather than menus

Today it is one flat `DropdownMenu` of groups. That is right for one group and
wrong for sixty.

Split it the way PSMF themselves present it on psmf.cz — `6. liga` and then
`A B C D E F G H I J K L`. That structure is not an invention; it is what
referees already read, and they say "šestka K" out loud.

  row 1   1 2 3 4 5 6 7 8          eight leagues, always shown
  row 2   A … L                    that league's groups, shown once one is picked

Group counts run 1, 2, 4, 6, 9, 12, 12, 14 — so neither row ever needs
scrolling. Use `FlowRow` and let the letters WRAP to a second line; wrapping
beats horizontal scrolling because nothing ends up hidden.

CHIPS, NOT DROPDOWNS. A menu costs a tap to open before the tap to choose, hides
the current state until opened, and gives smaller targets than chips — all three
are wrong for a cold thumb outdoors.

Two details decide whether a cascade is usable:

  - A LEAGUE ALONE IS A VALID FILTER. "Everything in the 6th league" is a real
    thing to want. Do not require a group.
  - CHANGING THE LEAGUE CLEARS THE GROUP, and with chips that is visible rather
    than a silent reset.

Do not gold-plate this. The league filter is for browsing; the referee's working
questions are "what am I refereeing" and "what is on at my pitch", which is where
the followed-team and venue paths earn their keep.

Venue is arguably the referee's most natural filter and is currently missing
entirely: a duty roster is *a pitch at a time*. `venues.json` still holds 7 codes
against a real ~35, so build the filter and let the data catch up.

And in Týmy:

  - THE TAB OPENS ON FOLLOWED TEAMS. Search adds more; browse-by-league is the
    secondary path. Rendering nine hundred rows flat is not a list anyone uses.
  - `Nesledovat` BECOMES A STAR, empty and filled, toggling in place. A button
    labelled with the verb for what will happen reads as a description of the
    current state and gets it backwards. Localised `contentDescription`, as above.

### GATE 3
  - filter by venue, by league+group, and by typed team name, on a device
  - the league cascade works with only one group shipped, and would work at sixty
  - picking a league without a group filters to the whole league
  - Týmy opens on followed teams and is usable with the browse list collapsed
  - the star toggles and reads correctly in all three languages
  - UI tests for the venue filter and the star
  - committed

## PHASE 4 — two carried-over fixes

### 4.1 The league rules panel translates

In Settings the rule labels — `Délka poločasu`, `Počet poločasů`,
`Hráčů na hřišti`, `Oslabení po vyloučení` — are hardcoded Czech constants in
`SettingsViewModel`, so they stay Czech in English and in Ukrainian.

That is the "always Czech" rule applied one layer too far, and it is the SECOND
time — the first cost us the language picker. State it narrowly:

> The ZoU is always Czech, because PSMF receive that document, and its labels are
> fixed strings in `ZouLabels` / `ZouWords`, never resources. EVERYTHING THE
> REFEREE READS ON SCREEN TRANSLATES, because the referee — and the captain
> looking over their shoulder — is who it is for.

Move them to `strings.xml` in all three languages. The values (30 min, 2, 5+1,
10 min) are numbers and do not translate.

**Then sweep for the same mistake elsewhere.** Any user-facing Czech string
literal outside `ZouLabels` / `ZouWords` is a bug of this class. `ZouLanguageTest`
must still pass unchanged.

### 4.2 The save flow asks for a folder once

`ACTION_CREATE_DOCUMENT` creates one document per launch, so three formats is
three chained system dialogs. Naming that constant was my specification error —
I named an API where I should have named an outcome.

Use `ACTION_OPEN_DOCUMENT_TREE`: ask once, take a PERSISTABLE URI PERMISSION,
write all three files into the chosen folder, and write silently there on every
later export. Offer a way to change the folder in Settings. iOS is unaffected.

Same three files, same names, same `ZouDocument.bytes()` encoder — the
byte-identical guarantee must survive unchanged, BOM included.

### GATE 4
  - the rules panel reads correctly in Czech, English and Ukrainian
  - no user-facing Czech literal survives outside ZouLabels / ZouWords
  - the export still comes out Czech with the app in en and uk
  - saving asks once, then never again, and the bytes still match the sent ones
  - committed

## Do not

- Scrape psmf.cz. Explicitly off the table — we are waiting for PSMF's own data.
- Add a backend, a network call, or Supabase. That waits for database access.
- Add a pause, stop or resume control. Half-time is not a pause.
- Rewrite a working screen.
- Copy code from /golblok-ref.
- Change the domain model beyond Phase 1 without telling me first.

## Report

The usual, plus: whether the split-card decision in Phase 2 went one icon or two,
and what on the device decided it. And say plainly if anything in Phase 1 turned
out to contradict how the referee actually keeps time — that rule came from a
document, not from watching anyone.
```
