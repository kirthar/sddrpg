package io.github.kirthar.sddrpg.core.status

import kotlin.jvm.JvmInline

/** Identifier for a status effect definition (research R2's identifier pattern, spec 001). */
@JvmInline
value class StatusEffectId(val value: String)

/**
 * A status effect as authored data (spec FR-001): identifier, display name, one
 * closed [EffectKind], and a default duration.
 */
data class StatusEffectDefinition(
    val id: StatusEffectId,
    val displayName: String,
    val kind: EffectKind,
    val duration: Int,
)

/**
 * Independent of spec 001's `Catalog` (research R4) — nothing about status effects
 * needs cross-validating against classes/characters/enemies at this stage.
 */
data class StatusEffectCatalog(val effects: List<StatusEffectDefinition> = emptyList())

/** A rejected status-effect catalog defect, always naming the offending definition (mirrors spec 001's CatalogError). */
sealed interface StatusEffectCatalogError {
    data class DuplicateId(val id: String) : StatusEffectCatalogError
    data class NonPositiveDuration(val definitionId: String, val duration: Int) : StatusEffectCatalogError
}

sealed interface StatusEffectCatalogResult {
    data class Valid(val catalog: StatusEffectCatalog) : StatusEffectCatalogResult
    data class Invalid(val errors: List<StatusEffectCatalogError>) : StatusEffectCatalogResult
}

/** Accumulate-all validation (research R4), mirroring spec 001's `validateCatalog`. */
fun validateStatusEffectCatalog(catalog: StatusEffectCatalog): StatusEffectCatalogResult {
    val errors = mutableListOf<StatusEffectCatalogError>()

    catalog.effects.groupBy { it.id }.filterValues { it.size > 1 }
        .forEach { (id, _) -> errors += StatusEffectCatalogError.DuplicateId(id.value) }

    catalog.effects.filter { it.duration <= 0 }
        .forEach { errors += StatusEffectCatalogError.NonPositiveDuration(it.id.value, it.duration) }

    return if (errors.isEmpty()) StatusEffectCatalogResult.Valid(catalog) else StatusEffectCatalogResult.Invalid(errors)
}
