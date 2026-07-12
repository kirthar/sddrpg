package io.github.kirthar.sddrpg.core.action

import io.github.kirthar.sddrpg.core.model.CoreStats
import io.github.kirthar.sddrpg.core.model.StatBlock

/** Whether an action's magnitude decreases or increases the target's health (spec Key Entities). */
enum class EffectKind { DAMAGE, HEAL }

/**
 * Substitutable damage/heal calculation (Strategy, spec FR-004). Always returns a
 * magnitude ≥ 0 — negative raw results floor at 0 (research R3), the engine's answer
 * to the spec's "formula may produce a negative result" edge case.
 */
sealed interface DamageFormula {
    fun rawMagnitude(actorStats: StatBlock, targetStats: StatBlock): Int

    data class Physical(val power: Int) : DamageFormula {
        override fun rawMagnitude(actorStats: StatBlock, targetStats: StatBlock): Int =
            (power + actorStats[CoreStats.ATTACK] - targetStats[CoreStats.DEFENSE]).coerceAtLeast(0)
    }

    data class Magical(val power: Int) : DamageFormula {
        override fun rawMagnitude(actorStats: StatBlock, targetStats: StatBlock): Int =
            (power + actorStats[CoreStats.MAGIC] - targetStats[CoreStats.RESISTANCE]).coerceAtLeast(0)
    }

    /** Ignores stats entirely — items and DEFEND authored with a flat, pre-set effect. */
    data class Fixed(val amount: Int) : DamageFormula {
        override fun rawMagnitude(actorStats: StatBlock, targetStats: StatBlock): Int = amount.coerceAtLeast(0)
    }
}
