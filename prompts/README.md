# Prompts

One brief per working session. Each was written in the overview thread, pasted
into a fresh session, and run to its gates.

**Historical prompts are not instructions.** Once a prompt has been executed it
becomes a record of how that session was briefed — including any paths, versions
and assumptions that were true at the time and are not now. Read them for *why* a
thing was built the way it was; do not re-run them.

| | Prompt | Where | Status |
|---|---|---|---|
| 01 | Android scaffold | WSL container | executed 2026-08-29 |
| 02 | iOS toolchain proof, from a template | Mac | **superseded by 07** |
| 03 | AGP 9 and the seed schema | WSL container | executed 2026-08-30 |
| 04 | The demo screens | WSL container | executed 2026-08-31 |
| 05 | Shell, Týmy tab and styling | WSL container | executed 2026-08-31 |
| 06 | Half-time and five phone fixes | WSL container | executed 2026-09-01 |
| 07 | iOS toolchain proof, against this repo | Mac | **live — Part 1 done, Parts 2–3 open** |
| 99 | golblok maintenance | separate repo | live, unrelated to this app |

## Known staleness in the executed ones

`01` copies planning documents in from `/mnt/c/MS/Projects/PSMFApp/`. That folder
was the planning repository until 2026-09-01, when its documents moved into
`docs/` here. The copy step is history, not an instruction.

Several prompts cite `docs/DEMO_SCOPE.md` and `docs/DECISIONS.md`. Both are now
correct; for most of the project's life the first was 176 lines stale and the
second did not exist. `docs/DECISIONS.md` records that.

## Writing the next one

What made these work, in rough order of value:

- **Name the outcome, not the API.** `prompts/05` said
  `ACTION_CREATE_DOCUMENT` where it meant "let the referee keep the file". The
  session implemented exactly what was written and was exactly wrong: three
  chained system dialogs. A prompt that names a mechanism can be followed
  perfectly and still produce the wrong thing.
- **Say what is already known to be broken**, so the session does not treat an
  expected failure as a discovery.
- **Gate every phase, and forbid batching.** One commit per gate is what makes a
  defect attributable to the phase that introduced it.
- **Ask for judgement calls back**, rather than pre-deciding what needs a device
  in hand. `06` left the split-card question open on purpose and got a reasoned
  answer with a screenshot.
- **Say plainly what must not change**, and why. The clock rule survived four
  sessions because it was restated every time — and the one time it was
  *over*-stated, it silently forbade the half-time control for two sessions.
