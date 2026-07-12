package io.github.kirthar.sddrpg.content

import io.github.kirthar.sddrpg.core.catalog.Catalog
import io.github.kirthar.sddrpg.core.event.SynergyCatalog
import io.github.kirthar.sddrpg.core.limitbreak.LimitBreakCatalog
import io.github.kirthar.sddrpg.core.limitbreak.SummonCatalog
import io.github.kirthar.sddrpg.core.model.CoreStats
import io.github.kirthar.sddrpg.core.status.EffectKind
import io.github.kirthar.sddrpg.core.status.StatusEffectCatalog

/**
 * Checks the four cross-catalog reference relationships that no single spec's own
 * validation can see (research R3) -- takes the *raw* [Catalog], not
 * `ValidatedCatalog`, since every field these checks read
 * (`knownLimitBreaks`/`knownSkills`/`customStats`/`elements`) already lives directly
 * on it. Runs unconditionally, regardless of whether [catalog]'s own validation
 * succeeded.
 */
fun validateCrossCatalogReferences(
    catalog: Catalog,
    statusEffects: StatusEffectCatalog,
    synergies: SynergyCatalog,
    limitBreaks: LimitBreakCatalog,
    summons: SummonCatalog,
): List<ContentProblem.DanglingReference> {
    val problems = mutableListOf<ContentProblem.DanglingReference>()

    val definedLimitBreakIds = limitBreaks.limitBreaks.map { it.id }.toSet()
    for (id in catalog.knownLimitBreaks) {
        if (id !in definedLimitBreakIds) {
            problems += ContentProblem.DanglingReference("classes", "limitBreaks", id.value)
        }
    }

    for (synergy in synergies.synergies) {
        if (synergy.firstSkillId !in catalog.knownSkills) {
            problems += ContentProblem.DanglingReference("synergies", "firstSkillId", synergy.firstSkillId.value)
        }
        if (synergy.secondSkillId !in catalog.knownSkills) {
            problems += ContentProblem.DanglingReference("synergies", "secondSkillId", synergy.secondSkillId.value)
        }
    }

    val declaredStatIds = CoreStats.ALL + catalog.customStats
    for (effect in statusEffects.effects) {
        val kind = effect.kind
        if (kind is EffectKind.StatModifier && kind.statId !in declaredStatIds) {
            problems += ContentProblem.DanglingReference("statusEffects", "statId", kind.statId.value)
        }
    }

    for (limitBreak in limitBreaks.limitBreaks) {
        val element = limitBreak.element
        if (element != null && element !in catalog.elements) {
            problems += ContentProblem.DanglingReference("limitBreaks", "element", element.value)
        }
    }

    for (summon in summons.summons) {
        val element = summon.element
        if (element != null && element !in catalog.elements) {
            problems += ContentProblem.DanglingReference("summons", "element", element.value)
        }
    }

    return problems
}
