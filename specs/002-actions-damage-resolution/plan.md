# Implementation Plan: Actions & Damage Resolution

**Branch**: `002-actions-damage-resolution` (feature dir; git work happens on the
session's designated branch) | **Date**: 2026-07-12 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `/specs/002-actions-damage-resolution/spec.md`

## Summary

Add an `action` package to `core` that turns a submitted combat action into a
deterministic health change: capability-gated Command submissions, substitutable
damage/heal Strategy formulas, uniform elemental adjustment over spec 001's `Affinity`
model, and a fixed targeting-shape vocabulary. Because resolving damage requires
tracking *current* health across a battle — an invariant spec 001 explicitly declared
but deferred ("enforced once battle state exists") — this feature introduces the first
piece of battle-time mutable state: an immutable `BattleState` that wraps existing
`Combatant`s with a `HealthTrack`, entirely additive and without changing `Combatant`'s
public contract (spec FR-012).

## Technical Context

**Language/Version**: Kotlin 2.4.0 (Multiplatform; jvm + js targets, same as `core`)

**Primary Dependencies**: none beyond what `core` already has (Kotlin stdlib); this
feature adds no new external dependency — pure functions over spec 001's existing types

**Storage**: N/A — in-memory, immutable state transitions (`BattleState` in, new
`BattleState` out); no persistence

**Testing**: kotest 6.2.2 in `commonTest`, same conventions as spec 001 (unit + property
tests, JVM + JS)

**Target Platform**: platform-agnostic common code, same as `core`

**Project Type**: library module — extends the existing `core` module, new package

**Performance Goals**: resolving one action against up to "all combatants" (~10)
completes in well under a frame budget; no measured target needed at this scale —
correctness and determinism matter more than throughput here

**Constraints**: 100% deterministic (constitution Principle I): no RNG in this feature
(hit/miss/critical chance is explicitly out of scope — every formula/adjustment here is
exact arithmetic); integer stat arithmetic preserved from spec 001 (research R4); must
not modify `Combatant`, `ValidatedCatalog`, `StatBlock`, or `Affinity`'s existing public
shape (spec FR-012)

**Scale/Scope**: battles of ~2–10 combatants (spec 001 precedent); this feature covers
one-shot action resolution only, no turn loop (that is `TurnScheduler`, a later feature)

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principle | Gate | Status |
|---|---|---|
| I. Pure & Deterministic Core | No RNG, no I/O, no platform deps; `resolveAction` is a pure function old-state → (new-state, outcomes) | ✅ PASS — hit/miss/crit chance explicitly deferred (spec Assumptions); all arithmetic is exact integer math |
| II. Data-Driven Content | Formulas are a closed Strategy set selected by data (`DamageFormula` discriminator), not one class per skill; elemental multipliers are one fixed engine table, not per-content code | ✅ PASS — see research R2/R4 |
| III. Test-First | Acceptance scenarios (spec US1–US4) map to failing kotest tests before implementation; property tests for bounds/determinism | ✅ PASS — test plan in quickstart.md |
| IV. Simulation–Presentation Separation | `ResolutionOutcome` is structured data (deltas, stances, resulting health), no formatted text; still no UI/localization in `core` | ✅ PASS |
| V. Extensible Architecture w/o Speculative Impl. | New formulas/targeting shapes are data-selectable additions (Strategy); this feature builds only what US1–US4 need (no status effects, no turn loop, no crit/miss) | ✅ PASS — resource costs, defend's stateful effect, and turn sequencing explicitly deferred (see research R7, R8) |

**Post-design re-check (after Phase 1)**: all five gates still pass. `BattleState` is
additive (new types, zero changes to spec 001's public API), keeping FR-012 satisfied.
No Complexity Tracking entries needed.

## Project Structure

### Documentation (this feature)

```text
specs/002-actions-damage-resolution/
├── plan.md              # This file (/speckit-plan output)
├── research.md          # Phase 0 output (/speckit-plan)
├── data-model.md        # Phase 1 output (/speckit-plan)
├── quickstart.md        # Phase 1 output (/speckit-plan)
├── contracts/
│   └── action-api.md    # Phase 1 output (/speckit-plan)
├── checklists/
│   └── requirements.md  # Spec quality checklist (from /speckit-specify)
└── tasks.md             # Phase 2 output (/speckit-tasks — NOT created by /speckit-plan)
```

### Source Code (repository root)

```text
core/
├── src/commonMain/kotlin/io/github/kirthar/sddrpg/core/
│   └── action/                      # NEW package for this feature
│       ├── TargetingShape.kt        # enum: SINGLE_ALLY, SINGLE_ENEMY, SELF, ALL_ALLIES, ALL_ENEMIES, ALL
│       ├── DamageFormula.kt         # sealed Strategy: Physical, Magical (+ Fixed for items)
│       ├── CombatAction.kt          # Command data: actor, CommandKind, skillId?, element?, effectKind, formula, targeting
│       ├── BattleState.kt           # HealthTrack, BattleCombatant, BattleState (wraps Combatant, adds current HP)
│       ├── ElementalAdjustment.kt   # fixed multiplier table (spec FR-006) + apply function
│       └── ActionResolution.kt      # resolveAction: validation (gating, targeting) + resolution, ActionError, ResolutionOutcome
└── src/commonTest/kotlin/io/github/kirthar/sddrpg/core/
    └── action/                      # unit + property tests per US1-US4
```

**Structure Decision**: single new package inside the existing `core` module (library
project type, same as spec 001). No new Gradle module needed — this feature is squarely
inside `core`'s existing dependency graph and target set (jvm + js).

## Complexity Tracking

No constitution violations — table intentionally empty.
