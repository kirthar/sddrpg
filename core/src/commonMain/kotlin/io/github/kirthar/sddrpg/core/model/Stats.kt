package io.github.kirthar.sddrpg.core.model

import io.github.kirthar.sddrpg.core.catalog.ClassDefinition
import kotlin.jvm.JvmInline

/**
 * The engine-defined core stat set (clarification Q1, spec FR-008): guaranteed present
 * on every combatant, so formulas and scheduling may always assume them. Catalogs may
 * declare additional custom [StatId]s.
 */
object CoreStats {
    val HP = StatId("hp")
    val MP = StatId("mp")
    val ATTACK = StatId("attack")
    val DEFENSE = StatId("defense")
    val MAGIC = StatId("magic")
    val RESISTANCE = StatId("resistance")
    val SPEED = StatId("speed")
    val LUCK = StatId("luck")

    val ALL: Set<StatId> = setOf(HP, MP, ATTACK, DEFENSE, MAGIC, RESISTANCE, SPEED, LUCK)
}

/**
 * A complete, bounded stat block (spec FR-008): contains every core stat, all values
 * non-negative, and may carry declared custom stats. Definitions carry raw maps; a
 * StatBlock is only constructed for validated data or derived values, so its invariants
 * hold everywhere downstream. The current-vs-maximum resource rule is enforced by
 * battle-state features, not here.
 */
@JvmInline
value class StatBlock(private val values: Map<StatId, Int>) {

    init {
        val missing = CoreStats.ALL - values.keys
        require(missing.isEmpty()) { "StatBlock is missing core stats: $missing" }
        val negative = values.filterValues { it < 0 }
        require(negative.isEmpty()) { "StatBlock has negative values: $negative" }
    }

    /** Value of [id]; throws if the stat is not present in this block. */
    operator fun get(id: StatId): Int = values.getValue(id)

    val statIds: Set<StatId> get() = values.keys

    fun toMap(): Map<StatId, Int> = values
}

/**
 * Effective stats of a character of [classDef] at [level] (spec FR-008a): stats with a
 * growth curve are derived from the curve, the rest keep their base value. Pure and
 * deterministic — same inputs produce the same block on every platform.
 */
fun statsAt(classDef: ClassDefinition, level: Int, baseStats: StatBlock): StatBlock {
    require(level >= 1) { "level must be >= 1, was $level" }
    val derived = baseStats.toMap().toMutableMap()
    for ((statId, curve) in classDef.growth) {
        derived[statId] = curve.at(level)
    }
    return StatBlock(derived)
}
