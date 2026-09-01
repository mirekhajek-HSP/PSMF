# TODO

Grouped by what blocks what, not by size. Last updated 2026-08-31.

---

## Next

- [x] **Half-time, and five fixes from the phone** — done. All four gates, tree
      clean, verified independently at `d9f16ab`: build and suite green, 388 shared
      tests, 162 UI tests.
- [ ] **Run the whole app on the phone again** — `builds/psmf-debug-d9f16ab.apk`.
      Six things changed that only a real device can judge: the half-time control
      in the flow of a match, the icon targets, the chip cascade under a thumb, the
      star, the folder pick, and the translated rules panel.
      - **Install over the last one.** Another live migration test, free.
      - The card control stayed **one icon**, decided on an emulator against its
        own reversal condition. That decision wants a real thumb.
- [ ] **Drop `materialIconsExtended`** — five icons are costing 31 MB. The debug
      APK went 38 → 70 MB and is 64 MB of DEX. Copy the five vectors locally.
      Mostly a debug-build artifact, but this project has never built a release and
      iOS has no R8 at all. A morning's work.
- [ ] **Wait for A1/A2, then import one whole league.** Every group in it, bundled
      on the device. **No scraping.**
- [ ] **iOS Part 2 — does the code compile?** Blocked on two installs, both yours:
      - **Xcode 26.3**, from developer.apple.com "More Downloads" — *not* the Mac
        App Store, which offers 26.4+ that will not run on Sequoia. **Take the
        Universal build**; Intel machines are sometimes served the arm64-only one.
        No Xcode is installed at all today, only Command Line Tools 16.4, and it
        carries **no iOS SDK** — so Kotlin/Native cannot link.
      - **JDK 17** (Temurin). Installed is Java 15 and 13; Gradle 9.7.1 will not
        start on those.
      Then `prompts/07` Part 2 runs: `linkDebugFrameworkIosArm64`, exercising five
      `actual` files on Kotlin/Native for the first time.
- [ ] **Part 3 needs a physical iPhone.** The Intel simulator does not exist for
      this project. An iPhone already owned, a cable and a free Apple Developer
      account is €0 and is the whole unblock.
- [x] **iOS Part 1 — can this Mac ship?** **Yes, and until ~April 2027.** Xcode
      26.3 is the ceiling and clears the current App Store floor. Xcode 27 is
      Apple-silicon-only and macOS 27 drops Intel entirely. A Mac purchase is a
      2027 budget line, not a blocker. See `DECISIONS.md`.
- [x] **Planning documents moved into the app repo** — `docs/`, `prompts/`,
      `reports/`. One copy of each, and `CLAUDE.md` now points at them.

- [ ] **Supabase — when the organisers grant database access**, and not before.
- [x] **Shell, Týmy tab and styling**, **the six screens**, **AGP 9 + seed schema**,
      **scaffold Phases 1 and 2**, **the demo milestone**, **the design questions**.

## Blocked on PSMF answers

**All 30 sent 2026-08-31 via the PM. Awaiting reply.** See `QUESTIONS.md`.
Nothing below can start until the relevant answer lands.

