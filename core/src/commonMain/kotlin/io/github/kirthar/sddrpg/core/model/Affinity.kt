package io.github.kirthar.sddrpg.core.model

import kotlinx.serialization.Serializable

/**
 * Per-element stance of a combatant (spec FR-009). The list of elements themselves is
 * catalog data; this scale is the engine capability content selects from. The damage
 * arithmetic that applies these stances belongs to the damage-resolution feature.
 */
@Serializable
enum class Affinity {
    WEAKNESS,
    NEUTRAL,
    RESISTANCE,
    IMMUNITY,
    ABSORPTION,
}

/** Total lookup: a combatant with no declared affinity for an element is NEUTRAL to it. */
fun Map<ElementId, Affinity>.affinityTo(element: ElementId): Affinity =
    this[element] ?: Affinity.NEUTRAL
