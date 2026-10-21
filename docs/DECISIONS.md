# Decisions

Newest first. Each entry records what was decided, why, and what would reverse it.
The reversal condition is the point — a decision without one is a preference.

---

## 2026-10-22 · 0.3.1, Gate 7: one phone checklist, 0.3.1 (5), and the documents brought level (prompt 15)

Closes prompt 15 (`reports/2026-10-21-0.3.1-tester-fixes.md`, §Gate 7). The version is
0.3.1, build 5, on both platforms, as the brief says; the three calls below are the
ones it left open.

**1. One checklist at the end of the report, merged from the six the gates wrote.**
Several steps were the same step in two gates (the lowest row with the keyboard, a large
system font, the Ukrainian bar, the update over 0.3.0). The merged list has 34 steps,
ordered by point with the typing first, as the brief asks. The gates' own lists stay
where they were written, each with a note of which steps it became, because their
reasoning sits beside them. **Reverses if** a tester, or a session working one gate,
needs the checklist next to that gate's reasoning: then hand out the per-gate lists,
which are all still there.

**2. DEMO_SCOPE §3 says a lineup in progress is kept, and is never a lineup.** The brief
names the row that opens (§3) and the controls, the shirt and the whistles (§4). Gate 3
left the file to this gate, and a lineup that survives a kill changes what §3 says the
lineup does. One bullet; nothing about tables or schema. **Reverses if** DEMO_SCOPE is
meant to say only what a screen shows and not what is kept: then the bullet goes.

**3. The entry written from the owner's brief is moved to its date.** "From the testers
of 0.3.0: a lineup row opens downwards, a drawn shirt shows the kit, and one session per
gate" sat between the 17 October entries; the file is newest first, and Gate 1 asked
for it to be put in order. Only its position changed (the file's lines, sorted, are
identical before and after). **Reverses if** an entry's place is meant to show when it
was written and not when it was decided: nothing refers to it by line.

---

## 2026-10-21 · 0.3.1, Gate 6: the app draws its seven icons itself, and `materialIconsExtended` is gone (prompt 15)

Carries out 2026-09-01 "Five icons must not cost 31 MB", which nothing since has
reversed (`reports/2026-10-21-0.3.1-tester-fixes.md`, §Gate 6). The debug APK goes
**70,945,487 → 38,527,991 bytes** and the unsigned release APK **52,514,993 →
30,019,337**; the DEX held 11,385 Material icon classes and now holds none.

**1. Seven icons, not five.** That entry counted five. The code uses seven: *Check*
(the tick in a select and the lineup's presence mark) and *KeyboardArrowUp* (the
fixtures' back-to-filter button) came after it. Three of the seven (*Check*,
*KeyboardArrowUp*, *Star*) belong to `material-icons-core`, which the build had only
as a dependency of the extended set: `compose.material3` does not carry it (checked
in the dependency listing of the JVM compile classpath). Removing the extended set
removes them too, and putting core back would be a dependency added, which the
prompt forbids; so all seven are copied, and no file imports
`androidx.compose.material.icons` any more. **Reverses if** the app wants more than
about a dozen icons (the 2026-09-01 condition), or the owner would rather declare
`material-icons-core` (about 300 files, 2 MB as a jar before dexing) and keep
*Check*, *KeyboardArrowUp* and *Star* on `Icons.Filled`.

**2. The path data is the SVG string Material publishes, read by `addPathNodes` the
first time an icon is shown.** The alternative, a builder call for each segment as
the library's own classes do, is about a hundred and twenty calls full of number
literals (119 path nodes in all, 56 of them the football); a string is one entry
for each icon and can be set beside Material's own SVG. The parser is common code, so iOS reads the strings as the JVM
does, and the JVM read them into the library's own nodes (3). **Reverses if** the
parse shows in a measured first frame (then builder calls), or iOS draws one icon
differently from the JVM.

**3. The copies were proved against the library once, and what stays committed is a
set of numbers.** Before the dependency left, a throwaway test (not committed)
compared each copy with `Icons.Filled.<name>`: the path nodes are identical, float
for float, for all seven (7, 7, 7, 7, 56, 11 and 24 nodes). The two `ImageVector`
objects are still not `==`, only because the library sets three stroke values that
nothing draws (there is no stroke). `PsmfIconsTest` keeps what each rendered at
96 px (ink and centre, 1 % and 0.1 px), Less against More upside down, a solid
star against a hollow one, and a probe for the extended set on the classpath.
**The limit:** a small feature moved by half a unit (the football's notch) is under
those tolerances and passes; a point moved by a unit on a large icon, or by a
quarter of a unit on the tick, does not. **Reverses if** an icon is edited or
added: measure it from its source, and tighten the tolerances if a Skiko upgrade
shows they can bear it.

**4. The guard is a test of the classpath, not of the imports.** Without the
library the compiler already refuses an import; the test refuses the dependency
itself, which could come back before any file imports it, with its eleven thousand
classes. It looks for `SportsSoccerKt`, which only the extended set has. **Reverses
if** the app has a real use for the extended set: then declare it, delete the test,
and settle the release build's shrinking first (6).

**5. Where it lives, and the licence.** `ui/common/PsmfIcons.kt`, beside `KitShirt`.
The Material icons are Apache-2.0: the notice heads the file, and the licence text
is `composeApp/licences/Apache-2.0-MaterialIcons.txt`, beside the two OFL texts for
the fonts, copied verbatim from Ubuntu's `/usr/share/common-licenses/Apache-2.0`.
The copyright line says "The Android Open Source Project" with no year: the jars
carry no notice file, so a year could only be guessed. **Reverses if** the licence
review, or a store, wants the notices shown in the app: then a screen lists
`licences/`.

**6. The release build was never shrunk.** 2026-09-01 said R8 strips unused icons
from a release build, so that most of the cost was a debug artifact. `androidApp`
sets `isMinifyEnabled = false` for release, so the unsigned release APK carried all
11,385 icon classes too (52.5 MB). That does not change this decision; it means the
"R8 will handle it" argument was never true here, and that a release with
minification, and the keep rules it needs, is still its own piece of work (TODO,
"A release build"). **Reverses if** minification is switched on and the extended
set would then cost nothing: that would make (1) a matter of taste, not of size.

---

## 2026-10-21 · 0.3.1, Gate 5: each team's kit colours on the console, as a shirt drawn in code from a table of names (prompt 15)

The testers' point 6 (`reports/2026-10-21-0.3.1-tester-fixes.md`, §Gate 5). Nothing
earlier is reversed: it carries out the owner's decision of this date ("a small
shirt, drawn in the colours of the kit worn in this match"), and the rule that a
report says what was written, never what a table says.

**1. The shirt is drawn, one band for each colour the kit lists.** On a `Canvas`,
no icon library (as the card glyph is, 2026-09-01). The body is cut into equal
slices, the first colour on the left, and a sleeve goes with the band beside it, so
two colours show as two and three as three. The pattern is a choice, because the
label does not say one: *bílo-modrá* does not say halves, stripes or hoops, and
slices claim the least. An outline in the theme's outline colour is always drawn,
so a white shirt shows on a light screen and a black one on a dark one (a test
looks at the pixels of all twenty, on both themes and on both grounds the shirt
sits on). **Reverses if** PSMF's data ever says what the pattern is, or referees
read the slices as a pattern the kit does not have.

**2. Where it goes.** Under each name on the scoreboard (the home side at the
start edge, the away side at the end, where each name is aligned); before the name
on each team's tab, on the console and in *Sestavy*. Not under the name on the tab:
a tab with a picture over its text is taller, on a screen that is open all match.
The scoreboard grows by about 18 dp (14 at a 130 % font; measured, the tabs go
from 300 to 318 dp down the screen). In *Sestavy* the tab shows the kit
picked in the chips, so choosing the other chip changes the shirt; on the console it
shows the kit the lineup recorded. **Reverses if** the scoreboard is too tall for
the lists under it (then the shirt goes beside the name there too).

**3. The table is in `composeApp`, and a name it lacks draws nothing.** Twenty names
(those league 6 uses), each the colour of its name: *světle* lighter and *tmavě*
darker than the plain name, *rudá* a deeper red than *červená*, *zlatá* duller than
*žlutá*. Case and the spaces around a name do not matter. Nothing in `shared/` or
the export reads it, and it could not: it is in the other module. **The colours are
my reading of the names; no one at PSMF or among the testers has confirmed them**
(the test pins each to its colour family, not to a hex value). **Reverses if** a
referee sees a colour that is not their team's: change the row.

**4. An unknown colour is an empty band, and a kit with none known is no shirt.**
For *bílo-tyrkysová* the white is drawn and the rest of the shirt is left empty. The
alternative, drawing only what is known, would show a plain white shirt for a
two-colour kit, and the referee would trust it. A kit with no known colour draws
nothing and takes no room, so the screen is as it was before. **Reverses if** an
empty band confuses more than it warns: then a partly known kit draws no shirt.

**5. The console is shown a kit only while it can be vouched for.**
`ConsoleTeam.kit` is the team's own kit that the lineup names, and only while it
still goes by the label the lineup wrote down. A kit the team no longer has, or one
renamed since, may not be what was worn, and a wrong colour is worse than none. The
importer keeps a kit's id by its label, so a rename there mints a new id and the
lineup's kit no longer resolves; the label check covers an edit that keeps the id.
The report is unaffected either way: it reads the snapshot. **Reverses if** kits
are renamed so often, with their colours unchanged, that shirts vanish from matches
in progress (then compare the colours instead of the label).

**6. The shirt is described by the kit's label** for a screen reader. Whenever a
shirt is shown this is also the lineup's snapshot, by 5. **Reverses if** a Czech
screen reader reads a label like *černo-bílá* badly (then a longer description in
the strings, which would have to be written in three languages).

---

## 2026-10-21 · 0.3.1, Gate 4: the console's controls laid out on purpose, the Ukrainian tab, and the whistles in the log (prompt 15)

The testers' points 4 and 5, and the review's *the log does not list the
whistles* (`reports/2026-10-21-0.3.1-tester-fixes.md`, §Gate 4). Nothing earlier
is reversed. The clock rule is untouched: there is still no pause, stop or
adjust anywhere, and the screen test that looks for one passes unchanged.

**1. The whistles are one row of equal buttons; Undo has a line of its own.**
Every state has the same two lines. The first is what moves the match on: the
end of the half, the start of the next, the end of the match, or the button
that carries on to the assessment (the kickoff before the match). One or two
buttons, as wide as the lists below them, the same width and the same height
where there are two, `EvenActionRow`. A label that does not fit on one line
wraps *inside* its button, at a space, and its neighbour grows to match. The
second line is Undo. Rejected: one row of three (Undo, a half's end, the end of
the match) did not fit at 360 dp even at the standard font (0.3.0's own render
showed the end-of-match button squeezed to a strip), and `ActionRow`'s wrapping
was the ragged look itself; full-width buttons stacked one under another
were regular and cost 180 dp of a screen that is open all match; a row that
decides between side by side and stacked by measuring the labels would lay out
differently from one state to the next, and "the same in every state" was the
point. The price of the chosen one: with one-line labels the first half's controls are
132 dp tall (124 before; 144 when the labels take two lines), and the state before
the kickoff is a button's height (60 dp) taller than it was.
**Reverses if** a referee on a phone finds the console's top too tall for the
list under it, in which case Undo goes beside the whistles again at the cost of
one of them wrapping below.

**2. Undo is apart, quiet, at the start edge, and only as wide as its word.**
Apart: 12 dp above it where the whistles have 12 dp between them. Quiet: text in
the secondary colour, no outline, no fill (a test looks at the pixels, so it
cannot be turned back into a button by accident). Its word starts where the
whistles' edge does. **Not full-width**: Undo takes back the last goal at
once, and a stray thumb on a target as wide as the screen would. It is at the
left, where a right thumb reaches least, which is where a control for mistakes
belongs; that is the 0.3.0 place too. **Reverses if** referees reach for it more
than for a whistle, or find it too easy to miss (then it is a button, and the
test of the pixels changes with it).

**3. The ordinal does not part from its noun.** *Zahájit 2.⎵poločas* and
*Розпочати 2.⎵тайм* have a no-break space after the number: at 360 dp and 130 %
the label wraps, and a break after "2." leaves the number alone on the first
line. One character in two strings; the English *Start half 2* has no such
break to prevent. **Reverses if** a native reader would rather have it
otherwise.

**4. The Ukrainian Settings tab says *Опції*; the screen's title is still
*Налаштування*.** The brief's choice between a shorter word, a break between
words (there is only one word) and something else. The measure, in the app's
Noto Sans at the bar's 12 sp: the four labels take 325 dp of text at 130 %, 32
dp of tab padding and 14 dp for the dot on the Report tab, 371 dp against a
360 dp phone, so **no layout shows *Налаштування* whole at 360 dp and 130 %
without shrinking the text or leaving about 1 % to spare**, which a different
text stack on a real phone would spend. *Опції* has 69 dp to spare there and
still fits at 150 %. Considered: *Параметри*, which is the nearer word and has 20
dp (5 %) to spare at 130 % and breaks at 150 %. **Not read by a native
speaker**; it is one string (`tab_settings` in `values-uk`) and a comment says
so. **Reverses if** a Ukrainian reader prefers *Параметри* or anything else of
up to nine letters; the bar will take it at 130 % on a 360 dp phone.

**5. The bar gives each tab a quarter, unless its label needs more.**
`TabBarRow` / `shareOutTheWidth`: equal shares; a tab whose label needs more keeps
what it needs, the others share the rest, repeated until none falls short; if
the labels together need more than the bar has they are scaled together (which is
where a word may break). Czech and English need no more than a quarter, so
their bars are the four equal quarters they were. It is needed whatever the
word in the Settings tab: at HEAD *Протокол* with the dot on its tab broke in two
at 130 % on a 360 dp phone, too. **Reverses if** a bar whose tabs differ in width
confuses referees who change language; then the bar is four quarters and the
labels are chosen to fit.

**6. The log lists the whistles, by the form's minute, in the quieter colour.**
Kickoff `0´`, the end of a half `30´+`, the start of the next `30´`, the final
whistle `60´+`: the minute the console's clock writes at each (`PlayClock.minuteAt`),
so a whistle needs no instant and one pressed before 0.3.0 is listed too. They
stand among the goals and cards by that minute and, within a minute, by recording
order: the end of the half after everything written `30´+` before it, a goal
written `30´+` in the interval after it, the start of the next before the second
half's first minute. The words are Undo's question's (one function names a whistle
for both). **They are not the same order as Undo's**: Undo walks back by recording
order, the log by minute, as it always was for goals. A test holds the two
together where it can: the log lists exactly the whistles Undo walks back through,
with the same period numbers. **Reverses if** referees want the log in strict
recording order, so that Undo's next step is always on top.

**7. *Save to device* and *Send to PSMF* are laid out the same way.** The brief
said to change the other `ActionRow` users only if they showed the same fault.
Five hold one `PrimaryAction` and cannot (a lone button right-aligned at its own
width is what it was). Export's pair showed it: at 360 dp they stacked,
right-aligned, 205 and 197 dp wide and 56 and 64 tall in Ukrainian. They are
`EvenActionRow` now, and `PrimaryAction` centres its text and takes a padding.
**Reverses if** `ActionRow` is wanted back for it: the test names what a
right-aligned pair got wrong.

**8. A test that could not fail is gone.** `theTabsStillReadAtAOneHundredAndThirtyPercentFontScale`
put its width box *outside* the density it then changed (so the bar was about 156
dp wide, not 411) and asserted `lineCount <= 2` of a label whose `maxLines` is 2:
it passed at HEAD, where the word is broken. `TabBarTest` asks where the lines
broke, at a true width. **Reverses if** never: it is the repo's own rule that a
test that cannot fail is worse than none.

---

## 2026-10-21 · 0.3.1, Gate 3: a lineup in progress is kept in a draft of its own, and is never a lineup (prompt 15)

The owed item from the review of 0.3.0 (`reports/2026-10-21-0.3.1-tester-fixes.md`,
§Gate 3). It ends one half of *Marks are written once the block is whole*
(2026-10-17, *The lineup, as built*) and keeps the other: **the report's block
is still written only when it is whole**; what is no longer true is "killing the
app between the first tap and the last loses them". Nothing in 2026-10-17 is
reversed.

