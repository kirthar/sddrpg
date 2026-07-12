package io.github.kirthar.sddrpg.core.limitbreak

import io.github.kirthar.sddrpg.core.action.BattleState
import io.github.kirthar.sddrpg.core.event.BattleEvent
import io.github.kirthar.sddrpg.core.model.CombatantId

/** Per-combatant accumulating limit gauge (spec Key Entities). Absent key = gauge 0. */
data class LimitGaugeState(val gauges: Map<CombatantId, Int> = emptyMap())

/**
 * Pure post-processing scan over already-derived [BattleEvent.DamageDealt] events
 * (research R2) -- `resolveAction`/`tickStatusEffects` never know this exists. A
 * combatant whose class declares no limit break (`capabilities.limitBreaks` empty,
 * research R5) is skipped entirely -- no gauge entry is ever created for them.
 */
fun chargeLimitGauge(
    state: LimitGaugeState,
    battle: BattleState,
    events: List<BattleEvent>,
    catalog: LimitBreakCatalog,
): LimitGaugeState {
    val definitionsById = catalog.limitBreaks.associateBy { it.id }
    var gauges = state.gauges

    for (event in events) {
        if (event !is BattleEvent.DamageDealt) continue
        val limitBreakId = battle.find(event.targetId)?.combatant?.capabilities?.limitBreaks?.firstOrNull() ?: continue
        val definition = definitionsById[limitBreakId] ?: continue
        val current = gauges[event.targetId] ?: 0
        gauges = gauges + (event.targetId to (current + event.amount).coerceAtMost(definition.threshold))
    }

    return LimitGaugeState(gauges)
}
