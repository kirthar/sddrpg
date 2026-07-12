package io.github.kirthar.sddrpg.core.action

/**
 * Declared cardinality/scope of an action's valid targets (spec FR-007). "Ally" shapes
 * include the actor itself (research R6); [SELF] is the stricter shape that forces the
 * target set to be exactly the actor with no choice.
 */
enum class TargetingShape {
    SINGLE_ALLY,
    SINGLE_ENEMY,
    SELF,
    ALL_ALLIES,
    ALL_ENEMIES,
    ALL,
}
