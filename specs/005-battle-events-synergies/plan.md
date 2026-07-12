# Implementation Plan: Battle Events & Synergies

**Branch**: `005-battle-events-synergies` (feature dir; git work happens on the
session's designated branch) | **Date**: 2026-07-12 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `/specs/005-battle-events-synergies/spec.md`

## Summary

Add an `event` package to `core`: a closed `BattleEvent` sealed type, an append-only
`EventLog`, and small pure "event-deriving" functions that take the inputs/outputs of
an *already-completed* spec 002/003/004 call and return the events it represents —
`resolveAction`, `tickStatusEffects`, and the turn scheduler are called exactly as
before and never modified (satisfying the Observer requirement without imperative
callbacks, which would violate constitution Principle I). On top of the log, a
data-driven `SynergyCatalog` (two required skills, a same-target condition, a
turn-based window, a bonus effect) is detected by a pure scan of newly-appended
qualifying events against the log, and a matched synergy's bonus is applied through
the same small health-clamp pattern spec 004 established for damage-over-time. During
planning, verifying spec 001's actual `Combatant.kt` source confirmed `classId` is not
part of its public contract — spec.md was updated to key synergies on required
*skills* instead of classes, achieving the same class-flavored business outcome
through genuinely public data (see spec.md's Assumptions for the full rationale).

## Technical Context

**Language/Version**: Kotlin 2.4.0 (Multiplatform; jvm + js targets, same as `core`)

**Primary Dependencies**: none beyond what `core` already has; pure functions over
spec 001–004's existing public types

**Storage**: N/A — in-memory, immutable state transitions, same convention as specs 001–004

**Testing**: kotest 6.2.2 in `commonTest` (unit + property tests, JVM + JS)

**Target Platform**: platform-agnostic common code, same as `core`

**Project Type**: library module — extends the existing `core` module, new package

**Performance Goals**: event derivation is O(outcomes) per call; synergy detection
scans the log once per newly-appended qualifying event, bounded by battle scale
(~2–10 combatants, bounded turn counts) — trivial at this scale, no measured target
needed

**Constraints**: 100% deterministic (constitution Principle I): no RNG, no I/O, no
callbacks/subscriber registration; must not modify `Combatant`, `BattleState`,
`CombatantId`, `resolveAction`, `TurnScheduler`, or any spec 001–004 file — event
derivation reads their already-produced inputs/outputs only, never their internals
(spec FR-011)

**Scale/Scope**: battles of ~2–10 combatants (specs 001–004 precedent); this feature
defines the event/synergy mechanism only — no specific synergy catalog content
(feature 007) and no event rendering/logging UI (constitution Principle IV)

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principle | Gate | Status |
|---|---|---|
| I. Pure & Deterministic Core | No RNG, no I/O, no callbacks; every function (event derivation, log append, synergy detection, bonus application) is pure, old state → new state | ✅ PASS — see research R2 for how "Observer" is realized without imperative subscription |
| II. Data-Driven Content | `SynergyDefinition` is catalog data; `BattleEvent`/`SynergyBonus` are closed, engine-recognized discriminators (mirrors spec 002's `DamageFormula`, spec 004's `EffectKind`) | ✅ PASS |
| III. Test-First | Acceptance scenarios (spec US1–US4) map to failing kotest tests before implementation; property tests for determinism | ✅ PASS — test plan in quickstart.md |
| IV. Simulation–Presentation Separation | `BattleEvent`/`EventLog` are structured data, no formatted text; no rendering/logging built here (spec Assumptions) | ✅ PASS |
| V. Extensible Architecture w/o Speculative Impl. | Exactly the event kinds and the one synergy shape spec.md asks for; N-way synergies, event pruning/retention, and any UI consumption are explicitly not built now | ✅ PASS |

**Post-design re-check (after Phase 1)**: all five gates still pass. The `event`
package is additive; zero changes to any spec 001–004 file — verified directly against
spec 001's `Combatant.kt` source during the specify/plan transition (see spec.md's
Assumptions), which is what drove the skill-based (not classId-based) synergy design.
No Complexity Tracking entries needed.

## Project Structure

### Documentation (this feature)

```text
specs/005-battle-events-synergies/
├── plan.md              # This file (/speckit-plan output)
├── research.md          # Phase 0 output (/speckit-plan)
├── data-model.md         # Phase 1 output (/speckit-plan)
├── quickstart.md        # Phase 1 output (/speckit-plan)
├── contracts/
│   └── event-api.md     # Phase 1 output (/speckit-plan)
├── checklists/
│   └── requirements.md  # Spec quality checklist (from /speckit-specify)
└── tasks.md             # Phase 2 output (/speckit-tasks — NOT created by /speckit-plan)
```

### Source Code (repository root)

```text
core/
├── src/commonMain/kotlin/io/github/kirthar/sddrpg/core/
│   └── event/                          # NEW package for this feature
│       ├── BattleEvent.kt              # sealed event kinds + EventLog
│       ├── EventDerivation.kt          # eventsFromResolution/Tick/Apply/Schedule, shared defeatedEvents helper
│       ├── SynergyDefinition.kt        # SynergyBonus, SynergyDefinition, SynergyCatalog + validation
│       └── SynergyResolution.kt        # detectSynergyTriggers, applySynergyBonus
└── src/commonTest/kotlin/io/github/kirthar/sddrpg/core/
    └── event/                          # unit + property tests per US1-US4
```

**Structure Decision**: single new package inside the existing `core` module (library
project type, same as specs 001–004). No new Gradle module needed.

## Complexity Tracking

No constitution violations — table intentionally empty.
