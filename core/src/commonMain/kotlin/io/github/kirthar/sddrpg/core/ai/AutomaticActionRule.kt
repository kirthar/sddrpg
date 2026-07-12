package io.github.kirthar.sddrpg.core.ai

import io.github.kirthar.sddrpg.core.action.BattleState
import io.github.kirthar.sddrpg.core.catalog.CommandKind
import io.github.kirthar.sddrpg.core.combatant.Combatant
import io.github.kirthar.sddrpg.core.model.CombatantId
import io.github.kirthar.sddrpg.core.model.SkillId

/**
 * spec.md's "Automatic Action Rule" (v1): the smallest deterministic rule that lets an
 * automatically-controlled combatant act without human input, phrased purely over
 * already-published `core` types so it stays reusable beyond one console demo
 * (constitution Principle IV: human input vs. AI are interchangeable decision sources).
 */

/** First command in [preferenceOrder] the actor's capabilities grant, or `null` if none match. */
fun selectAutomaticCommand(actor: Combatant, preferenceOrder: List<CommandKind>): CommandKind? =
    preferenceOrder.firstOrNull { it in actor.capabilities.commands }

/**
 * Deterministic regardless of `Set` iteration order (constitution Principle I):
 * lexicographically least [SkillId.value], or `null` if the actor has no skills.
 */
fun selectAutomaticSkill(actor: Combatant): SkillId? =
    actor.capabilities.skills.minByOrNull { it.value }

/**
 * The living opposing-allegiance participant with the lowest current health; ties
 * broken by earliest position in [BattleState.participants]. `null` if [actorId] is
 * unknown to [battle] or no living opposing participant exists.
 */
fun selectAutomaticTarget(battle: BattleState, actorId: CombatantId): CombatantId? {
    val actor = battle.find(actorId) ?: return null
    return battle.participants
        .filter { it.combatant.allegiance != actor.combatant.allegiance && !it.isDefeated }
        .minByOrNull { it.health.current }
        ?.combatant?.id
}
