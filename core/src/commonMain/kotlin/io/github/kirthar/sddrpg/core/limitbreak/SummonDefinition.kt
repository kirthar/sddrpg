package io.github.kirthar.sddrpg.core.limitbreak

import io.github.kirthar.sddrpg.core.action.DamageFormula
import io.github.kirthar.sddrpg.core.action.EffectKind
import io.github.kirthar.sddrpg.core.action.TargetingShape
import io.github.kirthar.sddrpg.core.model.ElementId

/** Identifier for a summon definition (own small identifier, spec 001 has no summon id type). */
data class SummonId(val value: String)

/**
 * A summon as authored data (spec FR-007): a class-agnostic burst effect gated by a
 * resource cost, resolved through spec 002's existing action/damage resolution.
 */
data class SummonDefinition(
    val id: SummonId,
    val cost: Int,
    val effectKind: EffectKind,
    val formula: DamageFormula,
    val targeting: TargetingShape,
    val element: ElementId? = null,
)

/** Independent of spec 001's `Catalog` (mirrors spec 004/005's own small catalogs). */
data class SummonCatalog(val summons: List<SummonDefinition> = emptyList())

/** A rejected summon catalog defect, always naming the offending definition (mirrors spec 001/004/005's *CatalogError). */
sealed interface SummonCatalogError {
    data class DuplicateId(val id: String) : SummonCatalogError
    data class NegativeCost(val definitionId: String, val cost: Int) : SummonCatalogError
}

sealed interface SummonCatalogResult {
    data class Valid(val catalog: SummonCatalog) : SummonCatalogResult
    data class Invalid(val errors: List<SummonCatalogError>) : SummonCatalogResult
}

/** Accumulate-all validation, mirroring spec 001/004/005's `validate*Catalog`. */
fun validateSummonCatalog(catalog: SummonCatalog): SummonCatalogResult {
    val errors = mutableListOf<SummonCatalogError>()

    catalog.summons.groupBy { it.id }.filterValues { it.size > 1 }
        .forEach { (id, _) -> errors += SummonCatalogError.DuplicateId(id.value) }

    catalog.summons.filter { it.cost < 0 }
        .forEach { errors += SummonCatalogError.NegativeCost(it.id.value, it.cost) }

    return if (errors.isEmpty()) SummonCatalogResult.Valid(catalog) else SummonCatalogResult.Invalid(errors)
}
