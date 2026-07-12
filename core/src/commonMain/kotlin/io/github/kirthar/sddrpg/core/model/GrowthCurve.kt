package io.github.kirthar.sddrpg.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Stat growth over levels (spec FR-011, research R5). The set of curve kinds is a
 * closed engine capability selected by content through the `type` discriminator —
 * adding a kind is an engine extension, never a content rewrite.
 */
@Serializable
sealed interface GrowthCurve {

    /** Stat value at [level] (1-based). Deterministic; never negative. */
    fun at(level: Int): Int

    @Serializable
    @SerialName("linear")
    data class Linear(val base: Int, val perLevel: Int) : GrowthCurve {
        override fun at(level: Int): Int {
            requireValidLevel(level)
            return (base + perLevel * (level - 1)).coerceAtLeast(0)
        }
    }

    @Serializable
    @SerialName("table")
    data class Table(val values: List<Int>) : GrowthCurve {
        init {
            require(values.isNotEmpty()) { "Table growth curve needs at least one value" }
        }

        /** Levels beyond the last entry clamp to it. */
        override fun at(level: Int): Int {
            requireValidLevel(level)
            return values[minOf(level - 1, values.lastIndex)].coerceAtLeast(0)
        }
    }
}

private fun requireValidLevel(level: Int) {
    require(level >= 1) { "level must be >= 1, was $level" }
}
