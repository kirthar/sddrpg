package io.github.kirthar.sddrpg.core.catalog

import io.github.kirthar.sddrpg.core.model.Affinity
import io.github.kirthar.sddrpg.core.model.ClassId
import io.github.kirthar.sddrpg.core.model.CoreStats
import io.github.kirthar.sddrpg.core.model.ElementId
import io.github.kirthar.sddrpg.core.model.GrowthCurve
import io.github.kirthar.sddrpg.core.model.StatId

/** Result of whole-catalog validation (research R6): valid XOR the complete error list. */
sealed interface CatalogResult {
    data class Valid(val catalog: ValidatedCatalog) : CatalogResult

    /** [errors] is exhaustive — every defect in the catalog, never just the first. */
    data class Invalid(val errors: List<CatalogError>) : CatalogResult
}

/** A single catalog defect, always naming the offending definition (spec FR-013/SC-002). */
sealed interface CatalogError {
    data class DuplicateId(val namespace: String, val id: String) : CatalogError
    data class UnknownReference(val fromDefinition: String, val field: String, val missingId: String) : CatalogError
    data class IncompleteStatBlock(val definitionId: String, val missingStatIds: Set<StatId>) : CatalogError
    data class UndeclaredCustomStat(val definitionId: String, val statId: StatId) : CatalogError
    data class CoreStatRedeclared(val statId: StatId) : CatalogError
    data class EmptyCommands(val classId: ClassId) : CatalogError
    data class NegativeValue(val definitionId: String, val field: String, val value: Int) : CatalogError
}

/**
 * Two-phase load, phase B (research R6): validates every cross-reference and invariant
 * of an already-deserialized [Catalog], accumulating all errors. Deterministic and
 * order-independent for the same catalog content.
 */
fun validateCatalog(raw: Catalog): CatalogResult {
    val errors = mutableListOf<CatalogError>()

    raw.customStats.filter { it in CoreStats.ALL }
        .forEach { errors += CatalogError.CoreStatRedeclared(it) }
    val declaredStats = CoreStats.ALL + raw.customStats

    fun <T, K> reportDuplicates(items: List<T>, namespace: String, idOf: (T) -> K, keyOf: (K) -> String) {
        items.groupBy(idOf).filterValues { it.size > 1 }
            .forEach { (id, _) -> errors += CatalogError.DuplicateId(namespace, keyOf(id)) }
    }
    reportDuplicates(raw.classes, "classes", { it.id }) { it.value }
    reportDuplicates(raw.archetypes, "archetypes", { it.id }) { it.value }
    reportDuplicates(raw.characters, "characters", { it.id }) { it.value }
    reportDuplicates(raw.enemies, "enemies", { it.id }) { it.value }

    fun checkStatMap(definitionId: String, field: String, stats: Map<StatId, Int>) {
        val missing = CoreStats.ALL - stats.keys
        if (missing.isNotEmpty()) errors += CatalogError.IncompleteStatBlock(definitionId, missing)
        stats.keys.filter { it !in declaredStats }
            .forEach { errors += CatalogError.UndeclaredCustomStat(definitionId, it) }
        stats.filterValues { it < 0 }
            .forEach { (statId, value) -> errors += CatalogError.NegativeValue(definitionId, "$field[${statId.value}]", value) }
    }

    fun checkAffinities(definitionId: String, affinities: Map<ElementId, Affinity>) {
        affinities.keys.filter { it !in raw.elements }
            .forEach { errors += CatalogError.UnknownReference(definitionId, "affinities", it.value) }
    }

    for (c in raw.classes) {
        val from = c.id.value
        if (c.commands.isEmpty()) errors += CatalogError.EmptyCommands(c.id)
        c.skills.filter { it !in raw.knownSkills }
            .forEach { errors += CatalogError.UnknownReference(from, "skills", it.value) }
        c.limitBreaks.filter { it !in raw.knownLimitBreaks }
            .forEach { errors += CatalogError.UnknownReference(from, "limitBreaks", it.value) }
        c.equipmentCategories.filter { it !in raw.equipmentCategories }
            .forEach { errors += CatalogError.UnknownReference(from, "equipmentCategories", it.value) }
        c.growth.keys.filter { it !in declaredStats }
            .forEach { errors += CatalogError.UndeclaredCustomStat(from, it) }
        for ((statId, curve) in c.growth) {
            when (curve) {
                is GrowthCurve.Linear ->
                    if (curve.base < 0) {
                        errors += CatalogError.NegativeValue(from, "growth[${statId.value}].base", curve.base)
                    }
                is GrowthCurve.Table ->
                    curve.values.filter { it < 0 }.forEach {
                        errors += CatalogError.NegativeValue(from, "growth[${statId.value}].values", it)
                    }
            }
        }
    }

    val classIds = raw.classes.map { it.id }.toSet()
    for (ch in raw.characters) {
        val from = ch.id.value
        if (ch.classId !in classIds) {
            errors += CatalogError.UnknownReference(from, "classId", ch.classId.value)
        }
        checkStatMap(from, "baseStats", ch.baseStats)
        checkAffinities(from, ch.affinities)
    }

    for (a in raw.archetypes) {
        val from = a.id.value
        if (a.aiProfile !in raw.knownAiProfiles) {
            errors += CatalogError.UnknownReference(from, "aiProfile", a.aiProfile.value)
        }
        if (a.rewardTier < 0) errors += CatalogError.NegativeValue(from, "rewardTier", a.rewardTier)
    }

    val archetypeIds = raw.archetypes.map { it.id }.toSet()
    for (e in raw.enemies) {
        val from = e.id.value
        if (e.archetypeId !in archetypeIds) {
            errors += CatalogError.UnknownReference(from, "archetypeId", e.archetypeId.value)
        }
        e.skills.filter { it !in raw.knownSkills }
            .forEach { errors += CatalogError.UnknownReference(from, "skills", it.value) }
        checkStatMap(from, "stats", e.stats)
        checkAffinities(from, e.affinities)
    }

    return if (errors.isEmpty()) CatalogResult.Valid(ValidatedCatalogImpl(raw)) else CatalogResult.Invalid(errors)
}
