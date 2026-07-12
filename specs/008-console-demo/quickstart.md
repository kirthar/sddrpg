# Quickstart: Validating the Console Demo

**Feature**: 008-console-demo

## Prerequisites

Same as specs 001-007: JDK 21+, Node.js (JS test target for the `core` additions
only — `demo-console` stays JVM-only, per its existing `build.gradle.kts`). No new
setup; this feature adds two small files to `core`, a `demo/` sub-package addition to
`content`, and the first real implementation of `demo-console`.

## Run the validation suite

```bash
/opt/gradle/bin/gradle :core:allTests        # kotest on JVM + JS/node — must be green on both
/opt/gradle/bin/gradle :content:allTests     # kotest on JVM + JS/node
/opt/gradle/bin/gradle :demo-console:allTests # kotest on JVM only (no JS target exists for this module)
/opt/gradle/bin/gradle build                 # whole project, all modules
```

## Expected mapping: spec item → executable test

| Spec item | Validation |
|---|---|
| FR-003 / automatic action rule (command/skill/target selection, determinism) | `core/ai/AutomaticActionRuleTest` |
| FR-005 / SC-001 (unambiguous victory/defeat, simultaneous-defeat tie-break) | `core/battle/BattleOutcomeTest` |
| FR-006 (demo action definitions: formula/targeting/element per command/skill) | `content/demo/DemoActionsTest` |
| R5 workaround (enemy commands granted, non-enemy participants untouched, determinism) | `content/demo/DemoRosterTest` |
| FR-002 (human input gated exactly as `resolveAction` gates it; reprompt on rejection; graceful EOF) | `demo-console/BattleLoopTest` |
| FR-003 (automatic turns never prompt; a stalled automatic combatant is skipped, not fatal) | `demo-console/BattleLoopTest` |
| FR-004 / US2 (every occurrence kind renders as text) | `demo-console/ConsoleRenderingTest` |
| FR-005 (battle ends the instant one side is wiped) | `demo-console/BattleLoopTest` |
| FR-007 (loop testable without real console I/O) | `demo-console/BattleLoopTest` itself is the proof — no test in this module touches `System.in`/`System.out` |
| FR-008 / US3 (a full playthrough exercises a status tick, a limit break, and a synergy-or-summon at least once) | `demo-console/BattleLoopPlaythroughTest`: a scripted full playthrough against the shipped demo content, asserting the `EventLog` contains at least one of each |
| FR-010 / SC-002 (deterministic replay: same inputs -> same outcome and event sequence) | `demo-console/BattleLoopDeterminismTest` |
| SC-003 (automatic combatant never rejected/crashes/stalls) | `demo-console/BattleLoopTest` |
| SC-005 (invalid input never ends the demo or the turn unrecoverably) | `demo-console/BattleLoopTest` |

## Expected outcome

- `:core:allTests` and `:content:allTests` green on JVM and JS with identical results.
- `:demo-console:allTests` green on JVM (its only target).
- `:build` (whole project) green.
- Test-first discipline (constitution Principle III) applies exactly as before: each
  acceptance scenario becomes a failing test before its implementation.

## Manual smoke (optional, once implemented)

```bash
/opt/gradle/bin/gradle :demo-console:run --console=plain
```

Play a full battle: cloud and aerith (player-controlled) vs. the bomb (automatic).
Confirm: prompts appear only on cloud/aerith's turns; the bomb acts on its own; every
occurrence (damage, poison tick, cleave+fira synergy, cloud's omnislash once his
gauge fills, aerith's meteor summon if MP allows) is shown in plain language; the
battle ends with a clear "Victory"/"Defeat" line. This is useful for eyeballing the
experience, not required for completion (the automated suite above is the actual
completion bar).
