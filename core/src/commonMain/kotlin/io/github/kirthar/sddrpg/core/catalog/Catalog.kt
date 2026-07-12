package io.github.kirthar.sddrpg.core.catalog

import io.github.kirthar.sddrpg.core.model.AiProfileId
import io.github.kirthar.sddrpg.core.model.ArchetypeId
import io.github.kirthar.sddrpg.core.model.CharacterId
import io.github.kirthar.sddrpg.core.model.ClassId
import io.github.kirthar.sddrpg.core.model.ElementId
import io.github.kirthar.sddrpg.core.model.EnemyId
import io.github.kirthar.sddrpg.core.model.EquipmentCategoryId
import io.github.kirthar.sddrpg.core.model.LimitBreakId
import io.github.kirthar.sddrpg.core.model.SkillId
import io.github.kirthar.sddrpg.core.model.StatId
import kotlinx.serialization.Serializable

/**
 * The raw, serializable aggregate of authored content (spec FR-014). `known*` sets
 * declare identifiers whose full definitions belong to later features (skills, limit
 * breaks, AI profiles) so reference validation is total today and referencing content
 * never changes when those features land (data-model.md).
 */
@Serializable
data class Catalog(
    val customStats: Set<StatId> = emptySet(),
    val elements: Set<ElementId> = emptySet(),
    val equipmentCategories: Set<EquipmentCategoryId> = emptySet(),
    val knownSkills: Set<SkillId> = emptySet(),
    val knownLimitBreaks: Set<LimitBreakId> = emptySet(),
    val knownAiProfiles: Set<AiProfileId> = emptySet(),
    val classes: List<ClassDefinition> = emptyList(),
    val archetypes: List<ArchetypeDefinition> = emptyList(),
    val characters: List<CharacterDefinition> = emptyList(),
    val enemies: List<EnemyDefinition> = emptyList(),
)

/**
 * A catalog whose cross-references have all been validated (spec FR-013). Only
 * obtainable through [validateCatalog]; lookups never fail for ids originating from
 * the same catalog.
 */
interface ValidatedCatalog {
    fun classDefinition(id: ClassId): ClassDefinition
    fun archetype(id: ArchetypeId): ArchetypeDefinition
    fun character(id: CharacterId): CharacterDefinition
    fun enemy(id: EnemyId): EnemyDefinition
    val customStats: Set<StatId>
    val elements: Set<ElementId>
}

internal class ValidatedCatalogImpl(raw: Catalog) : ValidatedCatalog {
    private val classes = raw.classes.associateBy { it.id }
    private val archetypes = raw.archetypes.associateBy { it.id }
    private val characters = raw.characters.associateBy { it.id }
    private val enemies = raw.enemies.associateBy { it.id }

    override fun classDefinition(id: ClassId): ClassDefinition = classes.getValue(id)
    override fun archetype(id: ArchetypeId): ArchetypeDefinition = archetypes.getValue(id)
    override fun character(id: CharacterId): CharacterDefinition = characters.getValue(id)
    override fun enemy(id: EnemyId): EnemyDefinition = enemies.getValue(id)
    override val customStats: Set<StatId> = raw.customStats
    override val elements: Set<ElementId> = raw.elements
}
