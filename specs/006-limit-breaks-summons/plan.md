# Implementation Plan: Limit Breaks & Summons

**Branch**: `006-limit-breaks-summons` (feature dir; git work happens on the
session's designated branch) | **Date**: 2026-07-12 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `/specs/006-limit-breaks-summons/spec.md`

## Summary

Add a `limitbreak` package to `core` (plus one new file inside the existing `event`
package) implementing two data-driven burst mechanics on top of specs 001-005. A
`LimitGaugeState` (new `Map<CombatantId, Int>` battle-time state, mirroring spec 004's
`StatusEffectState`) charges purely by scanning spec 005's already-derived
`BattleEvent.DamageDealt` events for the amount taken — no hook into `resolveAction`
itself. A `ResourceState` (same shape) tracks MP, deducted by summon casts. Both a
limit break and a summon resolve by synthesizing a `CombatAction` from existing fields
only and delegating to `resolveAction` unmodified: limit breaks reuse
`CommandKind.SKILL` (a class granting a limit break is expected to also grant `SKILL`
— this feature's *own* gate, gauge ≥ threshold checked against
`capabilities.limitBreaks`, is what's actually load-bearing); summons reuse
`CommandKind.SUMMON`, which spec 001 already declared for exactly this purpose — its
existing capability gate in `resolveAction` is reused as-is, with only the MP-
sufficiency check added on top as this feature's own wrapper. New `BattleEvent`
variants (`GaugeFull`, `LimitBreakUsed`, `SummonCast`) were verified empirically
(direct compiler test) to require the same package as `BattleEvent`'s declaration but
not the same file, so they live in a new `event/LimitBreakSummonEvents.kt` rather than
editing spec 005's `BattleEvent.kt`.

## Technical Context

**Language/Version**: Kotlin 2.4.0 (Multiplatform; jvm + js targets, same as `core`)

**Primary Dependencies**: none beyond what `core` already has; pure functions over
specs 001-005's existing public types

**Storage**: N/A — in-memory, immutable state transitions, same convention as specs 001-005

**Testing**: kotest 6.2.2 in `commonTest` (unit + property tests, JVM + JS)

**Target Platform**: platform-agnostic common code, same as `core`

**Project Type**: library module — extends the existing `core` module, two new
packages/files

**Performance Goals**: gauge charging is O(events) per call; limit-break/summon
resolution is a single `resolveAction` call plus O(1) gate checks — trivial at battle
scale (~2-10 combatants), no measured target needed

**Constraints**: 100% deterministic (constitution Principle I): no RNG, no I/O; must
not modify `Combatant`, `BattleState`, `CombatantId`, `resolveAction`,
`CommandKind`, `TurnScheduler`, `BattleEvent`, `EventLog`, or any spec 001-005 file —
new `BattleEvent` variants are additive in a new same-package file, verified to
compile without touching `BattleEvent.kt` (spec FR-012)

**Scale/Scope**: battles of ~2-10 combatants (specs 001-005 precedent); this feature
defines the two mechanisms only — no specific limit-break/summon catalog content
(feature 007) and no gauge/summon UI (constitution Principle IV)

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principle | Gate | Status |
|---|---|---|
| I. Pure & Deterministic Core | No RNG, no I/O; every function (gauge charge, resource deduction, limit-break/summon resolution, event derivation) is pure, old state → new state | ✅ PASS — see research R1-R3 |
| II. Data-Driven Content | `LimitBreakDefinition`/`SummonDefinition` are catalog data; no class/summon named in engine code | ✅ PASS |
| III. Test-First | Acceptance scenarios (spec US1-US4) map to failing kotest tests before implementation; property tests for determinism | ✅ PASS — test plan in quickstart.md |
| IV. Simulation-Presentation Separation | Gauge/resource state and new events are structured data, no formatted text; no rendering built here | ✅ PASS |
| V. Extensible Architecture w/o Speculative Impl. | Exactly the "one active limit break, single summon resolution" shape spec.md asks for; multi-limit-break selection and any UI are explicitly not built now | ✅ PASS |

**Post-design re-check (after Phase 1)**: all five gates still pass. `limitbreak` is a
new package; the one `event` package addition is a new file, empirically verified to
compile alongside `BattleEvent.kt` unmodified (research R4). Zero changes to any spec
001-005 file. No Complexity Tracking entries needed.

## Project Structure

### Documentation (this feature)

```text
specs/006-limit-breaks-summons/
├── plan.md              # This file (/speckit-plan output)
├── research.md          # Phase 0 output (/speckit-plan)
├── data-model.md         # Phase 1 output (/speckit-plan)
├── quickstart.md        # Phase 1 output (/speckit-plan)
├── contracts/
│   └── limitbreak-summon-api.md   # Phase 1 output (/speckit-plan)
├── checklists/
│   └── requirements.md  # Spec quality checklist (from /speckit-specify)
└── tasks.md             # Phase 2 output (/speckit-tasks — NOT created by /speckit-plan)
```

### Source Code (repository root)

```text
core/
├── src/commonMain/kotlin/io/github/kirthar/sddrpg/core/
│   ├── limitbreak/                     # NEW package for this feature
│   │   ├── LimitBreakDefinition.kt     # LimitBreakDefinition/Catalog + validation
│   │   ├── SummonDefinition.kt         # SummonDefinition/Catalog + validation
│   │   ├── LimitGaugeState.kt          # per-combatant gauge state + chargeLimitGauge
│   │   ├── ResourceState.kt            # per-combatant MP state + deductResource
│   │   └── LimitBreakSummonResolution.kt  # resolveLimitBreak/resolveSummon wrappers
│   └── event/
│       └── LimitBreakSummonEvents.kt   # NEW file, SAME package as BattleEvent:
│                                        # GaugeFull/LimitBreakUsed/SummonCast variants
│                                        # + their eventsFromX derivation functions
└── src/commonTest/kotlin/io/github/kirthar/sddrpg/core/
    └── limitbreak/                     # unit + property tests per US1-US4
```

**Structure Decision**: one new package (`limitbreak`) plus one new file inside the
existing `event` package (required by Kotlin's same-package sealed-subtype rule,
verified empirically — see research R4). No new Gradle module needed.

## Complexity Tracking

No constitution violations — table intentionally empty.
