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
