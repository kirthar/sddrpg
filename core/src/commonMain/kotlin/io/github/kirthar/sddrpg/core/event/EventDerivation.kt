package io.github.kirthar.sddrpg.core.event

import io.github.kirthar.sddrpg.core.action.ActionResolutionResult
import io.github.kirthar.sddrpg.core.action.BattleState
import io.github.kirthar.sddrpg.core.action.CombatAction
import io.github.kirthar.sddrpg.core.action.EffectKind
import io.github.kirthar.sddrpg.core.model.CombatantId
import io.github.kirthar.sddrpg.core.schedule.ScheduleResult
import io.github.kirthar.sddrpg.core.status.EffectKind as StatusEffectKind
import io.github.kirthar.sddrpg.core.status.StatusEffectCatalog
import io.github.kirthar.sddrpg.core.status.StatusEffectId
import io.github.kirthar.sddrpg.core.status.StatusEffectState
import io.github.kirthar.sddrpg.core.status.StatusTickResult
import kotlin.math.abs

/**
 * Shared "just defeated" check (research R6): reports a [BattleEvent.CombatantDefeated]
 * only the first time a combatant's health reaches 0, never again while it stays 0.
 */
internal fun defeatedEvents(wasDefeatedBefore: Boolean, resultingHealth: Int, combatantId: CombatantId): List<BattleEvent> =
    if (!wasDefeatedBefore && resultingHealth == 0) listOf(BattleEvent.CombatantDefeated(combatantId)) else emptyList()

/**
 * Derives the events an already-completed [resolveAction][io.github.kirthar.sddrpg.core.action.resolveAction]
 * call represents (research R1). `resolveAction` itself is never modified.
 */
fun eventsFromResolution(oldState: BattleState, action: CombatAction, result: ActionResolutionResult): List<BattleEvent> {
    if (result !is ActionResolutionResult.Resolved) return emptyList()

    return result.outcomes.flatMap { outcome ->
        val amount = abs(outcome.appliedDelta)
        val mainEvent: BattleEvent = if (action.effectKind == EffectKind.DAMAGE) {
            BattleEvent.DamageDealt(action.actorId, outcome.targetId, amount, outcome.resultingHealth)
        } else {
            BattleEvent.HealingApplied(action.actorId, outcome.targetId, amount, outcome.resultingHealth)
        }
        val wasDefeatedBefore = oldState.find(outcome.targetId)!!.isDefeated
        listOf(mainEvent) + defeatedEvents(wasDefeatedBefore, outcome.resultingHealth, outcome.targetId)
    }
}

/** Derives the event a [ScheduleResult] represents (research R1). */
fun eventsFromSchedule(result: ScheduleResult<*>): List<BattleEvent> = when (result) {
    is ScheduleResult.Ready<*> -> listOf(BattleEvent.TurnGranted(result.combatantId))
    ScheduleResult.NoOneReady -> emptyList()
}

/** Thin wrapper a caller uses right after `applyStatusEffect` (spec 004), unconditionally -- a refresh is still an application. */
fun eventsFromApply(combatantId: CombatantId, effectId: StatusEffectId): List<BattleEvent> =
    listOf(BattleEvent.StatusEffectApplied(combatantId, effectId))

/**
 * Derives the events an already-completed `tickStatusEffects` call represents (research
 * R2/R6): walks [oldEffects] in the same order `tickStatusEffects` itself iterates,
 * folding each DamageOverTime effect's per-tick amount against a running clamped
 * health value -- correct because every step is a pure subtraction floored at zero, so
 * the final value always equals [StatusTickResult.newBattle]'s (verified by
 * `StatusTickEventTest`). [result] is accepted for signature symmetry with the other
 * `eventsFromX` functions (spec.md's "already-completed call's inputs and output"
 * shape) even though this particular derivation doesn't need to dereference it.
 */
fun eventsFromTick(
    oldEffects: StatusEffectState,
    oldBattle: BattleState,
    catalog: StatusEffectCatalog,
    @Suppress("UNUSED_PARAMETER") result: StatusTickResult,
): List<BattleEvent> {
    val definitionsById = catalog.effects.associateBy { it.id }
    val events = mutableListOf<BattleEvent>()

    for ((combatantId, active) in oldEffects.active) {
        val participant = oldBattle.find(combatantId) ?: continue
        var current = participant.health.current
        var wasDefeatedBefore = participant.isDefeated

        for (activeEffect in active) {
            val definition = definitionsById[activeEffect.effectId] ?: continue
            if (definition.kind is StatusEffectKind.DamageOverTime) {
                val amount = definition.kind.amountPerTick
                current = (current - amount).coerceIn(0, participant.health.maximum)
                events += BattleEvent.DamageDealt(null, combatantId, amount, current)
                events += defeatedEvents(wasDefeatedBefore, current, combatantId)
                wasDefeatedBefore = wasDefeatedBefore || current == 0
            }
            if (activeEffect.remainingDuration - 1 == 0) {
                events += BattleEvent.StatusEffectExpired(combatantId, activeEffect.effectId)
            }
        }
    }

    return events
}
