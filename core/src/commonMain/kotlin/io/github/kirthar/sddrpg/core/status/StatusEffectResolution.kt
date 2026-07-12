package io.github.kirthar.sddrpg.core.status

import io.github.kirthar.sddrpg.core.action.BattleState
import io.github.kirthar.sddrpg.core.model.CombatantId

/** Applies [effectId]'s definition to [combatantId]: adds it, or refreshes duration to full if already active (spec FR-008). */
fun applyStatusEffect(
    state: StatusEffectState,
    combatantId: CombatantId,
    effectId: StatusEffectId,
    catalog: StatusEffectCatalog,
): StatusEffectState {
    val definition = catalog.effects.first { it.id == effectId }
    val existing = state.active[combatantId] ?: emptyList()
    val refreshed = existing.filterNot { it.effectId == effectId } + ActiveEffect(effectId, definition.duration)
    return StatusEffectState(state.active + (combatantId to refreshed))
}

/** Idempotent: removing an effect not currently active on [combatantId] is a no-op (edge case, spec.md). */
fun removeStatusEffect(
    state: StatusEffectState,
    combatantId: CombatantId,
    effectId: StatusEffectId,
): StatusEffectState {
    val existing = state.active[combatantId] ?: return state
    if (existing.none { it.effectId == effectId }) return state
    val filtered = existing.filterNot { it.effectId == effectId }
    return StatusEffectState(state.active + (combatantId to filtered))
}

/** The outcome of one [tickStatusEffects] call (spec FR-007). */
data class StatusTickResult(val newBattle: BattleState, val newEffects: StatusEffectState)

/**
 * One tick per call, applied to every active effect on every combatant, regardless of
 * whose turn triggered it (clarified FR-007 rule, research R7). A stale [ActiveEffect]
 * whose `effectId` is absent from [catalog] is skipped gracefully (F2, undefined
 * otherwise) rather than crashing.
 */
fun tickStatusEffects(
    effects: StatusEffectState,
    battle: BattleState,
    catalog: StatusEffectCatalog,
): StatusTickResult {
    val definitionsById = catalog.effects.associateBy { it.id }

    val decremented: Map<CombatantId, List<ActiveEffect>> = effects.active.mapValues { (_, active) ->
        active.map { it.copy(remainingDuration = it.remainingDuration - 1) }
    }

    var newBattle = battle
    for ((combatantId, active) in decremented) {
        for (activeEffect in active) {
            val definition = definitionsById[activeEffect.effectId] ?: continue
            if (definition.kind is EffectKind.DamageOverTime) {
                newBattle = applyTickDamage(newBattle, combatantId, definition.kind.amountPerTick)
            }
        }
    }

    val survivors = decremented.mapValues { (_, active) -> active.filter { it.remainingDuration > 0 } }
    return StatusTickResult(newBattle, StatusEffectState(survivors))
}

private fun applyTickDamage(battle: BattleState, combatantId: CombatantId, amount: Int): BattleState {
    val participant = battle.find(combatantId) ?: return battle
    val newCurrent = (participant.health.current - amount).coerceIn(0, participant.health.maximum)
    val updated = participant.copy(health = participant.health.copy(current = newCurrent))
    return BattleState(battle.participants.map { if (it.combatant.id == combatantId) updated else it })
}
