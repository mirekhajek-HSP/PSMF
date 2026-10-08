# TODO

Grouped by what blocks what, not by size. Last updated 2026-10-07.

---

## Next

- [ ] **Prompt 10: red cards, the clock, league 6, build 0.2.0 (2).**
      `prompts/10-cards-clock-and-league-6.md`, four gates in the container.
      The five card and clock defects and the scraping terms are in
      `DECISIONS.md`, 2026-10-07.
- [ ] **The tester's red-card report.** Not answering as of 2026-10-07, and
      the owner's call is not to chase it: the review found five defects, and
      prompt 10 fixes them. If the tester describes it later, check it against
      those five. Only something outside them reopens the digging.
- [ ] **Whose Apple membership "expires in 17 days"? Due about 17 October.**
      Shown on developer.apple.com on 30 September. The company's distribution
      certificate runs to 24 July 2027, which suggests a personal membership.
      If it is the company's, only the account holder can renew, and TestFlight
      stops when it lapses. **The PM is handling it** (owner, 2026-10-07).
- [ ] **On the phone: 0.2.0**, the "On the phone" checklist that prompt 10's
      report ends with. Supersedes the `d9f16ab` phone test below. Install
      over the old build; that is the upgrade test.
- [ ] **TestFlight build 2 (0.2.0) from `main` once prompt 10 lands.** On the
      Mac, *TestFlight Internal Only* while the TEST icon stands. Then the
      "First run on an iPhone" checklist, recorded step by step.
- [x] **`ios/export` merged**, 2026-10-07 (`152f637`), after `./gradlew build
      :shared:allTests detekt` went green in the container: 388 + 349 + 182
      tests. The hand-edited Android sources compiled first time.
- [x] **Half-time, and five fixes from the phone** — done. All four gates, tree
      clean, verified independently at `d9f16ab`: build and suite green, 388 shared
      tests, 162 UI tests.
- [ ] ~~**Run the whole app on the phone again** — `builds/psmf-debug-d9f16ab.apk`.~~
      Superseded by 0.2.0 above; the points below still apply to it.
      Six things changed that only a real device can judge: the half-time control
      in the flow of a match, the icon targets, the chip cascade under a thumb, the
      star, the folder pick, and the translated rules panel.
      - **Install over the last one.** Another live migration test, free.
      - The card control stayed **one icon**, decided on an emulator against its
        own reversal condition. That decision wants a real thumb.
- [ ] **Drop `materialIconsExtended`** — five icons are costing 31 MB. The debug
      APK went 38 → 70 MB and is 64 MB of DEX. Copy the five vectors locally.
      Mostly a debug-build artifact, but this project has never built a release and
      iOS has no R8 at all. A morning's work. **0.2.0's debug APK is 69.95 MB**
      (69,948,495 bytes, clean package), 52 KB under 70: the next addition
      crosses it.
- [x] ~~**Wait for A1/A2, then import one whole league. No scraping.**~~
      Reversed 2026-10-07: league 6 is scraped from psmf.cz (prompt 10).
      A1/A2 are still wanted, for RP numbers and dates of birth, which the
      site does not publish.
- [x] **iOS Part 2 — does the code compile?** **Yes, first attempt, no changes.**
      `linkDebugFrameworkIosArm64` green on the Intel Mac with Xcode 26.3 and
      JDK 17; the shared iOS test executable links against `libsqlite3`.
      Verdict (b) confirmed. See `reports/2026-09-01-ios-parts-2-and-3.md`.
- [x] **First TestFlight upload — done 2026-09-30.** Build **0.1.0 (1)**, from
      `ios/export` at `179ce80`, validated and uploaded. Owner's role is App
      Manager; the app record was created by the account holder; the first
      device run on an iPhone 15 worked after one `Info.plist` fix. See
      `reports/2026-09-29-ios-testflight-readiness.md` §8.
- [x] **TestFlight build 1 installed through TestFlight on two devices, and
      it works** (owner, 2026-10-07).
