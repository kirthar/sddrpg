# Implementation Plan: Status Effects

**Branch**: `004-status-effects` (feature dir; git work happens on the session's
designated branch) | **Date**: 2026-07-12 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `/specs/004-status-effects/spec.md`

## Summary

Add a `status` package to `core`: data-driven `StatusEffectDefinition`s (a closed
`EffectKind` discriminator — stat modifier / incapacitate / damage-over-time, per
constitution Principle II), a battle-time `StatusEffectState` tracking active effect
instances per combatant, and a pure derivation function that produces an *effective*
`BattleState` — combatants wrapped (via Kotlin interface delegation, since
`Combatant` is a plain public interface) with modifier-adjusted stats — which the
caller then feeds into spec 002's `resolveAction` and spec 003's
`ActiveTimeBattleScheduler` **unchanged**. Incapacitation is implemented by forcing an
affected combatant's effective Speed to `0`, reusing spec 003's already-built,
already-tested zero-Speed exclusion rather than adding any new scheduler logic.
Damage-over-time reuses spec 002's `[0, maximum]` health-clamp arithmetic (duplicated
in miniature — DoT has no actor, so it cannot honestly go through `resolveAction`'s
actor/capability-gated pipeline). Ticking follows the clarified FR-007 rule: one tick
per turn consumed anywhere in the battle, applied uniformly to every active effect on
every combatant.

## Technical Context

**Language/Version**: Kotlin 2.4.0 (Multiplatform; jvm + js targets, same as `core`)

**Primary Dependencies**: none beyond what `core` already has; pure functions and one
delegation wrapper over spec 001's `Combatant`, spec 002's `BattleState`, spec 003's
`ActiveTimeBattleScheduler`

**Storage**: N/A — in-memory, immutable state transitions, same convention as specs 001–003

**Testing**: kotest 6.2.2 in `commonTest` (unit + property tests, JVM + JS)

**Target Platform**: platform-agnostic common code, same as `core`

**Project Type**: library module — extends the existing `core` module, new package

**Performance Goals**: deriving an effective `BattleState` and ticking effects are both
O(participants × active effects), trivial at battle scale (~2–10 combatants); no
measured target needed

**Constraints**: 100% deterministic (constitution Principle I): no RNG anywhere;
integer-only arithmetic (stat deltas, durations, DoT damage); must not modify
`Combatant`, `BattleState`, `CombatantId`, `resolveAction`, `TurnScheduler`, or spec
001's `Catalog`/`validateCatalog` — this feature's own status-effect catalog is a
**separate, independent aggregate**, not an addition to spec 001's `Catalog`, so
nothing in `catalog/` needs to change either (spec FR-011)

**Scale/Scope**: battles of ~2–10 combatants (specs 001–003 precedent); this feature
defines the mechanism only — no specific status effect catalog content (that's
feature 007) and no battle-loop wiring (a later feature calls `tickStatusEffects` once
per consumed turn; this feature only provides the function)

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principle | Gate | Status |
|---|---|---|
| I. Pure & Deterministic Core | No RNG, no I/O; every function (apply/remove/tick/derive-effective-state) is pure, old state → new state | ✅ PASS |
| II. Data-Driven Content | `StatusEffectDefinition` is catalog data; `EffectKind` is a closed, engine-recognized set (Strategy-like discriminator, mirrors spec 002's `DamageFormula`) selected by data, never one class per effect | ✅ PASS — see research R2 |
| III. Test-First | Acceptance scenarios (spec US1–US5) map to failing kotest tests before implementation; property tests for determinism/bounds | ✅ PASS — test plan in quickstart.md |
| IV. Simulation–Presentation Separation | `StatusEffectState`/`ActiveEffect`/`StatusTickResult` are structured data, no formatted text | ✅ PASS |
| V. Extensible Architecture w/o Speculative Impl. | `EffectKind` covers exactly the three kinds spec.md asks for; resistance/immunity, per-effect stacking policy, and battle-loop wiring are explicitly not built now (spec Assumptions) | ✅ PASS |

**Post-design re-check (after Phase 1)**: all five gates still pass. The `status`
package is additive; zero changes to any spec 001–003 file. `Combatant` remains a
plain, non-sealed public interface — exactly the shape this design depends on — so no
retroactive change to specs 001–003 was needed to make this plan work. No Complexity
Tracking entries needed.

## Project Structure

### Documentation (this feature)

```text
specs/004-status-effects/
├── plan.md              # This file (/speckit-plan output)
├── research.md          # Phase 0 output (/speckit-plan)
├── data-model.md         # Phase 1 output (/speckit-plan)
├── quickstart.md        # Phase 1 output (/speckit-plan)
├── contracts/
│   └── status-api.md    # Phase 1 output (/speckit-plan)
├── checklists/
│   └── requirements.md  # Spec quality checklist (from /speckit-specify)
└── tasks.md             # Phase 2 output (/speckit-tasks — NOT created by /speckit-plan)
```

### Source Code (repository root)

```text
core/
├── src/commonMain/kotlin/io/github/kirthar/sddrpg/core/
│   └── status/                          # NEW package for this feature
│       ├── EffectKind.kt                # sealed: StatModifier, Incapacitate, DamageOverTime
│       ├── StatusEffectDefinition.kt    # catalog data + StatusEffectCatalog aggregate + validation
│       ├── StatusEffectState.kt         # ActiveEffect, StatusEffectState (battle-time tracking)
│       ├── EffectiveCombatant.kt        # Combatant-by-delegation wrapper with adjusted stats
│       └── StatusEffectResolution.kt    # applyStatusEffect, removeStatusEffect, tickStatusEffects,
│                                         # deriveEffectiveBattleState
└── src/commonTest/kotlin/io/github/kirthar/sddrpg/core/
    └── status/                          # unit + property tests per US1-US5
```

**Structure Decision**: single new package inside the existing `core` module (library
project type, same as specs 001–003). No new Gradle module needed.

## Complexity Tracking

No constitution violations — table intentionally empty.
