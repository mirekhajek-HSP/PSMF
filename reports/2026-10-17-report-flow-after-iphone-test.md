# The report after the first iPhone test — build 0.3.0 (4)

A referee filling in a report now marks who came rather than who stayed away,
names a captain and a goalkeeper, copies each player's number from the card,
writes a forgotten goal at its own minute, takes back a mis-tapped whistle, reads
the clock in minutes and seconds, and signs the finished report with three taps
under names already on it — and the keyboard goes away, and Zpět is there, on
every step.

**Where things are.** The tester's eighteen points and what became of each: §2.
The judgement calls: at the end of each gate's section (§3 to §7). Every text
field and what dismisses its keyboard: §3.1. The two checklists: §8 (Android) and
§9 (iPhone).

**Session date:** 2026-10-17 to 2026-10-18
**Brief:** `prompts/13-report-flow-after-iphone-test.md`
**Repository:** `~/dev/psmf-app` (WSL Ubuntu), branch `main`
**Commits:** Gate 1 `33da06f` · Gate 2 `0c8263d` · Gate 3 `8581f7f` · Gate 4
`e23f070` · Gate 5 is the commit this report is in. Baseline `bc6ee00`.
**Where it ran:** the Claude desktop app on the Windows host, driving the
`psmf-sandbox` image through the long-lived `psmf-work` container
(`docker exec … ./gradlew`); the Android emulator (*Medium_Phone*, Android
16, x86_64) on the Windows host for the HEAD observations and renders.
Commits made from WSL with the owner's git identity. Nothing pushed.

---

## 1 · Baseline

`./gradlew build :shared:allTests detekt` at `bc6ee00`: **BUILD SUCCESSFUL**.
Counted from the JUnit XML:

| Suite | Tests | Failures |
|---|---|---|
| `:shared:jvmTest` | 454 | 0 |
| `:shared:testAndroidHostTest` | 401 | 0 |
| `:composeApp:jvmTest` | 210 | 0 |
| `:league-import:jvmTest` | 23 | 0 |

The same four numbers as the report of prompt 12 ends on.

---

## 2 · The tester's points, and whether HEAD on Android shows them

HEAD is 0.2.1 (3) (`androidApp-debug.apk`, 70,047,639 bytes, byte-for-byte the
size the last report records). **Observed** means it was done on the emulator
before any change; **code** means it is a property of common code that the
brief already describes and that Android and iOS share, and it was read, not
walked. The emulator was not taken through the console, assessment and recap
at HEAD: a legal lineup for two teams takes dozens of taps to set up by hand
(everyone starts present, none has an identification), and it was not worth
that when the code says what the screens do.