- [ ] **Run the "First run on an iPhone" checklist properly**, step by step. On
      2026-09-30 the app launched and "seemed fine"; the itemised checks (fonts in
      three languages, picker persistence, database survives a kill, every send
      and save case, the CSV's `EF BB BF`) have not been recorded one by one.
- [ ] **Keep the team ID out of the repository.** Selecting the team made Xcode
      write `DEVELOPMENT_TEAM` into `project.pbxproj`, which is left modified and
      uncommitted on the Mac. Moving it into a git-ignored local `.xcconfig` would
      stop that file showing as changed.
- [ ] **Do not update the test iPhone to iOS 27** while this Mac is the build
      machine. Xcode 26.3 is its ceiling and most likely cannot deploy to iOS 27.
- [ ] **Bump `kotlin` only with a Mac run in the loop.** Kotlin/Native 2.4.10
      calls the Intel Mac host deprecated, with no removal version yet. The
      release that drops it ends iOS compilation on this Mac.
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

**All 30 sent 2026-08-31 via the PM. Awaiting reply; still nothing on
2026-10-07.** See `QUESTIONS.md`.
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

- [x] **`applicationId` / iOS bundle ID** — **`cz.hspinovace.psmf`**, both platforms.
      Settled 2026-09-29.
- [ ] **App icon** — to be provided. A temporary **TEST** icon is in place so
      internal TestFlight can run (DECISIONS, 2026-09-30), and on Android since
      0.2.0 (2026-10-07). **Replace it before external testing or any store
      submission**, on both platforms in one commit; delete
      `tools/launcher-icon/` with it. Ask PSMF about their logo.
- [x] **Merge `ios/export` after the Windows machine builds Android.** Done
      2026-10-07; the phone half rides on 0.2.0. Part 4
      of `prompts/09` changed common export code and **edited Android by hand
      without compiling it** (no Android SDK on the Mac). Run `./gradlew build`,
      `:shared:allTests` and `detekt`, then on a phone: save, back out of the
      folder picker (it now says *cancelled*), and send. Then merge.
- [ ] **First run on an iPhone** — the numbered checklist at the end of
      `reports/2026-09-29-ios-testflight-readiness.md`. Needs the company
      team role first; never a personal team.
- [ ] **Before every TestFlight upload:** `iosApp/scripts/check-required-reason-apis.sh`
      on the Release build. Candidate for CI.
- [x] **iPad stays in.** Owner, 2026-09-29: iPad support is welcome, and may
      be dropped if it ever complicates things. So far it has cost one
      `Info.plist` key (all four iPad orientations). It means iPad screenshots
      and iPad review at *store* submission, not at TestFlight.
- [ ] **App name — not decided, and it will be shorter** than *Zápis o
      utkání* (14 characters, which the iOS home screen truncates). Until it
      is, `CFBundleDisplayName` mirrors Android's `app_name` in all three
      languages. Change both together.
- [ ] **One version, both platforms.** Proposed in the TestFlight-readiness
      report, not built: one `Version.xcconfig` holding `MARKETING_VERSION` and
      `CURRENT_PROJECT_VERSION`, which Xcode includes and Gradle parses for
      `versionName`/`versionCode`. One integer build number, bumped for every
      upload to either store.
- [x] **Remote for the repo** — decided: temporary private GitHub repo, then
      transfer to the company org. Promoted to Next; the Mac needs it now.

## Never done, and due

- [ ] **Google Play internal testing**, Android's TestFlight. Needs the
      company's Play Console with a role for the owner, the app record, the
      upload keystore below, and the first release build (an AAB). A public
      release also needs a privacy policy URL and the Data safety form, which
      wait on A10/A26; check whether the internal track asks for them sooner.
      Until then, sideloaded APKs.

- [ ] **A release build.** Not once, in any session. It is where R8, the shrinker
      rules and signing all get exercised for the first time, and where the
      `materialIconsExtended` question gets a real answer rather than an argument.
      Expect keep-rule work for kotlinx.serialization, SQLDelight and Koin.
- [ ] **PSMF needs its own upload keystore, and it does not exist.** golblok's
      `keystore2.jks` / `key_main` is golblok's and must not be reused. Generate
      one, back it up somewhere that is not this machine, and **never let it near
      the container** — losing it means never updating the app on Play again.
      Blocked behind nothing except deciding to do it.
- [ ] **No date for showing PSMF the demo.** Still none on 2026-10-07. The
      point of the whole project, and it is not tracked anywhere. It also gates how much polish is worth doing.

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

- [x] **iOS has run**, on an iPhone 15, 2026-09-30, after one `Info.plist`
      fix (`CADisableMinimumFrameDurationOnPhone`). The itemised checklist is
      still open, under Next.
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
