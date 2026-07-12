package io.github.kirthar.sddrpg.core.limitbreak

import io.github.kirthar.sddrpg.core.action.DamageFormula
import io.github.kirthar.sddrpg.core.action.EffectKind
import io.github.kirthar.sddrpg.core.action.TargetingShape
import io.github.kirthar.sddrpg.core.model.LimitBreakId
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf

class LimitBreakCatalogValidationTest : FunSpec({
    test("an empty limit break catalog is valid") {
        validateLimitBreakCatalog(LimitBreakCatalog()).shouldBeInstanceOf<LimitBreakCatalogResult.Valid>()
    }

    test("a valid limit break definition passes") {
        validateLimitBreakCatalog(LimitBreakCatalog(listOf(omnislashDefinition))).shouldBeInstanceOf<LimitBreakCatalogResult.Valid>()
    }

    test("duplicate ids and non-positive thresholds are accumulated in one pass") {
        val dupId = LimitBreakId("dup")
        val def = { id: LimitBreakId, threshold: Int ->
            LimitBreakDefinition(id, threshold, EffectKind.DAMAGE, DamageFormula.Fixed(1), TargetingShape.SINGLE_ENEMY)
        }
        val catalog = LimitBreakCatalog(
            listOf(
                def(dupId, 10),
                def(dupId, 10),
                def(LimitBreakId("zero"), 0),
                def(LimitBreakId("negative"), -5),
            )
        )
        val invalid = validateLimitBreakCatalog(catalog).shouldBeInstanceOf<LimitBreakCatalogResult.Invalid>()
        invalid.errors shouldBe listOf(
            LimitBreakCatalogError.DuplicateId(dupId.value),
            LimitBreakCatalogError.NonPositiveThreshold("zero", 0),
            LimitBreakCatalogError.NonPositiveThreshold("negative", -5),
        )
    }

    test("an empty summon catalog is valid") {
        validateSummonCatalog(SummonCatalog()).shouldBeInstanceOf<SummonCatalogResult.Valid>()
    }

    test("a valid summon definition passes") {
        validateSummonCatalog(SummonCatalog(listOf(meteorDefinition))).shouldBeInstanceOf<SummonCatalogResult.Valid>()
    }

    test("duplicate ids and negative costs are accumulated in one pass") {
        val dupId = SummonId("dup")
        val def = { id: SummonId, cost: Int ->
            SummonDefinition(id, cost, EffectKind.DAMAGE, DamageFormula.Fixed(1), TargetingShape.SINGLE_ENEMY)
        }
        val catalog = SummonCatalog(listOf(def(dupId, 10), def(dupId, 10), def(SummonId("negative"), -1)))
        val invalid = validateSummonCatalog(catalog).shouldBeInstanceOf<SummonCatalogResult.Invalid>()
        invalid.errors shouldBe listOf(
            SummonCatalogError.DuplicateId(dupId.value),
            SummonCatalogError.NegativeCost("negative", -1),
        )
    }

    test("zero cost is a valid summon (a free summon is legal)") {
        val catalog = SummonCatalog(listOf(SummonDefinition(SummonId("free"), 0, EffectKind.DAMAGE, DamageFormula.Fixed(1), TargetingShape.SINGLE_ENEMY)))
        validateSummonCatalog(catalog).shouldBeInstanceOf<SummonCatalogResult.Valid>()
    }
})
