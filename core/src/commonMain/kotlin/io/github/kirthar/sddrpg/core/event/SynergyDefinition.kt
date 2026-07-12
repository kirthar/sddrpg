package io.github.kirthar.sddrpg.core.event

import io.github.kirthar.sddrpg.core.model.SkillId

/** A synergy's automatic bonus effect (spec FR-006); one variant for this feature's v1 scope. */
sealed interface SynergyBonus {
    data class BonusDamage(val amount: Int) : SynergyBonus
}

/**
 * A data-driven "class-flavored combination" synergy (spec FR-004): two required
 * distinct qualifying skills, a same-target condition (implicit in detection), a
 * turn-based window, and a bonus. `firstSkillId`/`secondSkillId` carry no ordering
 * meaning -- either capability may be satisfied first (research R3).
 */
data class SynergyDefinition(
    val id: SynergyId,
    val firstSkillId: SkillId,
    val secondSkillId: SkillId,
    val window: Int,
    val bonus: SynergyBonus,
)

data class SynergyCatalog(val synergies: List<SynergyDefinition> = emptyList())

/** A rejected synergy catalog defect, always naming the offending definition (mirrors spec 001/004's *CatalogError). */
sealed interface SynergyCatalogError {
    data class DuplicateId(val id: String) : SynergyCatalogError
    data class SameSkill(val id: String, val skillId: String) : SynergyCatalogError
    data class NonPositiveWindow(val id: String, val window: Int) : SynergyCatalogError
}

sealed interface SynergyCatalogResult {
    data class Valid(val catalog: SynergyCatalog) : SynergyCatalogResult
    data class Invalid(val errors: List<SynergyCatalogError>) : SynergyCatalogResult
}

/** Accumulate-all validation (data-model.md), mirroring spec 001/004's `validate*Catalog`. */
fun validateSynergyCatalog(catalog: SynergyCatalog): SynergyCatalogResult {
    val errors = mutableListOf<SynergyCatalogError>()

    catalog.synergies.groupBy { it.id }.filterValues { it.size > 1 }
        .forEach { (id, _) -> errors += SynergyCatalogError.DuplicateId(id.value) }

    for (synergy in catalog.synergies) {
        if (synergy.firstSkillId == synergy.secondSkillId) {
            errors += SynergyCatalogError.SameSkill(synergy.id.value, synergy.firstSkillId.value)
        }
        if (synergy.window <= 0) {
            errors += SynergyCatalogError.NonPositiveWindow(synergy.id.value, synergy.window)
        }
    }

    return if (errors.isEmpty()) SynergyCatalogResult.Valid(catalog) else SynergyCatalogResult.Invalid(errors)
}