**1. The draft lives in two tables of its own, `lineup_draft` and
`lineup_draft_member` (schema 8), and not in `lineup_record`.** A row in
`lineup_record` *is* the team's lineup: `Match.homeLineup` is read from it and
the console, the export and the signatures read that, and the type
(`Lineup`) refuses to hold a block that is not whole. Putting a half-marked
squad there would make every reader responsible for telling it from a finished
one, and one that forgot would take a squad of three for the team. In tables
that nothing but the lineup screen's two use cases (`SaveLineupDraft`,
`RestoreLineupDraft`) reads, the draft cannot be mistaken for a lineup by
construction: the types (`LineupDraft`, `DraftedMember`) are in `data.match`,
not `domain`, so nothing the report is built from can hold one. Delete every
row in them and no report changes. The brief allowed a schema change "if the
work needs one"; it could have been done without (a JSON text per match and side
in `preference_record`), and I did not, because that table is documented as the
app's two settings, a match deleted would need a key-prefix delete to take its
draft, and the repository's own rule is rows over blobs (`MatchRecord.sq`'s
header). `7.db` was recorded first, `7.sqm` creates the tables and touches no
row, `8.db` generated, and `SchemaMigrationTest` opens a report written by
0.3.0 across it. **Reverses if** a draft has to be something other than
per-match rows -- shared between devices, say -- or schema 8 proves a burden on
the iPhone that a settings row would not have been.

**2. The draft is the screen's working copy, written whole on every change and
before the lineup is.** Every member of the squad is in it, marked or not: a
row whose mark was taken back keeps what was typed in it, as it does in the
view model (Gate 2). The text in each field is kept as typed, half-typed
included, **and so is what that text wrote in the `Číslo RP` column**, because a
row read from a saved lineup has a value written and an empty field, and
working the value out again from the empty text would erase it. Players, not
appearance ids: the ids goals and cards point at are the report's, and a
draft cannot change them. Written *before* the lineup, so that a draft is
never older than the lineup written from it; the other order could leave the
screen coming back as an earlier state than the one the console is reading.
The one place it is the other way round is adding a player, where the use case
writes the lineup itself and the draft follows at once. **Reverses if** the
write on every keystroke is slow on a phone (it is one transaction of about
twenty-five small rows) or a draft has to carry something the view model's
state does not.

**3. The draft is put back over a lineup built from the report, not the other
way round, and never trusted past it.** `BuildLineupEntry` is unchanged: the
report says who is in the squad, which appearance ids exist and what the names
are. A draft then says what the screen showed -- marks, roles, numbers, text,
the kit, the captain's tap. A player or a kit the report no longer has is
dropped (a captain who has left the squad list is nobody, not a stale id), a
player the draft knows nothing of stays as built, and a team with no draft is
as built -- which is how every report written before this opens. **A draft is
kept after the lineup is whole**, not deleted: one way to reopen, and the text
in unmarked rows survives. **Reverses if** something other than the lineup
screen ever writes a lineup: the draft would then be older than the report and
the screen would show it over a newer one.

**4. Writes are queued.** The view model takes a lock around each draft and
lineup write, so that two quick edits finish in the order they were made. The
repository writes off the main thread, and without the queue the edit that
finished *last*, not the one made last, was what the disk kept; the same
unqueued writes also read and replaced `match` in turn, which two at once may
not. A latent fault of 0.3.0 that this gate makes matter more, because a draft
written out of order would be a screen that comes back wrong. **Reverses if**
the queue is seen to hold up a tap (it is only the writes that wait).

**5. A restored team is not written on opening.** A draft whole at the moment
of the kill, whose lineup write had not landed, comes back whole on the screen
and the report has the earlier lineup, or none, until the next edit or
*Pokračovat* writes it. No "healing" write on opening: that would change the
report -- and take the signatures back -- from a screen the referee has only
looked at. The window is the write between the draft and the lineup. **Reverses
if** a referee can be seen to go to the console from a screen that looked
whole without pressing anything.

---

## 2026-10-21 · 0.3.1, Gate 2: a squad row is one line until the player is marked, and opens downwards (prompt 15)

The testers' third point on 0.3.0 (`reports/2026-10-21-0.3.1-tester-fixes.md`,
§Gate 2). The correction to prompt 13's brief is already recorded in the entry
*From the testers of 0.3.0* below; these are the calls made in carrying it out.

**1. An unmarked row draws only the mark and the name.** The shirt number, the
`Číslo RP` field and *Nemá průkaz* are drawn when the player is marked and not
before, where 0.3.0 drew them for everyone, invisible and disabled, to hold the
room. A row that has not been marked is 57 dp from the next (a touch target and
its hairline; 164 dp at HEAD, 230 dp at font scale 1.5). What was typed in a
row lives in the state the fields are drawn from, which keeps it, so it is
there when the row opens again: pinned through the real ViewModel and on the
screen. **Reverses if:** a field has to exist while its row is closed, for
instance to keep the focus across unmarking.

**2. The rows of the squad touch.** The list gave every item a 12 dp gap, rows
included, so between two names there was a strip that was neither: a thumb
falling in it did nothing, and it put 12 dp on the 57 of every closed row.
The list now has no gap of its own; the items around the squad carry theirs
(`Spaced`). **Reverses if:** referees miss their name and hit its neighbour
more often than they did, on a phone: the only boundary between two rows is a
hairline, and each is 56 dp.

**3. The first line is top-aligned, not centred.** The name must not move when
the field beside it appears. Where the field is taller than the name -- at a
large font, and by a little at the standard one -- centring pushes the name
down by it: measured, 4 dp at font 1.0 and 12 dp at 1.5. Top-aligned, the name
is where it was and the field hangs a little lower than the name's own centre.
**Reverses if:** that offset looks wrong on a phone and a way is found that does
not move the name (the field lifted by the difference, which clips if
anything animates around it).

**4. No animation.** One was built (the row's height, 150 ms) and taken out.
A tap on the next name, made while the row above was still opening, was lost
in the JVM harness (clock frozen 60 ms in): the name had moved 15 dp, and a tap
on the row that was opening, which had not moved, registered. A tap whose
release lands outside its target is cancelled, which is the likely mechanism;
I did not read the framework to confirm it. It is not a tap *waiting* for the
animation; it is a target in motion, which an animation always makes. The brief made the animation
optional ("fine if a tap never waits for it"), and a phone cannot be asked
here. Rows open at once, as the fields did in 0.3.0, and `SquadRowTest`
pins that two frames after a tap the next name is where it will stay.
**Reverses if:** a phone shows the opening to be too abrupt to follow *and* a
way is found to open it that never moves a row under a finger (fading the new
fields in, the room already made, would qualify).

---

## 2026-10-21 · 0.3.1, Gate 1: typing lands at the cursor, and only a finger on the list takes the keyboard away (prompt 15)

Two of the testers' points on 0.3.0, both reproduced on the JVM before they
were changed (`reports/2026-10-21-0.3.1-tester-fixes.md`, §Gate 1).

**1. The cursor is the referee's, the text is the screen's** (`PsmfTextField`).
Every field in the app gets its text from a ViewModel's `StateFlow`, so a
field is told what it asked for a frame after it asked. Material's `String`
text field keeps a `TextFieldValue` of its own and, whenever it is recomposed
with a text that differs from its own, fits its cursor to that text *and
writes the fit back*. Recomposed once with the old, empty text after "M" was
typed, it wrote the cursor at 0 back, and "arek" went in front of the "M":
"arekM", to the letter what the testers saw. `PsmfTextField` now keeps the
`TextFieldValue` itself, shows the screen's text with it, and never writes
the fit back. A change the screen refuses, and a text it sets itself, still
show as the screen says. **Reverses if:** a state may arrive after the *next*
keystroke, which is a longer wait than this was built for -- the field then
shows the IME the older text and a character is lost, as it was before. Fixing
that means treating the typed text as the truth until the screen has answered,
which needs a way to tell a late answer from a refusal, and is not built.

**2. A scroll is the referee's when a finger moved it** (`KeyboardDismissal`).
The dismissal took any `NestedScrollSource.UserInput` scroll for the referee
scrolling, on the belief that a list scrolling a focused field into view is
not `UserInput`. It is: measured on the JVM, the foundation's bring-into-view
reports `UserInput` through the nested-scroll chain exactly as a finger does,
so the keyboard went the moment it came for a field low on the screen. Now a
scroll counts only when a finger has also moved past touch slop since it last
touched down. That is read in the pointer handler's first pass, and kept
until the next touch rather than cleared on lift, because the list hands a
drag's last distance on after the event that carried it and a quick flick is
over by then. A tap outside a field, leaving the step and the action key
dismiss as they did. **Reverses if:** a referee scrolls with something that is
not a finger on a touch screen (a mouse wheel, a hardware keyboard, an
accessibility action) and expects the keyboard to go; or a later foundation
reports the list's own scrolls as something other than `UserInput`, in which
case the finger test is redundant and harmless.

**3. Room for the control under the field.** The outcome was "the field and
the button under it are above the keyboard", and the foundation scrolls a
focused field only *just* into view, leaving it flush with the keyboard.
When the area a screen lives in gets shorter while a field has the focus --
the keyboard coming up -- the field is now kept in view with
`minTouchTarget + itemSpacing` (68 dp) below it. Fields report their focus to
`KeyboardDismissal` for this. **Reverses if:** a field has more than one
control under it that belongs with it, or it scrolls a screen for nothing
where nothing sits under the field. It is one constant, `roomBelow`.

---

## 2026-10-21 · From the testers of 0.3.0: a lineup row opens downwards, a drawn shirt shows the kit, and one session per gate

**The lineup row: a correction to my own brief.** Prompt 13 said rows must
not jump under the thumb while the referee is marking. The session read
that as "nothing below may move". It gave every unmarked player the full
height of a marked row, about 160 dp of blank, so a squad of twenty became
four screens of scrolling, and the testers found it. What was meant: the
row being tapped stays where it is. Rows below opening downwards is what
every list does, and that is fine.

**The kit's colours on the console** (owner, from the testers). A small
shirt, drawn in the colours of the kit worn in this match, goes under each
team's name on the scoreboard and on the team tabs. `Kit.colours` exists for
exactly this ("for the app only"). The report keeps the label snapshot and
never reads a colour. If the app has no colour for a name, it draws nothing:
a wrong colour is worse than none, because the referee would trust it.

**One fresh session per gate** (owner, 2026-10-18). Every step of a long
session re-sends its whole history, and Sonnet managed about three gates per
five-hour window that way. Gated prompts are now written to run cold:

- a reading list for each gate;
- one commit per gate, its subject starting "Prompt N, Gate M:";
- an uncommitted tree at the start is an interrupted attempt;
- named STOP conditions instead of questions.

The owner also wants gates to run overnight without anyone typing "go". The
runner for that is designed but not built (TODO).

**Reverses if:**

- referees would rather the rows never move, at the cost of the scrolling;
- the colour table misleads more than it helps, for example where a kit's
  name does not match what the team wears;
- a cold session misses context a long one had, gate after gate.

---

## 2026-10-18 · The assessment and the recap: a note per team, a result from the goals, three taps, and what takes a signature back (prompt 13, Part 4)

**1. A note per team.** In *Hodnocení*, under each team's ratings: optional, for
a complaint or a protest the team's captain asks the referee to note. **The
referee types it**, being the only recorder. It is exported in the commentary
block, labelled per team in fixed Czech (*Poznámka týmu D*, *Poznámka týmu H*),
as a row per team in the CSV and a `note` field per team in the JSON. Both
lines are always written -- *—* when there is none -- so that a reader can tell
"none was given" from "left out". It is **never a readiness problem**, and no
signature attests it: it stays editable until export, like the commentary.
Stored in two new columns of the unreleased schema 7 (`home_note`, `away_note`),
empty for every match already written. **Reverses if** the note should say whose
it is: it is labelled by team, not by the captain, because the referee types it.

**2. The result comes from the goals.** The recap **shows** *poločas* and
*Konečný výsledek* and a line says where a goal is corrected (the console: Undo,
or a goal at its own minute). `Match.resultFromGoals()` is the one derivation:
full time is the goals; half time is the goals at a minute no later than `30´+`,
whenever they were written, or -- for a match that never reached the break -- the
score so far. Removed as dead: `ScoreDisagreesWithGoals`, `MissingResult`,
`ResultDraft`, `RecordResult`, `ResultProblem`, the recap's four score fields,
and the export screen's two strings for them. **Old reports (the call the brief
left to me):**

- **A report that is CONFIRMED keeps what it says.** `resultForReport()` reads
  the typed result stored on a CONFIRMED report, so its export is what it was
  when it was signed, even if its goals add up to something else.
- **Any other old report says what its goals say**, and the typed result stored
  on it is dead data: still in the table, never read, never deleted. Why: nothing
  can edit a typed number any more, so one that disagrees with the goals could
  never be corrected in the app; the goals are what was recorded; and keeping the
  typed one would bring back exactly the disagreement this removes. A referee who
  had deliberately typed a result their goals did not make adds the missing goal in
  the console, which Part 3 made possible after the whistle.
- A report that is signed under the new rules has no typed result and says its
  goals; nothing can change its goals without taking the signatures back (4).

**3. Three taps, under names already known.** The home captain, the away captain
and the referee each confirm by one tap on a button that **shows the name it signs
with**: the captain marked in that team's lineup, the referee from the header.
Nothing is typed and there is no deputy switch (`Confirmation.asDeputy` stays in
the model for what 0.2.1 wrote). These are the **final** confirmations, separate
from Part 2's tap under the lineup, and all three make the report CONFIRMED.
A team with no captain marked -- a match upgraded from 0.2.1 -- has no button, only
what to do (go back to the lineups and mark one): there is nothing to type instead,
because the point is that the name is the lineup's. **The report shows both of each
captain's confirmations, each with its time**: under each lineup (*Podpis kapitána
před zahájením utkání*) and in the signatures at the end, in the TXT, as a name and
a time cell in the CSV, and as instants in the JSON. The times in the TXT and CSV
are **Prague time whatever the phone's zone is**, because the report is Czech and
a time that read two ways would make one report say two things. For a CONFIRMED
report written before 0.3.0 the TXT gains that time after each name: it was stored
all along, and the names are unchanged. **Reverses if** a referee has to sign for a
captain who is not the one marked: the captain is changed in the lineup, which
takes the signatures back (4).

**4. Taking things back.**

- **The "no cards" affirmation can be withdrawn** (*Vzít zpět*): the block is
  *not accounted for* again -- not "none", not "some" -- which is what it was
  before the referee said anything and what the readiness check then asks about.
- **What the signatures attest** is the header, the lineups (not the captain's
  own confirmation under one, which is a signature and not content), the goals,
  the cards with that affirmation, and the whistles. **A change to any of them
  takes every final confirmation back**, and a CONFIRMED report is FINISHED again
  and the recap asks for the three once more. The signatures given so far are
  taken back when only some were given: they attested an older report. A save
  that changes nothing a signature attests -- the assessment, the commentary, a
  team note, the same lineup written again -- leaves them where they are, and so
  does a change that is refused. One function, `afterAChangeFrom`, which every use
  case that changes the report passes through, so that no path forgets it.
- **The assessment, the commentary and the team notes stay editable until export
  without touching a signature** (A6).
- **The referee is told before it is saved.** Where there is one moment to ask --
  a goal, a card, an Undo, a whistle, the affirmation withdrawn -- a question says
  that the report is signed and the change takes the signatures back, and nothing
  is saved until it is answered. **The header and the lineups save as you type**,
  so there is no moment to ask: a notice at the top of each says it before the
  first edit, and the first edit takes the signatures back. **Reverses if** a
  referee loses signatures by editing a name by accident: then those two screens
  get an explicit *edit the signed report* step first, which would make the most
  common edit (a typo) one tap longer.
- **This reverses a Gate 3 decision**: a signed report's whistles were not
  offered. They are now, like the other things the signatures attest, and the
  question that names the whistle says in its own words that the signatures go
  with it: one dialog, not two.

---

## 2026-10-18 · The console: a goal at its own minute, a whistle taken back, minutes and seconds (prompt 13, Part 3)

**1. A goal with its own minute.** The one-tap goal is still one tap, at the
clock's minute. A new button under each team's list, *Gól s jinou minutou*,
opens a sheet -- scorer from the players still on the pitch (or none), the
minute entered the way the card sheet takes it: a number, or `30´+`. **There
is no `60´+`**, in the sheet or anywhere a goal is written: the use case
refuses it as well as the sheet not offering it.

- **A minute later than the clock is refused, not clamped.** A goal moved to
  another minute without being asked is a goal in the wrong place on a report
  the referee signs; a refusal says which minute is the latest. The latest is
  the clock's own reading as if the whistle had not gone: `30´+` in the first
  half's added time and in the interval, which keeps a second-half minute out
  of reach until the second half has started. **Reverses if** referees would
  rather have it clamped; it is one line in `LogGoal` and one problem in
  `GoalDraft`.
