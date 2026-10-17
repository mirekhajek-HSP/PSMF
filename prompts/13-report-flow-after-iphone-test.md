# Prompt — The report after the first iPhone test

**Where:** the WSL container · **Model:** Opus
**Follows:** `prompts/12`, and build 0.2.1 (3) on TestFlight (`prompts/11`).
The tester used it on an iPhone and wrote down eighteen points. **None of
them has been checked against Android.** Finding which of them Android shares
is part of this session's job.
**Builds:** 0.3.0 (4), both platforms.

The decisions are made: `docs/DECISIONS.md`, the four entries dated
2026-10-17. Read them first. This prompt is the order of work.

---

## The tester's points

Verbatim, then where each one goes. A nineteenth came from reading the code:
the captain's tap on the lineup is the same record as their final signature.

| # | Tester (cs) | Where |
|---|---|---|
| 1 | Nemizí klávesnice – všude | Part 1 |
| 2 | Hlavička zápisu – chybí tlačítko zpět | Part 1 |
| 3 | Sestavy – chybí brankář | Part 2 |
| 4 | Pořadí jako ve fyzickém zápisu: 1. kapitán, pak brankář, pak ostatní | Part 2 |
| 5 | Datum narození se do zápisu nevyplňuje, ale číslo RP nebo RČ hráče | Part 2: RP yes, RČ no |
| 6 | Otočit označování: klikat na hráče, kteří jsou přítomní | Part 2 |
| 7 | Potvrzení kapitána – přidat kód kapitána? | **Not built**: DECISIONS, "A forged report" |
| 8 | Přidat hráče mimo soupisku – datum narození → číslo RP | Part 2 |
| 9 | Potvrzení kapitána – zástupce kapitána je k ničemu | Part 2: the switch goes |
| 10 | Potvrzení kapitána – jen místo na kód, kapitán první v sestavě | Part 2, without the code |
| 11 | Zápis o utkání – chybí ruční zadání času gólu | Part 3 |
| 12 | Nelze zrušit/restartovat zahájení utkání nebo poločasu | Part 3 |
| 13 | Čas celý, tj. MM:SS | Part 3: the clock only, not the report |
| 14 | Hodnocení – poznámka k jednotlivým týmům (stížnosti apod.) | Part 4 |
| 15 | Rekapitulace – výsledek by měl být needitovatelný | Part 4 |
| 16 | Osobní tresty – když se potvrdí, už nejde vzít zpět | Part 4 |
| 17 | Podpisy – místo jména rozhodčího kód | **Not built** |
| 18 | Podpisy – převzít jméno rozhodčího z hlavičky | Part 4 |

---

