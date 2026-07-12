# Quickstart: Validating Actions & Damage Resolution

**Feature**: 002-actions-damage-resolution

## Prerequisites

Same as spec 001: JDK 21+, Node.js (JS test target). No new setup — this feature adds
one package (`action`) to the existing `core` module and reuses spec 001's fixtures
(`ValidatedCatalog`, `Roster`) as the starting point for `BattleState`.

## Run the validation suite

```bash
./gradlew :core:allTests        # kotest on JVM + JS/node — must be green on both
./gradlew :core:jvmTest --tests '*CapabilityGatingTest*'   # targeted run example
```

## Expected mapping: spec item → executable test

| Spec item | Validation |
|---|---|
| US1 (basic attack → damage number, scenarios 1–6) | `action/BasicAttackResolutionTest` (end-to-end via `resolveAction`), backed by `action/ElementalAdjustmentTest` (multiplier table unit tests) and `action/RoundingTest` |
| US1 scenario 6 / FR-013 / SC-003 (determinism) | `action/BasicAttackResolutionTest` (single case) and `action/ActionResolutionDeterminismTest` (cross-cutting property, randomized) |
| US2 (capability gating, scenarios 1–4, + DEFEND/ITEM, research R7) | `action/CapabilityGatingTest`: granted command/skill accepted; missing command/skill/job-changed class rejected with the right `ActionError`; rejection cases assert no state mutation (SC-002) |
| US3 (targeting shapes, scenarios 1–6) | `action/TargetingShapeTest`: single-target cardinality, self-only, all-enemies fan-out, unknown target, already-defeated exclusion (explicit vs. "all"-shape) |
| US4 (healing, scenarios 1–3) | `action/HealingTest`: HEAL formula increases health, caps at maximum, no element ⇒ no adjustment |
| FR-003 (defeated actor rejected) | `action/CapabilityGatingTest` |
| FR-009 / SC-004 (all-shape excludes defeated, zero outcomes is valid) | `action/TargetingShapeTest` |
| FR-011 / SC-005 (health stays within [0, maximum] across all cases) | `action/HealingTest` (property test over damage/heal × weakness/resistance/absorption/neutral) |
| FR-012 (spec 001 contract untouched) | `action/BattleStateTest` (construction/derivation) and `action/BattleStateContractTest` (`Roster.toBattleState()` preserves every `Combatant` field unchanged) |
| Edge cases (negative raw formula, rounding) | `action/DamageFormulaTest` (flooring at 0), `action/ElementalAdjustmentTest`/`action/RoundingTest` (round-half-away-from-zero on ×0.5) |

## Expected outcome

- `:core:allTests` green on JVM and JS with identical results (same as spec 001).
- Test-first discipline (constitution Principle III) applies exactly as in spec 001:
  each acceptance scenario becomes a failing test before its implementation.

## Manual smoke (optional)

No UI exists yet (`demo-console` remains a placeholder until feature 008). A scratch
test or `main` snippet may build a `Roster` from a small fixture catalog, convert it
with `.toBattleState()`, and print `resolveAction` outcomes for a couple of actions —
useful for eyeballing the numbers, not required for completion.