- **After the final whistle every goal asks for its minute.** The clock has
  none to give (`60´+` is not a goal's), so a tap on a player's goal icon opens
  the sheet with that player already chosen. The bound is the clock **frozen at
  the final whistle**, which needs the whistle's instant (below); a match
  finished before 0.3.0 has none, and the running clock is the bound there --
  weaker, never wrongly refusing.
- **Before the kickoff** the sheet refuses (there is no minute to have scored
  in). The one-tap goal still lands at `0´` there, as it did; that is a mistake
  to undo, not a state to model.
- **The goal list, `Stav` and the half-time score follow the minute written;
  goals at one minute keep the order they were recorded in.** The stored list is
  now in report order (`rescored()` sorts by minute, then by recording
  sequence), so every reader of it -- the console log, the three export
  formats, the recap's suggested result -- gets the same order without sorting
  for itself. The half-time cut was already by minute: a second-half goal
  written with a first-half minute counted for half-time at HEAD too; nothing
  could write one until now.
- **Undo is unchanged**: it takes back the last thing *recorded*, whatever
  minute it carries.

**2. A whistle taken back, in recording order.** A whistle is one more thing
recorded, with a place in the same order goals and cards have, and Undo takes
back the last thing recorded whichever kind it is. **Nothing pauses, stops,
resumes or adjusts anything; nothing edits an instant.** Taking a boundary
back removes it from the instants the clock is worked out from, and the clock
is what those that remain say: end the first half by mistake and take it back,
and play simply went on -- no gap, because the clock never learned of one. A
red card's ten minutes *of play* follow without anything being recomputed, as
they are worked out on demand from the clock.

- **It asks first, and says which.** A goal or a card still goes at once; a
  whistle brings up *Vzít zpět hvizd?* naming it (*Konec 1. poločasu*,
  *Výkop*, ...), because it changes the clock and the referee may not have
  expected Undo's next step to be one.
- **The price of one order:** a whistle with goals recorded after it is taken
  back only after those have gone, one Undo each. A mis-tapped half-time
  noticed three goals later costs three taps. **Reverses if** referees need a
  whistle out of order, in which case Undo becomes a list of what has been
  recorded, newest first.
- **A confirmed report's whistles are not offered**: changing the clock under
  signatures is for the part of the app that withdraws them (part 4). Goals and
  cards on a confirmed report go as they did.
- **Whistles pressed before 0.3.0 have no place in the order** and no invented
  one: they come back only after everything recorded since, last pressed first
  (final whistle, start of the second half, end of the first, kickoff). The
  final whistle had no instant either, so a match finished under 0.2.1 has
  none.
- **Pressing the final whistle twice is one whistle**: the second changes
  nothing and does not take a later place in the order.
- **Schema 7 amended, not an 8.** Version 7 has not shipped (0.2.1 is schema
  6), so the five columns -- `kickoff_sequence`, `finished_at`,
  `finished_sequence` on the match, `ended_sequence` and
  `next_started_sequence` on a period break -- are in `6.sqm` with the rest and
  `7.db` was regenerated. A period break can now be taken back, so the table is
  rewritten on every save instead of only added to. **Reverses if** a build with
  the first version-7 schema ever reaches a device: then it is an 8.

**3. Minutes and seconds on the clock.** Two lines, which are not the same
thing, and both stay on screen:

- *Play so far* as `mm:ss`: the first half counts on past `30:00` in its added
  time, **holds in the interval**, and the second half **starts at `30:00`**
  whatever the first added -- so it can read less than the interval showed,
  because that is how the form's minute restarts. Past `60:00` it counts on
  (`61:05`). After the final whistle it is frozen at the whistle.
- *The minute the next thing recorded gets*, as the form writes it: `12´` for
  12:40 (**which minute a moment maps to is unchanged**), `30´+` for the first
  half's added time and the interval, `60´+` once the match is over. It was the
  only thing shown before; it stays, on its own line, with the phase beside it
  (*Nastavení 1. poločasu*, *Poločas*, *Konec utkání*).

What it shows at each boundary is in the report (§5) and pinned by
`ConsoleClockTest` and `ClockReadingTest`. The log and the report keep the
form's minutes. **Reverses if** the seconds turn out to invite a precision the
report does not have; the second line alone is the old screen.

---

## 2026-10-17 · The lineup, as built: who came, two roles, the number from the card, one tap (prompt 13, Part 2)

**The session's calls, carrying out the owner's and my entries below.** The
five the prompt asked for come first.

**1. Rows do not move when marked, and it costs height.** A row that opened
under the thumb would push the next name away after every tap, so every row
lays out the same controls -- the jersey field, the card-number field, the
"no card" button -- and an unmarked one draws them disabled and invisible
rather than leaving a gap. (Drawn, not given a reserved height: a height that
is right at one font scale is wrong at the next. A test measures it at 1.0 and
at 1.5.) The price is a squad row about 160 dp tall whether or not it came, so
twenty names are about 3,200 dp; the count is pinned at the top so it stays in
view the whole way. "Nemá průkaz" is the same thumb one control over, and it
must not move the next name either, so the two fields a row can ask for each
carry one line of supporting text -- *z průkazu, 4–7 číslic* under the card
number, *např. 18.5.1992* (then the date as it read it) under the date of
birth -- and each is short enough to stay one line at 360 dp, where a hint a
few characters longer took a second line and the row grew by it (a test sets
it at that width and fails by one line when the hint is lengthened). A very
large system font can still wrap one of them. The compact form of a date
(`18051992`) is still read and is no longer advertised: the longer hint is
what wrapped. **Reverses if** a referee finds the blank space
worse than the jump, in which case rows open and the list scrolls to keep the
next name where it was: the thing this ruled out.

**2. The captain and the goalkeeper are two pick-lists, not a mark on every
row.** Each lists the players marked present and nobody else, and one person
may be both. Marking each row would have put two more controls in the rows the
referee is working down. Taking a player's mark back vacates a role they held,
so a role is never held by someone who did not come. Both are needed before a
team's block is written, named in the problems list with the team's name.

**3. How the paper form marks them is not known, so the report says it in
words.** The TXT writes `(kapitán)`, `(brankář)` or `(kapitán, brankář)` after
the name; the CSV has a column for each, `ano` or blank; the JSON has
`captain` and `goalkeeper` booleans. The captain is the first row, then the
goalkeeper, then the rest by jersey number, and one person who is both is one
row. A photograph of a real form (TODO) decides whether the words stay.

**4. A number from a card is four to seven digits.** Five is what every
example on the form has; a date of birth is six, and a typed number can be
told apart from one by its kind (below) rather than its length. Under four
is likelier a jersey number typed into the wrong box than a card number, and
the field beside it is the jersey. Over seven is refused by no one: a real
number a digit or two longer than the examples must not be turned away at the
pitch by an app that guessed. Spaces are dropped (`59 001`); a letter, a dot or
a dash is not a number from a card. No length check can tell a wrong digit from
a right one, so the field says only the rule, in the line under it (*z průkazu,
4–7 číslic*), and draws it red when what was typed is not that; the rule is in
words because a colour alone is not read by everyone.
**Reverses if** PSMF's numbering turns out to be shorter or longer than this.

**5. The upgrade does not invent, and does not undo.** Version 6 → 7
(`6.sqm`, `SchemaMigrationTest`, three matches built the way 0.2.1 wrote them):

- **Presence:** nothing changes and nothing is lost. A saved lineup only ever
  held the players who were present, so the switch from marking who stayed away
  to marking who came reads the same rows.
- **Captain and goalkeeper:** `NULL` for every lineup already written, and a
  lineup read back says so. An upgrade does not give a report a captain nobody
  marked.
- **Confirmations -- the one that needed a call.** 0.2.1 had one record per
  captain, written by the lineup's tap and by the recap's signature alike. For
  a match **not yet CONFIRMED**, a captain's confirmation made before kickoff,
  or on a match whose whistle never went, can only have been the lineup's tap:
  it moves to its lineup with its name, its time and its deputy mark, and the
  recap asks that captain again. For a match that **is CONFIRMED** nothing
  moves: it was signed under the old rules, its export must say what it said,
  and taking a signature from a finished report is rewriting it. A confirmation
  with no lineup to move to is left alone. **Reverses if** the lift is judged
  riskier than leaving them: it is one `UPDATE` and one `DELETE` in `6.sqm`.
- **Pitch-added players:** the table is rebuilt so a date of birth may be
  absent, rows and all.
- **What the export says:** the same, plus new fields. The captain's
  confirmation under each lineup, and the roles, are empty. The *kind* of each
  row's number is present and true, because it comes from a stored column that
  has always been there; a literal reading of "only new fields that are empty"
  would drop it, and it would then be absent from exactly the reports where it
  is most useful.

**The number from the card.**

- It lives on the appearance as what was written that day
  (`IdentificationSource.RP_TYPED`). It never becomes the player's `RpNumber`,
  is never written into league data or `jersey_override`, and nothing presents
  it as PSMF's record: the lineup shows a league number as read-only text and a
  typed one as an input. `RememberedCardNumberTest` and the model both keep it
  so: `Player.addedAtThePitch` has no parameter for a number, and the typed
  one is a `ReportedIdentification`, which is not an `RpNumber`.
- Remembered on the device for the next match, as the date of birth is
  (`remembered_card_number`): typed once, offered next time, editable, and a
  row already written keeps what it wrote even if the remembered one changes.
- **No rodné číslo** as a field, a hint or a label, in the screen or in the
  export. The old `BIRTH_NUMBER` source still exists in the model for
  A28's sake and is exported as "other"; nothing produces it.
- JSON and CSV say which of three each value is: the league's RP, the RP as
  typed, or the date of birth (`rp_league`, `rp_typed`, `date_of_birth`; the
  CSV in Czech). The TXT keeps the form's single column.

**"Nemá průkaz" turns the same row to the date of birth.** With a card, and no
number from the league, the row asks for the number; with no card, it shows the
league's own date of birth if there is one and asks for it if there is not. A
player the league has an RP number for shows it, read-only, until they say they
have no card.

**Adding a player takes surname, first name, and the number from the card or,
with a switch for no card, the date of birth.** Their record may now have no
date of birth, because the number is the appearance's. Adding works on the
entry the screen holds and not on one rebuilt from the database: a block is
only written once it is whole, so a referee who has marked eight players and
not yet chosen a captain has marks that exist nowhere else.

**The captain's tap under the lineup is its own record.** "Potvrzuje <name>",
no picking and no deputy switch, written on the lineup with its real time --
late or not -- and **not** counted as the signature under the finished report.
A different captain withdraws it: it said that the person who was captain then
confirmed this lineup. A later change to the lineup does not withdraw it, as a
late player added to the paper after it was signed does not either.
`LineupNotConfirmed` is listed by the export, like every problem it lists, so
it **does stop the report being sent** until the tap is given -- and it never
stops anyone starting the match. It is raised only for a lineup that has a
marked captain: an older one has nobody it could be waiting for, and a report
that was fine to send must not stop being so because the app learned a rule.
**Reverses if** a missing tap should be a warning and not a block on sending.

**Marks are written once the block is whole.** Presence, a captain, a
goalkeeper and an answer in every row's `Číslo RP`. Until then the marks live
on the screen, as a duplicate jersey number always did. Killing the app between
the first tap and the last loses them, which is a minute's work and nothing a
captain has signed.

**Reverses if** none of the above; each has its own condition.

---

## 2026-10-17 · The keyboard goes away, and the way back is always there (prompt 13, Part 1)

**Mine, the session's calls on the tester's points 1 and 2.** Both were
checked on the Android emulator at HEAD (0.2.1) before anything was changed,
and both are there too: a keyboard opened for one field stayed open after a
tap on the page and after a scroll (`mInputShown=true` both times), and the
header had no Zpět.

**One place makes a text field: `PsmfTextField`.** Thirteen call sites made
a field (one of them a pick-list), three of them chose an action key, and
nothing in the app ever let go of focus. A rule each field must remember is broken by the
next field, so the rule is in the wrapper and `KeyboardTest` fails the build
if a field is made any other way (a count of raw `OutlinedTextField(` calls
per file: the wrapper's one, and the fixture filter's select box, which is a
read-only field that opens a menu and raises no keyboard).

**What ends a keyboard, everywhere:**

- **A tap that is not in a text field**, a button, chip or switch included.
  The obvious way to write this, a tap handler on the parent, never hears a
  tap a child has consumed, and every control consumes its own: the keyboard
  would stay over the screen the referee had moved on from. So the handler
  listens in the first and last pass and consumes nothing, and asks "is the
  tap inside a field?" from the fields' own positions. It does not ask which
  field has focus: a tap on a second field and the focus change it causes
  arrive in one dispatch, and the order they are noticed in is not a thing to
  build on.
- **The start of a scroll** by a finger. Not a fling carrying on, and not the
  list scrolling itself to keep the focused field in view.
- **Leaving the step or the tab**, by any route: the tap, the system back, or
  the screen moving on by itself.
- **The action key.** `Next` moves to the next field. `Done`, `Search`, `Go`
  and `Send` drop focus and the keyboard. The platform's `Search` does
  nothing at all, which is what the fixtures and Týmy search boxes had. The
  last field of a form says Done; a field edited by itself (a jersey number
  in a roster row, a date of birth in a lineup row) says Done; the multi-line
  commentary keeps its return key.
- **A number pad has no action key on iOS**, so for it the three above are the
  only ways. That is what "dismissable without a return key" comes to.

A dialog is a window of its own and the screen's handler never hears a tap in
it, so each dialog's body carries its own (`KeyboardDismissal`).

**The screen is padded by the keyboard's height** (`imePadding`) and **the tab
bar steps aside while it is up**. Edge-to-edge means Android does not resize
the window, and before this a field low on a screen sat under the keyboard.
On a small phone the tab bar is the difference between two fields and three.
Checked on the emulator for the lineup's lowest row and for the add-player
dialog; what an iPhone does is on the Mac checklist.

**Zpět on every step of a report, the first included, and a report opened
part-way keeps the steps before it.** The header is the root of the report
tab's stack, so it had no Zpět, and a report opened from the fixture list at
the console had a stack of the console alone, so back left the report. The
stack is now built from the header up to the step opened. Only the last step
is ever composed, so the ones beneath cost a list entry each. The roots of
Zápasy, Týmy and Nastavení still have no Zpět: the tab bar is how you leave
them, and nothing in them was left. Android's system back and the button are
the same call.

**Detekt's `LongParameterList` now counts only parameters with no default.**
`PsmfTextField` takes twelve, as Material's own field takes twenty-odd, and
all but two have a default. What the rule is for, a long list every caller
must supply, is still counted.

**Dead ends found** (the prompt asked for a list): the header (fixed); every
report resumed at the console or the recap, and the one adopted after the app
was killed, which had no Zpět at all on that screen (fixed, same cause);
Týmy when its load fails, which shows a message and no retry, and is left by
switching tab and coming back, which reloads (**not changed**); the empty
report tab, whose one button goes to Zápasy (a root, not a dead end).

**Reverses if:** a referee finds the tab bar's absence while typing
disorienting, which is a line in `AppShell`. A scroll that drops the
keyboard turns out to be too eager over a long lineup, in which case dropping
it on a scroll only past some distance is one condition in `KeyboardDismissal`.

---

## 2026-10-17 · From the first iPhone test: Číslo RP takes the number on the card, the lineup marks who came, and the score comes from the goals

**Owner's decisions**, on points the tester raised after using 0.2.1 (3) on
an iPhone. Each one reverses something recorded earlier.

**1. The referee may type the number from the player's card into `Číslo
RP`.** This narrows the rule that an RP number is never user-editable. That
rule was written when PSMF were expected to supply RP numbers (A1/A2). They
have not, the psmf.cz data has none, and on paper the captain copies the
number from the card by hand. What stays true:

- A league-issued `RpNumber`, where the data has one, is still never typed or
  edited.
- A typed number is what was written in that row on that day. It lives on
  the appearance. It is never written back to the player or to league data,
  and never presented as PSMF's record.
- A date of birth stays as the fallback for a player with no card, which is
  the form's printed rule.
- **No rodné číslo.** The tester says the column takes the RP number or the
  RČ, never a date of birth. That is evidence for A28, not an answer to it.
  RČ is a national identifier with much heavier obligations, and it stays out
  until a real report shows it is what gets written.

**2. The lineup marks who came, not who stayed away** (reverses 2026-08-29
and analysis §5.1). The squad from psmf.cz is everyone who has played this
season. That is about 11 a team after round five, and it only grows. Marking
absentees meant a forgotten tap put a no-show on the report, where it counts
as an appearance: entry fees and the season statistics go by appearances.
Marking who came turns the same slip into a missing name, which the captain
sees at once. It costs a few taps.

**3. The score on the recap comes from the goals and is not edited there.**
This reverses DEMO_SCOPE §6 ("still editable") and the 2026-08-31 half-time
entry ("leave it editable — a goal can be logged late"). That reason goes
away, because a goal can now carry its own minute (two entries down). The
score PSMF types onto the website can then never disagree with the goal list
their crew retypes.

**Reverses if:** PSMF supply RP numbers, which shrinks (1) back to the
exception. A referee finds marking who came slower than it is worth over a
full season's squad (2). A case appears where the result legitimately
differs from the goals, such as a forfeit decided at the pitch (3).

---

## 2026-10-17 · The captain and the goalkeeper are marked in the lineup, and a captain confirms twice

**Mine, from the tester's points and a defect found reading the code.**

**The goalkeeper is a gap in the analysis.** §2.5 lists the lineup columns
and has no goalkeeper. But PSMF publish one for every played match: psmf.cz
lineups put the goalkeeper before a dash, which the importer already reads.
So the report their crew retypes must say who it was. The tester says the
paper form has the captain on the first row and the goalkeeper on the second.
**Not yet seen on a real form**; a photo settles it (TODO).

Decided:

- Each lineup marks one captain and one goalkeeper among the players present,
  possibly the same person.
- The report lists the captain first, then the goalkeeper, then everyone
  else.
- Every format says unambiguously who is which.

**The deputy switch goes.** The captain is whoever is marked captain for this
match, and that is how a stand-in shows up on paper anyway. Old reports keep
their `(zást.)`.

**A captain confirms twice, because the form has them sign twice:** under
the lineup before kickoff (*podpis kapitána před zahájením utkání*), and at
the end (*Podpis kapitána*).

The app records one confirmation per party, so the lineup's tap is the same
record the recap reads. After the lineup, the recap already shows both
captains as confirmed. A report can therefore become CONFIRMED without either
captain having seen it finished.

"One captain per team confirms" (owner, 2026-08-31) stays true. Reading it as
"one confirmation per captain" was the error.

What follows:

- **A late change withdraws the final confirmations.** This covers a change
  after them to anything the captains attest: the header, the lineups, the
  goals, the cards and the whistles. The referee is told before it is saved.
  Taking back the "no cards" affirmation is one such change.
- **The referee's own words are excluded.** The assessment, the commentary
  and the team notes stay editable until export (A6) and touch no
  confirmation.
- **Names come from what is already recorded:** the captain from the lineup,
  the referee from the header. Nobody types a name at the signatures.

**Reverses if:** a real form shows a different order, or PSMF say the
`(zást.)` mark or the pre-match signature is something they act on.

---

## 2026-10-17 · A whistle can be taken back, a goal can carry its own minute, and the clock shows seconds

**Mine, from the tester's points. None of them is a stoppage during play.**

- **Taking back a whistle.** A mis-tapped kickoff, end of half, start of the
  next half or final whistle can be taken back and pressed again. Removing a
  boundary that should not exist creates no pause: the clock is still derived
  from the instants that remain. Nothing edits an instant, which is the
  "adjust" that CLAUDE.md forbids. A kickoff taken back is simply pressed
  again, later.
- **A goal with its own minute**, for one the referee forgot. It is entered
  the way a card's minute is, and the one-tap goal stays one tap. The goal
  list, `Stav` and the half-time score follow the minute written, not the
  order of recording. Undo still goes by recording order. No goal at `60´+`:
  the form has none after the whistle.
- **The console clock reads minutes and seconds**, so it can be compared with
  the referee's stopwatch. The event log and the report keep the form's
  minutes. The Scoreboard KDoc argued for minutes only, for fear of implying
  a precision the report lacks. The report never shows the seconds, so the
  argument does not reach it.

**Open, for the referee visit:** at 12:40 of play the app writes `12´`. A
referee reading 12:40 on a stopwatch might write `13´`. Showing seconds makes
the question visible. It is not changed here.

**Reverses if:** a referee takes back a whistle by accident in practice.

---

## 2026-10-17 · A forged report: what a captain's PIN can and cannot do — open

**Analysis for the owner. Nothing is built.**

**The worry** (tester and owner): someone from the bench installs the app and
records the same fixture with a different score. They tap all three
signatures and email it to PSMF as the original. The tester suggested a code
that only the captain knows.

**Paper has the same hole.** The form is a public download, nobody checks a
signature, and a photographed report is an accepted channel. What stops a
forgery today:

- the real referee's report arrives as well;
- the delegation says which team's referee it should be;
- the paper original must follow (A5).

**A code only helps if someone who knows the right answer checks it.** The
referee's phone has no server and nothing to compare against, so it accepts
any PIN. The forger's copy of the app does the same. A PIN the captain
invents at the pitch is therefore a tap with extra steps. Shipping the right
answers inside the app does not work either: a four- to six-digit PIN can be
recovered from its hash in seconds.

**What would work, cheapest first:**

1. **Make a duplicate visible.** Today the export carries no report ID, no
   fixture ID and nothing about the device that made it. Add those, with the
   moments the whistles were pressed. Two reports for one fixture then become
   something PSMF notices at once, and the one from the referee the
   delegation names is the real one. It detects rather than prevents. It
   needs no identity and no work at PSMF, and it is a small prompt.
2. **A code PSMF issue and PSMF check.** Each team leader gets a code from
   PSMF; `vedouci@psmf.cz` already reaches them. The captain types it at the
   final signature. The report carries it encrypted to a PSMF key built into
   the app, so neither the referee nor anyone forwarding the email can read
   it. Only PSMF can check a guess. This is real protection with still no
   accounts in the app, but it needs a crypto library, and PSMF must issue and
   check the codes. That is a conversation with PSMF, not a feature. The same
   scheme works for referees.
3. **Accounts and a server** that knows the delegation. This contradicts
   §2.12 and needs the backend, which waits for A1/A2.

**For the demo and the pilot: none of them.** A pilot runs with PSMF's
knowledge and with known referees. Option 1 is the next step when the export
is next touched. Option 2 goes to PSMF alongside A7, once a pilot is on the
table.

**Reverses if:** PSMF say a forged report has happened, or ask for
protection before a pilot.

---

## 2026-10-08 · Zápasy: no single-day picker, a line that says what the list is narrowed to, and no quick return

**The session's calls under `prompts/12` Part 2**, carrying out the owner's
entry two below. The two judgement calls the prompt asked for come first.

**One specific match day: not added.** The season has 83 match days, 53
of them still to come on 2026-10-08. A select of 53 dates is a scroll, not a
choice, for a cold thumb. What a referee actually asks is covered already:
*Dnes* for match day; *Nadcházející* lists several groups by day, each
heading a weekday and a date, today's headed *Dnes*; and a pitch narrows the
upcoming list to a median of 10 rows (48 at the busiest). If it is wanted
after all, the cheap way is a fifth dates option, *Den…*, opening Material
3's own date picker with only the days that have a match in scope
selectable: no dependency, and the select stays four rows.

**Once the filter has scrolled away, a slim line at the top says what the
list is narrowed to**: the dates always, then the league or group, the pitch
and the team text, e.g. *Nadcházející · 6. liga K · ZAK*. Brand yellow, one
line, ellipsised. It is information, not a control; the button bottom right
is the way back. A referee deep in a list must not have to remember that it
is 6K and not everything, and the button alone could only say *a* filter
exists, not which.

**The filter does not come back on a short scroll up.** It is four or five
fields, about a third of a phone's list. Sliding that over the rows the
referee is scrolling back to read fights the list, and a second floating
copy of a text field and four menus is where focus and keyboard bugs live.
The line and the button already say and do what it would.

Also decided here:

- **"Odehrané" is before today**, and *Nadcházející* is today onwards, so
  the two never overlap and a match finished at 21:00 is still under
  *Dnes* until midnight.
- **"One group in view" is the rows, not the selects.** A team typed with no
  league chosen is one group, and its season reads by round.
- **A group chosen without its league gets it** (`FilterOptions.normalise`);
  a group under another league is dropped; a pitch outside the league and
  group is dropped. The selects and the ViewModel apply the same function.
- **League names come from the data** (`6. liga`, the groups' own name less
  the letter), so *Celá 6. liga* reads *Уся 6. liga* in Ukrainian: the label
  translates, the name does not.
- **Pitches with no fixture are never offered**, even under *Všechny ligy*:
  38 of the 43 in `venues.json` have a league 6 match.
- **The list rebuilds when a report changes state.** It was built once per
  filter, so a report started from the list came back without its badge
  until the filter changed — a second instance of the read-once shape
  `docs/TODO.md` already names. Now it observes the reports.
- **The menu is drawn on `surface`.** The theme sets no `surfaceContainer`,
  so Material's menu fell back to its own lavender. Spelled out on the menu
  only; the theme gap is a TODO, not this prompt's.

**Reverses if:** referees ask for a day (then *Den…* as above); the line
reads as noise rather than as a reminder; or a real phone shows the filter
in the way often enough that a quick return earns its complexity.

---

## 2026-10-08 · Zápasy: a spinner only after 300 ms, and the list built off the main thread

**The session's call under `prompts/12` Part 1**, carrying out the entry below.

Measured against all of league 6 (792 fixtures): a filter change is
**0.01–0.23 ms** on the JVM and **1–6 ms median, 10 ms worst** on the
emulator; the cold first load, which parses all twelve files, is
**115–126 ms** on the emulator. None of that is a visible wait on the
emulator. The flash was the screen being replaced, not the work.

- **Threshold 300 ms.** Above every wait measured, with room for a phone
  several times slower than the emulator; below the point where an
  unchanged screen reads as a tap that did not register. A spinner for a
  shorter wait would itself be the flash. It fades in and out over
  150 ms, so a wait that only just crosses the line does not blink. In
  practice it will show, if ever, on the cold start of a slow phone, over
  an empty frame.
- **Blur or dim is Compose's own answer**, `RenderEffect.isSupported()`,
  in common code: false below Android 12, where `Modifier.blur` is
  silently ignored. No platform code, nothing in `iosMain`.
- **The list is built on `Dispatchers.Default`.** A filter change does
  not need it; the first load does, because a spinner drawn by the thread
  doing the parsing does not spin.
- **The team field waits 250 ms after the last keystroke** before the
  list follows it, so a name typed at speed builds one list.

Checked for the same pattern elsewhere: no other screen swaps to a loading
state on a filter or a search. Týmy keeps its list while the query
changes; every other `loading` flag covers only a screen's first load.

**Reverses if:** a real phone shows the spinner on filter changes, which
would mean the work is far slower there than measured, and is worth
profiling before it is worth hiding.

---

## 2026-10-08 · Zápasy: select boxes instead of chips, a filter that scrolls away, and no loading screen for a filter change

**Owner's decision, from the first phone run of 0.2.0. It reverses "chips
throughout, not dropdowns"** (2026-08-31, *Filters are sized to their data*,
and the KDoc of `FixtureFilterRow`).

**What broke.** Real league data made the tab unusable. The filter sits
*above* the list and never scrolls. With 12 group chips and 43 pitch chips it
fills the whole screen, so no match is visible and the filter itself cannot
be scrolled. The chip argument (one tap fewer, the active option visible) held
for one group and seven pitches. It does not hold for twelve groups and
forty-three pitches.

**Decided:**

- **League, group and pitch are select boxes.** League first. Choosing one
  reveals a group select under it. Then pitch, then the team text field. A
  closed select still shows what is chosen, which was the part of the chip
  argument worth keeping.
- **Pitches show their name as well as their code**, now that the data has
  one. The list offers only pitches that have a match in the chosen league or
  group, not all 43 in Prague.
- **A date select**, defaulting to *upcoming* (today onwards). A referee
  opens this tab to find this week's match, not September's. A fixture whose
  report is started and not yet confirmed is never hidden by it: the list must
  not make a match the referee is in the middle of disappear.
- **The filter scrolls away with the list.** A scroll-to-top button appears
  once it has gone.
- **No loading screen for a filter change.** The league data is parsed once
  and cached (`SeedLeagueRepository`). After the first load, a filter
  change is in-memory work on 792 fixtures. The flash came from the
  ViewModel swapping the whole screen to *Loading* on every change, including
  every keystroke in the team field, which also throws away the field being
  typed in. Any wait that is genuinely noticeable gets the owner's design: a
  spinner in the middle over the content, blurred where the platform can (iOS,
  Android 12+), dimmed where it cannot (Android 9–11).

**Reverses if:** referees find the selects slower than they are worth at a
pitch. The test is a cold thumb choosing one pitch out of a dozen.

---

## 2026-10-08 · The Android TEST icon: the artwork 64 dp wide, and no themed variant

**The session's calls, carrying out the owner's 2026-10-07 entry below.**

The iOS PNG is drawn **64 dp wide in the middle of the 108 dp adaptive
canvas**, and its yellow and its red band are carried out to the edges, so
no mask finds a gap. That keeps "ZoU" and "TEST" inside the 66 dp circle
every launcher mask is promised to leave: the farthest corner of the Z is
27 dp from the centre. 72 dp, the iOS proportions exactly, would also be
inside, by 2 dp. At 64 dp the 1024 px source is composed 1:1 at 16 px per
dp, then scaled to the five densities. The foreground is the whole picture,
opaque; the background is the same yellow and is never seen.
`tools/launcher-icon/LauncherIcon.java` makes it, and renders it under a
circle, AOSP's squircle and a rounded square, which were looked at before
committing.

**No monochrome layer.** Android 13+ themed icons need one, and lint says
so (`MonochromeLauncherIcon`). Drawing one would be new artwork for a
placeholder. With themed icons on, a launcher shows this icon in colour or
derives its own; which, on the tester's phone, is not verified.

**Reverses if:** the real icon arrives — it replaces all of this,
`tools/launcher-icon/` included — or a tester with themed icons on cannot
find the app.

---

## 2026-10-07 · What the league import keys on, and what it does with what it cannot reconcile

**The session's calls under `prompts/10` Part 3**, written into the seed
README and `SeedFiles.kt`.

- **A fixture's ref is its pairing**, `6k-krabice-vs-hustec`. Upcoming
  fixtures carry no PSMF id. A pairing meets once a half-season, and it
  survives the reschedules PSMF actually makes, where a date, a pitch or a
  round would not.
- **A player's ref is the name, league-wide; a later namesake gets the team
  appended.** The first in import order — groups a to l, teams by slug,
  squads in *Statistiky* order — keeps `novak-jan`; a later one is
  `novak-jan-strazci-pomerance`, and `-<group>-<team>` if that is taken
  too. 18 on 2026-10-07. A re-run never re-decides: it finds an existing
  player by group, team and name before it mints anything, and recognises
  a transfer only when exactly one player of that name has left the team
  they were in.
- **Nothing that leaves the site is dropped.** A team or player in the old
  files and not on the site now is carried over and reported: a saved match
  may point at it.
- **A name that fits no squad row, or two, is reported, not guessed**, and
  its cards count for nobody. Six on 2026-10-07, all from two same-name
  pairs inside one team each.
- **`asOf` is the day the pages were fetched** — the oldest page read, a
  cached page's file time — never the day of the run. Even that overstates
  it: match details lag results (53 matches on 2026-10-07), and their cards
  are in no count. The counts are advisory and the app never claims
  eligibility, so this is said, not fixed.
- **A match saved against the retired placeholder is kept, and offered
  nowhere.** Nothing deletes it; no screen finds it, because its fixture is
  not in league data. Listing such matches somewhere would show a referee a
  match that can be neither continued nor sent.

**Reverses if:** a pairing ever meets twice in a half-season (a replay, a
cup), and the round joins the fixture ref; PSMF publish ids for upcoming
fixtures; or a tester needs an old placeholder match back — it is still in
the database, which `PlaceholderMatchAfterTheUpgradeTest` shows.

---

## 2026-10-07 · Two calls inside the card fix: the button says "Vyloučit", and a late red runs from its minute

**The session's calls, asked for by `prompts/10` and reported back for review.**

**How the card sheet says a yellow is a dismissal.** For a player already
booked in this match, the save button stops reading *Uložit* and reads
**Vyloučit (2. ŽK)**, in the error colour, and the line above it now says what
saving does: *"Uložení zapíše druhou ŽK i červenou kartu (2. ŽK) a hráč bude
vyloučen."* The old hint said the same thing and changed nothing; the button is
where the thumb is, so it is the one place the referee cannot miss. No extra
confirmation step: one tap sends off, one Undo restores, which is the cheaper
correction at the pitch.

**A red written up late runs from the minute written.** When the minute on the
card is the one the clock shows, the ten minutes start at the moment of saving.
When the referee writes an earlier one, they start at the beginning of that
minute, placed on the clock period by period — a first-half `28´` saved in the
second half has four minutes in the first half, nothing in the interval, six
after the restart. The mapping is clean because period boundaries are recorded
instants; `30´+` maps to the end of the first period; a minute later than the
clock is clamped to the moment of saving. The cost: a typo (`4` for `40`) gives
a power play that is already over, and nothing on screen says so beyond its
absence.

**Reverses if:** a referee finds the button change too easy to tap through, or
says late logging is rare and typos common — then run from the moment of saving,
which is one line in `LogCard`.

---

## 2026-10-07 · Scrape psmf.cz after all: league 6, all twelve groups, for internal testing

**Owner's decision, reversing 2026-08-31.** That entry said wait for PSMF's
own data, because an importer built against a guess gets built twice. A
month and four rounds later the guess is no longer a guess. Each team page
on psmf.cz now carries:

- the **squad**, as a statistics table of everyone who has appeared
  (`Bělohlávek Jan · 3 · 0`: surname first, matches, goals)
- **all eleven fixtures** with round, date, time and pitch code
- **every played match in detail**: both lineups (goalkeeper first), goals
  with minutes, cards marked `is-yellow` / `is-red` in the markup, the
  half-time score, and the referees

Plus `/dresy/` per group (each team's kit colours, verbatim) and `/hriste/`
(every pitch code with its name and address). `robots.txt` disallows only
`/cms/`.

**What it still cannot give us:** RP numbers, dates of birth, jersey numbers,
and anyone who has not yet played. So it does not answer A1/A2, it works
round them.

**The scope is the one already decided: one whole league, every group in
it.** League 6 is twelve groups, 6-A to 6-L, about 144 teams. Bundled on the
device, no network, unchanged.

**Conditions:**

- **Polite.** Sequential, at least a second between requests, a
  user-agent naming the project, raw pages cached so development re-runs do
  not hit the site again. About 170 requests per full run.
- **An importer, not a one-off.** It must preserve every existing id by
  natural key (`SeedIdentity`), because the next run, after round six, must
  not orphan the matches recorded since this one.
- **The invented placeholder group retires.** `6k.json` was made up. Real
  6-K replaces it, and a match saved against a placeholder team must not
  crash any screen.
- **Internal testing only.** The names are public on psmf.cz, but putting
  them in a store build is a different act and waits for A10 and A26.
  TestFlight internal testers and sideloaded APKs are fine.

**Reverses if:** PSMF supply the export A1/A2 ask for. That becomes the
source and the scraper stops. Also if PSMF object to the scraping, which
the PM should mention to them before anyone else does.

---

## 2026-10-07 · A league player may arrive with no identification; the referee writes the date of birth

`Player` refuses to exist without an RP number, a date of birth or a birth
number. Every scraped player has none of the three. The invented seed data
hid this by giving all 144 placeholder players a made-up date of birth.

**The rule moves from the player to the appearance**, which is where the
form puts it anyway. A league player with no identification can be in the
squad. To *field* them, the referee enters the date of birth at the pitch,
which is exactly the form's printed rule (*"uvedou místo čísla RP jejich
datum narození"*). `Appearance.reportedIdentification` stays non-null, so a
report still cannot be produced with an empty `Číslo RP` column.

Today a squad player without identification gets *"Chybí údaj do sloupce
Číslo RP"* with no way to fix it. That path has never been reachable,
because the invented data never produced one.

**Reverses if:** PSMF supply RP numbers or dates of birth (A1/A2).

---

## 2026-10-07 · The card and clock review: five defects, four of them about red cards

The tester mentioned problems with red cards without saying what. Reading
the code found these, in order of harm:

1. **A second yellow does not send the player off.** A yellow for a
   player who already has one saves a plain yellow: no dismissal, no power
   play, the row stays live, and the report shows two yellows and no red.
   Choosing red and then *2. ŽK* instead does dismiss, but then the season
   count gets 1 where the rule says 0. The model expects three entries
   (yellow, yellow, red 2. ŽK) for what is one action on the pitch, and no
   referee will make three entries. **Decided: a second yellow is one
   action that records both, and one undo takes both back.**
2. **`2. ŽK` can vanish from the report.** The kind is stored, but the
   export prints only the free-text reason. *2. ŽK* is pre-filled only into
   an empty reason, so a referee who typed the reason first exports a
   second-yellow red indistinguishable from a straight one. Switching back
   to straight keeps "2. ŽK" as the reason of a straight red. **Decided: the
   report writes the kind from the stored field, never from free text.**
3. **The power play runs on the wall clock.** A red at 25´ with a
   five-minute half-time expires during the break, and the second half
   starts at full strength. A red at `60´+` starts a ten-minute power play
   after the match has ended. **Decided: ten minutes of play**, not
   counting the interval; none after the final whistle; none for a card
   shown to someone not in the lineup (`NamedPerson`), who is not on the
   pitch to be replaced.
4. **Second-half minutes inherit the first half's added time.** End the
   first period at 32 minutes and the second starts at `32´`, not `30´`.
   This was built and tested on purpose ("continues the same sixty
   minutes"), and I think the intent was wrong. `30´+` already absorbs
   first-half added time, which only makes sense if the second half
   restarts at 30. psmf.cz agrees: Krabice–Hustec, 1:1 (0:0), has a goal at
   `32.`. **Decided: period *k* starts at (*k*−1) × half length.**
   Consequence: `30´+` must sort before the second half's `30´`, and the
   half-time score must count exactly the first period's goals.
5. **Undo removes the wrong event** when two share a minute, or when the
   referee typed an earlier minute. A card at 20´ then a goal at 20´: Undo
   takes the card. **Decided: undo goes by recording order, not by minute.**

Also stale: `CLAUDE.md`, `MatchClock.kt` and `Match.kickoffAt` still say
the clock has no accumulated time and nothing but kickoff. Since half-time
that is false. The rule that survives is *no stoppage during play*.

**Reverses if:** a referee says the second half's minutes really do
continue from the first half's added time (4), or that a team official's
red does reduce a side (3).

---

## 2026-10-07 · Android gets the same TEST icon

**Owner's decision.** Android has had no launcher icon at all, only the
system default. It takes the iOS TEST icon (2026-09-30) on the same terms:
obviously temporary, and replaced on both platforms in one commit when the
real icon arrives. Android masks icons to a circle or squircle, so the
artwork is fitted inside the adaptive icon's safe zone, not just copied.

**Reverses if:** the real icon arrives.

---

## 2026-09-30 · A temporary TEST icon, so internal TestFlight can start

**Owner's decision, reversing `prompts/09`**, which said to leave `AppIcon`
empty rather than risk a placeholder being uploaded by mistake. App Store
Connect rejects any build without the 1024 × 1024 icon, even for TestFlight,
so waiting for the real one would have blocked testing entirely.

The icon, `AppIcon-test-1024.png`, is **obviously temporary** by design: PSMF
yellow, "ZoU" in the app's own Oswald Bold, and a red **TEST** band across it.
It cannot be mistaken for a finished icon by anyone who sees it.

**It must be replaced before external TestFlight testing or any App Store
submission.** Internal testers see it; the public never does.

**Reverses if:** the real icon arrives. Replace the PNG, update the
`filename` in `AppIcon.appiconset/Contents.json`, and delete this one.

---

## 2026-09-29 · iOS export: the composer or the share sheet to send; a remembered folder to save

**Send.** Where Apple Mail has an account, the mail composer: recipient,
subject, body and all three files filled in, and the referee sends. Where it
has none (Gmail, Outlook), the share sheet, which **cannot fill in a
recipient**. So the export screen says so *before* send, and the address is
copied to the clipboard. Afterwards the screen says the report was handed
over and asks the referee to check where it went; it never claims it reached
PSMF. Neither route alone serves every referee.

**Save.** A folder picked once and remembered with a bookmark, which is
Android parity. The document picker in export mode costs a dialog on every
save, the mistake Android already corrected once. The app's own Documents
folder costs none, but the files go when the app is deleted, which is the one
thing saving exists to prevent.

**The interface changed, and the owner was asked first.** `ReportSender.send`
and `ReportSaver.save` return `SendOutcome` / `SaveOutcome` instead of
`Boolean`, so a cancelled draft is not reported as "no mail app", and a
cancelled folder pick is no longer lumped in with a failure, on Android too.
Plus `ReportSender.prefillsRecipient()` for the pre-send warning. The
decisions that can be plain functions are in `ExportRouting.kt` and tested
on the JVM, because no iOS code can run in this project's loop.

**Unverified until an iPhone:** all of it runs only on a device. See the
checklist at the end of `reports/2026-09-29-ios-testflight-readiness.md`.
**Android was edited without being compiled**, so the branch `ios/export`
is not merged until the Windows machine has built it.

**Reverses if:** a device shows the share sheet's completion is meaningless
for the common mail apps (then say less after it), or a provider will not
honour folder bookmarks (then fall back to the export picker for that case).

---

## 2026-09-29 · iPad stays; the app name is not decided

**iPad:** the Linux scaffold targeted iPhone *and* iPad by default. Asked,
the owner welcomes iPad, and it may be dropped if it ever complicates things.
So far the cost is one `Info.plist` key: all four orientations on iPad, without
which the upload is rejected. At store submission it will also mean iPad
screenshots and App Review on iPad.

**App name:** not decided, and it will be shorter than *Zápis o utkání*.
Until it is, the iOS `CFBundleDisplayName` mirrors Android's `app_name` in cs,
en and uk (`InfoPlist.strings`), so the two platforms change together.

**Reverses if:** iPad costs real layout or review work (drop it with
`TARGETED_DEVICE_FAMILY = 1`), or the name is chosen, which changes both
platforms' resources in one commit.

---

## 2026-09-29 · Correction: the privacy manifest declares two categories, not three, and not `0A2A.1`

The entry below listed three categories from JetBrains' guidance for Compose
Multiplatform, and said to verify them against the built binary. Verified, two
of the three rows were wrong **for this app**:

| | The entry below | The linked release binary | Declared |
|---|---|---|---|
| User defaults | `CA92.1` | `NSUserDefaults` imported; `setObject:forKey:` for `AppleLanguages` | **`CA92.1`** ✓ |
| File timestamp | `0A2A.1` | `stat`, `fstat` imported, from Skia's file I/O and ICU's data-file mapping | **`C617.1`**: files inside the app container |
| System boot time | `35F9.1` | **absent** | **not declared** |

**`0A2A.1` is not a reason an app can give.** Apple, verbatim: *"This reason may
only be declared by third-party SDKs."* It is what a library declares in its
*own* manifest when it wraps `stat` for its host app. This app reads its own
bundle and its own database, which is exactly `C617.1`.

**`mach_absolute_time` never reaches the app.** It is imported by the
`ComposeApp.framework` archive, from Adobe's DNG SDK inside Skia
(`libdng_sdk.dng_utils.o`, the RAW-photo decoder), and the linker dead-strips it
in both Debug and Release. JetBrains describe the *library*; Apple scans the
*app*.

**Because dead-stripping decides it, it can change silently** with a Compose
bump or any UI that happens to reach Skia's image decoders.
`iosApp/scripts/check-required-reason-apis.sh <app>` compares a built app's
symbols against the manifest and fails either way: used and not declared, or
declared and not used. It was proven to fail both ways before it was trusted.
**Run it on the release build before every upload.**

**Reverses if:** the check script reports a symbol in a category not declared,
which means add the category, with the reason that honestly applies.

---

## 2026-09-29 · Device builds are signed by the company team — never a free personal one

**Correcting my own earlier advice.** The 2026-09-01 entry, `prompts/08` and
`docs/TODO.md` all said a free Apple Developer account with seven-day provisioning
was a €0 way onto a device. With the bundle ID now fixed, that would be
unrecoverable.

Signing registers the bundle ID to whichever team signs first. If a free personal
team signs `cz.hspinovace.psmf`, the company account can never register it —
Xcode and the portal both answer "not available". A free account cannot see its
registered identifiers to delete them, and the company account cannot see them
at all. Freeing it takes Apple Developer support. Renaming is not an escape: the
ID was declared permanent the same day.

**So:**
- Every device run signs with the **company** team. The owner needs a role on it
  that can — which was already an owner item for TestFlight, and is now also the
  prerequisite for simply running on a phone.
- Sessions that only compile (`prompts/09`) use `CODE_SIGNING_ALLOWED=NO` and are
  told not to select a team in Xcode even to silence an IDE error, since
  selecting one is enough to register the ID.
- If a personal team is ever truly unavoidable: a throwaway bundle ID for that
  run, never committed.

Corrected in `iosApp/README.md`, `prompts/08`, `docs/TODO.md`; the 2026-09-01
entry carries a pointer here.

**Also decided:** the owner will test on a **real iPhone, later**, rather than
Appetize. `prompts/09` dropped its optional simulator-build part and instead ends
its report with a checklist for that first device run.

**Reverses if:** nothing. The registration rule is Apple's.

---

## 2026-09-29 · The bundle ID is `cz.hspinovace.psmf`, on both platforms

Open since August. **Settled by the project owner:** Android `applicationId` and
iOS `PRODUCT_BUNDLE_IDENTIFIER` are both **`cz.hspinovace.psmf`**, which is what
was already in the build files and the Xcode project — so nothing changes in code.

It becomes permanent the moment it is registered in App Store Connect or uploaded
to Play. The alternatives considered earlier (matching golblok's `cz.hsp.…`
convention, or `zapis` as the last segment) are closed.

Removed from `docs/TECH_STACK.md` §5 and `docs/TODO.md`; `iosApp/README.md` updated.

**Reverses if:** nothing, once registered. Before that, only a company naming rule
nobody has mentioned yet.

---

## 2026-09-29 · There is no iOS simulator for this project on the Intel Mac — definitively

Checked against Maven Central rather than restated from memory. Compose
Multiplatform's Intel-simulator artifacts (`runtime-iosx64`, `ui-iosx64`) **end at
`1.11.0-alpha01`**. This project is on 1.12.0, whose `iosx64` artifact returns 404.
The Apple-Silicon simulator line (`iossimulatorarm64`) continues to 1.13.0-alpha01.

Getting the Intel simulator back would mean downgrading Compose below 1.11 — and
with it the chain `BUILD_MATRIX.md` records (Compose 1.12 is what requires
compileSdk 37). Not viable, and not worth it for a machine with a ~2027 horizon.

**What does work, for seeing the app without an iPhone:**

| | Interactive | Really iOS | Cost |
|---|---|---|---|
| **Appetize.io** — an arm64 *simulator* build, run in a browser on Apple-Silicon hosts | yes | yes | free trial, then paid |
| **Rented Apple-Silicon Mac** — full Xcode and simulator over remote desktop | yes | yes | per day; Apple's licence imposes a 24-hour minimum |
| **A used iPhone** | yes | yes, real hardware | one-off |
| **CI on an Apple-Silicon runner** | no — screenshots and tests | yes | limited free minutes on a private repo |
| **Desktop window of the same Compose UI** | yes | **no** | €0 |

The Appetize route turns on one fact: **an Intel Mac can *build* an arm64
simulator app, it just cannot run it.** Kotlin/Native already linked
`iosSimulatorArm64` on this Mac in Part 2 (the test binary, then `SKIPPED`), and
Appetize recommends ARM simulator builds. End to end it is unproven — and the
owner chose a real iPhone instead, so nothing tries it for now.

The desktop window is cheap because every JVM `actual` already exists (DI, saver,
locale, database driver) and `compose.desktop.currentOs` is already a dependency
for the UI tests. It shows every screen and runs on the Mac *and* on Windows — but
nothing iOS-specific runs in it, so it answers layout questions, not platform ones.

**Reverses if:** an Apple-Silicon Mac arrives, which makes the simulator local and
free.

---

## 2026-09-29 · The privacy manifest must declare Compose's symbols, not just ours

Uploads missing a privacy manifest for "required reason" APIs are rejected
(`ITMS-91053`). The obvious manifest — declaring this app's own `NSUserDefaults`
write for the language picker — **would still be rejected.**

JetBrains document that **Compose Multiplatform itself puts `stat`, `fstat` and
`mach_absolute_time` into the binary**, and Apple's check is symbol-based, so it
flags them whether or not this app ever calls them. So the manifest needs at least:

| Category | Why | Reason |
|---|---|---|
| User defaults | the language picker writes `AppleLanguages` | `CA92.1` |
| File timestamp | `stat` / `fstat`, from Compose and Kotlin/Native | `0A2A.1` |
| System boot time | `mach_absolute_time`, from Compose | `35F9.1` |

**In the app target, not the framework.** `ComposeApp` is statically linked into
the app binary, so the app bundle is what Apple scans. JetBrains'
`apple-privacy-manifests` plugin is for distributing a Kotlin library separately
and is not needed here.

**And verified against the built binary, not taken from this table** — the list
above is JetBrains' for Compose in general, and the only authoritative list is the
symbols actually present in this app.

**Reverses if:** Apple changes the required-reason list, or Compose stops pulling
those symbols in.

> **Corrected the same day, above:** in the linked app, `0A2A.1` became
> `C617.1`, and system boot time is absent and not declared.

---

## 2026-09-28 · iOS: (b) confirmed — it compiles, unchanged, on the Intel Mac

Part 2 of the toolchain proof. With Xcode 26.3, the iOS 26.2 SDK and JDK 17
installed, `:composeApp:linkDebugFrameworkIosArm64` succeeded on the **first
attempt with no source or build change**. `:shared` also links into a full
arm64 test executable against UIKit, Foundation and `libsqlite3`. None of the
predicted breaks happened; the SDK headers are nullability-audited, so
`languageCode` imports as `String`.

**So Part 1's (b) stands, and it is not (c).** One qualification: `ComposeApp`
is a static framework, so the Compose half is proven only as a complete
archive until an app links it. That link needs no phone (an unsigned
`xcodebuild`, see `iosApp/README.md`).

**A third clock, alongside Apple's April floor and macOS 27 dropping Intel:**
Kotlin/Native 2.4.10 warns that the `macos_x64` *host* is deprecated and
will be removed in a future Kotlin release. There is no version or date yet,
and JetBrains' target-support page does not list it yet. The Kotlin release
that drops it ends iOS compilation on this Mac, whatever Apple's floor does.
**Consequence: `kotlin` in the catalog is bumped only with a Mac run in the
loop.**

Part 3 (running it) is blocked only on a physical iPhone.

**Reverses if:** the app-level link or a device run fails in a way that needs
common code to move, or a Kotlin release drops the Intel host before an
Apple Silicon Mac arrives. That would be (c) for this machine, not for the
project.

---

## 2026-09-01 · The planning documents moved into the app repository

`DECISIONS.md`, `TODO.md`, `QUESTIONS.md` and `DEMO_SCOPE.md` now live in `docs/`;
`prompts/` and `reports/` sit beside it. **One copy of each. The Windows planning
folder is frozen and carries a pointer README.**

Two reasons, and the second is the one that decided it.

**The audience premise failed.** The split was: agents read the code repo, humans
read the planning repo. But agents need the decisions — the iOS session was told
to read `docs/DECISIONS.md`, found it absent, and declined to finalise its verdict
without it. Exactly right, and exactly the premise breaking.

**The durability argument inverted.** The planning repo was once the half with any
permanence. Now the app repo has a remote, CI ahead of it, and two machines reading
it, while the planning folder had never left one disk.

Also settled by the same move: reports and prompts travel with the commits they
describe, and a session on another machine no longer needs anything copied to it.

**What this costs:** every planning edit now goes over the 9P share rather than
native NTFS. Irrelevant for markdown.

**What did NOT move:** `builds/`, the sideloading APKs — gitignored, Windows-side
convenience, no business in the repository.

**The rule that replaces the old one**, written into `CLAUDE.md`: *there is one
copy of each. Do not make a second.* The previous rule — "nothing is duplicated"
— was right and was not enforced by anything. This one is at least checkable.

**Reverses if:** the repository has to be handed to PSMF or split for ownership
reasons, at which point the commercial and question documents come back out.

---

## 2026-09-01 · iOS: the answer is (b), and the Mac has until roughly April 2027

Part 1 of the toolchain proof, on the MacBook Pro 15-inch 2018 (`MacBookPro15,1`,
Intel i7, 16 GB). Externally verified rather than taken on trust.

**It can develop *and* submit today** — which is better than expected:

| | |
|---|---|
| macOS | **15.7.9 Sequoia**, and that is its ceiling. Tahoe 26 dropped every 2018 MacBook Pro. |
| Xcode reachable | **26.3**, needing macOS ≥ 15.6. Xcode 26 ships Universal and runs on Intel. |
| App Store floor | **Xcode 26 / iOS 26 SDK**, in force since 28 April 2026. |
| Satisfied? | **Yes.** 26.3 ≥ 26, iOS 26.2 SDK ≥ iOS 26. |

**The ceiling is two point releases wide — 26.2 and 26.3 — and there is nothing
above it.** Xcode 27 fails here twice over: it needs macOS Tahoe 26.4, which this
Mac cannot run, *and* it is Apple-silicon-only.

**Firmer than the report put it.** It called the next SDK floor "a projection, not
a fact". It is close to a fact: macOS 27 was announced at WWDC 2026, is in
developer beta now, and is **Apple silicon only** — Intel support ends with Tahoe.
Xcode 27's own release notes carry an explicit Intel Deprecation section. So the
only unknown is Apple's *date*, and their pattern has been an April floor bump
three years running: Xcode 15 in April 2024, 16 in April 2025, 26 in April 2026.

**Read plainly: roughly seven months of submission capability left on this
machine.** Enough for the demo and a first release. Not enough to plan around.

**What an Apple Silicon Mac actually buys** is (i) the iOS simulator, and
(ii) headroom past ~April 2027 — **not** the ability to ship, which exists today.
So it is a 2027 budget line, not a blocker.

> **CORRECTED 2026-09-29:** the free-account advice below would permanently
> give the bundle ID away. Sign with the company team. See the 2026-09-29 entry.

**Cheapest thing that unblocks day-to-day iOS work: an iPhone already owned, a
cable, and a free Apple Developer account** (seven-day provisioning). €0 if a
phone is to hand. The Intel simulator does not exist for this project and no
purchase changes that short of a new Mac.

**Reverses if:** Apple's next floor lands later than April 2027, or an Apple
Silicon Mac arrives, either of which extends the runway rather than changing the
shape.

---

## 2026-09-01 · The no-duplication rule was broken, and the drift it predicted happened

`README.md` says: *"Nothing is duplicated across the two — duplicated documents
drift, and drifted documents are worse than missing ones."* Both halves came true.

The iOS session was told to read `docs/DECISIONS.md`, found it absent, and
**correctly withheld its verdict** rather than answering without it. That is the
first time the gap cost anything, and it cost the right thing.

Two failures, both mine:

- **`docs/DEMO_SCOPE.md` in the app repo had not been touched since `9d74f87`** —
  before the screens were built. It still described six screens in a wizard and
  "no ongoing-match hero", a decision reversed two sessions ago, while the planning
  copy had grown to 407 lines. **Every prompt since 04 told sessions to read it.**
  Nothing broke only because the prompts carry their substance inline, which is
  luck, not design.
- **`docs/DECISIONS.md` never existed in the app repo**, though prompts 05, 06 and
  07 all cite it.

**Immediate fix, done:** both synced into `docs/` and pushed (`a88ed1c`).

**The structural question is now open, and the trigger has changed.** The split was
by audience — agents read the code repo, humans read the planning repo — and that
premise has failed: **agents now need the decisions**, as the iOS session
demonstrated. The planning repo was also once the only half with any durability
story; now the app repo is the one with a remote, with CI ahead of it, and with two
machines reading it.

So the honest options are to **move the planning documents into the app repo**
(one truth, already remote, reports already travel that way) or to **keep the split
and strip every planning-doc reference out of the prompts.** Syncing copies by hand
is the option that just failed.

**Reverses if:** nothing. The drift is a fact; only the remedy is open.

---

## 2026-09-01 · The app repository has a remote, and it is the channel between machines

`https://github.com/mirekhajek-HSP/PSMF.git`, default branch **`main`**. Full
history pushed — ten commits, nothing rewritten. The GitHub-created `Initial
commit` was merged in rather than force-pushed over, so its README survives as the
repository root.

The local branch was renamed `master` → `main` so both machines use one name.
golblok stays on `master`; the two projects need not match.

**Credentials:** WSL git is pointed at the Windows Git Credential Manager
(`credential.helper` set locally, with the space-bearing path quoted — unquoted it
fails the *store* step with `/mnt/c/Program: not found` while the push still
succeeds, which is a confusing half-failure worth recognising). **Pushes happen
from the host, never from the container**, which deliberately mounts no SSH agent
and no credential helper. That property is the thing stopping an agent pushing to
origin and it is worth keeping.

**Reports now travel through the repository.** A session on another machine writes
`reports/<date>-<topic>.md`, commits and pushes it. That replaces copying files
by hand and means the report arrives with the commits it describes.

**The repository was PUBLIC when this was pushed**, verified by an anonymous API
call, while golblok-app is private. Raised before pushing; the project owner chose
to proceed and to have it made private afterwards. Recorded plainly because
flipping to private later does not un-publish anything already fetched, cloned or
indexed — so the window is a fact about this repository's history, not something
the setting change erases.

**Still local-only: the planning repository** (`C:\MS\Projects\PSMFApp`) holding
`DECISIONS.md`, `TODO.md`, `QUESTIONS.md`, every prompt and every report. It has
no remote and exists on exactly one disk.

**Reverses if:** the company org takes ownership, at which point the remote moves
and both machines re-point.

---

## 2026-09-01 · Five icons must not cost 31 MB — drop `materialIconsExtended`

The debug APK went **38.3 MB → 69.8 MB** in a session that added a half-time
button, some icons and a folder picker. The only dependency added was
`compose.materialIconsExtended`, in commit `02cde29`.

Measured, not assumed: the APK is **64.4 MB of DEX across 19 dex files** for an app
with roughly eleven thousand lines of Kotlin. The extended set ships every Material
icon as a generated `ImageVector` class, and this app uses **five**:

    ExpandLess · ExpandMore · SportsSoccer · Star · StarBorder

**Fix:** copy those five vector paths into local `ImageVector` definitions and drop
the dependency. It is the standard remedy and it is a morning's work.

**The nuance, stated fairly:** R8 strips unused icons from a *release* build, so
most of this is a debug-build artifact. Three reasons that is not good enough:

- The debug APK is what is being sideloaded onto a phone right now, and 70 MB is a
  lot to hand somebody.
- **This project has never built a release.** "R8 will handle it" is an untested
  assumption, and the first release build is a bad moment to find out.
- **iOS has no R8 at all.** Kotlin/Native's dead-code elimination is a different
  mechanism with different guarantees, and the iOS toolchain is still unproven.

Not a defect — the app works and the icons were the right call. A dependency cost
that wants paying before it compounds.

**Reverses if:** the icon set grows past roughly a dozen, at which point the
dependency is carrying its weight.

---

## 2026-09-01 · The card glyph is drawn, not borrowed — and `Stop` stays out

Worth recording because it is the rule being followed rather than broken, and a
`grep` for `Icons.Default.Stop` finds a hit in this codebase.

That hit is a **KDoc reference explaining why the icon was rejected**. golblok uses
`Icons.Default.Stop` for its card button; this app draws a small yellow-to-red card
shape instead, on the grounds that a borrowed glyph one thought away from
Pause/Stop/PlayArrow has no business on a screen whose whole discipline is that
the clock does not stop.

The card also deliberately does not commit to a colour before the sheet asks for
one — which is the same reasoning that kept the control as one icon rather than
splitting it into yellow and red.

**Reverses if:** nothing. But the next person grepping for banned icons should know
the hit is a comment, not a call.

---

## 2026-09-01 · Correction to the record — the six fixes came from a real phone

The session report frames its findings as *"using the built app on a phone-sized
screen (an emulator at 411dp, standing in for the physical device this session
didn't have)"*.

**The six issues were found on a real phone**, by the project owner, on 2026-08-31.
The session verified its *fixes* on an emulator. Both facts matter, and conflating
them undercuts the argument this project keeps re-proving: **an emulator has never
once found what a phone found.**

Recorded because a future reader taking the report at face value would conclude the
emulator is sufficient, and every report so far says otherwise.

**Reverses if:** nothing. It is a fact about who was holding what.

---

## 2026-09-01 · Two loose ends in the minute notation, both logged rather than fixed

Found reading `Minute.kt` and `ConsoleEntry.minuteAt` after the half-time work.
Neither is wrong for Hanspaulská liga; both would be wrong elsewhere.

**The notation constants are hardcoded while the clock is data-driven.**
`Minute.HALF_LENGTH = 30` and `FULL_LENGTH = 60` are compile-time constants, so
`Minute.HalfTime.written` is always `30´+`. Meanwhile `minuteAt` correctly reads
`halfLengthMinutes` from the group file to decide *when* half-time applies. A
competition playing 2 × 25 would therefore get the boundary right and print
`30´+`. Veteran and futsal are exactly the competitions that might differ.

**`30´+` and `60´+` are treated asymmetrically.** `30´+` covers added time in
period one *and* the interval. `60´+` means the final whistle only — so an event
at minute 62, before the whistle, is written `62´`. That follows the form's own
definition as `DEMO_SCOPE.md` records it, and the symmetry argument says it should
be `60´+`. **Neither of us knows, because this came from a document rather than
from watching anyone.** One for the referee visit.

**Reverses if:** a referee says otherwise, or a non-HL competition enters scope.

---

## 2026-08-31 · No scraping. Wait for PSMF's own data, one whole league, still on the phone

Scraping psmf.cz is off the table for now. The A-questions went out today, and the
answer to A1/A2 decides the shape of the roster data — building an importer against
a guess, then rebuilding it against the real export, is work done twice.

**When the data arrives: one whole league, every group in it.** Not all sixty
groups, and not one group. Enough to make search, filters and the league picker
real; small enough to review by hand.

**It stays bundled on the device.** No backend, no network, unchanged. **Supabase
when the organisers grant database access**, and not before — that is the trigger,
not a date and not a feature request.

**Correction to my own finding.** I reported that psmf.cz publishes no players
anywhere. Wrong: player names appear in each team's scoring table, which is empty
today only because the season's first fixtures are 6 September. The check was
right for the day it was run; the conclusion drawn from it was not. Any future
scrape would still only reach players who have *appeared*, never RP numbers, so it
remains a poor substitute for A1/A2 — but it is not nothing.

**Reverses if:** PSMF decline A1/A2 outright, at which point a partial roster
scraped from scoring tables after round one becomes the fallback.

---

## 2026-08-31 · A half-time exists. "The clock never pauses" was over-stated — mine

Found by using the app: there is **no way to end the first half**. One
`Ukončit utkání` button and nothing between kickoff and the final whistle.

**This is my specification error.** `prompts/04` said *"THE CLOCK NEVER PAUSES…
no pause, stop, resume or adjust control on the screen or behind it, and a test
asserts their absence"*, aimed at golblok's injury-stoppage behaviour. Written
that hard, it also forbade the legitimate control. A session following it faithfully
could not have built one.

**The rule, stated correctly:** the clock does not stop *during play* — PSMF's
`hrubý čas` runs through injuries and stoppages, and the referee adds time at the
end. **A half-time interval is not a pause during play.** 2 × 30 means two periods
with a break between them, and the break is not part of the 60 minutes.

What follows:

- The console needs **end-of-period** and **start-of-next-period** actions. Three
  stored instants instead of one, all persisted, all derived from — never ticking.
- **`30´+` stops being something the referee remembers to select.** An event logged
  while the first period is running past its 30th minute *is* `30´+`, derivable
  from the period state. Same for `60´+`. That is the notation getting more
  correct, not more complex.
- **The half-time score stops being typed in.** It is the score at the moment the
  first period ended. Pre-fill the recap from the event log and leave it editable —
  a goal can be logged late — but never blank, and `HALF_TIME_MISSING` as a
  readiness problem goes away.
- **Drive it from `periods`**, already in the group file beside `halfLengthMinutes`.
  Two is universal in HL; veteran and futsal competitions may differ, and both
  numbers are data, not constants.

Still true, and still asserted by a test: **no pause, stop or resume control, and
no golblok `Pause`/`Stop`/`PlayArrow` icon anywhere near this screen.**

**Reverses if:** nothing. A match with no half-time is not the game being played.

---

## 2026-08-31 · The league rules panel translates. The report does not

In Settings, the rule labels — `Délka poločasu`, `Počet poločasů`,
`Hráčů na hřišti`, `Oslabení po vyloučení` — are hardcoded Czech constants in
`SettingsViewModel`, so they stay Czech in English and Ukrainian.

That is **the "always Czech" rule applied one layer too far**, and it is the second
time this has happened — the first cost us the language picker. The rule is narrow
and worth restating precisely:

> **The ZoU is always Czech.** Its field labels are fixed strings in `ZouLabels`
> and `ZouWords`, never resources, because PSMF receive that document.
> **Everything the referee reads on screen translates**, because the referee — and
> the captain looking over their shoulder — is who it is for.

The rules panel is app UI. It translates. The *values* (30 min, 2, 5+1, 10 min) are
numbers and do not.

**Reverses if:** nothing. Ask of any string: does PSMF receive it, or does a person
holding the phone read it?

---

## 2026-08-31 · Console actions become icons, and follow becomes a star

Both from testing on a real phone, and both are golblok being right.

**`Gól` and `Karta` become icons.** Text in a player row buys a small tap target
in the widest part of the layout, on the one screen where attention is scarcest and
the referee may be gloved. An icon is bigger in the same space, recognised faster,
and identical in three languages.

Keep the localised string as the **`contentDescription`**: screen readers keep
working, and so do the existing UI tests, which find these by text.

Whether the card control splits into a yellow and a red icon — colour being the
fastest discriminator football has — or stays one icon opening the sheet, is a
call to make **with a phone in hand**, measured against row width and mistap risk
at 411dp. Not decidable from a desk.

**`Nesledovat` becomes a star**, empty and filled. A button labelled with the verb
for what will happen reads as a description of the current state and gets it
backwards. A star toggles in place, is the convention everywhere else, and needs no
translation. (Plus/minus was the alternative; it implies adding a team to something
rather than marking one.)

**Reverses if:** the split-card layout crowds the row on a real device, in which
case one icon and a colour choice in the sheet.

---

## 2026-08-31 · Filters are sized to their data: pickers for tens, a text field for hundreds

The Zápasy and Týmy filters were built against twelve teams in one group and were
knowingly provisional. At real scale each one wants a different control, and the
deciding factor is simply how many things are in it.

| Filter | Items | Control |
|---|---|---|
| League | ~60 in 8 leagues | **Two cascading rows of chips** — league number, then group letter. |
| Venue | ~35 | **Picker.** New — and arguably the referee's most natural filter: their duty roster is *a pitch at a time*. |
| Team | ~900 | **Text field.** A picker at nine hundred rows is not a control, it is a scroll. |

The league one is a cascade because that is **PSMF's own shape**: psmf.cz presents
`6. liga` and then `A … L`, and referees say "šestka K" out loud. Group counts run
1, 2, 4, 6, 9, 12, 12, 14, so neither row ever needs scrolling — against sixty rows
in one flat menu, which is what ships today.

**Chips rather than dropdowns**, for three reasons that all point the same way: a
menu costs a tap to open before the tap to choose, hides the current state until
opened, and gives smaller targets than a chip. All three are wrong for a cold thumb
outdoors. A league alone stays a valid filter — "everything in the 6th" is a real
thing to want.

Worth not gold-plating: the league filter is for **browsing**. The referee's working
questions are *what am I refereeing* and *what is on at my pitch*, which the
followed-team and venue paths answer.

Likewise **Týmy opens on followed teams**, with search to add more and browse-by-
league as the secondary path. Rendering nine hundred rows flat is not a list anyone
uses.

`/hriste/` on psmf.cz carries every venue with its code, address, surface and
footwear rules, so the venue filter is cheap the moment real data lands — and it
also closes the `venues.json` gap, which has been open since Phase 2.

**Reverses if:** the real distribution surprises us — for instance if most referees
only ever look at two or three pitches, which would make venue a pinned shortcut
rather than a filter.

---

## 2026-08-31 · Saving the export asks for a folder once, not for three files every time

**Reversing a decision I specified badly.** `prompts/05`, `DEMO_SCOPE.md` and the
entry below all named `ACTION_CREATE_DOCUMENT` as the mechanism. The build session
implemented that constant literally and correctly, and flagged the consequence at
the gate: the API creates exactly one document per launch, so three formats means
**three consecutive system dialogs**, chained.

For a referee standing on a pitch in November, doing this twice a week, that is
three dialogs too many. The mistake was mine — I named an API where I should have
named an outcome.

**Use `ACTION_OPEN_DOCUMENT_TREE`:** ask for a folder once, take a persistable URI
permission, and write all three files into it. Every later export writes silently
to the same folder, with a way to change it in Settings. iOS is unaffected — the
share sheet's "Save to Files" is already one interaction.

Nothing else changes: same three files, same names, and the same
`ZouDocument.bytes()` encoder that already makes saved and sent bytes identical.

**Reverses if:** a referee turns out to want a different destination per match, or
Android's persisted-permission behaviour proves unreliable across reinstalls. Both
would be visible on the first real outing.

---

## 2026-08-31 · Correction to the record — Oswald does carry Cyrillic

The shell-rework report states that *"Noto Sans carries body text because it has
Cyrillic coverage Oswald does not."* **That is wrong**, and it inverts the reason
the two faces were chosen.

Verified against the bundled files themselves, not the metadata: `oswald_regular`,
`oswald_bold`, `noto_sans_regular` and `noto_sans_bold` all cover `ě ř ů` **and**
`а і ї ґ`. Oswald was picked over PSMF's own **Anton** precisely *because* Anton
has no Cyrillic and Oswald does.

**The code is right** — `PsmfTheme.kt` says "the nearest condensed grotesque to
PSMF's own Anton *and has Cyrillic*, which Anton does not." Only the report
garbled it. Recorded here because a future session reading that sentence could
swap Oswald out on a false premise.

The real reason for the split is the ordinary one: Oswald is a condensed display
face and is unreadable as body text at 14sp. Noto Sans is a body face.

**Reverses if:** nothing. It is a fact about four files on disk.

---

## 2026-08-31 · Saving three files means three system dialogs, taken literally

> **SUPERSEDED** by the folder-picker entry at the top of this file. Kept because
> it records the reasoning, and because it correctly identified the fix.

`ACTION_CREATE_DOCUMENT` creates exactly one document per launch. There is no
batch form of it. DEMO_SCOPE and the earlier export decision both name it by
that exact constant, so the save step asks for it three times in a row — one
system "Save As" dialog per format, each pre-filled with that format's own file
name, chained automatically so the referee taps through rather than starting the
flow over.

Verified on the emulator rather than assumed: three "SAVE" taps land three files
in Downloads, visible in the system Files picker afterward, with sizes matching
the mailed copies exactly.

**The smoother alternative exists and was not taken.** `ACTION_OPEN_DOCUMENT_TREE`
asked once for a folder, then three `DocumentFile.createFile()` calls into it,
would read as one flow instead of three — closer to what the iOS share sheet
does when handed three items at once. Left as three dialogs because that is the
mechanism named twice in writing, not because it is the better experience.

**Reverses if:** the three-dialog flow is judged too clunky for a real referee to
use twice a week, at which point `ACTION_OPEN_DOCUMENT_TREE` is the fix and it is
a same-sized change.

---

## 2026-08-31 · The save step needed a different shape than the send step

`ReportSender` (mail) is a Koin `single` built from the Application context and
fires an intent with no result to wait for. `ReportSaver` (the new save step)
needed the opposite of both: `ACTION_CREATE_DOCUMENT` returns a `Uri` through
`ActivityResultRegistry`, which only a live `ComponentActivity` has — the
Application context Koin injects everywhere else in this app cannot open a
picker at all.

Built as `rememberReportSaver()`, a `@Composable expect`/`actual` constructed at
the point of use rather than injected — the same pattern `LocalAppLocale`
already established for a platform capability Koin cannot reach. Inside it,
`ActivityResultRegistry.register` is called directly rather than through
`registerForActivityResult`, because the launcher is built once and reused for
every export screen visited afterward, well after the point Compose's own helper
requires registration to happen by.

**Reverses if:** nothing. The shape follows from what the platform API needs,
not from a preference.

---

## 2026-08-31 · "The saved bytes are identical to the sent ones" is now structural, not a claim

`AndroidReportSender` wrote through `File.writeText`; the new save path writes
through a SAF `OutputStream`. Two Android APIs, easy to encode two slightly
different ways without anyone noticing until a Czech diacritic or the CSV's
byte-order mark came out wrong on one side and not the other.

Both now go through one `ZouDocument.bytes()` extension in `shared`, so nothing
downstream can drift. Proved twice: `ZouDocumentBytesTest` pins the byte-level
contract (the BOM survives encoding as three bytes, `EF BB BF`, and the other
two formats never gain one) on the JVM, where an Android write path cannot run
at all; the emulator then sent and saved the same match and `cmp`'d the six
files, byte for byte identical, first three CSV bytes `ef bb bf` on both sides.

**Reverses if:** nothing.

---

## 2026-08-31 · A test that cannot fail is worse than no test

> Third occurrence in this project, after the CSV byte-order mark. Note the
> counter-example from the same session though: the Ukrainian tab-label wrap
> *was* testable, by reading `SemanticsActions.GetTextLayoutResult` inside a
> fixed-width container. Reach for that before concluding something cannot be
> tested.

The Týmy tab had a defect only a device could show. Clearing the search left
the followed teams scrolled off the top of the screen: `LazyColumn` keeps its
place by the **key** of the first visible item, and clearing the query puts the
followed section back above the league heading the list was anchored to. The
referee clears a search and appears to have lost their followed teams.

Fixed in a line. The interesting part is what happened next: **three attempts at
a test that fails without that line all passed with it deleted.** A phone-sized
container, `performScrollTo`, and the list's own scroll-to-index with
`--rerun-tasks`. The JVM test host does not preserve a lazy list's scroll
position across the recomposition, so there is nothing for a test to catch.

The attempt was deleted rather than committed. A test that passes either way
does not merely fail to help — it asserts, in the place a reader looks for
assurance, that the behaviour is covered. The next person to touch that screen
would believe it.

What went in instead: the fix, a comment beside it saying plainly that nothing
in the repository covers it and only a device will notice if it is removed, and
this entry.

**Generalises**, and this is the third instance in two days: `assertIsDisplayed`
is not a layout assertion (Gate 2, tab labels), an unbounded test host is not a
phone (Gate 2 and here), and now — a lazy list's scroll behaviour is not
reproducible off-device at all. For anything that depends on how much fits on a
screen, the emulator is the instrument, not the test suite.

**Reverses if:** Compose Multiplatform's test host starts modelling lazy scroll
state faithfully, at which point the test is worth writing.

---

## 2026-08-31 · "Followed teams first" read as the filter's ordering, not the list's

The Týmy scope said the fixture list should "filter by league and by team,
followed teams first". Two readings: the *team picker* lists followed teams
first, or *fixtures involving followed teams* sort ahead of the rest.

Built the first. The fixture rows stay in round and kickoff order, which is the
order a referee reads them in and the order the paper schedule is printed in;
promoting some rows above others would break the round headings that make the
list navigable. The picker is where the ordering earns something real — at nine
divisions it is otherwise a nine-hundred-item scroll, which is the problem the
follow button exists to solve.

**Reverses if:** the other reading was meant. It is a small change and it was
flagged at the gate rather than assumed away.

---

## 2026-08-31 · The Týmy tab is thin because the data is thin, and that is worth seeing

Search across twelve teams in one group. Browse by league, where the league menu
holds one league. A roster where the `Číslo RP` column is empty for every player
because PSMF have not issued RP numbers.

None of that is a defect and all of it looks like one. The tab is built for nine
divisions and is being shown against one twelfth of one of them, so every control
on it is doing visibly less work than it is for. Three things follow:

- The screens say why rather than showing blanks — the roster carries a sentence
  about RP numbers instead of a column of dashes.
- The team picker is a `DropdownMenu`, which is right for twelve and wrong for
  nine hundred. At real scale it needs the searchable picker the Týmy tab already
  has. Left as a menu deliberately: a picker designed against twelve rows would
  be a guess.
- **Real seed data is the thing that would change most about how this demo
  reads**, which the "real psmf.cz seed data is now blocking" entry already says.
  This is the second piece of evidence for it.

**Reverses if:** nothing. It is an observation, not a decision — recorded so that
"the Týmy tab looked empty" is not mistaken for a build problem.

---

## 2026-08-31 · `key(language)` stays in the locale wrapper, unproven

JetBrains' documented `LocalAppLocale` workaround wraps the content in
`key(language) { ... }`, which disposes and rebuilds the subtree when the
language changes. **Neither target can show that it does anything.** Removed, the
language still switches live: on the JVM because the actual reads
`Locale.getDefault()` fresh on every composition, and on Android — tested on the
emulator with a purpose-built APK — because the pick itself invalidates
everything below it, so every `stringResource` re-resolves anyway.

**Kept regardless**, for two reasons. It is what JetBrains document, and the cost
of being wrong is asymmetric: the failure it guards against is a screen that
keeps the old language after a switch, which is invisible to a Czech-reading
developer and obvious to the Ukrainian captain holding the phone. The rebuild
costs nothing measurable and loses no state — the mid-match test and the device
both confirm the match, the clock and the tab's own back stack survive it.

What it would protect: any composable that caches a resolved string across
recompositions, e.g. `remember { }` around a `stringResource`. Nothing does today.

**Reverses if:** a Compose Multiplatform release documents the invalidation
behaviour as guaranteed, or the rebuild starts costing something visible.

---

## 2026-08-31 · Tab labels are 12sp, and `assertIsDisplayed` is not a layout assertion

The tab bar used `labelLarge` — 14sp bold — on the reasoning that touch targets
and contrast beat density outdoors. On the emulator in Ukrainian, `Налаштування`
broke after eleven of its twelve letters and left an orphaned `я` on a second
line against the edge of the screen.

Two things were wrong, and the second matters more than the first:

- **14sp was a deviation.** Material's own bottom bar is 12sp Medium. The 56dp
  touch target is what makes the tab tappable in gloves; the label does not have
  to carry that.
- **The test that should have caught it asserted the wrong thing.** It called
  `assertIsDisplayed()` on all four labels and passed — a wrapped label is still
  displayed. And the harness had no width, so a quarter of it was never a quarter
  of a phone. Replaced with a test that constrains the bar to 411dp and reads
  `lineCount` out of the real `TextLayoutResult`: one line at 100%, at most two at
  130%. Verified by canary — put 14sp back and it fails with
  `"Налаштування" wrapped expected:<1> but was:<2>`.

**Generalises:** for anything the "Ukrainian strings run longer" constraint
covers, `assertIsDisplayed` is not evidence. Measure the layout or size the
container.

**Reverses if:** nothing.

---

## 2026-08-31 · The app gets tabs, and the report stays a wizard inside one of them

The demo shipped as an eight-stop linear wizard. Reviewed on a device it reads as
one long form: the referee only ever presses back and forward, and Settings and the
fixture list are unreachable without abandoning the report.

**Four tabs**, which is exactly what golblok already has and where the mental model
came from:

| Tab | golblok's | Holds |
|---|---|---|
| **Zápasy** | Home | Fixtures across all groups, filterable by league and team |
| **Zápis** | Match | The report — still a linear wizard, badged when one is in progress |
| **Týmy** | Teams | Followed teams, search, and a roster view |
| **Nastavení** | Settings | Language, theme, league rules as information |

**The report itself stays linear.** A ZoU has a required order and a referee
outdoors should not be choosing where to go next. Tabs go *around* the wizard, not
through it.

Two consequences:

- **`AppNavigator` needs a stack per tab** — a map of stacks rather than one flat
  list. Kept hand-rolled: thirty lines against a navigation library in a version
  matrix `BUILD_MATRIX.md` works to keep at one real constraint.
- **The ongoing-match indicator comes back.** `DEMO_SCOPE.md` said fixtures was
  "not a dashboard, no ongoing-match hero". With tabs, a match in progress must be
  reachable from anywhere or the referee gets lost in Settings mid-half. **This
  entry reverses that clause.**

The clock is unaffected: elapsed time is *now minus kickoff*, one stored instant,
so leaving the console tab cannot drift or lose it. Designed for process death,
free for tabs.

**Reverses if:** nothing likely. Bottom navigation is the convention on both
platforms and the wizard survives intact inside it.

---

## 2026-08-31 · The language picker returns to Settings

The demo session made language follow the device and flagged the divergence from
`DEMO_SCOPE.md` itself, reasoning that "a referee who set their phone to Ukrainian
has already answered the question."

Wrong here, for a reason neither document had written down: **the phone is read by
more than one person.** The captain confirms the lineup on the referee's phone, and
both captains confirm the recap. A device-level language serves the phone's owner
only — a Ukrainian captain confirming on a Czech referee's phone is precisely the
case the three languages exist for.

**Implementation is not a setting.** JetBrains state there is no common public API
and document an `expect`/`actual` `LocalAppLocale` CompositionLocal as the
workaround — Android through `Configuration.setLocale`, iOS through
`NSUserDefaults` `AppleLanguages`. Roughly fifty lines of platform code. Use the
documented pattern rather than inventing one.

The report language is untouched: the ZoU is always Czech, whatever the picker
says. That rule is enforced by `ZouLabels` being fixed strings, not resources.

**Reverses if:** nothing. The multi-reader case is structural.

---

## 2026-08-31 · The Týmy tab is reference, not editing — absences stay per match

The tab is personal in the sense of *followed teams*, not in the sense of owning
them. **This is still a single-persona app: the referee's.**

**What it does:** search teams, follow them into a personal list, browse by league,
open one to see the roster — names now, RP numbers and statistics later. Jersey
numbers are editable here, because a default jersey number genuinely is a standing
attribute of a player.

**What it does not do: absences.** Absence is a fact about one match. Marking it in
a team screen would either stick forever or need a fixture attached, at which point
it is the lineup screen with extra steps. It stays where it is.

Two things this forces:

- **Jersey edits need their own table.** Seed data is a bundled resource replaced
  on every app update, so an override cannot live in it. And the lineup already
  snapshots the jersey per appearance, so editing one cannot move a past report —
  the same rule as the kit label and `reportedIdentification`, already satisfied.
- **"Download" is the wrong word** for content already bundled. It is *follow* —
  `Sledované týmy`. No progress bar for a file already on the device. It also stays
  the right verb if a backend ever appears.

**Reverses if:** the product grows a captain-facing surface. Then absences move
into a pre-match draft, and the device-to-device handoff (golblok's QR machinery)
or a backend becomes a prerequisite — neither exists, so a draft today could not
reach the referee's phone anyway.

---

## 2026-08-31 · Real psmf.cz seed data is now blocking, not queued

The Týmy tab is a search box over twelve placeholder teams in one group until real
data lands. Nine divisions and ~900 teams is what makes search and follow worth
building; 6K alone demos worse than the flat list it replaces.

Bundling every group costs nothing architecturally — adding a group is a file plus
an index line, by construction. The data is the blocker, not the code.

**Reverses if:** nothing. Moved from `TODO.md` Queued to Next.

---

## 2026-08-31 · Visual identity — PSMF's own, sampled from psmf.cz

Read off the live site rather than guessed.

| Role | Value | Where it is on the site |
|---|---|---|
| Brand yellow | `#FBBA00` | The logo block, section headings, primary buttons |
| Ink | `#2B2B2B` | Body text, the dark nav bar |
| Black | `#000000` | The top nav strip |
| Surface | `#FFFFFF` | Content cards |
| Page | `#F2F2F2` | Behind the cards |
| Alert red | `#D60010` | The site's single accent — takes the red card |

Yellow on white fails contrast for text. It is a **surface and accent** colour:
fills, bars, the selected tab indicator, the match-in-progress badge. Text on it is
`#2B2B2B`, never white.

**Fonts — and the trap.** The site uses **Anton** for headings and **Barlow** for
body. Both are Google Fonts under the OFL, so bundling is free. **Neither has
Cyrillic** — verified against Google's font metadata: both are `latin`,
`latin-ext`, `vietnamese`. The replacements' coverage is verified harder, because
metadata is a claim about a file rather than the file: a test parses the `cmap`
table of each bundled `.ttf` and asserts the six letters that separate Ukrainian
from Russian, and the Czech diacritics beside them, are actually in it. Czech diacritics are covered by `latin-ext`; Ukrainian
is not covered at all and would silently fall back to a system face mid-screen.

So: **Oswald** for display and **Noto Sans** for body — both carry `cyrillic` and
`cyrillic-ext`, Oswald is the nearest condensed grotesque to Anton, and Noto Sans
is what golblok's `Type.kt` always intended to load.

**golblok has no custom fonts at all.** Its `Type.kt` carries a comment saying the
families were mapped to `FontFamily.SansSerif` pending real files that never
arrived. Take its type *scale* and its icon vocabulary; do not expect a font.

**Reverses if:** PSMF supply a brand guide that disagrees, or object to their logo
appearing in the app. Worth asking alongside the A-questions.

---

## 2026-08-31 · Export also saves to the device, not just to mail

Files currently go to app-private storage and out through a mail intent. The
referee can never open them afterwards, which makes the app the only route to work
they are personally accountable for.

Adding a save-to-device step needs a platform mechanism on each side
(`ACTION_CREATE_DOCUMENT` on Android, the share sheet's "Save to Files" on iOS).
Small, and it changes how much the app can be trusted.

**Reverses if:** nothing.

---

## 2026-08-31 · SQLDelight migrations before any further schema change

There are none. Two phases have already added columns, and the demo report states
a clean install is required. The Týmy tab adds tables — followed teams, jersey
overrides — and the phone is now a repeated install target.

Without migrations every update either crashes on open or needs an uninstall, and
an uninstall takes the match database with it. **Migrations land before the tab
work, not after.**

**Reverses if:** nothing.

---

## 2026-08-30 · AGP 9 migration done — four of six pins were dead

Everything is now on latest stable: AGP 9.3.2, Gradle 9.7.1, Kotlin 2.4.10, Compose
Multiplatform 1.12.0, lifecycle 2.11.0, compileSdk/targetSdk 37. Three modules —
`androidApp` (entry point only), `composeApp` and `shared`, the latter two on
`com.android.kotlin.multiplatform.library`.

**Only one of the six pins was real:** Compose Multiplatform 1.12.0 requires
compileSdk ≥ 37, verified by building at 36 and reading the AAR-metadata failure.
The Kotlin pin in particular was collateral damage — the old matrix blamed Compose
tooling for not reading Kotlin 2.4 metadata, which did not reproduce. Kotlin went
to 2.4.10 on its own, before Compose moved, and built clean.

Payoff beyond tidiness: compileSdk can move again, so the annually rising Play
Store targetSdk floor stops being a deadline. Tests went 84 → 155; cold build
136 → 112 s; `:shared:jvmTest` 13 → 4 s, which is what the Stop hook pays.

Traps found, all now in `BUILD_MATRIX.md`: the JetBrains migration guide is out of
date (`androidLibrary {}` is deprecated for `android {}` nested in `kotlin {}`, and
its import does not resolve); SDK platforms now carry minor versions
(`platforms;android-37.0`); `buildToolsVersion` still needs pinning or AGP
re-downloads it every container run. The `org.jetbrains:annotations` force is still
required and was never SQLDelight-specific — Gradle pins 13.0 on the buildscript
classpath and AGP drags 23.0.0 in via ddmlib.

**Reverses if:** nothing. AGP 10 removes the legacy path entirely, so this was
mandatory regardless.

---

## 2026-08-30 · Kit label must be snapshotted, not referenced — CORRECTION TO MY OWN SPEC

The prompt said the lineup records *which kit was worn* as a reference. That is
inconsistent with the rule applied one section earlier to identification, and the
build session flagged it correctly.

Editing a team's kit label later would change an old report's `Barva dresů`.
Both fields are printed on the ZoU, and a report must state what was written on the
day — the same principle as `reportedIdentification` and as the versioning rule in
analysis §5.3.

**Fix:** the lineup stores the kit **label verbatim** as a snapshot, alongside the
reference kept for UI. The report reads the snapshot and never the reference.

**Reverses if:** nothing. The inconsistency was an error in the specification.

---

## 2026-08-29 · Kit sets and player identification, corrected

**Kits.** A team does not have *a* colour — it owns **two kit sets** and picks one
per match so two teams do not play in similar colours. That is why the ZoU asks for
`Barva dresů` on the lineup block, filled at the match: it records what was worn
that day. Seed data holds the sets; match data holds the choice.

Each kit keeps a verbatim `label` and a structured `colours` list. Both are needed
because the report takes exactly what PSMF writes, and "bílo-černá" is not
mechanically derivable from ["bílá", "černá"] — the first element takes a different
grammatical suffix in Czech. `label` is authoritative and never derived.

**Identification.** Replaces the earlier single `identifier` + `identifierType`
pair, which collapsed two different things into one.

- **RP number** — issued by PSMF at registration, immutable, arrives from their
  database. The user can never type or edit one.
- **Date of birth** (or rodné číslo, pending A28) — the fallback, entered by a
  person when there is no RP number available.

A player must carry at least one of the three, enforced at construction. If the RP
number is present nothing else is required.

This supports a case the paper already handles and the earlier model did not: a
player turns up who is **not yet in PSMF's database**. The user adds them with name,
surname and date of birth. No RP field is offered, and they are flagged as
user-added for later reconciliation.

What was actually written in the ZoU's single column is recorded **per match** with
its source, not derived at export — a report states what was written on the day. If
a player later gains an RP number, old reports must not change retroactively. Same
principle as the versioning rule in analysis §5.3.

**Reverses if:** A28 resolves the rodné číslo question, which removes one of the
three cases.

---

## 2026-08-29 · minSdk 28, and no network in the app

`minSdk 28`, chosen for consistency with the company's other apps. Moves out of
TECH_STACK §5 Open.

No network calls at all. The report leaves by platform email intent. Ktor stays in
the version catalog but is wired to no module.

**Reverses if:** RP numbers arrive and the roster has to be fetched (A1/A2), at
which point Ktor is already catalogued and ready.

Side effect worth recording so nobody investigates it twice: the release APK went
9.4 MB → 25.7 MB at minSdk 28. That is not bloat. From API 28, AGP stores DEX
**uncompressed** so ART can memory-map it instead of extracting at install. Proven
by building one commit twice with only `minSdk` differing. Installed footprint and
cold start both improve. **Watch uncompressed DEX for real growth, not APK size.**

---

## 2026-08-29 · AGP pinned to 8.13.2 — CORRECTED 2026-08-29

The scaffold session bisected a real incompatibility: since AGP 9, a module
applying `org.jetbrains.kotlin.multiplatform` cannot also apply
`com.android.application` or `com.android.library`. That finding is **correct** and
confirmed by JetBrains and Google documentation.

The conclusion drawn from it was **wrong**. It recorded six versions as locked
together until "AGP becomes compatible with the KMP plugin." AGP 9 is already
compatible — it requires a **module restructure**, not upstream work:

- extract the Android entry point into its own `androidApp` module applying
  `com.android.application`
- move `composeApp/src/androidMain` to `androidApp/src/main`
- switch the shared module to `com.android.kotlin.multiplatform.library`
- replace its `android {}` block with `kotlin.androidLibrary {}`

**This is not optional.** The legacy path can be held open with
`android.enableLegacyVariantApi=true`, but it is **removed entirely in AGP 10**,
which JetBrains dated to Q2 2026 — already passed.

Cost of staying pinned: `compileSdk`/`targetSdk` frozen at 36 while Play's
`targetSdk` requirement escalates annually, plus Compose Multiplatform 1.12,
Kotlin 2.4 and lifecycle 2.11 blocked for no reason.

**Do the restructure before the six screens exist.** At 15 commits with one
placeholder screen and almost nothing in `androidMain`, this is the cheapest it
will ever be.

Sources: [KMP AGP 9 migration](https://kotlinlang.org/docs/multiplatform/multiplatform-project-agp-9-migration.html) ·
[JetBrains blog](https://blog.jetbrains.com/kotlin/2026/01/update-your-projects-for-agp9/)

---

## 2026-08-29 · Repo on WSL, management layer on Windows

Code lives at `~/dev/psmf-app` (ext4). This folder holds planning only.

**Why:** measured 34s warm build on ext4 versus 187s on a Windows bind mount, and
the container even beat native Windows Gradle (74s). The overview session writes
documents rather than building, so it does not need the fast filesystem.

**Reverses if:** never, for the code. The measurement is unambiguous.

---

## 2026-08-29 · TAUT deferred for this project; piloted on golblok instead

**Why:** TAUT was originally justified by a hard fork into two similar Android
repos needing shared process. The business analysis cancelled that — this is green
field KMP, golblok is legacy Android, different stacks and different laws. The
"one pack, two repos" value evaporated. What remains (hash-locked workspace,
runtime guard, MCP pinning) is good hygiene rather than a problem we have. Adding
an unproven build step between us and CLAUDE.md at project inception is the wrong
risk at the wrong moment.

Reviewed v0.2.0 (6 commits, last 2026-08-20): ~5,200 lines of source, 2,921 of
tests, CI, Apache-2.0. Pack authoring is cheap — the reference pack is 12 files,
411 lines, and `repos/<repo>/CLAUDE.md` means an existing CLAUDE.md moves in
nearly as-is. Windows still unsupported and WSL still unmentioned, but that is now
moot: Ubuntu is Linux with Node 24.

**Reverses if:** the golblok pilot goes well and this project grows a second repo
or a second developer.

---

## 2026-08-29 · Supabase over PostgREST, and no backend at all until RP numbers exist

For the demo and shadow-recording phase: no backend. Public data is scrapeable and
the report goes out by email, an already-accepted channel.

When RP numbers arrive: Supabase (Postgres), read from the app via PostgREST over
Ktor with **no SDK**. A scheduled job scrapes psmf.cz into it; the app never reads
PSMF directly.

**Why Postgres:** the domain is relational and season-scoped — `TeamEntry` and
`SquadMembership` are season-scoped joins (analysis §6). A document store fights
that shape.
**Why PostgREST and not an SDK:** neither Supabase nor Firebase ships a first-party
Kotlin Multiplatform SDK. Plain REST from shared code carries no dependency and
behaves identically on both platforms. Firestore effectively requires a community
wrapper. Firebase's real advantage is its offline cache, and that is neutralised
because one whole group (~180 players, tens of KB) fits on the device permanently.

**Reverses if:** A1/A2 reveal the roster data is shaped very differently, or the
product needs real-time multi-writer sync — which the analysis argues it must not,
since there is exactly one recorder.

---

## 2026-08-29 · Report output: JSON, CSV and formatted text first. PDF and xlsx server-side.

**Why:** in KMP, text-based formats are shared code and effectively free. PDF and
`.xlsx` have no good shared-Kotlin library, so building them in the app means
writing each twice. Rendering them server-side from a JSON payload gives one
implementation and no platform code. Note the official ZoU is itself an `.xlsx`,
so a spreadsheet in their layout may be both easier than PDF and more useful.

**Reverses if:** A8 says PSMF needs PDF specifically and server-side rendering is
not available in time.

---

## 2026-08-29 · Published under the PM's company; `cz.hspinovace.psmf` provisional

The company already ships golblok and one other app on both stores, so the Apple
organisation account and Play Console account exist. This removes the D-U-N-S
enrolment lead time entirely — previously the longest-lead item in the project.

**Still open:** golblok is `cz.hsp.footballmatch`, so the convention looks like
`cz.hsp.*` rather than `cz.hspinovace.*`. Also worth considering `zapis` over
`psmf` in the last segment — it names the product rather than the customer, which
ages better if the company serves a second league.

**Reverses if:** never, after publication. `applicationId` and the iOS bundle
identifier are permanent once a listing exists. Settle before first upload.

---

## 2026-08-29 · Player identifier is one field plus a discriminator

`identifier: String` + `identifierType ∈ {RP, DATE_OF_BIRTH, BIRTH_NUMBER}`.

**Why:** the ZoU has a single column holding either a `Číslo RP` or a date of
birth. The model should match the form. `BIRTH_NUMBER` exists only because A28 is
unresolved — the Soutěžní řád mentions *rodné číslo* while the form does not.

Note on risk: an RP number tied to a name *is* personal data under GDPR, but it is
ordinary personal data — not special category, and nothing like *rodné číslo*,
which is a national identifier with extra protection under Czech law.

**Reverses if:** A28 confirms *rodné číslo* is what actually gets written, which
materially raises the data-protection obligations.

---

## 2026-08-25 · Green field. Not a fork of golblok.

**Why:** the business analysis reuse audit found that assists and substitutions
die outright (neither appears on the ZoU), along with local team creation, QR
roster sharing and user-configurable match rules. What survives is the *interaction
model* of the match console, the timer, undo, crash recovery and the match log —
design, not code. golblok's code is also KMP-hostile: `Context` in repositories,
`org.json`, `AndroidViewModel`.

**Reverses if:** nothing. Confirmed by a screen-by-screen audit.

---

## 2026-08-25 · No team-facing surface for the pilot

**Why:** the ZoU states *"Soupisky vypisují kapitáni týmů"* — captains write the
lineups, at the pitch, before kickoff, in person. Lineups submitted in advance was
an assumption, not a requirement. This removed the argument that iOS was mandatory
for a team-facing app, and cut real scope.

**Reverses if:** the pilot shows a significant share of matches are entered after
the fact rather than live (analysis §5.4) — a referee reconstructing a match at
22:30 will not remember twenty names, and advance lineup submission becomes
important. **This is the single most informative number the pilot can produce.**

---

## 2026-08-24 · iOS in scope for referees. Kotlin Multiplatform.

**Why:** referees cannot be assumed to be on Android, and KMP plus Compose
Multiplatform is by far the smallest jump from Kotlin and Compose. Shared UI
rather than native SwiftUI because the UI is utilitarian data entry for a small
audience — one implementation is worth more than platform-idiomatic polish.

**Open risk:** the MacBook Pro 2018 is Intel, and recent macOS has been dropping
Intel Macs. App Store submission requires a minimum Xcode, which requires a
minimum macOS. Unverified — see `prompts/02-ios-toolchain-proof.md`. Fallback if
it fails: GitHub Actions macOS runners for release builds, Mac for development
only.

---

## 2026-08-23 · Three-way topology, settled by measurement

| | |
|---|---|
| WSL ext4 | agent workspace, 34s builds, cannot push, no keystore visible |
| Windows NTFS | release checkout only, never edited, keystore stays here |
| origin | source of truth |

**Why:** Android Studio cannot run Gradle over `\\wsl.localhost\` — `FileHasher`
memory-maps files in `.gradle/` and mmap is unsupported over 9P. Pointing
`--project-cache-dir` at local NTFS fixes sync and debug builds but **not**
Generate Signed Bundle, because `app/build/intermediates/` is inside the project
and therefore on 9P by definition. Proven by a control build of the same commit on
Windows: successful in 1m34s.

**Applies to this project too**, with iOS as a fourth station.

---

## 2026-08-23 · The agent sandbox is kept, and it is also the fast path

Claude Code runs in a container that cannot push, has no SSH keys, no credential
helper at any scope, and cannot see the keystore. All verified, and re-verified
after the WSL git credentials were wired to the Windows credential manager.

**Why it is not a tax:** the warm container build at 34s beats native Windows
Gradle at 74s by 2.2×. Containerised Linux Gradle on ext4 is simply faster.
Isolation stopped being a trade-off.

---

