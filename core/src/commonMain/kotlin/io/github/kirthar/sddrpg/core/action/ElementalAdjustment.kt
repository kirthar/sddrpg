package io.github.kirthar.sddrpg.core.action

import io.github.kirthar.sddrpg.core.model.Affinity
import kotlin.math.roundToInt

/** Ties round away from zero (research R5): 7.5 -> 8, -7.5 -> -8. */
fun roundHalfAwayFromZero(x: Double): Int =
    if (x >= 0) kotlin.math.floor(x + 0.5).toInt() else kotlin.math.ceil(x - 0.5).toInt()

/**
 * Fixed multiplier per affinity stance (spec FR-006, research R4), applied to the
 * *signed* health delta (negative = damage, positive = heal) so weakness/resistance/
 * immunity/absorption all fall out of one function instead of per-case branching.
 */
private fun multiplierFor(stance: Affinity): Double = when (stance) {
    Affinity.NEUTRAL -> 1.0
    Affinity.WEAKNESS -> 2.0
    Affinity.RESISTANCE -> 0.5
    Affinity.IMMUNITY -> 0.0
    Affinity.ABSORPTION -> -1.0
}

/** Applies [stance]'s multiplier to [baseDelta], rounding half away from zero. */
fun applyElementalAdjustment(baseDelta: Int, stance: Affinity): Int =
    roundHalfAwayFromZero(baseDelta * multiplierFor(stance))
