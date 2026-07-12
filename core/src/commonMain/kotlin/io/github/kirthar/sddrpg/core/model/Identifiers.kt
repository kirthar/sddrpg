package io.github.kirthar.sddrpg.core.model

import kotlin.jvm.JvmInline
import kotlinx.serialization.Serializable

/**
 * Typed identifiers for every definition namespace (research.md R2). All are value
 * classes over String so they serialize as plain JSON strings (including as map keys)
 * while keeping cross-references compile-time safe.
 */

@Serializable @JvmInline value class StatId(val value: String)

@Serializable @JvmInline value class ElementId(val value: String)

@Serializable @JvmInline value class ClassId(val value: String)

@Serializable @JvmInline value class ArchetypeId(val value: String)

@Serializable @JvmInline value class SkillId(val value: String)

@Serializable @JvmInline value class LimitBreakId(val value: String)

@Serializable @JvmInline value class AiProfileId(val value: String)

@Serializable @JvmInline value class EquipmentCategoryId(val value: String)

@Serializable @JvmInline value class CharacterId(val value: String)

@Serializable @JvmInline value class EnemyId(val value: String)

/** Battle-instance identity; assigned deterministically by roster insertion order (R7). */
@Serializable @JvmInline value class CombatantId(val value: String)
