package io.github.kirthar.sddrpg.core.limitbreak

import io.github.kirthar.sddrpg.core.action.ActionError
import io.github.kirthar.sddrpg.core.action.ActionResolutionResult
import io.github.kirthar.sddrpg.core.action.BattleState
import io.github.kirthar.sddrpg.core.action.CombatAction
import io.github.kirthar.sddrpg.core.action.ResolutionOutcome
import io.github.kirthar.sddrpg.core.action.resolveAction
import io.github.kirthar.sddrpg.core.catalog.CommandKind
import io.github.kirthar.sddrpg.core.model.CombatantId
import io.github.kirthar.sddrpg.core.model.LimitBreakId

/** A rejected limit break attempt, always naming the offending combatant (mirrors spec 002's ActionError). */
sealed interface LimitBreakError {
    data class NoActiveLimitBreak(val actorId: CombatantId) : LimitBreakError
    data class GaugeNotFull(val actorId: CombatantId, val current: Int, val threshold: Int) : LimitBreakError
    /** The gauge gate passed, but the synthesized action itself was rejected by resolveAction (e.g. the class never granted SKILL, research R1). */
    data class ActionRejected(val actorId: CombatantId, val underlying: ActionError) : LimitBreakError
}

sealed interface LimitBreakResolutionResult {
    data class Resolved(val newState: BattleState, val newGauges: LimitGaugeState, val outcomes: List<ResolutionOutcome>) : LimitBreakResolutionResult
    data class Rejected(val error: LimitBreakError) : LimitBreakResolutionResult
}

/**
 * This feature's own gauge gate, enforced *before* calling `resolveAction` unmodified
 * (research R1). `resolveAction`/`CombatAction`/`CommandKind` know nothing about
 * limit breaks.
 */
fun resolveLimitBreak(
    battle: BattleState,
    gauges: LimitGaugeState,
    actorId: CombatantId,
    targetIds: Set<CombatantId>,
    catalog: LimitBreakCatalog,
): LimitBreakResolutionResult {
    val actor = battle.find(actorId)
    val limitBreakId: LimitBreakId = actor?.combatant?.capabilities?.limitBreaks?.firstOrNull()
        ?: return LimitBreakResolutionResult.Rejected(LimitBreakError.NoActiveLimitBreak(actorId))
    // A stale id (declared on the class but absent from this catalog) is treated the
    // same as having no active limit break, rather than crashing (mirrors spec 004's F2).
    val definition = catalog.limitBreaks.firstOrNull { it.id == limitBreakId }
        ?: return LimitBreakResolutionResult.Rejected(LimitBreakError.NoActiveLimitBreak(actorId))

    val current = gauges.gauges[actorId] ?: 0
    if (current < definition.threshold) {
        return LimitBreakResolutionResult.Rejected(LimitBreakError.GaugeNotFull(actorId, current, definition.threshold))
    }

    val action = CombatAction(
        actorId = actorId,
        command = CommandKind.SKILL,
        skillId = null,
        effectKind = definition.effectKind,
        element = definition.element,
        formula = definition.formula,
        targeting = definition.targeting,
    )
    return when (val result = resolveAction(battle, action, targetIds)) {
        is ActionResolutionResult.Resolved -> {
            val newGauges = LimitGaugeState(gauges.gauges + (actorId to 0))
            LimitBreakResolutionResult.Resolved(result.newState, newGauges, result.outcomes)
        }
        is ActionResolutionResult.Rejected -> LimitBreakResolutionResult.Rejected(LimitBreakError.ActionRejected(actorId, result.error))
    }
}

/** A rejected summon attempt, always naming the offending combatant/summon (mirrors spec 002's ActionError). */
sealed interface SummonError {
    data class UnknownSummon(val summonId: SummonId) : SummonError
    data class InsufficientResource(val actorId: CombatantId, val required: Int, val available: Int) : SummonError
    data class ActionRejected(val actorId: CombatantId, val underlying: ActionError) : SummonError
}

sealed interface SummonResolutionResult {
    data class Resolved(val newState: BattleState, val newResources: ResourceState, val outcomes: List<ResolutionOutcome>) : SummonResolutionResult
    data class Rejected(val error: SummonError) : SummonResolutionResult
}

/**
 * This feature's own resource gate, enforced *before* calling `resolveAction`
 * unmodified. `resolveAction`'s own existing `CommandKind.SUMMON` capability gate
 * (already declared by spec 001) applies natively here (research R1).
 */
fun resolveSummon(
    battle: BattleState,
    resources: ResourceState,
    actorId: CombatantId,
    summonId: SummonId,
    targetIds: Set<CombatantId>,
    catalog: SummonCatalog,
): SummonResolutionResult {
    val definition = catalog.summons.firstOrNull { it.id == summonId }
        ?: return SummonResolutionResult.Rejected(SummonError.UnknownSummon(summonId))

    val available = resources.current[actorId] ?: 0
    if (available < definition.cost) {
        return SummonResolutionResult.Rejected(SummonError.InsufficientResource(actorId, definition.cost, available))
    }

    val deducted = deductResource(resources, actorId, definition.cost)
    val action = CombatAction(
        actorId = actorId,
        command = CommandKind.SUMMON,
        skillId = null,
        effectKind = definition.effectKind,
        element = definition.element,
        formula = definition.formula,
        targeting = definition.targeting,
    )
    return when (val result = resolveAction(battle, action, targetIds)) {
        is ActionResolutionResult.Resolved -> SummonResolutionResult.Resolved(result.newState, deducted, result.outcomes)
        is ActionResolutionResult.Rejected -> SummonResolutionResult.Rejected(SummonError.ActionRejected(actorId, result.error))
    }
}