| # | Tester | On Android at HEAD | What happened to it |
|---|---|---|---|
| 1 | Keyboard does not go away, anywhere | **Observed, yes.** Opened by the referee field, still shown (`mInputShown=true`) after a tap on the page and after a scroll | **Fixed, Gate 1** |
| 2 | Header: no back button | **Observed, yes** (and Android has the system back, so only the button was missing) | **Fixed, Gate 1**, and so was a resumed report's way back |
| 3 | No goalkeeper | **Observed**: no role anywhere in the lineup | **Fixed, Gate 2**: a goalkeeper per team, picked from those present |
| 4 | Order as on paper: captain, goalkeeper, the rest | Observed for the lineup (squad order); code for the report (jersey order) | **Fixed, Gate 2**, in the report and its three formats; the lineup screen keeps the squad's order and names the roles under the name |
| 5 | The column takes the RP number or RČ, not a date of birth | **Observed**: the row asks *Datum narození*, and the line above it shows *Číslo RP: 920518*, which is the date of birth as YYMMDD. That is why it read as a date | **Fixed, Gate 2**: the row asks for the number from the card; the date of birth only after *Nemá průkaz*. **Not done: the rodné číslo** (the brief forbids it anywhere) |
| 6 | Mark who is present | **Observed**: everyone starts present, *Klepnutím na jméno označte, kdo nepřišel* | **Fixed, Gate 2**: nobody is present until tapped |
| 7 | A captain's code | not built (DECISIONS, *A forged report*) | not built |
| 8 | Add a player: the number, not the date of birth | **Observed**: the dialog is surname, first name, date of birth | **Fixed, Gate 2**: surname, first name, then the card number or, with a switch, the date of birth |
| 9 | The deputy switch is no use | **Observed**: *Zástupce kapitána* in the captain's dialog | **Fixed, Gate 2**: gone from the lineup's confirmation. **Gate 4**: gone from the recap's signatures too |
| 10 | Only a place for the code; the captain first | **Observed**: the dialog asks who is confirming, from a list | **Fixed, Gate 2**: one button, *Potvrzuje <jméno>*, for the captain marked above it. (The "code" is not built — see 7) |
| 11 | No manual goal time | code (`LogGoal` takes the clock's minute; the screen has one goal button per player) | **Fixed, Gate 3**: *Gól s jinou minutou*, and every goal after the final whistle asks for its minute |
| 12 | A kickoff or half cannot be undone | code (`UndoLastEvent` takes back goals and cards only; the test of it fails at HEAD, §5) | **Fixed, Gate 3**: Undo takes back a whistle too, in recording order, after asking |
| 13 | Time as MM:SS | code | **Fixed, Gate 3**: `mm:ss` of play, and the minute that will be written stays on its own line |
| 14 | A note per team | code (`TeamAssessment` had none; the test of it fails at HEAD, §6) | **Fixed, Gate 4**: a note per team in *Hodnocení*, in the commentary block of the report and in a field of the JSON and a row of the CSV |
| 15 | The result should not be editable | code (the recap had four score fields; the test of it fails at HEAD, §6) | **Fixed, Gate 4**: the result is the goals', shown, and a line says where a goal is corrected |
| 16 | A confirmed card cannot be taken back | code (the *Žádné karty nebyly uděleny* button had no way back; the test of it fails at HEAD, §6) | **Fixed, Gate 4**: *Vzít zpět*, and a card, a goal or a whistle written after the signatures takes the signatures back, after asking |
| 17 | The referee's code | not built | not built |
| 18 | Take the referee's name from the header | code (the recap asked for a typed name; the test of it fails at HEAD, §6) | **Fixed, Gate 4**: one tap, *Potvrzuje <jméno z hlavičky>*; the captains the same, under the captain marked in their lineup |

**In sum.** Sixteen of the eighteen are built; the other two (7 and 17, a code or
PIN) are not built, as the brief says. **Android shares all sixteen**: eight were
seen on the emulator at HEAD (1, 2, 3, 5, 6, 8, 9, 10), one was seen in part and
read in part (4), and seven are properties of common code, which both platforms
run (11 to 16 and 18). None turned out to be iPhone-only. What the iPhone adds to
1 and 2 (no return key on the number pad, no system back button) is common code's
to fix and only an iPhone's to confirm: §9.

---

## 3 · Gate 1 — the keyboard and the way back

### What changed for a referee

- **The keyboard goes when they are done with it**: a tap anywhere that is
  not a text field (a button, a chip or a switch included), the start of a
  scroll, leaving the step or the tab by any route, or the keyboard's own
  action key.
- **The last field of a form says Done** and drops the keyboard; the ones
  before it say Next and move on. A field edited by itself says Done.
- **The screen is padded by the keyboard** and the tab bar steps aside, so the
  field being typed in and the button below it are above the keyboard.
- **Zpět is on every step of a report**, the header included, and goes to
  Zápasy from the first. A report opened at the console or the recap goes
  back through the lineup and the header; before, it left the report.

### How it was proved

Tests first, against HEAD: of the 46 tests in the three classes touched
(`KeyboardTest`, `AppShellTest`, `AppNavigatorTest`), **15 failed there, all by
assertion**. One of the 15, `nextMovesOnToTheNextFieldOfTheForm`, failed for the
test host and not for HEAD — a switch between the header's two name fields
takes focus in desktop traversal — and was rewritten against the add-player
form, where it is a guard. The messages that matter:

| Test | Failure at HEAD |
|---|---|
| `tappingOutsideAFieldTakesTheFocusAwayFromIt` | `Failed to assert the following: (Focused = 'false')` — the referee field, `Focused = 'true'` |
| `startingToScrollTakesTheFocusAway` | `Did not expect any node but found '1' node that satisfies: (Focused = 'true')` |
| `leavingTheStepTakesTheKeyboardAway` | `the keyboard stayed up over the next screen` |
| `theLastFieldOfTheHeaderSaysDoneAndLetsGo` | focus had moved on to the next switch: `Role = 'Switch'`, `Focused = 'true'` |
| `theFirstStepOfAReportOffersBack…` | `could not find any node that satisfies: (… contains 'Zpět')` |
| `aReportOpenedAtTheConsoleGoesBackThroughTheLineupAndTheHeader` | `expected:<Lineup(…)> but was:<Fixtures>` |
| `everyTextFieldInTheAppGoesThroughTheOneThatKnowsTheKeyboard` | a map of nine files with raw fields against the two allowed |

Three tests were **guards that pass at HEAD and must go on passing** (tapping
the focused field again leaves it focused; tapping another field moves focus
to it; Next walks the add-player form). Two of them were broken on purpose to
prove they can fail: with `PsmfTextField`
no longer telling the dismissal where it is, **9 of the 12 `KeyboardTest`s
failed**, both guards among them. Restored.

Then on the emulator, 0.2.1 (3) against the build with Gate 1 (`mInputShown`
from `dumpsys input_method`): tap on the page **true → false**; a scroll
**true → false**; tap on the field again **→ true**; in the add-player dialog
a tap on its body **true → false**; the lineup's lowest row with the keyboard
up is above it and the tab bar is gone; Gboard shows its ✓ Done key on the
date-of-birth field, which is the last of its form.

**What the emulator could not show.** `adb shell input keyevent` is a
hardware key and switches Compose into keyboard input mode, where a switch
between two fields takes focus in the traversal; a phone's soft keyboard does
not. So *Next* and *Done* are proved on the JVM, where `performImeAction` is
the soft key, and the ✓ key on the emulator is the one sign that the action
reached Gboard.

### Judgement calls

- **A tap handler that hears consumed taps**, and asks "is it inside a field?"
  from the fields' positions rather than from focus. The plain version, a tap
  handler on the parent, is silent for every button, chip and switch.
- **The tab bar steps aside while the keyboard is up**, instead of floating
  above it.
- **`Search` ends the way `Done` does.** The platform's `Search` does nothing,
  which is what the two search boxes had.
- **Detekt's `LongParameterList` ignores parameters with a default.**
  `PsmfTextField` has twelve, and two without a default.
- The brief said *"list every text field in the app"*. That list is §3.1.

### 3.1 · Every text field, and what dismisses its keyboard

All of them go through `PsmfTextField`, and `KeyboardTest` fails if one does not.
Every row below is ended by: **a tap outside any field · the start of a scroll
· leaving the step or tab** — and, where it has one, **the action key**. The
iOS number pad has none, so for the rows marked ↯ the other three are all there
is.

| Screen | Field | Keyboard | Action key |
|---|---|---|---|
| Hlavička | Rozhodčí | text, capitals | Next |
| Hlavička | Asistent | text, capitals | Done (Next while *Jiný tým* is showing) |
| Hlavička | Jiný delegující tým | text, capitals | Done |
| Sestavy | Dres, each player present | number ↯ | Done |
| Sestavy | Číslo RP, each present player with no league RP and a card | number ↯ | Done |
| Sestavy | Datum narození, each present player with no card (and no date on file) | number ↯ | Done |
| Sestavy → dialog | Příjmení | text, capitals | Next |
| Sestavy → dialog | Jméno | text, capitals | Next |
| Sestavy → dialog | Číslo RP, or Datum narození after *Nemá průkaz* | number ↯ | Done |
| Zápis → gól s jinou minutou | Minuta hry | number ↯ | Done |
| Zápis → karta | Osoba (a card to someone not in the lineup) | text | Next |
| Zápis → karta | Minuta | number ↯ | Next |
| Zápis → karta | Důvod | text | Done |
| Hodnocení | NH and Čd, for each team (four) | number ↯ | Next |
| Hodnocení | Poznámka týmu, for each team (two; **new, Gate 4**) | several lines | return key (a new line) |
| Hodnocení | Komentář | several lines | return key (a new line) |
| Zápasy | Tým | text | Search (ends) |
| Týmy | Hledat | text | Search (ends) |
| Týmy → soupiska | Dres, each player | number ↯ | Done |

**Rekapitulace has no text field any more** (Gate 4): its four score fields and
the name field of the confirmation dialog are gone; the result is shown, and a
signature is one tap on a button that names who signs. Nothing there opens a
keyboard.

Not a text field to type in, and so not on the list: the select boxes on
Zápasy (league, group, pitch, dates), which are read-only fields that open a
menu.

### 3.2 · Dead ends

The header (fixed). Every report opened at the console or the recap from the
fixture list, and the one adopted after the app was killed: no Zpět on that
screen at all (fixed, same cause). **Týmy when its load fails** shows the
message and no retry; leaving the tab and coming back reloads it (**not
changed**). The empty report tab has one button, to Zápasy.

---

## 4 · Gate 2 — the lineup, schema 7

### What changed for a referee

- **Nobody is present until they are tapped.** A tap on the name marks the
  player present and opens the row (shirt number, number from the card); a tap
  on the name again takes it back. *Přítomno 8 z 20* is pinned at the top while
  the list scrolls. **No row moves when another is marked**: an unmarked row
  already holds the room the controls will take.
- **One captain and one goalkeeper per team, chosen from those present**, who
  may be the same person. Both are needed to continue, and the problems list
  names the team and which of the two is missing. Taking a player's mark back
  takes a role from them.
- **The *Číslo RP* column is what the card says.** A player the league has an
  RP number for shows it, read-only. A player it has none for is asked the
  number *from the card*; **Nemá průkaz** turns that same row to the date of
  birth (and **Má průkaz** turns it back). The number typed is remembered on the
  device and offered next time; it is never the player's RP number and never
  goes into league data. There is no rodné číslo anywhere.
- **Adding a player** asks surname, first name, then the card number — or, with
  *Nemá průkaz* switched on, the date of birth.
- **The confirmation before kickoff is one tap**, *Potvrzuje Novák Jan*, by the
  captain marked above it: no list, no deputy. A missing one is listed in the
  export's problems but never stops the match from starting; a late one is
  allowed and keeps its real time. It is **not** the signature under the
  finished report (Gate 4): the recap still asks each captain.
- **The report** lists each lineup captain first, then goalkeeper, then the rest
  by shirt number, and says in all three formats which is which and which kind
  of number each row has.

### How it was proved

Counts after this gate, from the JUnit XML, `./gradlew build :shared:allTests
detekt` green: **491 · 436 · 246 · 23** (shared JVM · Android host · composeApp ·
importer), against 454 · 401 · 210 · 23 at the baseline.

**Against HEAD first.** The lineup's model changed shape (presence inverted,
two roles, a typed-number kind, a confirmation on the lineup), so most of the
rewritten lineup tests cannot compile at HEAD and could not "fail by assertion"
there; I did not pretend they could. The one the brief asks for could be and
was written against the code as it stood: **`aCaptainWhoTappedOnTheLineupIsStillAskedToConfirmAtTheRecap`**
failed there with *the recap already shows the home captain confirmed, from the
lineup's tap* — at HEAD the lineup's tap and the recap's signature were one
record, so a report could reach CONFIRMED with neither captain having seen it
finished. It passes now, with the other four in `LineupToRecapFlowTest`.

**The migration, against the recorded `6.db`** (`SchemaMigrationTest`): three
matches built the way 0.2.1 writes them — in progress, finished and
unconfirmed, CONFIRMED — are all **open after the upgrade**, with their players
present, no captain or goalkeeper invented, and the CONFIRMED one's export
saying what it said, apart from new fields that are empty. `7.db` is generated;
`6.db` is untouched.

**Each new behaviour broken once** (a mutation, then restored, each checked by
`grep` or `cmp`), and what caught it:

