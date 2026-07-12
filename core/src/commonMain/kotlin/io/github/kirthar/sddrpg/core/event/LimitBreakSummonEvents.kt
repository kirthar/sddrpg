package io.github.kirthar.sddrpg.core.event

import io.github.kirthar.sddrpg.core.action.BattleState
import io.github.kirthar.sddrpg.core.action.EffectKind
import io.github.kirthar.sddrpg.core.limitbreak.LimitBreakCatalog
import io.github.kirthar.sddrpg.core.limitbreak.LimitBreakResolutionResult
import io.github.kirthar.sddrpg.core.limitbreak.LimitGaugeState
import io.github.kirthar.sddrpg.core.limitbreak.SummonId
import io.github.kirthar.sddrpg.core.limitbreak.SummonResolutionResult
import io.github.kirthar.sddrpg.core.model.CombatantId
import io.github.kirthar.sddrpg.core.model.LimitBreakId
import kotlin.math.abs

/**
 * Extends [BattleEvent] from a new file in the same package (research R4, spec 006) --
 * `BattleEvent.kt` itself is never edited; Kotlin's sealed-type rule requires the same
 * package, not the same file.
 */
data class GaugeFull(val combatantId: CombatantId) : BattleEvent
data class LimitBreakUsed(val combatantId: CombatantId, val limitBreakId: LimitBreakId) : BattleEvent
data class SummonCast(val combatantId: CombatantId, val summonId: SummonId) : BattleEvent

/** One [GaugeFull] per combatant crossing from below their threshold to at-or-above it. */
fun eventsFromGaugeCharge(
    battle: BattleState,
    before: LimitGaugeState,
    after: LimitGaugeState,
    catalog: LimitBreakCatalog,
): List<BattleEvent> {
    val definitionsById = catalog.limitBreaks.associateBy { it.id }
    val events = mutableListOf<BattleEvent>()

    for ((combatantId, afterValue) in after.gauges) {
        val limitBreakId = battle.find(combatantId)?.combatant?.capabilities?.limitBreaks?.firstOrNull() ?: continue
        val threshold = definitionsById[limitBreakId]?.threshold ?: continue
        val beforeValue = before.gauges[combatantId] ?: 0
        if (beforeValue < threshold && afterValue >= threshold) {
            events += GaugeFull(combatantId)
        }
    }
    return events
}

/**
 * Builds `DamageDealt`/`HealingApplied`/`CombatantDefeated` directly from
 * `result.outcomes` (research finding during /speckit-analyze: reusing spec 005's
 * `eventsFromResolution` would require reconstructing a fake `ActionResolutionResult`).
 * The trigger event is first, mirroring spec 005's `SynergyTriggered` ordering.
 */
fun eventsFromLimitBreak(
    oldState: BattleState,
    actorId: CombatantId,
    limitBreakId: LimitBreakId,
    effectKind: EffectKind,
    result: LimitBreakResolutionResult,
): List<BattleEvent> {
    if (result !is LimitBreakResolutionResult.Resolved) return emptyList()
    val effectEvents = result.outcomes.flatMap { outcome ->
        val amount = abs(outcome.appliedDelta)
        val mainEvent: BattleEvent = if (effectKind == EffectKind.DAMAGE) {
            BattleEvent.DamageDealt(null, outcome.targetId, amount, outcome.resultingHealth)
        } else {
            BattleEvent.HealingApplied(null, outcome.targetId, amount, outcome.resultingHealth)
        }
        val wasDefeatedBefore = oldState.find(outcome.targetId)!!.isDefeated
        listOf(mainEvent) + defeatedEvents(wasDefeatedBefore, outcome.resultingHealth, outcome.targetId)
    }
    return listOf(LimitBreakUsed(actorId, limitBreakId)) + effectEvents
}

/** Same shape as [eventsFromLimitBreak], for summons. */
fun eventsFromSummon(
    oldState: BattleState,
    actorId: CombatantId,
    summonId: SummonId,
    effectKind: EffectKind,
    result: SummonResolutionResult,
): List<BattleEvent> {
    if (result !is SummonResolutionResult.Resolved) return emptyList()
    val effectEvents = result.outcomes.flatMap { outcome ->
        val amount = abs(outcome.appliedDelta)
        val mainEvent: BattleEvent = if (effectKind == EffectKind.DAMAGE) {
            BattleEvent.DamageDealt(null, outcome.targetId, amount, outcome.resultingHealth)
        } else {
            BattleEvent.HealingApplied(null, outcome.targetId, amount, outcome.resultingHealth)
        }
        val wasDefeatedBefore = oldState.find(outcome.targetId)!!.isDefeated
        listOf(mainEvent) + defeatedEvents(wasDefeatedBefore, outcome.resultingHealth, outcome.targetId)
    }
    return listOf(SummonCast(actorId, summonId)) + effectEvents
}
