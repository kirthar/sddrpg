package io.github.kirthar.sddrpg.core.action

import io.github.kirthar.sddrpg.core.catalog.CommandKind
import io.github.kirthar.sddrpg.core.combatant.Allegiance
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

    val resolvedTargets = resolveTargets(state, actor, action.targeting, targetIds)
        ?: return ActionResolutionResult.Rejected(
            ActionError.TargetShapeMismatch(action.targeting, "target selection does not match ${action.targeting}")
        )

    val outcomes = mutableListOf<ResolutionOutcome>()
    var newState = state
    for (targetId in resolvedTargets) {
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

/**
 * Validates [targetIds] against [shape] and resolves the final id list to affect
 * (research R6). For `SINGLE_*`/`SELF`, the caller's explicit selection must match the
 * shape exactly, including cardinality — an explicitly-selected defeated combatant is
 * a mismatch, not a silent exclusion. For `ALL_*`, an empty [targetIds] auto-selects
 * the full matching scope; a non-empty one is validated as the caller's subset. Either
 * way, already-defeated combatants are excluded from the final `ALL_*` result without
 * failing the match (spec FR-009). Returns `null` on any mismatch.
 */
private fun resolveTargets(
    state: BattleState,
    actor: BattleCombatant,
    shape: TargetingShape,
    targetIds: Set<CombatantId>,
): List<CombatantId>? {
    val actorId = actor.combatant.id
    val allegiance = actor.combatant.allegiance

    fun sameAllegiance() = state.participants.filter { it.combatant.allegiance == allegiance }.map { it.combatant.id }
    fun oppositeAllegiance() = state.participants.filter { it.combatant.allegiance != allegiance }.map { it.combatant.id }

    return when (shape) {
        TargetingShape.SELF -> {
            if (targetIds != setOf(actorId)) null else listOf(actorId)
        }
        TargetingShape.SINGLE_ALLY -> {
            val id = targetIds.singleOrNull() ?: return null
            if (id !in sameAllegiance() || state.find(id)!!.isDefeated) null else listOf(id)
        }
        TargetingShape.SINGLE_ENEMY -> {
            val id = targetIds.singleOrNull() ?: return null
            if (id !in oppositeAllegiance() || state.find(id)!!.isDefeated) null else listOf(id)
        }
        TargetingShape.ALL_ALLIES -> resolveAllShape(state, targetIds, sameAllegiance())
        TargetingShape.ALL_ENEMIES -> resolveAllShape(state, targetIds, oppositeAllegiance())
        TargetingShape.ALL -> resolveAllShape(state, targetIds, state.participants.map { it.combatant.id })
    }
}

/** Shared "all" logic: empty selection means the full scope; a non-empty one is the caller's subset. */
private fun resolveAllShape(state: BattleState, targetIds: Set<CombatantId>, scope: List<CombatantId>): List<CombatantId>? {
    val candidates = if (targetIds.isEmpty()) scope else {
        if (!scope.containsAll(targetIds)) return null
        targetIds.toList()
    }
    return candidates.filterNot { state.find(it)!!.isDefeated }
}
