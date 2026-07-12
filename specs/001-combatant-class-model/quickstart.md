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
| US1 (classes grant capabilities) | `catalog/` tests: load fixture catalog, assert character capabilities equal class grants (scenarios 1–3) |
| US1 scenario 4 + SC-005 (job change) | `combatant/` test: `withActiveClass` swap — capabilities change, identity/stats inputs don't |
| US2 (archetypes) | `catalog/` tests: enemies expose archetype AI profile + reward tier; new enemy = data only |
| US3 (uniform surface) | `combatant/RosterTest`: party member + enemy + temporary ally in one roster expose identical attribute surface; allegiance/kind axes correct (SC-003) |
| FR-003 / SC-004 | test: `withDecisionSource` changes nothing else |
| FR-008 (hybrid stats) | property test: every `StatBlock` contains the 8 core stats; custom stats must be declared |
| FR-008a / SC-006 (determinism) | property tests: `statsAt` same inputs ⇒ same output; catalog load twice ⇒ identical models; `Linear`/`Table` curves non-negative, `Table` clamps |
| FR-013 / SC-002 (validation) | `CatalogValidationTest`: fixtures with dangling refs, duplicate IDs, incomplete stat blocks, undeclared custom stats — every error reported, accumulated, naming the definition |
| FR-014 (portability) | round-trip test: JSON → model → JSON structural equality on the contract example from [contracts/catalog-api.md](contracts/catalog-api.md) |
| Edge cases | tests: empty-skill class valid, empty-commands class invalid, absent affinities ⇒ NEUTRAL, three instances of one enemy definition get distinct `CombatantId`s |

## Expected outcome

- `:core:allTests` green on JVM and JS with identical results.
- Test-first discipline (constitution Principle III): during implementation these tests
  are written before the code they exercise; a task is done only when green.

## Manual smoke (optional)

No UI exists yet. A scratch `main` in `demo-console` may load the contract example JSON
and print the roster surface, but the console demo proper is feature 008.
