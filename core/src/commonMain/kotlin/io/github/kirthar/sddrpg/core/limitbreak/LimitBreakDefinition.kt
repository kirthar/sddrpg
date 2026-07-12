package io.github.kirthar.sddrpg.core.limitbreak

import io.github.kirthar.sddrpg.core.action.DamageFormula
import io.github.kirthar.sddrpg.core.action.EffectKind
import io.github.kirthar.sddrpg.core.action.TargetingShape
import io.github.kirthar.sddrpg.core.model.ElementId
import io.github.kirthar.sddrpg.core.model.LimitBreakId

/**
 * A limit break as authored data (spec FR-003): spec 001's `ClassDefinition.limitBreaks`
 * only references the bare id; this feature gives it real behavior.
 */
data class LimitBreakDefinition(
    val id: LimitBreakId,
    val threshold: Int,
    val effectKind: EffectKind,
    val formula: DamageFormula,
    val targeting: TargetingShape,
    val element: ElementId? = null,
)

/** Independent of spec 001's `Catalog` (mirrors spec 004/005's own small catalogs). */
data class LimitBreakCatalog(val limitBreaks: List<LimitBreakDefinition> = emptyList())

/** A rejected limit break catalog defect, always naming the offending definition (mirrors spec 001/004/005's *CatalogError). */
sealed interface LimitBreakCatalogError {
    data class DuplicateId(val id: String) : LimitBreakCatalogError
    data class NonPositiveThreshold(val definitionId: String, val threshold: Int) : LimitBreakCatalogError
}

sealed interface LimitBreakCatalogResult {
    data class Valid(val catalog: LimitBreakCatalog) : LimitBreakCatalogResult
    data class Invalid(val errors: List<LimitBreakCatalogError>) : LimitBreakCatalogResult
}

/** Accumulate-all validation, mirroring spec 001/004/005's `validate*Catalog`. */
fun validateLimitBreakCatalog(catalog: LimitBreakCatalog): LimitBreakCatalogResult {
    val errors = mutableListOf<LimitBreakCatalogError>()

    catalog.limitBreaks.groupBy { it.id }.filterValues { it.size > 1 }
        .forEach { (id, _) -> errors += LimitBreakCatalogError.DuplicateId(id.value) }

    catalog.limitBreaks.filter { it.threshold <= 0 }
        .forEach { errors += LimitBreakCatalogError.NonPositiveThreshold(it.id.value, it.threshold) }

    return if (errors.isEmpty()) LimitBreakCatalogResult.Valid(catalog) else LimitBreakCatalogResult.Invalid(errors)
}
