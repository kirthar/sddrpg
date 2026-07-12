# Contract: Turn Scheduler Public API

**Feature**: 003-turn-scheduler | **Consumers**: future battle-loop feature, demos, tests

Normative signatures for the `schedule` package (commonMain,
`io.github.kirthar.sddrpg.core.schedule`). Consumes spec 001's `Combatant`/
`CombatantId`/`CoreStats.SPEED` and spec 002's `BattleState` unchanged. See
[data-model.md](../data-model.md) for field tables and algorithm detail.

## Kotlin API

```kotlin
// TurnScheduler.kt
sealed interface ScheduleResult<out State> {
    data class Ready<State>(val combatantId: CombatantId, val advancedSchedule: State) : ScheduleResult<State>
    data object NoOneReady : ScheduleResult<Nothing>
}

interface TurnScheduler<State> {
    /** Fresh bookkeeping for a battle that hasn't granted any turns yet. */
    fun initialSchedule(battle: BattleState): State

    /**
     * Pure: same (schedule, battle) always returns an equal result (spec FR-006,
     * SC-003). Reads [battle]'s defeated status fresh — never caches it (research R4).
     */
    fun nextTurn(schedule: State, battle: BattleState): ScheduleResult<State>

    /** Pure: consumes the turn just granted to [combatantId] so it isn't offered again immediately. */
    fun markSpent(schedule: State, combatantId: CombatantId): State
}

// ActiveTimeBattleScheduler.kt
data class AtbScheduleState(val readiness: Map<CombatantId, Int>)

object ActiveTimeBattleScheduler : TurnScheduler<AtbScheduleState> {
    const val READY_THRESHOLD: Int = 1000

    override fun initialSchedule(battle: BattleState): AtbScheduleState
    override fun nextTurn(schedule: AtbScheduleState, battle: BattleState): ScheduleResult<AtbScheduleState>
    override fun markSpent(schedule: AtbScheduleState, combatantId: CombatantId): AtbScheduleState
}
```

Stability rules:
- `nextTurn`/`markSpent` never throw for well-formed inputs; a battle with no eligible
  combatants returns `ScheduleResult.NoOneReady`, never an exception (spec FR-009).
- `nextTurn` never mutates its `BattleState` argument, and never returns a
  `Ready.combatantId` for a defeated combatant (spec FR-004) or one with
  `stats[SPEED] <= 0` acting purely on that non-positive speed (research R2).
- Given the same `(schedule, battle)` pair, `nextTurn` always returns a structurally
  equal `ScheduleResult` (spec SC-003); given the same `(schedule, combatantId)` pair,
  `markSpent` always returns a structurally equal state.
- Ties are broken by `battle.participants` index order, deterministically, every time
  (spec FR-007, SC-004) — never by `CombatantId` string comparison (research R3) and
  never randomly.
- `markSpent(schedule, id)` should only be called with an `id` that `nextTurn` most
  recently returned as `Ready.combatantId` for that `schedule`; behavior for any other
  id is unspecified by this feature (no defined use case exists yet — a future battle
  loop feature is the only intended caller).

## Typical usage (informative, not itself a contract)

```kotlin
var schedule = ActiveTimeBattleScheduler.initialSchedule(battle)
when (val result = ActiveTimeBattleScheduler.nextTurn(schedule, battle)) {
    is ScheduleResult.Ready -> {
        schedule = result.advancedSchedule
        // ... caller resolves an action for result.combatantId via spec 002's resolveAction ...
        schedule = ActiveTimeBattleScheduler.markSpent(schedule, result.combatantId)
    }
    ScheduleResult.NoOneReady -> { /* battle over */ }
}
```
