# Quickstart: Validating Status Effects

**Feature**: 004-status-effects

## Prerequisites

Same as specs 001–003: JDK 21+, Node.js (JS test target). No new setup — this feature
adds one package (`status`) to the existing `core` module and reuses spec 002's
`BattleState`/spec 003's `ActiveTimeBattleScheduler` fixtures as the starting point.

## Run the validation suite

```bash
./gradlew :core:allTests        # kotest on JVM + JS/node — must be green on both
./gradlew :core:jvmTest --tests '*StatusEffectLifecycleTest*'   # targeted run example
```

## Expected mapping: spec item → executable test

| Spec item | Validation |
|---|---|
| US1 (apply/track/tick/expire lifecycle, scenarios 1–4) | `status/StatusEffectLifecycleTest` |
| US2 (stat-modifying effect changes resolution, scenarios 1–4) | `status/StatModifierTest`: resolves an attack through the effective `BattleState` |
| US3 (incapacitation excludes scheduling, scenarios 1–3) | `status/IncapacitateTest`: effective Speed forced to 0, fed into `ActiveTimeBattleScheduler.nextTurn` |
| US4 (damage-over-time, scenarios 1–3) | `status/DamageOverTimeTest` |
| US5 (reapplication refreshes duration, scenarios 1–2) | `status/ReapplicationTest` |
| FR-004 additive combination on the same stat | `status/StatModifierTest` |
| FR-007 (uniform tick rule) | `status/TickTimingTest`: a still-incapacitated combatant's own effect still ticks down even though it never gets a turn |
| FR-008 (refresh, no stacking) | `status/ReapplicationTest` |
| FR-010/SC-007 (determinism) | `status/StatusDeterminismTest`: property test over apply/tick/remove sequences |
| FR-011 (specs 001–003 contracts untouched) | no dedicated test needed — `EffectiveCombatant` only implements the existing public `Combatant` interface via delegation; compilation itself is the proof (no source changes to `model`/`combatant`/`action`/`schedule`/`catalog` packages) |
| Edge cases (non-positive duration, stat floor, idempotent removal) | `status/StatusEffectCatalogValidationTest`, `status/StatModifierTest`, `status/StatusEffectLifecycleTest` |

## Expected outcome

- `:core:allTests` green on JVM and JS with identical results (same as specs 001–003).
- Test-first discipline (constitution Principle III) applies exactly as before: each
  acceptance scenario becomes a failing test before its implementation.

## Manual smoke (optional)

No UI or battle loop exists yet (`demo-console` remains a placeholder until feature
008; wiring `tickStatusEffects` into an actual per-turn loop is a later feature). A
scratch test may apply a poison effect to a combatant, tick a few times, and print the
resulting health sequence — useful for eyeballing the pacing, not required for
completion.
