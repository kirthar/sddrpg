package io.github.kirthar.sddrpg.core.status

import io.github.kirthar.sddrpg.core.combatant.Combatant
import io.github.kirthar.sddrpg.core.model.StatBlock

/**
 * Wraps a [Combatant] with modifier-adjusted [stats] via Kotlin interface delegation
 * (research R1): every other member forwards to [base] unchanged. `Combatant` is a
 * plain, non-sealed public interface (spec 001), so this needs no change to spec
 * 001's files — only stat *reads* differ for the lifetime of one resolution call, the
 * original [base] combatant inside the caller's `BattleState` is never touched.
 */
internal data class EffectiveCombatant(
    private val base: Combatant,
    override val stats: StatBlock,
) : Combatant by base