- [ ] Roster storage design — blocked on **A1, A2** (does a usable player database
      exist, and can we get one group's export?)
- [ ] Report output format — blocked on **A8** (PDF, spreadsheet, or both?)
- [ ] Whether the pilot removes any referee work at all — blocked on **A5** (can
      the paper original be dropped?)
- [ ] Commentary entry design, the hardest usability problem — blocked on **A6**
- [ ] Whether "no accounts" survives — blocked on **A7**
- [ ] Data-protection footing — blocked on **A10**, **A26**, **A28**

## Decisions still open

- [ ] **`applicationId` / iOS bundle ID — decide before the first release build.**
      Currently `cz.hspinovace.psmf` on both. Permanent at publication. golblok uses
      `cz.hsp.footballmatch`; check the company's second app and match the
      convention. Consider `zapis` over `psmf` in the last segment.
- [x] **Remote for the repo** — decided: temporary private GitHub repo, then
      transfer to the company org. Promoted to Next; the Mac needs it now.

## Never done, and due

- [ ] **A release build.** Not once, in any session. It is where R8, the shrinker
      rules and signing all get exercised for the first time, and where the
      `materialIconsExtended` question gets a real answer rather than an argument.
      Expect keep-rule work for kotlinx.serialization, SQLDelight and Koin.
- [ ] **PSMF needs its own upload keystore, and it does not exist.** golblok's
      `keystore2.jks` / `key_main` is golblok's and must not be reused. Generate
      one, back it up somewhere that is not this machine, and **never let it near
      the container** — losing it means never updating the app on Play again.
      Blocked behind nothing except deciding to do it.
- [ ] **No date for showing PSMF the demo.** The point of the whole project, and
      it is not tracked anywhere. It also gates how much polish is worth doing.

## Queued

- [x] **Skills, hooks and permissions** — now part of the scaffold prompt rather
      than a follow-up. §1.8 installs `chrisbanes/skills` plus a KMP set,
      project-scoped and separate from golblok's; adds a ktlint format hook and a
      guard rejecting `org.junit` / `io.mockk` in `commonTest`; and commits a Gradle
      permissions allowlist. §2.4 adds the Stop hook once a suite exists.
- [ ] **Scope the ZoU generator.** The actual deliverable and still unestimated.
      Specified field by field in the analysis §2.5, gated on A8 for format.
- [ ] **CI on GitHub Actions** — Linux runner for shared + Android, macOS runner
      for iOS. **The remote no longer blocks this.**

## Known gaps in the demo

None blocking, all from the build reports.

- [ ] **iOS has never been compiled.**
- [ ] **The minute notation has two loose ends** — `Minute.HALF_LENGTH`/`FULL_LENGTH`
      are hardcoded 30/60 while the clock reads the group file, and `60´+` means
      only the final whistle while `30´+` also covers added time. Neither is wrong
      for HL. See `DECISIONS.md`; one for the referee visit.
- [ ] **The `ViewModel`-reads-once-at-startup bug shape** was found once
      (`SettingsViewModel.exportFolderChosen`) and fixed once. Nothing guards
      against the next instance.
- [ ] **The Týmy scroll-restore fix has no test** — the JVM Compose host cannot
      model lazy-list scroll anchoring. Deleting the fake tests was right.
- [ ] **The venue filter is real UI against provisional data** — 7 codes, not ~35.
      `/hriste/` on psmf.cz has all of them with addresses and surfaces.
- [ ] **The commentary field is a placeholder**, not a design. Blocked on **A6**.
- [ ] **TXT/CSV/JSON are a stand-in.** Blocked on **A8**.
- [ ] **One deprecation warning** — `BackHandler`.
- [ ] **Ask PSMF whether their logo may appear in the app.**

## Field research — cheap and high value

**The season's first fixtures are Sunday 6 September 2026.** Until now this was
hard to arrange because nothing was being played. From Sunday there are matches
every weekend across sixty groups, which makes all three of these easy.

- [ ] **Show a referee the working demo.** Was cheap and high-value before any
      code existed; is now cheap, high-value *and* answerable against something
      real. Settles A13–A19 and tests the two designs the build had to guess at.
- [ ] **Watch one referee fill in a real ZoU, start to finish.** Answers most of
      A13–A19 at once and tests the two hardest design problems (lineup capture
      without handing over the phone, and where the commentary actually gets
      written) before a line of code exists.
- [ ] **Photograph one completed real ZoU** — settles A28, the largest legal
      exposure, in a minute.

## Not now

Deferred, not rejected. Listed so they are not rediscovered as new ideas.

- Backend of any kind, until RP numbers exist
- PDF / `.xlsx` rendering — server-side when it happens
- Team-facing web surface
- Device-to-device lineup handoff (golblok's QR machinery suits this job)
- Player photographs — blocked on A9 and A27, currently no regulatory standing
- Suspension warnings — depends on A3
- TAUT-managed workspace — piloted on golblok first

---

## Done

- [x] Business analysis delivered — 728 lines, sourced to PSMF regulations and the
      official form
- [x] Platform decision — Android + iOS, Kotlin Multiplatform
- [x] Stack decisions — recorded in `DECISIONS.md` and the repo's `TECH_STACK.md`
- [x] Ownership — PM's company, which already has both store accounts. Removed the
      Apple organisation enrolment lead time entirely.
- [x] TAUT reviewed and deferred for this project
- [x] Dev environment — WSL2 Ubuntu, native Docker Engine, 34s builds
- [x] golblok separated into its own maintenance track