```
Fix what the tester found on the iPhone, on both platforms, in common code.
Five parts. STOP AND REPORT after each; one commit per gate, on main.

## Read first

  CLAUDE.md
  docs/DECISIONS.md   the four 2026-10-17 entries ARE THE SPEC. Also
                      2026-10-07 "A league player may arrive with no
                      identification" and "Two calls inside the card fix";
                      2026-08-31 "A half-time exists"; 2026-08-29 "Kit sets
                      and player identification"
  docs/LEAGUE_APP_ANALYSIS.md   §2.5 (lineups, goals, signatures), §5.1, §5.5
  docs/DEMO_SCOPE.md  §3 Lineup, §4 Live console, §6 Recap
  shared/src/commonMain/sqldelight/README.md   before any schema change
  prompts/README.md   "name the outcome, not the API"

Baseline first: ./gradlew build :shared:allTests detekt green at HEAD, the
four test counts recorded. If not green, STOP.

## The iPhone, from here

You cannot build or run iOS in this container. Everything here goes in
commonMain, which reaches both platforms. Where iOS behaves differently,
the fix still lives in common code; say in the report what only an iPhone
can confirm. If you conclude that something needs iosMain or iosApp, do not
write it blind. Describe it for the Mac session.

What differs, and matters here:
  - iOS has no system back button. An edge swipe may or may not reach
    BackHandler; do not rely on it.
  - The iOS number pad has no return key, so an IME action never fires from
    a number field.
  - The Android emulator is your stand-in for both. For each of the
    eighteen points, record whether HEAD on Android shows it too.

## What is already known (from reading the code, 2026-10-17)

  - Nothing clears focus anywhere: no LocalFocusManager, no clearFocus.
    Three fields set an ImeAction.
  - The report's first step is the root of the report tab's stack, so
    canGoBackWithinTab is false and AppShell draws no Zpět. On Android the
    system back goes to Zápasy; on iOS only the tab bar leaves. A resumed
    report's stack is [Console] alone, so back leaves the report instead of
    going to the lineup.
  - Appearance has no role. BuildZouReport sorts rows by jersey number. The
    captain is picked inside the confirmation dialog, with a deputy switch.
  - THE LINEUP'S CAPTAIN TAP AND THE RECAP'S CAPTAIN SIGNATURE ARE ONE
    RECORD. LineupViewModel.confirm and ConfirmReport both write
    Confirmation(party = …_CAPTAIN). After the lineup, the recap shows both
    captains as confirmed, and a report reaches CONFIRMED with neither
    captain having seen it finished.
  - The date-of-birth field appears only for a league player with no
    identification. AddPlayerDialog takes surname, first name and date of
    birth. ZouAppearance.identification is a bare string, so JSON and CSV
    cannot tell an RP number from a date of birth.
  - LogGoal takes the clock's minute. rescored() computes Stav in list
    order, and the export lists goals in list order, which is recording
    order. scoreFromGoals() and scoreAtEndOfFirstPeriod() sort by minute.
    Once a goal can carry an earlier minute, Stav and the goal list are
    wrong unless that changes.
  - UndoLastEvent removes the highest sequence among goals and cards.
    Kickoff, the period boundaries and the final whistle are not events and
    cannot be taken back. StartMatch ignores a second press.
  - Scoreboard shows minutes only, on purpose (its KDoc). The power play
    already shows m:ss.
  - Recap: the scores are text fields (ResultDraft, "entered rather than
    derived"). The no-cards affirmation cannot be withdrawn. The dialog asks
    for a typed name for all three parties, the referee included, whose
    name is already in the header.
  - Assessment: no per-team note.

## PART 1 — the keyboard, and the way back

OUTCOME, KEYBOARD. On every screen the keyboard goes away when the referee
is done with it: tapping outside a field, starting to scroll, the keyboard's
own action key where it has one, and leaving the screen or the step. On a
form, Next moves to the next field and the last field says Done. The
focused field and the step's main button are never hidden behind the
keyboard.
  - A number field must be dismissable without a return key, because iOS
    does not give it one.
  - List every text field in the app in the report, with what dismisses its
    keyboard. None missing.

OUTCOME, BACK. Every step of the report shows Zpět. From the first step it
goes to Zápasy. After a report is resumed, Zpět walks the wizard backwards
(console, lineup, header, Zápasy) instead of leaving the report. Android's
system back does what the button does. The roots of Zápasy, Týmy and
Nastavení stay without Zpět.
  - Check every other screen for a dead end like the header's, and list what
    you found.

### GATE 1
  - a test that fails on HEAD for each: a tap outside clears focus; the
    header shows Zpět and it lands on Zápasy; a resumed report goes back
    from the console to the lineup
  - green; commit

## PART 2 — the lineup (schema 7)

Record schema 6 before editing any .sq file, in the README's sequence.

1. MARK WHO CAME. Nobody is present until tapped. Tapping a player marks
   them present and opens their row (jersey, Číslo RP); tapping again takes
   it back. The count says how many are marked. It stays usable with a
   squad of 25, and rows do not jump under the thumb while the referee is
   marking.

2. CAPTAIN AND GOALKEEPER. Per team, one captain and one goalkeeper, both
   among the players marked present, possibly the same person. Both are
   needed to continue, named in the problems list as today's problems are.
   The report lists the captain first, then the goalkeeper, then the rest in
   jersey order. TXT, CSV and JSON each say unambiguously who is captain and
   who is goalkeeper, including when one person is both. We have not seen
   how the paper form marks them, so choose how and report it.

3. ČÍSLO RP, AS WRITTEN ON THE CARD. For a present player with no league RP
   number, the row asks for the number from the card. "Nemá průkaz"
   switches the same row to the date of birth, which is the form's printed
   rule and today's field. A league RP number, where the data has one, is
   shown and never editable, as now.
   NON-NEGOTIABLE:
     - A typed number lives on the appearance as what was written that day.
       It never becomes the player's RpNumber, and is never written into
       league data or the jersey override table. Nothing anywhere presents
       it as PSMF's record.
     - No rodné číslo anywhere: not as a field, a hint or a label.
   The typed number is remembered on the device for the next match, as the
   date of birth is (DECISIONS 2026-10-07): typed once, offered next time,
   editable. JSON and CSV say which kind each value is: RP from the league,
   RP as typed, or date of birth. The TXT keeps the form's single column.
   Validation: digits only, and a length you choose and justify.
   ADDING A PLAYER NOT ON THE LIST takes surname, first name, and either the
   number from the card or, with no card, the date of birth.

4. THE CONFIRMATION BEFORE KICKOFF is one tap by the marked captain,
   "Potvrzuje <name>", with no name picking and no deputy switch. It is
   recorded as the pre-match confirmation and nothing else: it must not
   count as the captain's final signature (Part 4). A missing pre-match
   confirmation is a problem the export lists, not a block on kickoff. A
   late one is allowed and carries its real time.

MIGRATION, 6 → 7. Build a 0.2.1 database with three matches: one in
progress, one finished and unconfirmed, one CONFIRMED. All three open after
the upgrade.
  - The in-progress match keeps every player it had as present. The switch
    from marking absence to marking presence silently removes nobody.
  - Old lineups have no captain or goalkeeper and must not be given one.
  - Old confirmations keep meaning what they meant. Choose how, and say why.
  - The CONFIRMED match's export says what it said before, apart from new
    fields that are empty.

Tests must not read the shipped league files.

### GATE 2
  - the migration test, against the recorded 6.db
  - a test that fails on HEAD: after the lineup's captain tap, the recap
    still asks that captain to confirm
  - renders of the lineup on a narrow phone, cs and uk: a squad of 20 with
    8 marked, the captain and goalkeeper marked, a typed RP number, and a
    "no card" row with a date of birth
  - green; commit

## PART 3 — the console

1. A GOAL WITH ITS OWN MINUTE.
   - The one-tap goal stays one tap, at the clock's minute.
   - A goal can also be written with a different minute, for one the
     referee forgot, during the match or after the final whistle. Enter the
     minute the way the card sheet does.
   - A minute later than the clock is refused or clamped: your call,
     reported.
   - No goal at 60´+.
   - The goal list in every format, Stav and the half-time score follow the
     minute written; ties keep recording order.
   - Undo still takes back the last thing recorded.

2. TAKING BACK A WHISTLE. A mis-tapped kickoff, end of half, start of the
   next half or final whistle can be taken back, then pressed again.
   Taking it back in recording order, along with everything else, is the
   obvious mechanism; the choice is yours. Taking back a whistle asks first,
   because it changes the clock.
   NON-NEGOTIABLE: no pause, stop, resume or adjust. Nothing edits an
   instant. Taking a boundary back leaves a clock derived from the instants
   that remain, and the test that asserts there are no pause controls
   stays.
   Power plays follow: a red card's ten minutes of play are recomputed from
   what is left.

3. MINUTES AND SECONDS ON THE CLOCK. The console clock reads mm:ss of play,
   with the second half starting at 30:00. The minute the next event would
   be written with stays visible, especially in first-half added time (30´+)
   and in the interval. The log and the report keep the form's minutes. Do
   not change which minute a moment maps to (12:40 is 12´, today's rule).
   Say in the report what the screen shows at each boundary.

### GATE 3
  - tests that fail on HEAD:
    - a goal written at 12´ after one at 20´ lists first, and Stav is right
    - a goal written in the second half with a first-half minute counts
      for the half-time score
    - a mis-tapped kickoff can be taken back and pressed again
    - taking back the end of the first half leaves the clock continuous
  - renders of the console mid-second-half and at 30´+
  - green; commit

## PART 4 — assessment and recap

1. A NOTE PER TEAM in Hodnocení, optional, for a complaint or a protest the
   captain asks the referee to note. The referee types it, because the
   referee is the only recorder. It is exported in the commentary block,
   labelled per team in fixed Czech, and as fields in JSON and CSV. It is
   never a readiness problem.

2. THE RESULT COMES FROM THE GOALS. The half-time and final score on the
   recap are shown, not edited, and a line says corrections are made in the
   console. ScoreDisagreesWithGoals can no longer happen; remove what
   becomes dead. For an old report whose typed result disagrees with its
   goals, a CONFIRMED one keeps what it says. For the rest, your call,
   reported.

3. SIGNATURES. Three taps (home captain, away captain, referee), each
   showing the name it will sign with: the captain marked in the lineup,
   and the referee from the header. No typing and no deputy switch. These
   are the final confirmations, separate from Part 2's, and CONFIRMED needs
   all three. The report shows both of each captain's confirmations, each
   with its time.

4. TAKING THINGS BACK.
   - The no-cards affirmation can be withdrawn.
   - After the final confirmations, a change to anything the captains
     attest (the header, the lineups, the goals, the cards including that
     affirmation, the whistles) withdraws them. The referee is told before
     the change is saved, and the recap then asks for the confirmations
     again.
   - The assessment, the commentary and the team notes stay editable until
     export without touching any confirmation (A6).

NOT BUILT: a code or PIN for the captain or the referee (DECISIONS
2026-10-17, "A forged report"). If you see a cheap way to make a duplicate
report visible, describe it in the report; do not build it.

### GATE 4
  - tests that fail on HEAD:
    - the result cannot be edited
    - the referee signs under the header's name without typing
    - a card after the final confirmations withdraws them
    - the no-cards affirmation can be withdrawn
  - renders of the recap, cs and uk
  - green; commit

## PART 5 — documents, version, build

  - CLAUDE.md, "Domain rules that are easy to get wrong": restate the RP
    rule as decided. Proposed wording: "An RpNumber is issued by PSMF,
    immutable, and never user-editable. What a referee types from a
    player's card is not an RpNumber: it is what was written in Číslo RP
    that day, it lives on the appearance, and it never reaches the player or
    league data. A date of birth is the fallback for a player with no card.
    No rodné číslo until A28." Change it only where the code says otherwise,
    and say so.
  - DEMO_SCOPE:
    - §3: mark who came; the captain and goalkeeper; Číslo RP.
    - §4: a goal's own minute; taking back a whistle; mm:ss.
    - §6: the result from the goals; two captain confirmations; names from
      the lineup and the header.
  - 0.3.0, build 4, both platforms: versionName/versionCode, and
    MARKETING_VERSION/CURRENT_PROJECT_VERSION in Debug and Release. Those
    values only in the Xcode project; never DEVELOPMENT_TEAM.
  - ./gradlew :androidApp:clean :androidApp:assembleDebug; report the size
    against 0.2.1's 70,047,639 bytes. It is already over 70 MB; do not fix
    that here.

### GATE 5
  - green; commit

## Do not

  - Add a dependency.
  - Change the seed files or the importer.
  - Touch iosMain or iosApp beyond the version numbers.
  - Add network access, a backend, accounts, or a code or PIN.
  - Add rodné číslo anywhere.
  - Let a typed number reach the player or league data.
  - Add a pause, stop, resume or adjust to the clock.
  - Push. The container cannot.

## Report

reports/2026-10-17-report-flow-after-iphone-test.md, committed. Open with one
sentence: what changed for a referee filling in a report. Then:
  - a table of the tester's eighteen points: whether HEAD on Android showed
    it, and what happened to it
  - the judgement calls
  - every text field, with what dismisses its keyboard

End with two numbered checklists. Give each step what should happen and what
failure looks like.
  - "On the Android phone": install over 0.2.1 without uninstalling, with a
    match in progress recorded in 0.2.1; then each of the eighteen points,
    the migration, and the signatures.
  - "On the iPhone", for the next Mac prompt: what only iOS can show. At
    least the number pad without a return key, the way back without a
    system button, the edge swipe, the keyboard over the lineup's lower
    rows, and the upgrade from 0.2.1 (3).
```
