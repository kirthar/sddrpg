# Quickstart: Validating the Combatant & Class Model

**Feature**: 001-combatant-class-model

## Prerequisites

- JDK 21+, Node.js (for the JS test target). No other setup: the model is pure
  `commonMain` code in `core` with inline JSON fixtures in tests.

## Run the validation suite

```bash
./gradlew :core:allTests        # kotest on JVM + JS/node — must be green on both
./gradlew :core:jvmTest --tests '*CatalogValidationTest*'   # targeted run example
```

All acceptance evidence for this feature is executable tests in
`core/src/commonTest/kotlin/io/github/kirthar/sddrpg/core/`. Expected mapping:

| Spec item | Validation |
|---|---|
| US1 (classes grant capabilities) | `catalog/ClassCapabilityTest` (scenarios 1–4), `catalog/DefinitionSerializationTest` |
| US1 scenario 4 + SC-005 (job change) | `combatant/ClassChangeTest`: `withActiveClass` swap — capabilities and growth-derived stats change, identity/config don't |
| US2 (archetypes) | `catalog/ArchetypeTest` (scenarios 1–3 + enemy-side validation), `catalog/EnemySerializationTest` |
| US3 (uniform surface) | `combatant/RosterUniformityTest`: party member + enemy + temporary ally expose identical attribute surface; allegiance/kind axes correct (SC-003) |
| FR-003 / SC-004 | `combatant/RosterUniformityTest`: `withDecisionSource` changes nothing else |
| FR-008 (hybrid stats) | `model/StatBlockTest`: every `StatBlock` contains the 8 core stats; bounds enforced; custom stats allowed |
| FR-008a / SC-006 (determinism) | `model/StatsAtTest`, `model/GrowthCurveTest`, `combatant/RosterDeterminismTest`, `catalog/CatalogDeterminismTest` |
| FR-013 / SC-002 (validation) | `catalog/CatalogValidationTest` + `catalog/ArchetypeTest`: dangling refs, duplicate IDs, incomplete stat blocks, undeclared custom stats — every error accumulated, naming the definition |
| FR-014 (portability) | `catalog/ContractRoundTripTest`: the full contract example deserializes, validates and round-trips; strict parsing rejects unknown keys |
| Edge cases | `CatalogValidationTest` (empty-skill class valid, empty-commands invalid), `model/AffinityTest` (absent ⇒ NEUTRAL), `RosterDeterminismTest` (three instances of one definition get distinct `CombatantId`s) |

## Expected outcome

- `:core:allTests` green on JVM and JS with identical results.
- Test-first discipline (constitution Principle III): during implementation these tests
  are written before the code they exercise; a task is done only when green.

## Manual smoke (optional)

No UI exists yet. A scratch `main` in `demo-console` may load the contract example JSON
and print the roster surface, but the console demo proper is feature 008.
