package io.github.kirthar.sddrpg.content

import io.github.kirthar.sddrpg.content.dto.ContentFileDto
import io.github.kirthar.sddrpg.content.dto.toCore
import io.github.kirthar.sddrpg.core.catalog.CatalogResult
import io.github.kirthar.sddrpg.core.catalog.validateCatalog
import io.github.kirthar.sddrpg.core.event.SynergyCatalog
import io.github.kirthar.sddrpg.core.event.SynergyCatalogResult
import io.github.kirthar.sddrpg.core.event.validateSynergyCatalog
import io.github.kirthar.sddrpg.core.limitbreak.LimitBreakCatalog
import io.github.kirthar.sddrpg.core.limitbreak.LimitBreakCatalogResult
import io.github.kirthar.sddrpg.core.limitbreak.SummonCatalog
import io.github.kirthar.sddrpg.core.limitbreak.SummonCatalogResult
import io.github.kirthar.sddrpg.core.limitbreak.validateLimitBreakCatalog
import io.github.kirthar.sddrpg.core.limitbreak.validateSummonCatalog
import io.github.kirthar.sddrpg.core.status.StatusEffectCatalog
import io.github.kirthar.sddrpg.core.status.StatusEffectCatalogResult
import io.github.kirthar.sddrpg.core.status.validateStatusEffectCatalog
import kotlinx.serialization.SerializationException
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

private val contentJson = Json { ignoreUnknownKeys = false }

/**
 * Parses [text] as a [ContentFileDto], maps every DTO to its `core` type, runs every
 * existing `validate*Catalog` function unconditionally, and (spec 007 US2) runs
 * cross-catalog reference checks -- accumulating every problem found into one
 * report (research R1/R2/R4). Never modifies `resolveAction`, any spec 001-006
 * catalog type, or their validation functions.
 */
fun loadContentPack(text: String): ContentLoadResult {
    val dto = try {
        contentJson.decodeFromString<ContentFileDto>(text)
    } catch (e: SerializationException) {
        return ContentLoadResult.Invalid(listOf(ContentProblem.MalformedContent(e.message ?: "malformed content")))
    }

    val statusEffects = StatusEffectCatalog(dto.statusEffects.map { it.toCore() })
    val synergies = SynergyCatalog(dto.synergies.map { it.toCore() })
    val limitBreaks = LimitBreakCatalog(dto.limitBreaks.map { it.toCore() })
    val summons = SummonCatalog(dto.summons.map { it.toCore() })

    val problems = mutableListOf<ContentProblem>()

    val catalogResult = validateCatalog(dto.catalog)
    if (catalogResult is CatalogResult.Invalid) {
        problems += catalogResult.errors.map { ContentProblem.CoreCatalogProblem(it) }
    }

    val statusResult = validateStatusEffectCatalog(statusEffects)
    if (statusResult is StatusEffectCatalogResult.Invalid) {
        problems += statusResult.errors.map { ContentProblem.StatusEffectProblem(it) }
    }

    val synergyResult = validateSynergyCatalog(synergies)
    if (synergyResult is SynergyCatalogResult.Invalid) {
        problems += synergyResult.errors.map { ContentProblem.SynergyProblem(it) }
    }

    val limitBreakResult = validateLimitBreakCatalog(limitBreaks)
    if (limitBreakResult is LimitBreakCatalogResult.Invalid) {
        problems += limitBreakResult.errors.map { ContentProblem.LimitBreakProblem(it) }
    }

    val summonResult = validateSummonCatalog(summons)
    if (summonResult is SummonCatalogResult.Invalid) {
        problems += summonResult.errors.map { ContentProblem.SummonProblem(it) }
    }

    problems += validateCrossCatalogReferences(dto.catalog, statusEffects, synergies, limitBreaks, summons)

    if (problems.isNotEmpty()) {
        return ContentLoadResult.Invalid(problems)
    }

    // problems is empty here, so catalogResult can't have been Invalid (its errors would be non-empty).
    check(catalogResult is CatalogResult.Valid)
    return ContentLoadResult.Valid(
        ContentPack(catalogResult.catalog, statusEffects, synergies, limitBreaks, summons)
    )
}
