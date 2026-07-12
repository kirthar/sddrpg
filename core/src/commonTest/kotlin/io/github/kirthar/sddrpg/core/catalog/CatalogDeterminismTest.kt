package io.github.kirthar.sddrpg.core.catalog

import io.github.kirthar.sddrpg.core.model.CharacterId
import io.github.kirthar.sddrpg.core.model.ClassId
import io.github.kirthar.sddrpg.core.model.CoreStats
import io.github.kirthar.sddrpg.core.model.SkillId
import io.github.kirthar.sddrpg.core.model.StatId
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf

/** SC-006: loading the same catalog twice produces identical models and errors. */

private fun completeStats(): Map<StatId, Int> = CoreStats.ALL.associateWith { 10 }

private val goodCatalog = Catalog(
    knownSkills = setOf(SkillId("cleave")),
    classes = listOf(
        ClassDefinition(ClassId("warrior"), "Warrior", setOf(SkillId("cleave")), setOf(CommandKind.ATTACK)),
    ),
    characters = listOf(
        CharacterDefinition(CharacterId("cloud"), "Cloud", ClassId("warrior"), completeStats()),
    ),
)

private val badCatalog = goodCatalog.copy(
    customStats = setOf(CoreStats.HP),
    characters = listOf(
        CharacterDefinition(CharacterId("cloud"), "Cloud", ClassId("ghost"), completeStats()),
        CharacterDefinition(CharacterId("cloud"), "Cloud", ClassId("warrior"), completeStats()),
    ),
)

class CatalogDeterminismTest : StringSpec({

    "validating the same valid catalog twice yields equivalent lookups" {
        val first = validateCatalog(goodCatalog).shouldBeInstanceOf<CatalogResult.Valid>().catalog
        val second = validateCatalog(goodCatalog).shouldBeInstanceOf<CatalogResult.Valid>().catalog
        first.classDefinition(ClassId("warrior")) shouldBe second.classDefinition(ClassId("warrior"))
        first.character(CharacterId("cloud")) shouldBe second.character(CharacterId("cloud"))
        first.customStats shouldBe second.customStats
        first.elements shouldBe second.elements
    }

    "validating the same broken catalog twice yields the identical error list" {
        val first = validateCatalog(badCatalog).shouldBeInstanceOf<CatalogResult.Invalid>().errors
        val second = validateCatalog(badCatalog).shouldBeInstanceOf<CatalogResult.Invalid>().errors
        first shouldBe second
    }
})
