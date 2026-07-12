package io.github.kirthar.sddrpg.content

import io.github.kirthar.sddrpg.core.catalog.CatalogError
import io.github.kirthar.sddrpg.core.catalog.ValidatedCatalog
import io.github.kirthar.sddrpg.core.event.SynergyCatalog
import io.github.kirthar.sddrpg.core.event.SynergyCatalogError
import io.github.kirthar.sddrpg.core.limitbreak.LimitBreakCatalog
import io.github.kirthar.sddrpg.core.limitbreak.LimitBreakCatalogError
import io.github.kirthar.sddrpg.core.limitbreak.SummonCatalog
import io.github.kirthar.sddrpg.core.limitbreak.SummonCatalogError
import io.github.kirthar.sddrpg.core.status.StatusEffectCatalog
import io.github.kirthar.sddrpg.core.status.StatusEffectCatalogError

/** The single, complete, validated bundle a successful load produces (spec Key Entities). */
data class ContentPack(
    val catalog: ValidatedCatalog,
    val statusEffects: StatusEffectCatalog,
    val synergies: SynergyCatalog,
    val limitBreaks: LimitBreakCatalog,
    val summons: SummonCatalog,
)

/**
 * One reported validation failure (spec Key Entities). Each variant wraps the real,
 * already-typed error from its source rather than stringifying it, preserving
 * structure.
 */
sealed interface ContentProblem {
    data class MalformedContent(val message: String) : ContentProblem
    data class CoreCatalogProblem(val error: CatalogError) : ContentProblem
    data class StatusEffectProblem(val error: StatusEffectCatalogError) : ContentProblem
    data class SynergyProblem(val error: SynergyCatalogError) : ContentProblem
    data class LimitBreakProblem(val error: LimitBreakCatalogError) : ContentProblem
    data class SummonProblem(val error: SummonCatalogError) : ContentProblem
    data class DanglingReference(val fromCatalog: String, val field: String, val missingId: String) : ContentProblem
}

/** Accumulate-all result, mirroring every `*CatalogResult` this project has built. */
sealed interface ContentLoadResult {
    data class Valid(val pack: ContentPack) : ContentLoadResult
    data class Invalid(val problems: List<ContentProblem>) : ContentLoadResult
}
