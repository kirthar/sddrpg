package io.github.kirthar.sddrpg.demo.console

import io.github.kirthar.sddrpg.core.action.ActionError
import io.github.kirthar.sddrpg.core.action.BattleState
import io.github.kirthar.sddrpg.core.event.BattleEvent
import io.github.kirthar.sddrpg.core.event.GaugeFull
import io.github.kirthar.sddrpg.core.event.LimitBreakUsed
import io.github.kirthar.sddrpg.core.event.SummonCast

/**
 * US2: every battle occurrence as plain language, never raw ids/enum names. `core`
 * never renders text itself (constitution Principle IV) -- this is the one place that
 * does. Total over [BattleEvent]'s whole sealed hierarchy, including the
 * [GaugeFull]/[LimitBreakUsed]/[SummonCast] variants spec 006 added in a separate
 * same-package file.
 */
fun BattleEvent.toDisplayText(battle: BattleState): String {
    fun name(id: io.github.kirthar.sddrpg.core.model.CombatantId): String =
        battle.find(id)?.combatant?.displayName ?: id.value

    return when (this) {
        is BattleEvent.DamageDealt -> {
            val target = name(targetId)
            val actor = actorId
            if (actor != null) "${name(actor)} deals $amount damage to $target (now $resultingHealth HP)."
            else "$target takes $amount damage (now $resultingHealth HP)."
        }
        is BattleEvent.HealingApplied -> {
            val target = name(targetId)
            val actor = actorId
            if (actor != null) "${name(actor)} heals $target for $amount (now $resultingHealth HP)."
            else "$target recovers $amount HP (now $resultingHealth HP)."
        }
        is BattleEvent.StatusEffectApplied -> "${name(combatantId)} is afflicted with ${effectId.value}."
        is BattleEvent.StatusEffectExpired -> "${effectId.value} wears off ${name(combatantId)}."
        is BattleEvent.CombatantDefeated -> "${name(combatantId)} is defeated!"
        is BattleEvent.TurnGranted -> "-- ${name(combatantId)}'s turn --"
        is BattleEvent.SynergyTriggered ->
            "${name(firstActorId)} and ${name(secondActorId)} combine their attacks on ${name(targetId)}!"
        is GaugeFull -> "${name(combatantId)}'s limit gauge is full!"
        is LimitBreakUsed -> "${name(combatantId)} unleashes ${limitBreakId.value}!"
        is SummonCast -> "${name(combatantId)} calls forth ${summonId.value}!"
    }
}

/** Plain-language rejection reasons (spec 008 FR-002): the exact rule `resolveAction` already enforces. */
fun ActionError.toDisplayText(battle: BattleState): String {
    fun name(id: io.github.kirthar.sddrpg.core.model.CombatantId): String =
        battle.find(id)?.combatant?.displayName ?: id.value

    return when (this) {
        is ActionError.DefeatedActor -> "${name(actorId)} has been defeated and cannot act."
        is ActionError.MissingCommand -> "${name(actorId)} cannot use $command."
        is ActionError.MissingSkill -> "${name(actorId)} does not know ${skillId.value}."
        is ActionError.UnknownTarget -> "No such target."
        is ActionError.TargetShapeMismatch -> "That target isn't valid for this action ($reason)."
    }
}
