package io.github.kirthar.sddrpg.content

import io.github.kirthar.sddrpg.content.demo.DEMO_CONTENT_JSON
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf

class DemoContentTest : FunSpec({
    test("the shipped demo content pack loads successfully with zero reported problems (US3 scenario 1)") {
        loadContentPack(DEMO_CONTENT_JSON).shouldBeInstanceOf<ContentLoadResult.Valid>()
    }

    test("the loaded demo package has at least one entry in every catalog kind (US3 scenario 2)") {
        val valid = loadContentPack(DEMO_CONTENT_JSON).shouldBeInstanceOf<ContentLoadResult.Valid>()
        val pack = valid.pack

        pack.statusEffects.effects.shouldNotBeEmpty()
        pack.synergies.synergies.shouldNotBeEmpty()
        pack.limitBreaks.limitBreaks.shouldNotBeEmpty()
        pack.summons.summons.shouldNotBeEmpty()
        // classes/characters/enemies, via the ValidatedCatalog's own lookup surface:
        pack.catalog.classDefinition(io.github.kirthar.sddrpg.core.model.ClassId("warrior")).id shouldBe io.github.kirthar.sddrpg.core.model.ClassId("warrior")
        pack.catalog.enemy(io.github.kirthar.sddrpg.core.model.EnemyId("bomb")).id shouldBe io.github.kirthar.sddrpg.core.model.EnemyId("bomb")
    }
})
