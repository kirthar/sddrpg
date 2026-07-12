package io.github.kirthar.sddrpg.core.catalog

import io.github.kirthar.sddrpg.core.model.Affinity
import io.github.kirthar.sddrpg.core.model.AiProfileId
import io.github.kirthar.sddrpg.core.model.ArchetypeId
import io.github.kirthar.sddrpg.core.model.CharacterId
import io.github.kirthar.sddrpg.core.model.ClassId
import io.github.kirthar.sddrpg.core.model.ElementId
import io.github.kirthar.sddrpg.core.model.EnemyId
import io.github.kirthar.sddrpg.core.model.EquipmentCategoryId
import io.github.kirthar.sddrpg.core.model.GrowthCurve
import io.github.kirthar.sddrpg.core.model.LimitBreakId
import io.github.kirthar.sddrpg.core.model.SkillId
import io.github.kirthar.sddrpg.core.model.StatId
import kotlinx.serialization.Serializable

/**
 * Battle command categories a class may permit (spec FR-004). Only the gate lives
 * here; command behavior is later features' scope.
 */
@Serializable
enum class CommandKind {
    ATTACK,
    SKILL,
    MAGIC,
    DEFEND,
    ITEM,
    SUMMON,
}

/**
 * A character class as authored data (spec FR-004): everything a class grants is
 * referenced by id and interpreted by the engine — never subclassed in code
 * (constitution Principle II).
 */
@Serializable
data class ClassDefinition(
    val id: ClassId,
    val displayName: String,
    val skills: Set<SkillId> = emptySet(),
    val commands: Set<CommandKind>,
    val growth: Map<StatId, GrowthCurve> = emptyMap(),
    val equipmentCategories: Set<EquipmentCategoryId> = emptySet(),
    val limitBreaks: Set<LimitBreakId> = emptySet(),
)

/**
 * A playable character as authored data (spec FR-005). [classId] is the mutable
 * progression point (research R1): job change swaps this reference and every derived
 * capability follows; the character itself is never redefined.
 */
@Serializable
data class CharacterDefinition(
    val id: CharacterId,
    val displayName: String,
    val classId: ClassId,
    val baseStats: Map<StatId, Int>,
    val affinities: Map<ElementId, Affinity> = emptyMap(),
)

/**
 * An enemy role as authored data (spec FR-006): selects the AI behavior profile and
 * the reward/difficulty tier shared by all enemies of this archetype.
 */
@Serializable
data class ArchetypeDefinition(
    val id: ArchetypeId,
    val displayName: String,
    val aiProfile: AiProfileId,
    val rewardTier: Int,
)

/**
 * An enemy as authored data (spec FR-006): archetype reference plus individual stats,
 * affinities, and an optional skill set listing the actions available to it.
 */
@Serializable
data class EnemyDefinition(
    val id: EnemyId,
    val displayName: String,
    val archetypeId: ArchetypeId,
    val skills: Set<SkillId> = emptySet(),
    val stats: Map<StatId, Int>,
    val affinities: Map<ElementId, Affinity> = emptyMap(),
)
