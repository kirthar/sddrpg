package io.github.kirthar.sddrpg.core.limitbreak

import io.github.kirthar.sddrpg.core.action.BattleState
import io.github.kirthar.sddrpg.core.model.CombatantId
import io.github.kirthar.sddrpg.core.model.CoreStats

/**
 * Per-combatant resource pool (MP), tracked separately since spec 002's `HealthTrack`/
 * `BattleCombatant` never modeled a second resource (research R3).
 */
data class ResourceState(val current: Map<CombatantId, Int> = emptyMap())

/** Snapshots every participant's `CoreStats.MP` at battle-start, mirroring `Roster.toBattleState()`'s HP snapshot. */
fun BattleState.initialResourceState(): ResourceState =
    ResourceState(participants.associate { it.combatant.id to it.combatant.stats[CoreStats.MP] })

/** Unconditional subtraction -- callers MUST check sufficiency first (spec FR-008); this trusts that precondition. */
fun deductResource(state: ResourceState, combatantId: CombatantId, amount: Int): ResourceState {
    val current = state.current[combatantId] ?: 0
    return ResourceState(state.current + (combatantId to current - amount))
}
