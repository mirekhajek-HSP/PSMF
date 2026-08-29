# Agent tooling

Everything here is committed and travels with the repository. Per-machine
settings belong in `.claude/settings.local.json`, which is gitignored.

## Skills

Fourteen, deliberately chosen rather than copied wholesale. Two sources,
both kept under their own licence (`LICENSE.*` in this directory).

From [chrisbanes/skills](https://github.com/chrisbanes/skills):

| Skill | Why it is here |
|---|---|
| `using-chrisbanes-skills` | The router. Trimmed and extended — see below. |
| `compose-state-and-effects` | ViewModels expose `StateFlow`; the live console is state-driven. |
| `compose-performance` | The live console recomposes on every logged event and a running clock. |
| `compose-component-design` | The action row must take mirroring as a parameter, so its API shape matters. |
| `compose-ui-testing-patterns` | Screen tests, once screens exist. |
| `kotlin-concurrency-and-flow` | The match clock is derived from a kickoff timestamp, and the power-play timer runs alongside it. |
| `kotlin-api-design` | Single-field domain types and value classes — exactly what `Minute` and the player identifier need. |
| `kotlin-control-flow` | Sealed exhaustiveness for `MatchEvent` and the card variants. |
| `gradle-run` | This build is fragile; see `docs/BUILD_MATRIX.md`. |
| `compose-animations`, `compose-focus-navigation` | Installed only because four other skills link to them. Low relevance here: the app has almost no motion and is touch-only. |

From [mmiani/kotlin-kmp-claude-agent-skills](https://github.com/mmiani/kotlin-kmp-claude-agent-skills):

| Skill | Why it is here |
|---|---|
| `kotlin-platform-kmp-bridges` | Source-set placement and `expect`/`actual` — the rule CLAUDE.md is strictest about. |
| `kotlin-project-modularization` | Module boundaries between `shared` and `composeApp`. |
| `kotlin-build-kmp-gradle-governance` | Version catalog and source-set wiring. |

**Not installed:** `to-plan`, `shepherd`, `run-github-project`,
`implement-with-subagents`, `grounded-writing` (GitHub and authoring
workflows; this repository has no remote yet), and the remaining KMP skills
covering navigation, deep links, adaptive UI, data layer, state management,
bugfix, feature implementation, refactor safety, testing and review. Those
are not bad — they are simply outside what this phase needed, and a skill
that never triggers is context that never pays for itself. Revisit
`kotlin-testing-kmp` and `kotlin-project-state-management` when screens
arrive.

The router (`using-chrisbanes-skills/SKILL.md`) is the one modified file:
rows for `to-plan` and `shepherd` were removed, and the three KMP skills
were added, because otherwise the routing table is blind to the thing this
project mostly is. Everything else is upstream and untouched.

## Hooks

Both are `PostToolUse` on `Edit|Write`, and both are verified to fire.

**`ktlint-format.sh`** runs `ktlint -F` on the Kotlin file just written.
It uses the CLI, not `./gradlew ktlintFormat`, which would pay Gradle
startup on every edit. The CLI version is pinned in the `Dockerfile` to
match `ktlint` in the version catalog, and both read `.editorconfig`, so
the hook and `./gradlew ktlintCheck` cannot disagree. It never blocks.

**`guard-shared-tests.sh`** rejects a write under `shared/src/commonTest/`
that contains `org.junit` or `io.mockk`. Both are JVM-only; in a source set
that compiles for iOS they produce an unresolved reference much later, in a
Kotlin/Native compile whose error points nowhere near the mistake. Exit 2
feeds the explanation back to the model.

> **Known limitation.** `PostToolUse` fires *after* the write, so the
> offending file is on disk when the block is reported. The model is told
> and normally fixes it, but if a session ends at that moment the file
> remains. A `PreToolUse` hook would prevent the write outright; it was not
> added because it has to inspect tool input rather than the file, which is
> more fragile across Edit and Write. Worth revisiting.

There is deliberately **no `Stop` hook yet**. Phase 2 adds one running
`./gradlew :shared:jvmTest` once there is a suite worth running.

### Testing a hook by hand

```bash
printf '{"tool_input":{"file_path":"/path/to/File.kt"}}' | .claude/hooks/ktlint-format.sh
```

## Permissions

Three broad allow entries — `./gradlew`, `git`, `ktlint` — and one explicit
deny on `git push`. Kept broad on purpose: golblok accumulated nine
near-identical entries one prompt at a time, which is how an allowlist
stops being reviewable. The container also holds no SSH key or credential
helper, so it cannot push regardless.

> **Trust dialog.** Claude Code ignores `permissions.allow` from a project
> `settings.json` until the workspace is trusted, reporting
> *"Ignoring N permissions.allow entries … this workspace has not been
> trusted"*. Accept the trust prompt once in an interactive session in the
> container, or set `projects["/workspace"].hasTrustDialogAccepted` in
> `~/.claude.json`. Until then the allowlist has no effect and every Gradle
> command prompts. Hooks are unaffected and run either way.
