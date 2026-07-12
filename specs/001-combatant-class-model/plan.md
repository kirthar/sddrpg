# Implementation Plan: Combatant & Class Model

**Branch**: `001-combatant-class-model` (feature dir; git work happens on the session's
designated branch) | **Date**: 2026-07-11 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `/specs/001-combatant-class-model/spec.md`

## Summary

Build the engine's foundational domain model: combatants (party members, enemies,
temporary allies) sharing one attribute surface, data-defined character classes and
enemy archetypes, a hybrid stat system (engine core set + catalog custom stats),
level-derived stats from class growth curves, elemental affinities, and whole-catalog
reference validation. Technical approach: pure `commonMain` Kotlin in `core`,
kotlinx.serialization data classes for every definition, identifier value classes with
a two-phase load (deserialize → validate all cross-references, accumulating errors),
and kotest unit + property tests derived from the spec's acceptance scenarios.

## Technical Context

**Language/Version**: Kotlin 2.4.0 (Multiplatform; jvm + js targets configured)

**Primary Dependencies**: kotlinx.serialization 1.11.0 (JSON) — the only non-stdlib
dependency permitted in `core` by constitution Principle I

**Storage**: N/A — in-memory model; definitions are serializable to/from JSON text
(no filesystem I/O inside `core`; loaders that touch platforms come in feature 007)

**Testing**: kotest 6.2.2 in `commonTest` (StringSpec/FunSpec unit tests +
kotest-property for invariants), running on JVM (junit5) and JS (node)

**Target Platform**: platform-agnostic common code; consumed later by JVM console demo,
Android, and web

**Project Type**: library module (`core`) inside the existing Gradle KMP multi-module
build

**Performance Goals**: catalog validation linear in catalog size; instant (<1s) for
realistic catalogs (hundreds of definitions); no per-battle allocation constraints yet

**Constraints**: 100% deterministic (this feature needs no RNG at all — stat derivation
is a pure function); no platform APIs; no UI/text formatting; integer stat arithmetic
(identical results on JVM and JS, no floating-point drift)

**Scale/Scope**: catalogs of ~10–500 definitions; battle rosters of ~2–10 combatants;
model must not assume party-of-4 or any fixed roster shape

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principle | Gate | Status |
|---|---|---|
| I. Pure & Deterministic Core | No platform/UI deps beyond stdlib + kotlinx.serialization; no randomness; stat derivation is a pure function of (class, level, modifiers) | ✅ PASS — feature introduces no RNG, no I/O, no clocks |
| II. Data-Driven Content | Classes, archetypes, characters, enemies, elements, equipment categories, custom stats: all serializable data; zero code subclasses per content item | ✅ PASS — growth-curve *types* are a closed set of engine capabilities selected by a data discriminator (capability, not content) |
| III. Test-First | Acceptance scenarios map to failing kotest tests before implementation; property tests for bounds/determinism/order-independence | ✅ PASS — test plan included in quickstart.md; tasks phase must order tests before implementation |
| IV. Simulation–Presentation Separation | No rendering/localization; `displayName` is opaque data; decision source is a reference, not an input mechanism | ✅ PASS |
| V. Extensible Architecture w/o Speculative Impl. | Character→class reference mutable (progression-ready); custom stats extensible; no job-system implementation in v1; no positional data | ✅ PASS — see research.md R1 for the fixed-vs-job-system evaluation |

**Post-design re-check (after Phase 1)**: all five gates still pass; data-model.md
introduces no code-per-content, no platform types, no nondeterminism. No Complexity
Tracking entries needed.

## Project Structure

### Documentation (this feature)

```text
specs/001-combatant-class-model/
├── plan.md              # This file (/speckit-plan output)
├── research.md          # Phase 0 output (/speckit-plan)
├── data-model.md        # Phase 1 output (/speckit-plan)
├── quickstart.md        # Phase 1 output (/speckit-plan)
├── contracts/
│   └── catalog-api.md   # Phase 1 output (/speckit-plan)
├── checklists/
│   └── requirements.md  # Spec quality checklist (from /speckit-specify)
└── tasks.md             # Phase 2 output (/speckit-tasks — NOT created by /speckit-plan)
```

### Source Code (repository root)

```text
core/
├── src/commonMain/kotlin/io/github/kirthar/sddrpg/core/
│   ├── model/                  # value types shared by definitions & battle instances
│   │   ├── Identifiers.kt      # value-class IDs (ClassId, StatId, ElementId, …)
│   │   ├── Stats.kt            # StatBlock, core stat constants, bounds
│   │   ├── Affinity.kt         # ElementalAffinity (5-stance scale)
│   │   └── GrowthCurve.kt      # closed curve strategies + data discriminator
│   ├── catalog/                # authored definitions + validation
│   │   ├── Definitions.kt      # ClassDefinition, ArchetypeDefinition, Character/EnemyDefinition
│   │   ├── Catalog.kt          # aggregate + lookup
│   │   └── CatalogValidation.kt# two-phase load, accumulated errors
│   └── combatant/              # battle-facing instances
│       ├── Combatant.kt        # kind, allegiance, capability surface
│       └── Roster.kt           # deterministic instantiation from definitions
└── src/commonTest/kotlin/io/github/kirthar/sddrpg/core/
    ├── model/                  # unit + property tests per model type
    ├── catalog/                # validation & (de)serialization round-trip tests
    └── combatant/              # roster/uniformity tests (US3 scenarios)
```

**Structure Decision**: everything lives in the existing `core` KMP module (library
project type). Three packages mirror the domain split: `model` (pure value types),
`catalog` (authored data + validation), `combatant` (battle-facing instances). The
`content` module is intentionally untouched until feature 007 (content loading);
test fixtures for this feature are inline JSON strings in `commonTest`.

## Complexity Tracking

No constitution violations — table intentionally empty.
