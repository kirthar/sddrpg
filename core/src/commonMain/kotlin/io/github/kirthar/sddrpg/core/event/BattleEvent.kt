package io.github.kirthar.sddrpg.core.event

import io.github.kirthar.sddrpg.core.model.CombatantId
import io.github.kirthar.sddrpg.core.status.StatusEffectId

/** Identifies one data-driven synergy definition (spec FR-004); see SynergyDefinition.kt. */
data class SynergyId(val value: String)

/**
 * A structured record of one battle occurrence (spec FR-001/FR-002/FR-009). Never
 * freeform text -- kind-appropriate identifying data only (constitution Principle IV).
 */
sealed interface BattleEvent {
    /** [actorId] is null for damage-over-time ticks and synergy bonuses, which are
     *  genuinely actor-less (research R2/R4) -- same shape, not the same fields populated. */
    data class DamageDealt(val actorId: CombatantId?, val targetId: CombatantId, val amount: Int, val resultingHealth: Int) : BattleEvent
    data class HealingApplied(val actorId: CombatantId?, val targetId: CombatantId, val amount: Int, val resultingHealth: Int) : BattleEvent
    data class StatusEffectApplied(val combatantId: CombatantId, val effectId: StatusEffectId) : BattleEvent
    data class StatusEffectExpired(val combatantId: CombatantId, val effectId: StatusEffectId) : BattleEvent
    data class CombatantDefeated(val combatantId: CombatantId) : BattleEvent
    data class TurnGranted(val combatantId: CombatantId) : BattleEvent
    data class SynergyTriggered(
        val synergyId: SynergyId,
        val firstActorId: CombatantId,
        val secondActorId: CombatantId,
        val targetId: CombatantId,
    ) : BattleEvent
}

/** One [BattleEvent] stamped with the turn it occurred during (research R2). */
data class LoggedEvent(val turnIndex: Int, val event: BattleEvent)

/**
 * The ordered, append-only accumulation of [BattleEvent]s for one battle (spec
 * FR-003). [append] is the one place turn-index stamping happens -- every
 * `eventsFromX` derivation function stays turn-agnostic (research R2).
 */
data class EventLog(val entries: List<LoggedEvent> = emptyList()) {
    val turnsGranted: Int get() = entries.count { it.event is BattleEvent.TurnGranted }

    fun append(newEvents: List<BattleEvent>): EventLog {
        var turnIndex = turnsGranted
        val logged = newEvents.map { event ->
            LoggedEvent(turnIndex, event).also { if (event is BattleEvent.TurnGranted) turnIndex += 1 }
        }
        return EventLog(entries + logged)
    }
}
