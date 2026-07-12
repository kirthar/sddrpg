package io.github.kirthar.sddrpg.core.event

import io.github.kirthar.sddrpg.core.action.BattleState
import io.github.kirthar.sddrpg.core.model.CombatantId

/** The fact of a [SynergyDefinition]'s condition becoming satisfied (spec Key Entities). */
data class SynergyTrigger(
    val definition: SynergyDefinition,
    val firstActorId: CombatantId,
    val secondActorId: CombatantId,
    val targetId: CombatantId,
)

private fun BattleEvent.qualifyingActorAndTarget(): Pair<CombatantId, CombatantId>? = when (this) {
    is BattleEvent.DamageDealt -> actorId?.let { it to targetId }
    is BattleEvent.HealingApplied -> actorId?.let { it to targetId }
    else -> null
}

/**
 * Nearest-prior-match backward scan (research R3): for each newly-appended qualifying
 * event, and each synergy definition whose actor currently holds one of the two
 * required skills, scans strictly earlier log entries for the nearest qualifying event
 * on the same target, within the window, by a different combatant currently holding
 * the other required skill.
 */
fun detectSynergyTriggers(
    newEvents: List<BattleEvent>,
    log: EventLog,
    battle: BattleState,
    catalog: SynergyCatalog,
): List<SynergyTrigger> {
    if (catalog.synergies.isEmpty()) return emptyList()

    val triggers = mutableListOf<SynergyTrigger>()
    val candidateEntries = log.entries.takeLast(newEvents.size)
    val basePosition = log.entries.size - newEvents.size

    for ((offset, entry) in candidateEntries.withIndex()) {
        val (actorId, targetId) = entry.event.qualifyingActorAndTarget() ?: continue
        val actorSkills = battle.find(actorId)?.combatant?.capabilities?.skills ?: continue
        val candidatePosition = basePosition + offset

        for (synergy in catalog.synergies) {
            val partnerSkill = when {
                synergy.firstSkillId in actorSkills -> synergy.secondSkillId
                synergy.secondSkillId in actorSkills -> synergy.firstSkillId
                else -> null
            } ?: continue

            for (i in candidatePosition - 1 downTo 0) {
                val priorEntry = log.entries[i]
                if (entry.turnIndex - priorEntry.turnIndex > synergy.window) break

                val (priorActorId, priorTargetId) = priorEntry.event.qualifyingActorAndTarget() ?: continue
                if (priorTargetId != targetId || priorActorId == actorId) continue
                val priorSkills = battle.find(priorActorId)?.combatant?.capabilities?.skills ?: continue
                if (partnerSkill !in priorSkills) continue

                triggers += SynergyTrigger(synergy, priorActorId, actorId, targetId)
                break
            }
        }
    }
    return triggers
}

/** Spec FR-006: the same clamp arithmetic `applyTickDamage` (spec 004) uses, never `resolveAction` (research R4). */
fun applySynergyBonus(battle: BattleState, targetId: CombatantId, bonus: SynergyBonus): BattleState {
    val participant = battle.find(targetId) ?: return battle
    return when (bonus) {
        is SynergyBonus.BonusDamage -> {
            val newCurrent = (participant.health.current - bonus.amount).coerceIn(0, participant.health.maximum)
            val updated = participant.copy(health = participant.health.copy(current = newCurrent))
            BattleState(battle.participants.map { if (it.combatant.id == targetId) updated else it })
        }
    }
}

/** Derives the events an already-completed [applySynergyBonus] call represents (research R4/R6). */
fun eventsFromSynergyBonus(oldBattle: BattleState, targetId: CombatantId, bonus: SynergyBonus, newBattle: BattleState): List<BattleEvent> {
    val amount = when (bonus) { is SynergyBonus.BonusDamage -> bonus.amount }
    val resultingHealth = newBattle.find(targetId)!!.health.current
    val wasDefeatedBefore = oldBattle.find(targetId)!!.isDefeated
    return listOf(BattleEvent.DamageDealt(null, targetId, amount, resultingHealth)) + defeatedEvents(wasDefeatedBefore, resultingHealth, targetId)
}
