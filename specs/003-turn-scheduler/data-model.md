# Data Model: Turn Scheduler

**Feature**: 003-turn-scheduler | **Date**: 2026-07-12
**Sources**: spec.md (Key Entities, FRs), research.md (R1–R4)

Extends `core` additively: `Combatant`, `BattleState`, `CombatantId` (specs 001–002)
are consumed exactly as published (spec FR-010), nothing here changes them.

## Scheduler abstraction (R1)

### TurnScheduler\<State\>
| Member | Signature | Rules |
|---|---|---|
| initialSchedule | `(BattleState) -> State` | readiness/bookkeeping starts fresh for every participant |
| nextTurn | `(State, BattleState) -> ScheduleResult<State>` | pure; reads `BattleState` fresh each call (R4) |
| markSpent | `(State, CombatantId) -> State` | pure; `combatantId` must be the id `nextTurn` just returned |

### ScheduleResult\<State\>
Sealed: `Ready(combatantId: CombatantId, advancedSchedule: State)` XOR `NoOneReady`.
`advancedSchedule` already reflects the readiness advancement needed to reach this
ready moment (R2) — the caller does not separately "commit" that advancement.

## ATB implementation (research R1–R4)

### AtbScheduleState
| Field | Type | Rules |
|---|---|---|
| readiness | Map\<CombatantId, Int\> | one entry per participant at battle start; ≥ 0, no fixed upper bound (overshoot carries over, R2) |

### ActiveTimeBattleScheduler : TurnScheduler\<AtbScheduleState\>
- `READY_THRESHOLD = 1000` (engine constant, R3).
- `initialSchedule`: readiness = 0 for every `battle.participants` entry.
- `nextTurn`:
  1. Candidates = non-defeated participants (fresh `BattleCombatant.isDefeated` check
     against the passed-in `battle`, R4) with `combatant.stats[CoreStats.SPEED] > 0`.
  2. If candidates empty → `NoOneReady` (covers both "all defeated" and "everyone has
     non-positive Speed", spec FR-009 and the zero-Speed edge case).
  3. For each candidate, `ticksNeeded = ceilDiv(READY_THRESHOLD - readiness, speed)`
     (integer ceiling division, R2).
  4. `minTicks = min(ticksNeeded)`; advance every non-defeated participant's readiness
     by `minTicks * speed` (defeated participants' readiness is left untouched — they
     don't need to be comparable once excluded).
  5. Among participants whose advanced readiness `>= READY_THRESHOLD`, pick the one
     with the lowest index in `battle.participants` (R3 tie-break).
  6. Return `Ready(winnerId, AtbScheduleState(advancedReadiness))`.
- `markSpent(schedule, combatantId)`: `readiness[combatantId] -= READY_THRESHOLD`
  (carry-over, never reset to exactly 0 — R2); all other entries unchanged.

## State transitions

- `initialSchedule` → `AtbScheduleState` with all-zero readiness.
- `nextTurn` → advances every non-defeated participant's readiness by the same
  `minTicks * speed_i` (per-participant, since speed differs), never mutates the input
  `BattleState`.
- `markSpent` → only the named combatant's readiness changes (threshold subtracted);
  everyone else's readiness from the preceding `nextTurn` call is preserved, so a tied
  loser is still at/above threshold on the very next `nextTurn` call (spec US1
  scenario 2's "different combatant reported ready" holds even under ties).

## Determinism guarantee (spec FR-006/SC-003)

`nextTurn` and `markSpent` are pure integer-arithmetic functions of their inputs; no
`Double`, no RNG, no wall-clock. Given the same starting `AtbScheduleState` +
`BattleState` and the same sequence of `nextTurn`/`markSpent` calls, every call
produces byte-for-byte identical results, on both JVM and JS.
