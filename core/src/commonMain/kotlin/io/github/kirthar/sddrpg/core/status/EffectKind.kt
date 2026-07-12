package io.github.kirthar.sddrpg.core.status

import io.github.kirthar.sddrpg.core.model.StatId

/**
 * Closed, engine-recognized category of status effect (spec FR-001, research R2):
 * content picks a kind and its parameters, engine code never grows a new class per
 * named effect (constitution Principle II).
 */
sealed interface EffectKind {
    data class StatModifier(val statId: StatId, val delta: Int) : EffectKind
    data object Incapacitate : EffectKind
    data class DamageOverTime(val amountPerTick: Int) : EffectKind
}
