---
name: using-chrisbanes-skills
description: Use when debugging, benchmarking, or profiling leads into Kotlin or Jetpack Compose source before the cause is known, or when one task spans multiple Kotlin or Compose concerns, especially plain Kotlin Flow or navigation delivery plus sealed branching.
paths:
  - "**/*.kt"
  - "**/*.kts"
---

# Using chrisbanes skills

> **Local note.** Rows for `to-plan` and `shepherd` were removed: both are
> GitHub-workflow skills and this repository has no remote yet. Three
> Kotlin Multiplatform skills from a second source were added, because the
> routing table is otherwise blind to the thing this project mostly is.
> Everything else is upstream and unmodified.
> Sources: https://github.com/chrisbanes/skills and
> https://github.com/mmiani/kotlin-kmp-claude-agent-skills

## Core principle

Route by the decision the code needs, not by the number of APIs mentioned.
Load one cluster when its shared procedure owns the concern; add a specialist
only when its independent behavior changes the same work.

## Routing procedure

1. Read the task and the Kotlin source that makes the concern concrete.
2. If one focused skill clearly matches, load it directly and stop routing.
3. Before loading a Compose skill, point to a concrete Compose API or composable
   in the inspected source, or to an explicit request to create or design
   Compose code. A hypothetical UI consumer is not evidence. If neither form
   of evidence exists, stay in the Kotlin cluster even when the task mentions
   UI, routes, or navigation.
4. Match each observed code signal to the table below.
5. Add a second skill only when it owns an independent decision in the same
   change; do not load adjacent skills speculatively.
6. Finish routing when every material concern has one focused owner and those
   skills are loaded before advice or edits.

## Common routes

| Task signal | Start with |
|---|---|
| Evidenced Compose state, effects, screen ownership, or UI event collection | [`compose-state-and-effects`](../compose-state-and-effects/SKILL.md) |
| Recomposition, stability, frame-rate reads, back-writing, or `@ReadOnlyComposable` | [`compose-performance`](../compose-performance/SKILL.md) |
| Component modifiers, caller placement, slots, or public content shape | [`compose-component-design`](../compose-component-design/SKILL.md) |
| Visibility, value, transition, content-swap, or other motion API choice | [`compose-animations`](../compose-animations/SKILL.md) |
| Keyboard, TV, D-pad, focus targets, custom traversal, or key events | [`compose-focus-navigation`](../compose-focus-navigation/SKILL.md) |
| Compose UI, screenshot, semantics, focus/key, or interaction-state tests | [`compose-ui-testing-patterns`](../compose-ui-testing-patterns/SKILL.md) |
| Coroutine ownership, cancellation, Flow state/events, sharing, or replay | [`kotlin-concurrency-and-flow`](../kotlin-concurrency-and-flow/SKILL.md) |
| Kotlin classification, `when`, guards, exhaustiveness, smart casts, or null branches | [`kotlin-control-flow`](../kotlin-control-flow/SKILL.md) |
| Kotlin function ownership, domain types, expect/actual, or platform seams | [`kotlin-api-design`](../kotlin-api-design/SKILL.md) |
| Planned Gradle execution or a Gradle-centered warning/failure workflow | [`gradle-run`](../gradle-run/SKILL.md) |
| Source-set placement, hierarchical sharing, `expect`/`actual`, platform API access | [`kotlin-platform-kmp-bridges`](../kotlin-platform-kmp-bridges/SKILL.md) |
| Module boundaries, dependency direction, visibility between `shared` and `composeApp` | [`kotlin-project-modularization`](../kotlin-project-modularization/SKILL.md) |
| KMP Gradle structure, version catalog, convention plugins, source-set wiring | [`kotlin-build-kmp-gradle-governance`](../kotlin-build-kmp-gradle-governance/SKILL.md) |

## Combination boundaries

- Add [`kotlin-concurrency-and-flow`](../kotlin-concurrency-and-flow/SKILL.md)
  to Compose state work only when delivery, replay, sharing, or cancellation is
  a separate concern. Add state ownership or performance only when animation
  work changes that concern too.
- Add [`kotlin-control-flow`](../kotlin-control-flow/SKILL.md) when a Kotlin
  concern also changes branching. Plain Kotlin route delivery plus a sealed
  mapping stays in the Kotlin cluster; do not add Compose without the evidence
  required in step 3.
- Pair focus navigation with UI testing when the task also needs a test shape.
- Load [`gradle-run`](../gradle-run/SKILL.md) only for planned Gradle execution
  or an existing Gradle workflow, not incidental Kotlin or Compose advice.
