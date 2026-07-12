package io.github.kirthar.sddrpg.core.schedule

import io.github.kirthar.sddrpg.core.action.BattleState
import io.github.kirthar.sddrpg.core.model.CombatantId

/**
 * The result of asking a [TurnScheduler] who acts next (spec FR-001). [advancedSchedule]
 * already reflects whatever internal bookkeeping was needed to reach this ready moment —
 * the caller does not separately "commit" that advancement.
 */
sealed interface ScheduleResult<out State> {
    data class Ready<State>(val combatantId: CombatantId, val advancedSchedule: State) : ScheduleResult<State>
    data object NoOneReady : ScheduleResult<Nothing>
}

/**
 * Determines whose turn is next in a battle, decoupled from any one ordering algorithm
 * (Strategy, spec FR-005). Generic over its own private state type [State] so a future
 * phase-based scheduler can own a completely different kind of bookkeeping behind this
 * same interface — no change to [BattleState]/`Combatant`/callers needed (research R1).
 */
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
