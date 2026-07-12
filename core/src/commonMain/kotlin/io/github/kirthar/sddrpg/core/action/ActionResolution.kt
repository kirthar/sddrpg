package io.github.kirthar.sddrpg.core.action

import io.github.kirthar.sddrpg.core.catalog.CommandKind
import io.github.kirthar.sddrpg.core.model.Affinity
import io.github.kirthar.sddrpg.core.model.CombatantId
import io.github.kirthar.sddrpg.core.model.SkillId

/** A rejected action submission, always naming the offending id/rule (spec FR-002/FR-008, mirrors spec 001's CatalogError). */
sealed interface ActionError {
    data class DefeatedActor(val actorId: CombatantId) : ActionError
    data class MissingCommand(val actorId: CombatantId, val command: CommandKind) : ActionError
    data class MissingSkill(val actorId: CombatantId, val skillId: SkillId) : ActionError
    data class UnknownTarget(val targetId: CombatantId) : ActionError
    data class TargetShapeMismatch(val shape: TargetingShape, val reason: String) : ActionError
}

/** The reported result of resolving an action against one affected combatant (spec FR-010). */
data class ResolutionOutcome(
    val targetId: CombatantId,
    val appliedDelta: Int,
    val affinityApplied: Affinity?,
    val resultingHealth: Int,
)

/** An action never partially resolves: either every affected target is reported, or none are (spec FR-001). */
sealed interface ActionResolutionResult {
    data class Resolved(val newState: BattleState, val outcomes: List<ResolutionOutcome>) : ActionResolutionResult
    data class Rejected(val error: ActionError) : ActionResolutionResult
}

/**
 * Pure: same (state, action, targetIds) always returns an equal result (spec FR-013,
 * SC-003). Never mutates [state] — on [ActionResolutionResult.Rejected] the input is
 * left completely untouched (spec SC-002).
 */
fun resolveAction(
    state: BattleState,
    action: CombatAction,
    targetIds: Set<CombatantId>,
): ActionResolutionResult {
    val actor = state.find(action.actorId)
        ?: return ActionResolutionResult.Rejected(ActionError.UnknownTarget(action.actorId))

    if (actor.isDefeated) {
        return ActionResolutionResult.Rejected(ActionError.DefeatedActor(action.actorId))
    }
    if (action.command !in actor.combatant.capabilities.commands) {
        return ActionResolutionResult.Rejected(ActionError.MissingCommand(action.actorId, action.command))
    }
    action.skillId?.let { skillId ->
        if (skillId !in actor.combatant.capabilities.skills) {
            return ActionResolutionResult.Rejected(ActionError.MissingSkill(action.actorId, skillId))
        }
    }

    for (targetId in targetIds) {
        if (state.find(targetId) == null) {
            return ActionResolutionResult.Rejected(ActionError.UnknownTarget(targetId))
        }
    }

    val outcomes = mutableListOf<ResolutionOutcome>()
    var newState = state
    for (targetId in targetIds) {
        val target = newState.find(targetId)!!
        val rawMagnitude = action.formula.rawMagnitude(actor.combatant.stats, target.combatant.stats)
        val baseDelta = if (action.effectKind == EffectKind.DAMAGE) -rawMagnitude else rawMagnitude
        val stance = action.element?.let { target.combatant.affinityTo(it) }
        val adjustedDelta = if (stance != null) applyElementalAdjustment(baseDelta, stance) else baseDelta
        val newCurrent = (target.health.current + adjustedDelta).coerceIn(0, target.health.maximum)
        val updatedTarget = target.copy(health = target.health.copy(current = newCurrent))
        newState = BattleState(newState.participants.map { if (it.combatant.id == targetId) updatedTarget else it })
        outcomes += ResolutionOutcome(
            targetId = targetId,
            appliedDelta = newCurrent - target.health.current,
            affinityApplied = stance,
            resultingHealth = newCurrent,
        )
    }

    return ActionResolutionResult.Resolved(newState, outcomes)
}
