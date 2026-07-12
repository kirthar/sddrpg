# Quickstart: Validating Limit Breaks & Summons

**Feature**: 006-limit-breaks-summons

## Prerequisites

Same as specs 001-005: JDK 21+, Node.js (JS test target). No new setup — this feature
adds one package (`limitbreak`) plus one new file inside the existing `event` package
to the existing `core` module, reusing spec 002's `resolveAction`/`BattleState` and
spec 005's `BattleEvent`/`EventLog` fixtures as the starting point.

## Run the validation suite

```bash
./gradlew :core:allTests        # kotest on JVM + JS/node — must be green on both
./gradlew :core:jvmTest --tests '*LimitBreakResolutionTest*'   # targeted run example
```

## Expected mapping: spec item → executable test

| Spec item | Validation |
|---|---|
| US1 scenarios 1-2 (gauge rises from damage, capped at threshold) | `limitbreak/LimitGaugeChargeTest` |
| US1 scenario 3 (no limit break → no gauge state produced) | `limitbreak/LimitGaugeChargeTest` |
| US2 scenario 1 (below-threshold use rejected) | `limitbreak/LimitBreakResolutionTest` |
| US2 scenario 2 (full-gauge use resolves within health bounds) | `limitbreak/LimitBreakResolutionTest` |
| US2 scenario 3 (gauge resets to zero after use) | `limitbreak/LimitBreakResolutionTest` |
| US3 scenarios 1-2 (sufficient/insufficient resource cast) | `limitbreak/SummonResolutionTest` |
| US3 scenario 3 (no new participant added) | `limitbreak/SummonResolutionTest` |
| US4 scenarios 1-3 (GaugeFull/LimitBreakUsed/SummonCast events) | `limitbreak/LimitBreakSummonEventTest` |
| FR-002 (gauge never exceeds threshold) | `limitbreak/LimitGaugeChargeTest` |
| FR-008/SC-004 (insufficient resource rejects, zero deducted) | `limitbreak/SummonResolutionTest` |
| FR-011/SC-005 (determinism) | `limitbreak/LimitBreakDeterminismTest`: property test over charge/resolve sequences |
| FR-012 (specs 001-005 contracts untouched) | `limitbreak/LimitBreakContractTest`: compilation itself is partial proof — no source changes to `model`/`combatant`/`action`/`schedule`/`status`/`catalog`/`event` packages' existing files; a battle with empty limit-break/summon catalogs behaves identically to specs 001-005 alone |
| Edge cases (defeated combatant's gauge persists, exact-cost summon allowed, class with no limit break) | `limitbreak/LimitGaugeChargeTest`, `limitbreak/SummonResolutionTest` |

## Expected outcome

- `:core:allTests` green on JVM and JS with identical results (same as specs 001-005).
- Test-first discipline (constitution Principle III) applies exactly as before: each
  acceptance scenario becomes a failing test before its implementation.

## Manual smoke (optional)

No UI or battle loop exists yet (`demo-console` remains a placeholder until feature
008; wiring gauge charging and limit-break/summon submission into an actual per-turn
loop is a later feature). A scratch test may resolve several attacks against a
combatant with a defined limit break, watch the gauge climb, use the limit break once
full, and print the resulting `EventLog.entries` — useful for eyeballing the sequence,
not required for completion.
