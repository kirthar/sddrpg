package io.github.kirthar.sddrpg.core.action

import io.github.kirthar.sddrpg.core.combatant.Combatant
import io.github.kirthar.sddrpg.core.combatant.Roster
import io.github.kirthar.sddrpg.core.model.CombatantId
import io.github.kirthar.sddrpg.core.model.CoreStats

/**
 * Current-vs-maximum health for one participant (spec FR-011). Introduced by this
 * feature to resolve the invariant spec 001 declared but deferred ("enforced once
 * battle state exists"), without adding any mutable field to [Combatant] itself.
 */
data class HealthTrack(val current: Int, val maximum: Int)

/**
 * A [Combatant] as it participates in one battle: the combatant's spec-001 data is
 * read-only here, [health] is what this feature actually changes.
 */
data class BattleCombatant(val combatant: Combatant, val health: HealthTrack) {
    val isDefeated: Boolean get() = health.current == 0
}

/**
 * Immutable battle-time state (research R1): every resolution produces a *new*
 * `BattleState` rather than mutating this one (constitution Principle I).
 */
data class BattleState(val participants: List<BattleCombatant>) {
    fun find(id: CombatantId): BattleCombatant? = participants.firstOrNull { it.combatant.id == id }
}

/** Snapshots a [Roster] into starting battle state: current health equals maximum for everyone. */
fun Roster.toBattleState(): BattleState = BattleState(
    combatants.map { combatant ->
        val maxHp = combatant.stats[CoreStats.HP]
        BattleCombatant(combatant, HealthTrack(current = maxHp, maximum = maxHp))
    }
)
