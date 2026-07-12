package io.github.kirthar.sddrpg.content.dto

import io.github.kirthar.sddrpg.core.catalog.Catalog
import kotlinx.serialization.Serializable

/**
 * The single top-level shape a content file decodes into (research R2). Every field
 * defaults to empty so an omitted catalog kind loads successfully as empty (spec
 * FR-006). [catalog] is spec 001's own already-serializable type, embedded directly
 * -- no DTO of its own needed.
 */
@Serializable
data class ContentFileDto(
    val catalog: Catalog = Catalog(),
    val statusEffects: List<StatusEffectDefinitionDto> = emptyList(),
    val synergies: List<SynergyDefinitionDto> = emptyList(),
    val limitBreaks: List<LimitBreakDefinitionDto> = emptyList(),
    val summons: List<SummonDefinitionDto> = emptyList(),
)
