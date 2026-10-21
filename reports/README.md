# Reports

What has actually been built, session by session. One file per working
session, newest first.

These are **records of work done** — distinct from the other documents in
this repository, which describe intent:

| Document | Answers |
|---|---|
| `docs/LEAGUE_APP_ANALYSIS.md` | What the product must do |
| `docs/DEMO_SCOPE.md` | What is in the demo and what is cut |
| `docs/DECISIONS.md` · `docs/QUESTIONS.md` | What was settled, what is still open |
| `docs/TODO.md` | What is next |
| `prompts/` | How each working session was briefed |
| **`reports/`** | **What was actually built, and what it cost** |

---

| Date | Report | Covers | Outcome |
|---|---|---|---|
| 2026-10-21 – 22 | [What the testers found in 0.3.0 — build 0.3.1 (5)](2026-10-21-0.3.1-tester-fixes.md) | The cursor that jumped and the keyboard on a low field, compact lineup rows, a lineup that survives the app being killed (schema 8), the console's controls, a Ukrainian tab that fits, the whistles in the log, each kit as a drawn shirt, `materialIconsExtended` gone | Seven gates met · one commit each · 593 + 524 + 378 + 23 tests · debug APK 38.5 MB (0.3.0: 70.3) · proved on the JVM, **none of it yet on a phone** |
| 2026-10-19 | [iOS build 4 — 0.3.0 to TestFlight](2026-10-19-ios-build-4.md) | Build 3's report finished (Part 0); 0.3.0 (4) archived for upload | Built and archived · **the upgrade over build 3 (schema 6 → 7) and the iPhone checklist NOT RUN**, at the owner's request |
| 2026-10-17 – 18 | [The report after the first iPhone test — build 0.3.0 (4)](2026-10-17-report-flow-after-iphone-test.md) | Mark who came, a captain and a goalkeeper, the number from the card, a goal at its own minute, a whistle taken back, the clock in seconds, three-tap signing, the keyboard and *Zpět* | Five gates met · sixteen of the tester's eighteen points · 559 + 500 + 302 + 23 tests · debug APK 70,313,351 bytes |
| 2026-10-11 – 13 | [iOS build 3 — 0.2.1 to TestFlight, and the first upgrade on an iPhone](2026-10-11-ios-build-3.md) | The team ID out of git, 0.2.1 (3) uploaded 13 Oct, the first database migration on iOS (schema 4 → 6) | Finished 2026-10-19 · the upgrade kept the old match, no crash, **by the owner's account; no step record was kept** |
| 2026-10-08 | [Zápasy for the real league — build 0.2.1 (3)](2026-10-08-fixtures-tab-redesign.md) | No loading flash, measured; select boxes, scoped pitches, a date filter, a filter that scrolls away; a stale badge; 0.2.1 (3) | Three gates met · 3 commits · debug APK 70.05 MB, **47,639 bytes over 70** · 454 + 401 + 210 + 23 tests · on the emulator, not yet on a phone |
| 2026-10-07 – 08 | [Cards, the clock, league 6 — build 0.2.0 (2)](2026-10-07-cards-clock-and-league-6.md) | Five card-and-clock defects, schema 5 and 6, league players with no identification, **league 6 imported from psmf.cz**, the Android TEST icon, 0.2.0 (2) | Four gates met · 4 commits · 170 requests to psmf.cz · debug APK 69.95 MB, 52 KB under 70 · 436 + 383 + 194 + 23 tests · not yet on a phone |
| 2026-09-29 – 30 | [iOS TestFlight readiness](2026-09-29-ios-testflight-readiness.md) | Whole-app link, privacy manifest from the binary, `Info.plist` for upload, send and save on iOS, **first device run, first TestFlight upload** | Four gates met · **build 0.1.0 (1) uploaded** · one device-only defect (launch abort, fixed) · export on `ios/export` until Android is checked · 388 + 182 tests |
| 2026-09-28 | [iOS Parts 2 and 3](2026-09-01-ios-parts-2-and-3.md) | Mac prerequisites, `iosArm64` compile and link, Part 1 verdict | Gate 2 met with zero source changes · Gate 3 blocked on hardware · verdict (b) confirmed · new clock: K/N Intel host deprecated |
| 2026-09-01 | [Six fixes from a physical phone](2026-09-01-six-fixes-from-a-physical-phone.md) | Half-time domain fix, console action icons, follow star, cascading filters, translated rules panel, save-folder-once | Four gates met · 4 commits · 388 + 162 tests · four owner-spec corrections · one device-found bug |
| 2026-08-31 | [The shell rework and the Týmy tab](2026-08-31-the-shell-rework-and-tymy-tab.md) | SQLDelight migrations, the four-tab shell, language and PSMF's identity, the Týmy tab, save-to-device | Four gates met · 5 commits · 364 + 151 tests · three device-only defects |
| 2026-08-31 | [The demo screens](2026-08-31-the-demo-screens.md) | Kit-label snapshot, the Android test trap, all eight screens, the ZoU in three formats | Five gates met · 5 commits · 315 + 82 tests · seven device-only defects |
| 2026-08-30 | [AGP 9 and the seed schema](2026-08-30-agp9-and-seed-schema.md) | Three-module restructure, six version bumps, opaque ids, identification split, kit sets, venues, discipline, match clock | Both gates met · 9 commits · 155 tests |
| 2026-08-29 | [Phase 1 and 2 — scaffold and domain](2026-08-29-phase-1-2-scaffold-and-domain.md) | Container sandbox, KMP scaffold, agent tooling, domain model, seed data, persistence | Both gates met · 15 commits · 84 tests |

> **Note on the 2026-08-29 report:** its build-matrix section is
> superseded. It recorded six toolchain versions as locked together pending
> upstream work on AGP; four of them were not, and the 2026-08-30 report
> explains why. The rest of it still stands.

---

## Where everything is

**One repository, since 2026-09-01.** Code, docs, prompts and reports all live
here. The planning documents used to sit in a separate Windows folder; they
drifted badly, which is why they no longer do — see `docs/DECISIONS.md`.

The working copy is at `~/dev/psmf-app` inside WSL Ubuntu, on ext4 deliberately:
34s warm builds there against 187s on a Windows bind mount. Read it from Windows
at the `\\wsl.localhost\Ubuntu\` share. **Never move it to `C:`.**

A session on another machine writes its report here, commits and pushes it. The
repository is the channel.

## Writing the next one

Keep to the shape of the first: status at a glance, what exists, each gate
against its criteria, decisions taken and by whom, findings worth not
rediscovering, open items and known gaps, commit list.

The parts that earn their place later are the **findings** and the **known
gaps** — a version pin whose reason is written down does not get
accidentally bumped, and a gap that is recorded does not get mistaken for
an oversight six weeks on.
