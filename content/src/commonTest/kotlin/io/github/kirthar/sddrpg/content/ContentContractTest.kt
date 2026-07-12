package io.github.kirthar.sddrpg.content

import io.github.kirthar.sddrpg.core.status.StatusEffectCatalog
import io.github.kirthar.sddrpg.core.status.StatusEffectCatalogResult
import io.github.kirthar.sddrpg.core.status.StatusEffectDefinition
import io.github.kirthar.sddrpg.core.status.StatusEffectId
import io.github.kirthar.sddrpg.core.status.EffectKind
import io.github.kirthar.sddrpg.core.status.validateStatusEffectCatalog
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf

/**
 * FR-009: specs 001-006's catalog types and validation functions are never modified
 * -- loadContentPack's reported problems are exactly what calling each spec's own
 * validate*Catalog function directly would produce, never a looser or stricter
 * parallel rule set (FR-002). This test, plus the fact that everything else in this
 * module compiles against unmodified spec 001-006 files, is the evidence.
 */
class ContentContractTest : FunSpec({
    test("loadContentPack's StatusEffectProblem errors are byte-for-byte what validateStatusEffectCatalog produces directly") {
        val catalog = StatusEffectCatalog(
            listOf(
                StatusEffectDefinition(StatusEffectId("poison"), "Poison", EffectKind.DamageOverTime(5), 3),
                StatusEffectDefinition(StatusEffectId("poison"), "Poison 2", EffectKind.DamageOverTime(3), 2),
            )
        )
        val direct = validateStatusEffectCatalog(catalog).shouldBeInstanceOf<StatusEffectCatalogResult.Invalid>()

        val result = loadContentPack(duplicateStatusEffectContentText).shouldBeInstanceOf<ContentLoadResult.Invalid>()
        val wrapped = result.problems.filterIsInstance<ContentProblem.StatusEffectProblem>().map { it.error }

        wrapped shouldBe direct.errors
    }

    test("an empty content text loads a package with every catalog empty, identical to constructing each catalog type directly") {
        val result = loadContentPack("{}").shouldBeInstanceOf<ContentLoadResult.Valid>()

        result.pack.statusEffects shouldBe StatusEffectCatalog()
        result.pack.synergies shouldBe io.github.kirthar.sddrpg.core.event.SynergyCatalog()
        result.pack.limitBreaks shouldBe io.github.kirthar.sddrpg.core.limitbreak.LimitBreakCatalog()
        result.pack.summons shouldBe io.github.kirthar.sddrpg.core.limitbreak.SummonCatalog()
    }
})
