# Implementation Plan: Turn Scheduler

**Branch**: `003-turn-scheduler` (feature dir; git work happens on the session's
designated branch) | **Date**: 2026-07-12 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `/specs/003-turn-scheduler/spec.md`

## Summary

Add a `schedule` package to `core`: a generic `TurnScheduler<State>` Strategy interface
(initialize schedule, query next-ready combatant, mark a turn spent) plus one concrete
implementation, `ActiveTimeBattleScheduler`, whose private `AtbScheduleState` tracks
per-combatant readiness accrued from spec 001's `Speed` stat. Readiness math is
integer-only ceiling-division "tick-jump" arithmetic — no simulated per-tick loop, no
floating point, no RNG — so the same battle configuration and turn-consumption sequence
always replays identically. Defeated combatants (spec 002's `BattleState`) are excluded
from candidacy every query; ties are broken by roster position. The interface is
generic precisely so a future phase-based scheduler can own a completely different
state shape without any change to `TurnScheduler`, `BattleState`, or `Combatant`.

## Technical Context

**Language/Version**: Kotlin 2.4.0 (Multiplatform; jvm + js targets, same as `core`)

**Primary Dependencies**: none beyond what `core` already has; pure functions over
spec 001's `Combatant`/`CoreStats.SPEED` and spec 002's `BattleState`

**Storage**: N/A — in-memory, immutable state transitions, same convention as specs 001–002

**Testing**: kotest 6.2.2 in `commonTest` (unit + property tests, JVM + JS)

**Target Platform**: platform-agnostic common code, same as `core`

**Project Type**: library module — extends the existing `core` module, new package

**Performance Goals**: one `nextTurn` query over ~2–10 combatants is O(n) integer
arithmetic, no loops over simulated ticks — trivially fast at this scale; no measured
target needed

**Constraints**: 100% deterministic (constitution Principle I): no RNG, no wall-clock,
no floating point anywhere in the accrual math (pure integer ceiling division, stricter
than spec 002's rounding since there is no genre-mandated ×0.5 case here); must not
modify `Combatant`, `BattleState`, `CombatantId`, or any spec 001/002 public shape
(spec FR-010)

**Scale/Scope**: battles of ~2–10 combatants (specs 001–002 precedent); this feature
answers "whose turn is it" only — no battle loop, no AI, no action submission (that
remains spec 002's `resolveAction`, invoked by a future feature once a turn is granted)

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principle | Gate | Status |
|---|---|---|
| I. Pure & Deterministic Core | No RNG, no wall-clock, no I/O; integer-only ceiling-division arithmetic (no `Double` anywhere, stricter than spec 002); `nextTurn`/`markSpent` are pure functions, old state → new state | ✅ PASS |
| II. Data-Driven Content | No new "content" concept here — Speed is already spec 001 data; which `TurnScheduler` implementation a battle uses is itself a configuration choice, not hardcoded per battle | ✅ PASS (principle largely N/A to this feature, no violation) |
| III. Test-First | Acceptance scenarios (spec US1–US3) map to failing kotest tests before implementation; property tests for determinism/proportionality/bounds | ✅ PASS — test plan in quickstart.md |
| IV. Simulation–Presentation Separation | `ScheduleResult` is structured data (combatant id + new state), no formatted text; still no UI/localization in `core` | ✅ PASS |
| V. Extensible Architecture w/o Speculative Impl. | `TurnScheduler<State>` generic interface: a future phase-based scheduler owns its own state type behind the same interface, zero rework of `BattleState`/`Combatant`/callers; only the ATB implementation ships now (FR-005) | ✅ PASS — see research R1 |

**Post-design re-check (after Phase 1)**: all five gates still pass. `ScheduleState`
(ATB's concrete state) is additive and lives entirely in the new `schedule` package;
zero changes to specs 001–002's public API. No Complexity Tracking entries needed.

## Project Structure

### Documentation (this feature)

```text
specs/003-turn-scheduler/
├── plan.md              # This file (/speckit-plan output)
├── research.md          # Phase 0 output (/speckit-plan)
├── data-model.md         # Phase 1 output (/speckit-plan)
├── quickstart.md        # Phase 1 output (/speckit-plan)
├── contracts/
│   └── scheduler-api.md # Phase 1 output (/speckit-plan)
├── checklists/
│   └── requirements.md  # Spec quality checklist (from /speckit-specify)
└── tasks.md             # Phase 2 output (/speckit-tasks — NOT created by /speckit-plan)
```

### Source Code (repository root)

```text
core/
├── src/commonMain/kotlin/io/github/kirthar/sddrpg/core/
│   └── schedule/                     # NEW package for this feature
│       ├── TurnScheduler.kt          # generic Strategy interface + ScheduleResult
│       └── ActiveTimeBattleScheduler.kt  # AtbScheduleState + concrete ATB implementation
└── src/commonTest/kotlin/io/github/kirthar/sddrpg/core/
    └── schedule/                     # unit + property tests per US1-US3
```

**Structure Decision**: single new package inside the existing `core` module (library
project type, same as specs 001–002). No new Gradle module needed.

## Complexity Tracking

No constitution violations — table intentionally empty.
