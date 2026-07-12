# Quickstart: Validating the Turn Scheduler

**Feature**: 003-turn-scheduler

## Prerequisites

Same as specs 001–002: JDK 21+, Node.js (JS test target). No new setup — this feature
adds one package (`schedule`) to the existing `core` module and reuses spec 002's
`BattleState`/roster fixtures as the starting point.

## Run the validation suite

```bash
./gradlew :core:allTests        # kotest on JVM + JS/node — must be green on both
./gradlew :core:jvmTest --tests '*ActiveTimeBattleSchedulerTest*'   # targeted run example
```

## Expected mapping: spec item → executable test

| Spec item | Validation |
|---|---|
| US1 scenarios 1, 4 (a single ready combatant, available from the start) | `schedule/NextTurnTest` |
| US1 scenario 2 (marking spent changes who's next) | `schedule/MarkSpentTest` |
| US1 scenario 3 / SC-001 (faster combatants get proportionally more turns) | `schedule/ReadinessProportionalityTest`: property test over many consumed turns |
| US2 (defeated combatants excluded, scenarios 1–3) / SC-002 | `schedule/DefeatedExclusionTest` |
| US3 (deterministic order and tie-breaking, scenarios 1–2) / SC-003, SC-004 | `schedule/DeterminismTest`, `schedule/TieBreakTest` |
| FR-009 / SC-005 (all defeated ⇒ no next turn) | `schedule/DefeatedExclusionTest` |
| Edge case: Speed of zero never becomes ready | `schedule/ZeroSpeedTest` |
| Edge case: querying without consuming doesn't advance | `schedule/NextTurnTest` |
| FR-010 (specs 001–002 contracts untouched) | no dedicated test needed — the `schedule` package only reads `Combatant`/`BattleState` through their existing public members; compilation itself is the proof (no source changes to `model`/`combatant`/`action` packages) |

## Expected outcome

- `:core:allTests` green on JVM and JS with identical results (same as specs 001–002).
- Test-first discipline (constitution Principle III) applies exactly as before: each
  acceptance scenario becomes a failing test before its implementation.

## Manual smoke (optional)

No UI or battle loop exists yet (`demo-console` remains a placeholder until feature
008; wiring `resolveAction` + `TurnScheduler` into an actual loop is a later feature).
A scratch test may build a small roster, run `nextTurn`/`markSpent` in a loop, and
print the resulting turn sequence — useful for eyeballing the ATB pacing, not required
for completion.
