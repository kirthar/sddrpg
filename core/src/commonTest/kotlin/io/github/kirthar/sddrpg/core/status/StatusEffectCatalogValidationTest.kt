package io.github.kirthar.sddrpg.core.status

import io.kotest.core.spec.style.StringSpec
import io.kotest.inspectors.forOne
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf

class StatusEffectCatalogValidationTest : StringSpec({

    "a well-formed catalog validates" {
        validateStatusEffectCatalog(statusEffectFixtureCatalog).shouldBeInstanceOf<StatusEffectCatalogResult.Valid>()
    }

    "a duplicate id is rejected, naming it" {
        val dup = StatusEffectDefinition(StatusEffectId("poison"), "Poison Again", EffectKind.Incapacitate, duration = 1)
        val catalog = StatusEffectCatalog(statusEffectFixtureCatalog.effects + dup)
        val errors = validateStatusEffectCatalog(catalog)
            .shouldBeInstanceOf<StatusEffectCatalogResult.Invalid>().errors
        errors.forOne {
            it.shouldBeInstanceOf<StatusEffectCatalogError.DuplicateId>().id shouldBe "poison"
        }
    }

    "a non-positive duration is rejected, naming it" {
        val bad = StatusEffectDefinition(StatusEffectId("broken"), "Broken", EffectKind.Incapacitate, duration = 0)
        val catalog = StatusEffectCatalog(listOf(bad))
        val errors = validateStatusEffectCatalog(catalog)
            .shouldBeInstanceOf<StatusEffectCatalogResult.Invalid>().errors
        errors.forOne {
            val e = it.shouldBeInstanceOf<StatusEffectCatalogError.NonPositiveDuration>()
            e.definitionId shouldBe "broken"
            e.duration shouldBe 0
        }
    }

    "errors accumulate: duplicate id and bad duration are both reported" {
        val dup = StatusEffectDefinition(StatusEffectId("poison"), "Poison Again", EffectKind.Incapacitate, duration = -1)
        val catalog = StatusEffectCatalog(statusEffectFixtureCatalog.effects + dup)
        val errors = validateStatusEffectCatalog(catalog)
            .shouldBeInstanceOf<StatusEffectCatalogResult.Invalid>().errors
        errors shouldHaveSize 2
    }
})