| Broken | Caught by |
|---|---|
| An unmarked row does not lay out its second line | 3 `LineupScreenTest`s: *markingARowDoesNotMoveTheRowsBelowIt*, the same at font scale 1.5, and the role pick-list test |
| The role pick-lists offer everybody, not those present | *theCaptainAndTheGoalkeeperArePickedFromThoseMarkedAndNobodyElse* |
| The recap counts the lineup's tap as the final signature | *aCaptainWhoTappedOnTheLineupIsStillAskedToConfirmAtTheRecap* |
| The export lists the captain last | *theCaptainIsFirstThenTheGoalkeeper…*, *oneManWhoIsBothIsOneRowAndIsFirst* |
| The goalkeeper is never named in the text | *theTextSaysInWordsWhoIsWhoAndLeavesTheOthersAlone*, *theTextSaysBothForOnePersonWhoIsBoth* |
| The upgrade also moves a CONFIRMED match's confirmation (`6.sqm`) | both migration tests: *threeMatchesWrittenByTheLastReleaseAllOpenAndMeanWhatTheyMeant* and *aReportWrittenBeforeCaptainsIsExportedWithOnlyNewFieldsEmpty* |
| The *Nemá průkaz* hint wraps to a second line (the cs string lengthened) | *sayingAPlayerHasNoCardDoesNotMoveTheRowsBelowIt*: `expected:<-332.0.dp> but was:<-348.0.dp>` — one line |

**On the emulator** (a debug build with this gate installed after
`adb uninstall`; the phone is 1080 × 2400, 411 dp, in English): the lineup of
HeyMed has 13 players and *0 of 13 present*; two taps on names mark two, and
the third row (Hadžič Haris) is at y = 1497 before and after. A card number
typed (`59001`) and a tap outside the field dropped the keyboard (`mInputShown`
false). *No card* on the second row turned it to *Date of birth* and *Has card*.
The count stayed pinned through the scroll, and the foot of the screen shows
*Captain and goalkeeper* (two pick-lists, *— not chosen —*), *Captain's
confirmation* (*Choose the captain first.*) and *Continue to the match*.

That pass **found two things the tests had not**, both fixed before this
commit:

1. *Nemá průkaz* moved the next name down by a line (Haris 1497 → 1542), because
   the date-of-birth field carried a hint line and the card-number field did
   not. Both fields now carry one line, each short enough for 360 dp.
2. A card number the app could not use turned the field red and said nothing.
   The line under it now says the rule in words (*z průkazu, 4–7 číslic*) and
   goes red with the field.

And one fault in a test, not the app: `theAddPlayerButtonOpensTheForm` did its
scroll outside the `withLanguage("cs")` block, so rows the scroll composed read
English and the test lost its button. Moved inside; it passes for the right
reason.

**Renders** (360 dp, 2.625×): `reports/2026-10-17-lineup-cs.png` and
`reports/2026-10-17-lineup-uk.png`. Left, the top of a squad of 20 with 8
marked: a captain, a goalkeeper, a number from a card, a league RP number, a
*Nemá průkaz* row with a date of birth and its echo, and a number too short to
use. Right, the foot of the same 8 with one man chosen as both captain and
goalkeeper, then the roles and *Potvrzuje Novák Jan*.

### Judgement calls

Each has its own entry and **Reverses if** in `docs/DECISIONS.md`
(*The lineup, as built*).

- **Card numbers are 4 to 7 digits.** Five is what the examples have; under four
  is likelier a jersey number in the wrong box; over seven is not refused by an
  app that guessed.
- **The upgrade moves the old lineup tap, not the old signature.** For a match
  not yet CONFIRMED, a captain's confirmation made before kickoff is the
  lineup's tap and moves to its lineup; for a CONFIRMED match nothing moves, so
  its export says what it said.
- **A different captain withdraws the lineup confirmation** (the person who
  confirmed is no longer captain). A later change to the lineup does not.
- **Roles are two pick-lists under the squad**, not a mark on every row.
- **How the paper form marks them is unknown**, so the report says it in words
  (*kapitán*, *brankář*) and a photograph of a real form (TODO) decides.
- **An unmarked row is a tall blank** (about 160 dp, so a squad of twenty is
  about 3,200 dp). That is the price of rows that do not move.
- **A block is only written once whole** — presence, a captain, a goalkeeper
  and a number or date in every row. Marks made and the app killed before that
  are lost. A minute's work, nothing a captain has signed.
- **The old rodné číslo source stays in the model** and is exported as *jiný
  údaj*, so no label for it appears anywhere.
- **`LineupNotConfirmed` is a problem the export lists, so it does stop a report
  being sent** until the tap is given, but only for a lineup with a marked
  captain: an old lineup has no one it could be waiting for.

### Not done

- *Číslo RP* **as the paper form lays it out** — no one has shown us the form.
- **Not checked on an iPhone.** It is common code; the iPhone checklist at the
  end lists what to look at.

---

## 5 · Gate 3 — the console

### What changed for a referee

