# Decisions

Newest first. Each entry records what was decided, why, and what would reverse it.
The reversal condition is the point — a decision without one is a preference.

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

