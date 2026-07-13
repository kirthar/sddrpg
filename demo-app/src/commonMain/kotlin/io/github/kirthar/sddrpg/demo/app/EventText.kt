package io.github.kirthar.sddrpg.demo.app

import io.github.kirthar.sddrpg.core.action.ActionError
import io.github.kirthar.sddrpg.core.action.BattleState
import io.github.kirthar.sddrpg.core.event.BattleEvent
import io.github.kirthar.sddrpg.core.event.GaugeFull
import io.github.kirthar.sddrpg.core.event.LimitBreakUsed
import io.github.kirthar.sddrpg.core.event.SummonCast
import io.github.kirthar.sddrpg.core.limitbreak.LimitBreakError
import io.github.kirthar.sddrpg.core.limitbreak.SummonError
import io.github.kirthar.sddrpg.core.model.CombatantId

/**
 * US2: every battle occurrence as plain language, never raw ids/enum names -- the
 * commonMain equivalent of spec 008's console rendering (that file is a JVM-only
 * demo-console artifact this multiplatform module cannot depend on). Total over
 * [BattleEvent]'s whole sealed hierarchy, including the [GaugeFull]/[LimitBreakUsed]/
 * [SummonCast] variants spec 006 added in a separate same-package file.
 */
fun BattleEvent.toDisplayText(battle: BattleState): String {
    fun name(id: CombatantId): String = battle.find(id)?.combatant?.displayName ?: id.value

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

/** Plain-language rejection reasons: the exact rule `resolveAction` already enforces (FR-002). */
fun ActionError.toDisplayText(battle: BattleState): String {
    fun name(id: CombatantId): String = battle.find(id)?.combatant?.displayName ?: id.value

    return when (this) {
        is ActionError.DefeatedActor -> "${name(actorId)} has been defeated and cannot act."
        is ActionError.MissingCommand -> "${name(actorId)} cannot use $command."
        is ActionError.MissingSkill -> "${name(actorId)} does not know ${skillId.value}."
        is ActionError.UnknownTarget -> "No such target."
        is ActionError.TargetShapeMismatch -> "That target isn't valid for this action ($reason)."
    }
}

/** Rejection reasons for spec 006's limit-break gate, same plain-language convention. */
fun LimitBreakError.toDisplayText(battle: BattleState): String {
    fun name(id: CombatantId): String = battle.find(id)?.combatant?.displayName ?: id.value

    return when (this) {
        is LimitBreakError.NoActiveLimitBreak -> "${name(actorId)} has no limit break available."
        is LimitBreakError.GaugeNotFull -> "${name(actorId)}'s limit gauge isn't full yet ($current/$threshold)."
        is LimitBreakError.ActionRejected -> underlying.toDisplayText(battle)
    }
}

/** Rejection reasons for spec 006's summon gate, same plain-language convention. */
fun SummonError.toDisplayText(battle: BattleState): String {
    fun name(id: CombatantId): String = battle.find(id)?.combatant?.displayName ?: id.value

    return when (this) {
        is SummonError.UnknownSummon -> "No summon named ${summonId.value} exists."
        is SummonError.InsufficientResource -> "${name(actorId)} needs $required MP but only has $available."
        is SummonError.ActionRejected -> underlying.toDisplayText(battle)
    }
}