- **A goal at its own minute.** *Gól s jinou minutou*, under each team's list,
  opens a sheet: the scorer (the team's players still on the pitch, or none)
  and the minute, entered as on the card sheet — a number, or `30´+`. There is
  no `60´+`. The one-tap goal is still one tap, at the clock's minute.
- **Every goal after the final whistle asks for its minute.** The clock has
  none to give, so the tap on a player's goal icon opens the sheet with that
  player already chosen; nothing is written at `60´+`.
- **A minute the clock has not reached is refused**, in words, with the latest
  one named (*Tato minuta ještě nebyla odehrána (nejpozději 25´)*). It is not
  moved to another minute.
- **The goal list, `Stav` and the half-time score follow the minute written**,
  in the console log and in all three export formats; goals at one minute keep
  the order they were recorded in.
- **Undo takes back a whistle too** — the kickoff, the end of the half, the
  start of the next, the final whistle — in the order things were recorded, and
  **asks first**, naming it (*Vzít zpět hvizd? Konec 1. poločasu se vezme
  zpět…*). A goal or a card still goes at once. Then the whistle can be pressed
  again. Nothing pauses, stops, resumes or adjusts anything; a red card's ten
  minutes of play follow the clock that is left.
- **The clock reads `mm:ss` of play**, and the minute the next event would be
  written with stays on a line of its own.

### What the screen shows at each boundary

Pinned by `ConsoleClockTest` (what is drawn) and `ClockReadingTest` (what it is
computed from). The first line is play so far; the second is the minute the next
goal, card or log row is written with.

| Moment | Play so far | The minute written |
|---|---|---|
| Before the kickoff | nothing | *Před výkopem* |
| 12:40 into the first half | `12:40` | *Zápis: 12´* (12:40 is 12´, as it was) |
| 29:59 | `29:59` | *Zápis: 29´* |
| 30:00, first half still running | `30:00` | *Nastavení 1. poločasu · zápis 30´+* |
| 31:12, in its added time | `31:12`, counting on | the same: `30´+` |
| The half ended at 31:12 (the interval) | `31:12`, **held** | *Poločas · zápis 30´+* |
| The second half kicks off | **`30:00`** — less than the interval showed, because the form's minute restarts at 30 whatever the first half added | *Zápis: 30´* |
| 47:05 into the second half | `47:05` | *Zápis: 47´* |
| 61:05, the second half's added time | `61:05` | *Zápis: 61´* |
| After the final whistle at 61:07 | `61:07`, **frozen at the whistle** | *Konec utkání · zápis 60´+* |
| A match finished before 0.3.0 | nothing: no whistle instant was kept | *Konec utkání · zápis 60´+* |

Renders at 360 dp: `reports/2026-10-17-console-cs.png` and
`reports/2026-10-17-console-uk.png`. Left to right: the first half's added time
(`31:12`, *Nastavení 1. poločasu · zápis 30´+*), the interval (the clock held at
`31:12`), and mid second half (`47:05`, with a red card's power play still
counting down).

### How it was proved

Counts after this gate, from the JUnit XML, `./gradlew build :shared:allTests
detekt` green: **529 · 471 · 272 · 23**, against 491 · 436 · 246 · 23 after
Gate 2.

**Against HEAD first.** Ten tests in `GoalWithItsOwnMinuteTest` and
`TakingBackAWhistleTest`, written against the use cases as they stood: **seven
failed there, all by assertion**; three pass and are guards.

| Test | Failure at HEAD |
|---|---|
| *a goal written at 12´ after one at 20´ lists first, and Stav is right* | `expected:<[12´, 20´]> but was:<[20´, 12´]>` |
| *no goal is written at 60´+* | `a goal was written at 60´+: [60´+]` |
| *a mis-tapped kickoff can be taken back and pressed again* | `the kickoff is still there expected null, but was:<2026-08-31T19:00:00Z>` |
| *taking back the end of the first half leaves the clock continuous* | `Expected value to be false` (the match was still in the interval) |
| *taking back the start of the next half puts the match back in the interval* | `Expected value to be true` |
| *taking back the final whistle sets the match going again* | `expected:<IN_PROGRESS> but was:<FINISHED>` |
| *Undo walks back through goals and whistles in the order recorded* | `expected:<[whistle, goal, whistle, goal, whistle]> but was:<[goal, goal]>` |

**One of the four the brief names passes at HEAD, and I am saying so rather
than pretending otherwise:** *a second-half goal with a first-half minute counts
for the half-time score*. At HEAD `scoreAtEndOfFirstPeriod` already cuts by the
minute, so the half-time score was right; nothing in the app could write such a
goal. It is kept as a guard, with the other two that pass (*goals at one minute
keep their recording order*, *Undo takes the last goal recorded, not the latest
minute*).

**Each new behaviour broken once** (a mutation, the file restored, each checked
by `cmp`), and what caught it. All fifteen were caught (the table puts the two
that break the minute bound on one row):

| Broken | Caught by |
|---|---|
| Goals listed in recording order, not by minute | 4 tests: the goal-order test, and three in `GoalsInMinuteOrderInTheReportTest` (text, CSV, JSON, half-time) |
| A goal may be written at `60´+` | *noGoalIsWrittenAtSixtyPlus* |
| A minute later than the clock is accepted (use case; and the bound itself) | 3 tests each: later-than-clock, interval, after-the-whistle |
| Undo cannot see whistles | 5 tests in `TakingBackAWhistleTest` |
| The end of the half is not removed when taken back | 4 there, and *aWhistleTakenBackStaysTakenBackAfterTheAppRestarts* |
| The kickoff has no place in the recording order | *aGoalTappedBeforeTheWhistleIsTakenBackAfterTheWhistleIs* (the only test that can: a goal tapped before the whistle) |
| The second half starts a second late on the clock | 6 in `ClockReadingTest` |
| First-half added time not told apart from play | *atThirtyMinutes…* |
| A confirmed report's whistles are offered | *aConfirmedReportsWhistlesAreNotOffered* and the migration test |
| The clock is not frozen at the final whistle | *afterTheFinalWhistleNoMinuteIsLaterThanThePlayWas* |
| A break taken back is still on disk | *aWhistleTakenBackStaysTakenBackAfterTheAppRestarts* |
| The console takes a whistle back without asking | 3 in `ConsoleFlowTest` |
| A one-tap goal after the whistle writes the clock's minute | *afterTheFinalWhistleTheOneTapGoal…* |
| The screen shows only the minute, as before | 8 in `ConsoleClockTest`, 2 in `ConsoleScreenTest` |

**The upgrade.** `SchemaMigrationTest`, against the recorded `6.db`, now also
checks the three 0.2.1 matches' whistles: none is numbered, none has a final
instant, a whistle pressed under 0.2.1 can still be taken back (the kickoff of
the one in progress, the final whistle of the finished one), and the signed
match's whistles are not offered.

**On the emulator** (a debug build with this gate, installed after `adb
uninstall`; 411 dp, in English), with both lineups filled in and a match
started: the clock read `00:02` and *Written: 0´*; the three controls (*Undo*,
*End the half*, *End the match*) fit on one row. A mis-tap of *End the half* at
three seconds showed the interval — `00:03` held, *Half-time · written 30´+* —
and Undo then asked *Take back the whistle? The end of half 1 will be taken
back…* with *Cancel* and *Yes, take back*; yes, and the clock read `00:23` a few
seconds on, running continuously, with *Written: 0´*. The goal sheet showed
*Latest minute now: 0´.*, the scorer, *Minute of play* and *30´+ (half-time)* and
no `60´+`, and `12` in the field was refused in words, in red: *That minute has
not been played yet (latest 0´).*

**What the emulator could not show:** the clock past thirty minutes. It was not
moved, so `30:00`, the added time, the interval after a real half and the
second half are in the JVM tests and the renders, not on the phone.

**Found on the way, and fixed in this commit.**

1. **The end-of-match button was squeezed to a strip a letter wide** at 360 dp
   — in a render of the first half, where *Undo*, *End the half* and *End the
   match* did not fit on one row (`Ukončit utkání is 58.0.dp wide: squeezed`).
   This is not a Gate 3 regression: the row was a `Row` before. `ActionRow` now
   wraps, so a button that does not fit goes to the line below at its own width.
   It is the shared row, so every screen's button row benefits; on the 411 dp
   emulator the three still sit on one line.
2. Five tests in the shell and language suites used `25´` on the console as a
   sign that the console was on screen. It is now *Zápis: 25´* (*Запис: 25´* in
   Ukrainian).
3. detekt: `ConsoleViewModel` and `PlayClock` each had more functions than the
   project allows. The pure helpers became extensions and the clock reading
   moved to its own file. No rule was suppressed.

### Judgement calls

Each has its own entry and **Reverses if** in `docs/DECISIONS.md` (*The
console: a goal at its own minute…*).

- **A minute later than the clock is refused, not clamped**, and the sheet says
  which is the latest.
- **After the final whistle the bound is the clock frozen at the whistle**, which
  needed an instant for it; a match finished before 0.3.0 has none, and the
  running clock is the bound there (weaker, never wrongly refusing).
- **One Undo, in recording order, asking before a whistle.** The price: a
  whistle with goals recorded after it comes back only after them, one Undo
  each. The log does not list whistles (TODO).
- **A confirmed report's whistles are not offered**; part 4 decides what
  changing one does to the signatures.
- **Pre-0.3.0 whistles come back last**, last pressed first.
- **Schema 7 amended, not an 8**: version 7 has not shipped, so the five new
  columns are in `6.sqm` and `7.db` was regenerated (`6.db` is untouched). Any
  install of the Gate 2 build has to be uninstalled; mine was.
- **`Stav` and the stored goal list are in report order**, not recording order,
  so no reader has to sort.
- **The seconds are on the screen only**; the log and the report keep the form's
  minutes.

### Not done

- **The log does not list the whistles**, though Undo walks through them.
- **The clock past thirty minutes was not seen on the phone.**
- **Not checked on an iPhone.**

---

## 6 · Gate 4 — the assessment and the recap

### What changed for a referee

- **A note per team.** *Hodnocení* has a *Poznámka týmu* field under each team's
  ratings, for a complaint or a protest the team's captain asks the referee to
  note. The referee types it. It is optional, never a readiness problem, and it
  goes out in the commentary block of the report, labelled per team in fixed
  Czech (*Poznámka týmu D: …*, *Poznámka týmu H: …*; *—* when there is none), as
  a field per team in the JSON and a row per team in the CSV.
- **The result is no longer typed.** The recap shows *Poločas* and *Konečný
  výsledek*, worked out from the goals, and a line says that a goal is corrected
  in the console (*Zápis*): Undo, or a goal at its own minute. The four score
  fields, and the two export problems that went with them (*missing result*,
  *score disagrees with the goals*), are gone.
- **Three taps, no typing.** *Kapitán domácích*, *Kapitán hostí* and *Rozhodčí*
  each have one button, *Potvrzuje <jméno>*, and the name on it is the one that
  signs: the captain marked in that team's lineup, the referee from the header.
  After the tap the line reads *Kapitán domácích: Houžev Karel — potvrzeno 31. 8.
  2026 22:05*. There is no deputy switch. All three make the report CONFIRMED.
  A team with no captain marked has no button, only red words saying to go back
  and mark one.
- **The report shows both of each captain's confirmations**, the one under the
  lineup before kickoff and the final one at the end, each with its time.
- **The "no cards" affirmation can be taken back** (*Vzít zpět*): the block is
  *not accounted for* again, so the readiness check asks about it.
- **A signed report that is changed asks first.** A goal, a card, an Undo, a
  whistle, or the "no cards" affirmation made or withdrawn, on a report that has
  signatures, opens a question — *Zrušit podpisy?* — that says the signatures of
  both captains and the referee go and the recap will ask for them again;
  *Ano, zrušit podpisy* does it, *Ponechat podpisy* leaves everything as it was.
  A whistle's take-back question carries that sentence as one extra line, so it
  is one dialog and not two. **The header and the lineups save as you type**, so
  there they say it first instead: a red line at the top, *The report is signed…*,
  and the first edit takes the signatures back and the line goes.
- **What is not asked about:** the assessment, the commentary and the team notes
  stay editable until export and never touch a signature, because nobody signs
  them.

### How it was proved

Counts after this gate, from the JUnit XML, `./gradlew build :shared:allTests
detekt :androidApp:assembleDebug` green: **559 · 500 · 302 · 23**, against
529 · 471 · 272 · 23 after Gate 3.

**Against HEAD first.** The four the brief names, written against the code as it
stood (they compile there, so they fail by assertion, not by a missing symbol):

| Test | Failure at HEAD |
|---|---|
| *a card after the final confirmations withdraws them* (`ChangesAfterTheSignaturesTest`) | `the signatures outlived the card expected:<[]> but was:<[Confirmation(party=HOME_CAPTAIN, at=2026-08-31T20:05:00Z, confirmedBy=Vlk Jiří, asDeputy=false), …` |
| *the referee signs under the header's name without typing* (`RecapFlowTest`) | `the referee did not sign under the header's name expected:<Vlk Jiří> but was:<null>` |
| *the "no cards" affirmation can be withdrawn* (`RecapScreenTest`) | `No node found that matches Text + InputText + EditableText contains 'Vzít zpět'` |
| *the result cannot be edited* (`RecapScreenTest`) | `Failed to assert count of nodes. Did not expect any node but found '4' nodes that satisfy: (SetText is defined)` (the four score fields) |

The rest of the gate's tests — the notes in the three formats, the times, the
result from the goals, the withdrawal in each use case, the question dialogs and
the notices — use things that do not exist at HEAD (`WithdrawNoCards`, the new
`ConfirmReport`, `Match.afterAChangeFrom`, the note fields), so they could not
be run against it; they were written first, and each was broken once below.

**Each new behaviour broken once** (a mutation, the file restored, each checked
by `cmp`), and what caught it. All seventeen were caught:

| # | Broken | Caught by |
|---|---|---|
| N1 | A change never takes the signatures back | 10 of the 14 in `ChangesAfterTheSignaturesTest` |
| N2 | The lineups are not part of what is signed | the header-or-lineup test and the lineup-written-again test |
| N3 | The captain's own confirmation under the lineup counts as content | *theCaptainsOwnConfirmationUnderTheLineupIsASignatureNotContent* |
| N4 | An old CONFIRMED report loses its typed result | `ResultFromTheGoalsTest` and the migration test, against the recorded `6.db` |
| N5 | A typed result is read on a report that is not confirmed | the same two |
| N6 | Withdrawing the "no cards" affirmation does nothing | `WithdrawNoCardsTest` and the both-ways test |
| N7 | The header is saved without taking signatures back | the header test |
| N8 | A lineup is saved without taking signatures back | the lineup test |
| N9 | The assessment edit takes the signatures back | *theAssessmentTheCommentaryAndTheTeamNotesStayEditableWithoutTouchingAnySignature* (the first attempt did not compile, a missing import; the second, which did, was caught) |
| N10 | Prague time is UTC | 3 in `TeamNotesAndSignaturesInTheReportTest` (winter and summer, the CSV cells, the two confirmations) |
| N11 | The team notes are not stored | *theTeamNotesSurviveTheProcessBeingKilledWhateverIsInThem* |
| N12 | The home team's note is not in the text report | 2 in `TeamNotesAndSignaturesInTheReportTest` |
| N13 | The recap signs the home captain under the away captain's name | 2 in `RecapFlowTest` |
| N14 | The recap changes a signed report without asking | *onASignedReportTakingTheAffirmationBackAsksFirstAndSaysNothingUntilAnswered* |
| N15 | The console never asks before changing a signed report | 3 in `ConsoleFlowTest` (a card, a goal, an Undo) |
| N16 | The whistle question does not say the signatures go | *onASignedReportTheWhistleQuestionAlsoSaysTheSignaturesGoWithIt* |
| N17 | The header shows no notice on a signed report | *aSignedReportSaysBeforeTheFirstEditThatAnyChangeTakesTheSignaturesBack* |

**The upgrade.** Schema 7 is amended again, not an 8, for the same reason as at
Gate 3: it has not shipped. `6.sqm` adds `home_note` and `away_note` (empty for
every match already written) and `7.db` was regenerated; `6.db` is untouched, and
`SchemaMigrationTest` still opens the three recorded 0.2.1 matches and checks
what they mean: the typed result of the CONFIRMED one is still the result it
reports, the typed result of the finished one that was never signed is still
stored but not read (it reports its goals' result), and every note is empty.
Any install of an earlier gate's build has to be uninstalled; mine was.

**The renders** (`reports/2026-10-17-recap-cs.png` and `…-uk.png`, 360 dp, three
panels each): the top of the recap with the result and the first of the report;
its end, with the note lines, the cards block (*Potvrzeno: bez karet*,
*Vzít zpět*) and the signatures with the home captain signed and the other two
waiting; and the same with the away captain not marked, which has red words and
no button. Czech and Ukrainian; the Ukrainian wording is mine and has not been
read by a native speaker (as in the earlier gates).

**On the emulator** (a debug build with this gate, installed fresh; 411 dp, in
English): both lineups filled in, a match started and ended; *Hodnocení* shows
the two *Team note* fields under the ratings, multi-line; the recap shows
`0:0` and `0:0` read-only, three *X confirms* buttons with the names on them, and
tapping one turns it into the name and the Prague time (`18. 10. 2026 11:24`).
With all three given, *No cards issued* on a signed report opened *Take the
signatures back?* with *Yes, take them back* and *Keep the signatures*; yes made
the affirmation, the signatures were gone and *Take back* appeared; signed again,
and an edit to the commentary left the signatures alone. Undo on the console of a
signed report opened one dialog, naming *The end of the match*, with the signed
sentence in bold. The lineup of a signed report showed the red notice, and the
first tap on a kit colour took it away.

**What the emulator did not show:** that the signatures are gone *at the recap*
after that lineup edit (the test *aLineupChangedAfterTheSignaturesWithdrawsThem…*
is what shows it), a report signed on an upgraded 0.2.1 match, and Ukrainian on
the phone.

**Found on the way, and fixed in this commit.**

1. **A CONFIRMED report's console offered *End the match* again.** The button
   chose between *End the match* and *Continue* by `status == FINISHED`, so a
   report with all three signatures, which is CONFIRMED, got the end-of-match
   button and not the way on. It now asks whether the match is past the final
   whistle. `aConfirmedReportsConsoleOffersToContinueAndNotToEndTheMatchAgain`.
2. Two exact-match collisions in the tests — *Zapsat gól* was both a sheet's title
   and its button, and *Vzít zpět* both Undo and the dialog's confirmation — were
   settled by renaming the strings (*Ano, vzít zpět*), not by loosening the
   matching.
3. detekt: the console's view model and the signature code were restructured to
   stay inside the project's limits (an extension for the guarded state update, one
   branch for the two dismissals); nothing is suppressed.

### Judgement calls

Each has its entry and **Reverses if** in `docs/DECISIONS.md` (*The assessment
and the recap: a note per team…*).

- **An old report that is CONFIRMED keeps its typed result; every other old
  report says what its goals say.** The brief left this to me. Nothing can edit a
  typed number now, so one that disagreed with the goals could never be put right;
  a signed one is what the captains signed. The typed result stays in the table,
  unread, never deleted.
- **One function, `afterAChangeFrom`, takes the signatures back**, and every use
  case that saves a change to the report passes through it. It compares what the
  signatures attest — the header, the lineups, the goals, the cards with the
  affirmation, the whistles — and nothing else, so a save that changes none of
  them (the same lineup written again, a refused goal, the commentary) takes
  nothing back.
- **The signatures given so far go too when only some were given**: they attested
  an older report.
- **A question where there is a moment to ask, a notice where there is not.** The
  header and the lineup save as you type, so the first edit is the change.
- **A team whose captain is not marked cannot be signed for**, and there is no
  field to type a name in instead. A match upgraded from 0.2.1 has no captains
  marked; the referee goes back to the lineups and marks one, which is a lineup
  change and takes any signatures back.
- **The two confirmations of a captain are separate records.** Giving one does
  not give the other, and the report prints both.
- **The times are Prague time** in the TXT and the CSV whatever zone the phone is
  in, and instants in the JSON. A CONFIRMED report written before 0.3.0 gains
  the time after each name in the TXT; the time was stored all along.
- **A signed report's whistles are offered by Undo.** This reverses what Gate 3
  decided; a whistle is something the signatures attest like the rest, and the
  question says so.
- **Withdrawing the affirmation touches only the affirmation.** Recorded cards
  are never removed by it; Undo takes them back one at a time.

### Not built, as the brief says

- **No code or PIN**, for a captain or for the referee (points 7 and 17).
- **A duplicate report is not visible, and there is a cheap way to make it so;
  it is described here and not built.** A report is sent by hand, as a file or
  as text, so the same fixture can reach the league twice, from two phones or
  from one after a correction, and nothing on the report says which is which.
  What would: a fingerprint line at the foot of every export, a short checksum of
  the report's own text (without the line itself) printed next to the time it was
  exported. A re-export of an unchanged report has the same fingerprint, so a
  second copy reads as a copy; a corrected one has a different one, so two for
  one fixture read as two versions. It needs no server and no new dependency —
  a plain checksum written in the shared code is enough for *visible* — and it
  is not an anti-forgery measure: anyone can compute it, which is why the real
  answer (*A forged report* in `DECISIONS.md`) is still a code that only the
  captain and the referee hold.

### Not done

- **The log does not list whistles**, and **the clock past thirty minutes was not
  seen on a phone** (Gate 3, unchanged).
- **Not checked on an iPhone.**

---

## 7 · Gate 5 — documents, version, build

No Kotlin changed in this gate.

### CLAUDE.md — the RP rule, and where the code said otherwise

The brief's proposed wording is in, nearly sentence for sentence. The brief said
to change it only where the code says otherwise; the code said otherwise in four
places, and this is what I did about each.

| The old text said | The code does | Now |
|---|---|---|
| there is "no UI path to typing one" | A referee types a number from the card on every lineup row. It is not an `RpNumber`, but "no UI path to typing one" now reads as false | "no UI path *makes* one", and the typed number is named: `IdentificationSource.RP_TYPED`, on the appearance, never in the player, league data or the jersey override table |
| a league player with no identification "is fielded by the referee typing the date of birth in the lineup row" | The row asks for the number from the card; the date of birth only after *Nemá průkaz* | says so |
| "a player added at the pitch must have one" (of the three) | Adding takes surname, first name, and the number from the card *or*, with *Nemá průkaz*, the date of birth. A player with a card has no date of birth on their record at all | "needs one of the two" |
| `reportedIdentification` is "non-null, stored, never derived" | Still true; it now also carries which kind of value it is | "a value and which of the kinds it is" |

Two additions beyond the proposed wording, both from the brief's own
NON-NEGOTIABLE list: the **jersey override table** among the places a typed number
never reaches, and **"not as a field, a hint or a label"** after *No rodné číslo
until A28*.

### DEMO_SCOPE.md

What the brief asked for:

- **§3** — mark who came (and the 2026-10-17 turn-round away from "mark who is
  absent"); one captain and one goalkeeper per team; Číslo RP as written on the card,
  `Nemá průkaz` and the date of birth, no rodné číslo; adding a player; the
  captain's confirmation before kickoff as one tap and not the final signature.
- **§4** — a goal at its own minute (refused, not clamped, if the clock has not
  reached it; no `60´+`; every goal after the whistle asks); taking a whistle back,
  with the no-pause rule stated beside it; `mm:ss` of play and the minute written.
- **§6** — the result from the goals, shown and not edited; three taps under the
  lineup's captain and the header's referee; two confirmations per captain, each
  with its time; what a change after the signatures does.

What I changed that the brief did not list, because each otherwise contradicted
what the same file now says: the Screens table's rows 3, 5 and 6 (they restate
those sections); **§5** gains the team note, which no listed section covers;
the Týmy paragraph *Absences are not here* now says *Who came is not here*; the
`60´+` line in §4 says it is for a card, since a goal no longer has one; and the
seed-data paragraph's "RP can never be typed" now says what is copied from a card
instead. The 2026-08-29 *Decisions — settled* table is a dated record, so I did
not rewrite its two rows that the tester overturned (§5.1 *mark the absentees*, A15
*identifier stays editable but not prominent*): each keeps its text and gains an
italic line saying what replaced it and when.

### Documents I found stale and did not touch

- **`docs/TECH_STACK.md` §4, around lines 203 to 214**, still carries the old rule:
  "a pitch-added player must have a date of birth, and the referee writes one in
  the lineup when the record has nothing", and "three situations produce a value".
  CLAUDE.md sends the reader there for "full list and sources", so the two now
  disagree. The brief names CLAUDE.md and DEMO_SCOPE only; it is a few lines, and
  I would rather you said than I guessed.
- `docs/QUESTIONS.md` A16 asks whether "mark the absentees" is fewer taps. It is an
  open question about a design that has since been turned round; I left it as history.

### Version

`androidApp/build.gradle.kts`: `versionCode` 3 → **4**, `versionName` 0.2.1 →
**0.3.0**. `iosApp/iosApp.xcodeproj/project.pbxproj`: `CURRENT_PROJECT_VERSION`
3 → **4** and `MARKETING_VERSION` 0.2.1 → **0.3.0**, in Debug and in Release (four
lines, `git diff -U0` shows exactly those). `DEVELOPMENT_TEAM` is not in that file
at all (it is in the git-ignored `Local.xcconfig`) and was not touched; nothing
else in `iosApp` or `iosMain` was.

### Build

- `./gradlew build :shared:allTests detekt` — **BUILD SUCCESSFUL**. Gradle
  reported the test and detekt tasks `UP-TO-DATE`, which is right (no Kotlin
  changed since Gate 4) but is an inference rather than a run. So I ran them: the
  four test suites with their results deleted and the build cache off, and detekt
  the same way. Both **passed**:

  | Suite | Baseline `bc6ee00` | Gate 2 | Gate 3 | Gate 4 | **Gate 5** |
  |---|---|---|---|---|---|
  | `:shared:jvmTest` | 454 | 491 | 529 | 559 | **559** |
  | `:shared:testAndroidHostTest` | 401 | 436 | 471 | 500 | **500** |
  | `:composeApp:jvmTest` | 210 | 246 | 272 | 302 | **302** |
  | `:league-import:jvmTest` | 23 | 23 | 23 | 23 | **23** |

  Zero failures, errors or skips in any. (Gate 1's counts were not written down.)
- `./gradlew :androidApp:clean :androidApp:assembleDebug` — **BUILD SUCCESSFUL**.
  `androidApp-debug.apk` is **70,313,351 bytes**, against 0.2.1's 70,047,639:
  **+265,712 bytes (+0.4%)**. It was over 70 MB before and the brief says not to
  fix that here; I did not.
- The APK is also at `C:\MS\Projects\PSMFApp\builds\psmf-0.3.0-debug-<commit>.apk`
  on the Windows side (git-ignored), named for the commit this report is in, so
  the Android checklist's first step has a file to install.

### Judgement calls

- **The Gate 5 tests were re-run rather than read from Gradle's up-to-date
  check**, for the reason above. It cost a minute.
- **DEMO_SCOPE edits beyond the brief's three sections**, and **TECH_STACK
  flagged and not edited**: both above, both because the brief named the files.
- **No new entry in `docs/DECISIONS.md`.** Nothing in this gate is a new decision;
  the rule CLAUDE.md now states is the one the four 2026-10-17 entries and *The
  lineup, as built* already record, each with its own *Reverses if*.

### Not done

- **Not checked on an iPhone**, nor on a phone at all in this gate: nothing here
  runs. §8 and §9 are what to do with the build.
- **Nothing is pushed.** The container cannot, and the brief says not to.

---

## 8 · Checklist — on the Android phone

Install `psmf-0.3.0-debug-<commit>.apk` **over 0.2.1 (3), without uninstalling**.
Use Czech unless a step says otherwise. Anything that fails: write down the step
number and what you saw.

**Time.** The clock steps (13) need thirty minutes of play. Do the upgrade (A) and
the lineup steps (1 to 10), kick off, and do the rest while the half runs.

### Before you install, on 0.2.1

0. Have three reports on the phone, and note what each shows: **(a) in progress**,
   kicked off, one goal and one card recorded, not ended (write down how many are
   present in each lineup and the goal's minute); **(b) finished and not signed**;
   **(c) CONFIRMED** (open its *Odeslání* and note the result and the names under
   the signatures). Do not uninstall anything afterwards: that deletes what this
   checklist is for.

### A · The upgrade

A1. **Install over 0.2.1** (`adb install -r`, or open the file).
   *Should:* it installs as an update, opens on *Zápasy*, the fixtures are there,
   and *Zápis* has the badge *Zápis právě probíhá*.
   *Failure:* "App not installed" or a signature conflict (stop; do not uninstall;
   say so), a crash on opening, empty *Zápasy*, or no badge.

A2. **Zápis → the report in progress.**
   *Should:* the console opens; the clock reads `mm:ss` and carries on from the
   kickoff; *Zápis: N´* is on its own line; the goal and the card are there at the
   minutes you noted.
   *Failure:* *Před výkopem* (the kickoff is lost), the clock at `00:00`, a goal or
   card missing or at another minute.

A3. **Zpět from that console.**
   *Should:* you land in *Sestavy* (not *Zápasy*). Each team reads *Přítomno N z M*
   with N the number you noted, the same people marked, their shirt numbers kept.
   **No captain and no goalkeeper is chosen.**
   *Failure:* someone who was present is unmarked, N differs, or a captain or
   goalkeeper is already chosen.

A4. **Zpět twice more.**
   *Should:* *Hlavička zápisu*, then *Zápasy*; both steps show *Zpět*.
   *Failure:* a step with none, or *Zpět* leaving the report at once.

A5. **In *Sestavy*, choose a captain and a goalkeeper for each team**, then
   *Pokračovat na zápis*.
   *Should:* until both are chosen the problems list says which team lacks which;
   then you are back on the same running match with its goals and cards.
   *Failure:* the match restarts, or anything recorded is gone.

A6. **Open the finished, unsigned report; go to *Rekapitulace*.**
   *Should:* *Poločas* and *Konečný výsledek* are shown and cannot be edited, and
   are the goals' (if you had typed a result under 0.2.1 that disagreed with the
   goals, this is the goals' result, as decided). Each captain is asked again, even
   one who tapped under the lineup in 0.2.1 (that tap moved to the lineup). A team
   with no captain marked has red words (*Kapitán není označen v sestavě…*) and no
   button; mark one in *Sestavy* (*Zpět*) and the button *Potvrzuje <jméno>* appears.
   *Failure:* a captain already shown as signed whom you only tapped at the lineup;
   an editable score.

A7. **Open the CONFIRMED report; *Odeslání*, then *Uložit do zařízení* and open
   the text.**
   *Should:* it is still CONFIRMED and says what it said: the result it was signed
   with (even if the goals say otherwise), the same names under the signatures —
   now with the time after each — and no captain or goalkeeper marks and no team
   notes (the new fields are empty). Its console offers *Pokračovat na hodnocení*,
   not *Ukončit utkání*.
   *Failure:* it asks for signatures again, the result has changed to the goals',
   or *Ukončit utkání* is offered.

### B · The eighteen points

Start a new match from *Zápasy*. The number is the tester's.

1. **The keyboard.** In *Hlavička*, tap *Rozhodčí*, type a letter, tap empty page;
   tap the field again and scroll; tap *Rozhodčí*, press the keyboard's Next, then
   the last key (✓) on the last field. Then a tap outside on each of: a *Dres* in
   *Sestavy*, *Tým* in *Zápasy*, *Hledat* in *Týmy*, *Minuta* in *Gól s jinou
   minutou*, *Důvod* on a card, *NH* and *Poznámka týmu* in *Hodnocení*.
   *Should:* the keyboard goes on a tap that is not a field (a button, chip or
   switch included), on the start of a scroll and on leaving the step or tab; Next
   moves to the next field; the last key drops it; the field you type in and the
   button below it are above the keyboard, and the tab bar has stepped aside.
   *Failure:* it stays up after a tap or a scroll; a field or its button is behind
   it; the tab bar floats above it.
2. **The way back.** *Hlavička zápisu* shows *Zpět* and it goes to *Zápasy*.
   Open a report at its console: *Zpět* walks *Sestavy*, *Hlavička*, *Zápasy*.
   Android's own back does the same. *Zápasy*, *Týmy* and *Nastavení* have none.
   *Failure:* no *Zpět* on the header; a resumed report's *Zpět* leaves the report
   at once; the system back closes the app from inside a report.
3. **The goalkeeper.** Under the squad, *Kapitán a brankář* has a pick-list for
   each; leave the goalkeeper of one team unchosen.
   *Should:* each lists only players marked present; the problems list says
   `<tým>: není vybraný brankář.` and *Pokračovat na zápis* waits.
   *Failure:* an absent player is offered, or you can continue without.
4. **The order.** *Odeslání → Uložit do zařízení*; open the text, the CSV and the
   JSON.
   *Should:* the captain is first, then the goalkeeper, then the rest by shirt
   number; the text writes `(kapitán)` and `(brankář)`, `(kapitán, brankář)` for one
   man who is both, as one row; the CSV and JSON have a column or field for each.
   *Failure:* the captain is not first, or the two roles of one man are two rows.
5. **Číslo RP.** Mark a player with no league RP number.
   *Should:* the row asks for the number *z průkazu, 4–7 číslic*; `59001` is
   accepted; `123` turns the field and the line under it red;
   *Nemá průkaz* turns the same row to *Datum narození*, and *Má průkaz* turns it
   back, **without the next name moving**. A number from the league (where there is
   one) is text, not a field. Start a second match with the same team: the number
   is offered, editable. In *Týmy → soupiska* that player still has **no** RP
   number. In the files, the text keeps one column and the CSV and JSON say which
   kind each value is (`rp_league`, `rp_typed`, `date_of_birth`). **Nowhere does
   the app say *rodné číslo*.**
   *Failure:* the typed number shows up as the player's RP number in *Týmy*; the
   next row jumps when the switch is pressed; the words *rodné číslo* appear.
6. **Mark who came.** *Should:* the lineup opens with *Přítomno 0 z N*; a tap on a
   name marks the player and opens their row (*Dres*, *Číslo RP*); a tap again takes
   it back; the count follows; scrolling a long list keeps the count at the top;
   tapping the second name over and over does not move the third.
   *Failure:* anyone is marked on opening; a row jumps under the thumb; a squad of
   twenty-five is unusable.
7. **Not built: a captain's code.** *Should:* nothing in the app asks for a code or
   a PIN. *Failure:* anything does.
8. **A player not on the list.** *Přidat hráče mimo soupisku*: surname, first name,
   then the number from the card; or *Nemá průkaz* and the date of birth.
   *Should:* the player appears in the lineup already marked present and labelled
   *Dopsán na hřišti*, the others you had marked stay marked, and there is no RP
   field. *Failure:* the date of birth is asked first; marks are lost; the player
   is missing afterwards.
9. **No deputy.** *Should:* *Zástupce kapitána* is nowhere: not under the lineup,
   not in *Rekapitulace*. *Failure:* it is.
10. **The captain's confirmation.** With a captain chosen, *Potvrzení kapitána* is
    one button, *Potvrzuje <jméno>*, for that captain; no list.
    *Should:* the tap reads *Potvrdil kapitán <jméno>*. Kick off **without** the other
    team's tap: the match starts; *Odeslání* lists `Kapitán (<tým>) nepotvrdil sestavu
    před zahájením utkání`; tapping later works and shows the real, later time.
    *Failure:* you are asked who is confirming; kickoff is blocked; or this tap
    already counts as the final signature (S5).
11. **A goal at its own minute.** In the console, *Gól s jinou minutou* under a team.
    *Should:* choose a scorer (only players on the pitch) or none; a minute at or
    before the clock is written, the log lists it in minute order and *Stav* is
    right; a later one is refused in red words (*Tato minuta ještě nebyla odehrána
    (nejpozději N´)*); there is no `60´+`; after *Ukončit utkání* a tap on a player's
    goal asks for the minute. The one-tap goal is still one tap, at the clock's
    minute. *Failure:* a minute the clock has not reached is accepted; goals list
    in the order tapped; the one-tap goal opens a sheet during play.
12. **Taking a whistle back.** Press *Ukončit poločas* by mistake, then *Vzít zpět*.
    *Should:* it asks *Vzít zpět hvizd?* and names it (*Konec 1. poločasu*); *Ano,
    vzít zpět* and the clock carries on without a jump; the half can be ended again
    later. The same for *Zahájit utkání* right after it (the console says *Před
    výkopem* and the kickoff can be pressed again). A goal or card is taken back at
    once, without a question. *Failure:* no way back; no question; the clock jumps,
    stops or restarts; any pause, stop or adjust control appears.
13. **Minutes and seconds.** *Should:* `mm:ss` of play; below it *Zápis: N´*; at
    `30:00` of the first half *Nastavení 1. poločasu · zápis 30´+* and the clock
    counts on; after *Ukončit poločas* *Poločas · zápis 30´+* and the clock **holds**;
    *Zahájit 2. poločas* reads `30:00`; after *Ukončit utkání* *Konec utkání · zápis
    60´+* and the clock is frozen. **This is the first time this is looked at on a
    phone** (the emulator never got past the first minute). *Failure:* no seconds;
    the clock keeps running in the interval; the second half starts at another
    number; the minute line is missing at `30´+`.
14. **A note per team.** In *Hodnocení* each team has *Poznámka týmu* under its
    ratings. Write one note, leave the other empty, continue.
    *Should:* nothing blocks; *Odeslání* shows `Poznámka týmu D: …` / `H: …` in the
    commentary (`—` for the empty one), a row per team in the CSV and a field per
    team in the JSON. *Failure:* the note is missing in a format, or an empty note
    blocks the report.
15. **The result.** *Rekapitulace*. *Should:* *Poločas* and *Konečný výsledek* are
    shown, with a line saying a goal is corrected in the console; there is no field
    (a tap opens no keyboard); add a goal at its own minute in the first half and
    the half-time score changes with it. *Failure:* a score can be typed; the
    result differs from the goals.
16. **Cards.** *Žádné karty nebyly uděleny*, then the *Vzít zpět* beside it.
    *Should:* the block reads as not accounted for again and *Odeslání* asks about
    it; no recorded card is removed by it. *Failure:* no *Vzít zpět*, or it deletes
    cards.
17. **Not built: the referee's code.** *Should:* nothing asks for one.
    *Failure:* something does.
18. **The referee's name.** *Rekapitulace → Rozhodčí*.
    *Should:* one button, *Potvrzuje <the name from the header>*; one tap, no
    keyboard. With the header's referee empty it says so in red (*V hlavičce chybí
    jméno rozhodčího*) and has no button. *Failure:* a name is asked for.

### S · The signatures

S1. **Three taps.** In *Rekapitulace*, *Kapitán domácích*, *Kapitán hostí* and
   *Rozhodčí* each have one button naming who signs: the captain marked in that
   team's lineup, the referee from the header. Tap all three.
   *Should:* each line then reads the name and the time in Prague time
   (*potvrzeno 18. 10. 2026 11:24*); only after the third are the
   missing-confirmation problems gone from *Odeslání*, and the report is
   CONFIRMED. *Failure:* any name is typed; two
   signatures are enough; the home captain is signed under the away captain's name.
S2. **The assessment is free.** With all three given, change the commentary, a
   rating and a team note. *Should:* all three signatures stay. *Failure:* one goes.
S3. **A change asks first.** Still signed, go to the console and write a card (or
   a goal, or *Vzít zpět*).
   *Should:* *Zrušit podpisy?* says the signatures of both captains and the referee
   go; *Ponechat podpisy* leaves **everything** as it was (the card is not written);
   *Ano, zrušit podpisy* does it and *Rekapitulace* asks all three again. Taking a
   whistle back on a signed report is one dialog, with the sentence in it, not two.
   *Failure:* the change is made first and asked about after; two dialogs; the
   signatures survive a change.
S4. **The header and the lineups say it first.** Sign again; open *Sestavy*.
   *Should:* a red line at the top says the report is signed and any change takes
   the signatures back; the first edit (a kit colour will do) removes the line, and
   *Rekapitulace* asks again. The same line is on *Hlavička*. *Failure:* no line,
   or the signatures are still there after an edit.
S5. **Two confirmations per captain.** In the exported text, each captain has the
   confirmation under the lineup *and* the final one, each with a time.
   *Failure:* one is missing, or a captain who only tapped at the lineup makes the
   report CONFIRMED.
S6. **Withdrawing the affirmation.** On a signed report, *Žádné karty nebyly
   uděleny* / *Vzít zpět* ask *Zrušit podpisy?* before they do anything.
   *Failure:* they change the report without asking.

---

## 9 · Checklist — on the iPhone, for the next Mac prompt

Build 0.3.0 (4) on a physical iPhone, as prompt 08 and prompt 11 did: the build
number is higher than 3, so TestFlight and an over-install both accept it. Nothing
below has ever run on an iPhone; each is something the Android phone cannot show.
Run A1 to A7 and S1 to S6 from §8 as well, with the iPhone's own names for things.

I1. **The upgrade from 0.2.1 (3).** Have the three reports of step 0 on the
   iPhone, install 0.3.0 (4) over it, without deleting the app.
   *Should:* it opens, the three reports are there, *Zápis* has its badge, and A2 to
   A7 behave as on Android. *Failure:* it opens empty (the data was not migrated, or
   was wiped), crashes on launch, or the in-progress match has lost players.
I2. **The number pad has no return key.** In *Sestavy* tap *Dres* and a *Číslo RP*
   field; in *Gól s jinou minutou* the *Minuta*; on a card the *Minuta*; in
   *Hodnocení* *NH* and *Čd*.
   *Should:* every one can be dismissed by a tap on the page or by starting to
   scroll, with no key on the pad. *Failure:* any number field whose keyboard stays
   until you leave the screen.
I3. **The action keys of the text keyboards.** *Rozhodčí* (Next), *Asistent* (Done),
   *Příjmení* then *Jméno* in the add-player dialog (Next, Next), *Důvod* (Done),
   *Tým* in *Zápasy* and *Hledat* in *Týmy* (the key ends the search).
   *Should:* the key's label is the one in §3.1 and it does that. *Failure:* the key
   does nothing, inserts a line, or moves to the wrong place.
I4. **The way back without a system button.** *Hlavička* shows *Zpět* and it goes
   to *Zápasy*; a report resumed at its console goes back through *Sestavy* and
   *Hlavička*. *Failure:* no *Zpět* on any step; it leaves the report early.
I5. **The edge swipe.** Swipe from the left edge on *Hlavička*, *Sestavy*, the
   console and *Rekapitulace*. The code does not depend on it. **Write down what it
   does on each.** *Acceptable:* nothing, or one step back as *Zpět* does.
   *Failure:* it leaves the report or the app, lands on a screen *Zpět* would not,
   or leaves a half-drawn screen. That would be a note for iosApp, not a fix here.
I6. **The keyboard over the lineup's lower rows.** In a squad of at least twenty,
   scroll to the last row, mark it, tap its *Číslo RP* field. Then open *Přidat
   hráče mimo soupisku* and fill it in.
   *Should:* the field and its row are above the keyboard (the list scrolls to
   them); in the dialog the last field and *Přidat* are reachable; the tab bar has
   stepped aside; nothing sits under the home indicator. *Failure:* the field is
   behind the keyboard, or the button under it can't be reached.
I7. **The clock after the phone has been locked.** Kick off, lock the iPhone for
   several minutes, unlock. *Should:* the clock has caught up to the real elapsed
   play (it is derived from the kickoff). *Failure:* it stopped, restarted or lost
   the minutes. (iOS cannot run a background timer; this only works because nothing
   ticks.)
I8. **Dynamic Type.** Set the text size to a large accessibility size and mark rows
   in *Sestavy*. *Should:* a row opens without the next name jumping far; hint lines
   may wrap, as DECISIONS records. *Failure:* controls cut off or unreachable.
I9. **Time zone.** Set the iPhone to another time zone (or abroad) and sign a
   report. *Should:* the text and the CSV still print Prague time. *Failure:* they
   print the phone's.
I10. **Export.** *Odeslat na PSMF* (the share sheet or Mail) and *Uložit do
   zařízení* (Files), with team notes, roles and the number kinds in the report.
   *Should:* the text arrives intact, with *háčky*, *čárky* and the `´` minute mark,
   and the three files carry the new fields. *Failure:* broken characters, or the
   new fields missing.
I11. **Ukrainian.** Switch the app to Ukrainian and walk the lineup and the recap
   on the narrowest iPhone you have. *Should:* nothing is cut off. The wording is
   mine and has not been read by a native speaker. *Failure:* clipped buttons or
   dialogs.

---
