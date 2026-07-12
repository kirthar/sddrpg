package io.github.kirthar.sddrpg.core.status

import io.github.kirthar.sddrpg.core.model.CombatantId

/** One application of a [StatusEffectDefinition] to one combatant (spec Key Entities). */
data class ActiveEffect(val effectId: StatusEffectId, val remainingDuration: Int)

/**
 * Battle-time tracking of every combatant's currently active effects (research R1
 * pattern: additive alongside `BattleState`, never inside it). At most one
 * [ActiveEffect] per distinct `effectId` per combatant (spec FR-008).
 */
data class StatusEffectState(val active: Map<CombatantId, List<ActiveEffect>> = emptyMap())
