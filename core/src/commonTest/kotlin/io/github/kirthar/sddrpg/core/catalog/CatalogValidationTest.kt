package io.github.kirthar.sddrpg.core.catalog

import io.github.kirthar.sddrpg.core.model.CharacterId
import io.github.kirthar.sddrpg.core.model.ClassId
import io.github.kirthar.sddrpg.core.model.CoreStats
import io.github.kirthar.sddrpg.core.model.EquipmentCategoryId
import io.github.kirthar.sddrpg.core.model.LimitBreakId
import io.github.kirthar.sddrpg.core.model.SkillId
import io.github.kirthar.sddrpg.core.model.StatId
import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.inspectors.forOne
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf

private fun completeStats(v: Int = 10): Map<StatId, Int> = CoreStats.ALL.associateWith { v }

private fun warriorClass(id: String = "warrior") = ClassDefinition(
    id = ClassId(id),
    displayName = "Warrior",
    skills = setOf(SkillId("cleave")),
    commands = setOf(CommandKind.ATTACK, CommandKind.DEFEND),
)

private fun cloud(classId: String = "warrior") = CharacterDefinition(
    id = CharacterId("cloud"),
    displayName = "Cloud",
    classId = ClassId(classId),
    baseStats = completeStats(),
)

private val validCatalog = Catalog(
    knownSkills = setOf(SkillId("cleave")),
    classes = listOf(warriorClass()),
    characters = listOf(cloud()),
)

class CatalogValidationTest : StringSpec({

    "a well-formed catalog validates" {
        validateCatalog(validCatalog).shouldBeInstanceOf<CatalogResult.Valid>()
    }

    "unknown character classId is reported naming the character" {
        val result = validateCatalog(validCatalog.copy(characters = listOf(cloud(classId = "mage"))))
        val errors = result.shouldBeInstanceOf<CatalogResult.Invalid>().errors
        errors.forOne {
            val e = it.shouldBeInstanceOf<CatalogError.UnknownReference>()
            e.fromDefinition shouldBe "cloud"
            e.field shouldBe "classId"
            e.missingId shouldBe "mage"
        }
    }

    "dangling class references are reported per field" {
        val broken = warriorClass().copy(
            skills = setOf(SkillId("missing-skill")),
            limitBreaks = setOf(LimitBreakId("missing-lb")),
            equipmentCategories = setOf(EquipmentCategoryId("missing-cat")),
            growth = mapOf(StatId("undeclared") to io.github.kirthar.sddrpg.core.model.GrowthCurve.Linear(1, 1)),
        )
        val result = validateCatalog(validCatalog.copy(classes = listOf(broken)))
        val errors = result.shouldBeInstanceOf<CatalogResult.Invalid>().errors
        errors.forOne { it.shouldBeInstanceOf<CatalogError.UnknownReference>().field shouldBe "skills" }
        errors.forOne { it.shouldBeInstanceOf<CatalogError.UnknownReference>().field shouldBe "limitBreaks" }
        errors.forOne { it.shouldBeInstanceOf<CatalogError.UnknownReference>().field shouldBe "equipmentCategories" }
        errors.forOne { it.shouldBeInstanceOf<CatalogError.UndeclaredCustomStat>().statId shouldBe StatId("undeclared") }
    }

    "duplicate ids are reported per namespace" {
        val result = validateCatalog(
            validCatalog.copy(
                classes = listOf(warriorClass(), warriorClass()),
                characters = listOf(cloud(), cloud()),
            )
        )
        val errors = result.shouldBeInstanceOf<CatalogResult.Invalid>().errors
        errors.forOne {
            val e = it.shouldBeInstanceOf<CatalogError.DuplicateId>()
            e.namespace shouldBe "classes"
            e.id shouldBe "warrior"
        }
        errors.forOne {
            val e = it.shouldBeInstanceOf<CatalogError.DuplicateId>()
            e.namespace shouldBe "characters"
            e.id shouldBe "cloud"
        }
    }

    "incomplete stat block, undeclared custom stat and negative values are reported" {
        val badStats = cloud().copy(
            baseStats = completeStats().minus(CoreStats.LUCK)
                .plus(StatId("bravery") to 3)
                .plus(CoreStats.SPEED to -5),
        )
        val result = validateCatalog(validCatalog.copy(characters = listOf(badStats)))
        val errors = result.shouldBeInstanceOf<CatalogResult.Invalid>().errors
        errors.forOne {
            val e = it.shouldBeInstanceOf<CatalogError.IncompleteStatBlock>()
            e.definitionId shouldBe "cloud"
            e.missingStatIds shouldBe setOf(CoreStats.LUCK)
        }
        errors.forOne { it.shouldBeInstanceOf<CatalogError.UndeclaredCustomStat>().statId shouldBe StatId("bravery") }
        errors.forOne {
            val e = it.shouldBeInstanceOf<CatalogError.NegativeValue>()
            e.definitionId shouldBe "cloud"
            e.value shouldBe -5
        }
    }

    "declared custom stats are usable in stat blocks" {
        val withCustom = validCatalog.copy(
            customStats = setOf(StatId("bravery")),
            characters = listOf(cloud().copy(baseStats = completeStats() + (StatId("bravery") to 3))),
        )
        validateCatalog(withCustom).shouldBeInstanceOf<CatalogResult.Valid>()
    }

    "core stats may not be redeclared as custom stats" {
        val result = validateCatalog(validCatalog.copy(customStats = setOf(CoreStats.HP)))
        val errors = result.shouldBeInstanceOf<CatalogResult.Invalid>().errors
        errors.forOne { it.shouldBeInstanceOf<CatalogError.CoreStatRedeclared>().statId shouldBe CoreStats.HP }
    }

    "empty commands is invalid, empty skills is valid" {
        val commoner = warriorClass(id = "commoner").copy(skills = emptySet(), commands = emptySet())
        val result = validateCatalog(validCatalog.copy(classes = listOf(warriorClass(), commoner)))
        val errors = result.shouldBeInstanceOf<CatalogResult.Invalid>().errors
        withClue("only the empty-commands error should be reported") {
            errors shouldHaveSize 1
        }
        errors.forOne { it.shouldBeInstanceOf<CatalogError.EmptyCommands>().classId shouldBe ClassId("commoner") }
    }

    "errors accumulate: a fixture with several defects reports all of them (SC-002)" {
        val result = validateCatalog(
            Catalog(
                customStats = setOf(CoreStats.HP),
                classes = listOf(
                    warriorClass().copy(skills = setOf(SkillId("nope")), commands = emptySet()),
                ),
                characters = listOf(cloud(classId = "ghost-class")),
            )
        )
        val errors = result.shouldBeInstanceOf<CatalogResult.Invalid>().errors
        errors shouldHaveSize 4
    }
})
