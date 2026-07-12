package io.github.kirthar.sddrpg.core.catalog

import io.github.kirthar.sddrpg.core.model.CharacterId
import io.github.kirthar.sddrpg.core.model.ClassId
import io.github.kirthar.sddrpg.core.model.CoreStats
import io.github.kirthar.sddrpg.core.model.LimitBreakId
import io.github.kirthar.sddrpg.core.model.SkillId
import io.github.kirthar.sddrpg.core.model.StatId
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf

/** US1 acceptance scenarios: class grants are derived through the class reference. */

private fun completeStats(v: Int): Map<StatId, Int> = CoreStats.ALL.associateWith { v }

private val warrior = ClassDefinition(
    id = ClassId("warrior"),
    displayName = "Warrior",
    skills = setOf(SkillId("skill-a"), SkillId("skill-b")),
    commands = setOf(CommandKind.ATTACK, CommandKind.SKILL, CommandKind.DEFEND),
    limitBreaks = setOf(LimitBreakId("braver")),
)

private val mage = ClassDefinition(
    id = ClassId("mage"),
    displayName = "Mage",
    skills = setOf(SkillId("fira")),
    commands = setOf(CommandKind.ATTACK, CommandKind.MAGIC),
)

private val baseCatalog = Catalog(
    knownSkills = setOf(SkillId("skill-a"), SkillId("skill-b"), SkillId("fira")),
    knownLimitBreaks = setOf(LimitBreakId("braver")),
    classes = listOf(warrior, mage),
    characters = listOf(
        CharacterDefinition(CharacterId("cloud"), "Cloud", ClassId("warrior"), completeStats(10)),
        CharacterDefinition(CharacterId("barret"), "Barret", ClassId("warrior"), completeStats(20)),
    ),
)

private fun validated(catalog: Catalog): ValidatedCatalog =
    validateCatalog(catalog).shouldBeInstanceOf<CatalogResult.Valid>().catalog

class ClassCapabilityTest : StringSpec({

    "scenario 1: a character's skills and commands are exactly its class's grants" {
        val catalog = validated(baseCatalog)
        val cloud = catalog.character(CharacterId("cloud"))
        val cloudClass = catalog.classDefinition(cloud.classId)
        cloudClass.skills shouldBe setOf(SkillId("skill-a"), SkillId("skill-b"))
        cloudClass.commands shouldBe setOf(CommandKind.ATTACK, CommandKind.SKILL, CommandKind.DEFEND)
    }

    "scenario 2: same class shares grants, individual stats stay individual" {
        val catalog = validated(baseCatalog)
        val cloud = catalog.character(CharacterId("cloud"))
        val barret = catalog.character(CharacterId("barret"))
        cloud.classId shouldBe barret.classId
        cloud.baseStats.getValue(CoreStats.HP) shouldBe 10
        barret.baseStats.getValue(CoreStats.HP) shouldBe 20
    }

    "scenario 3: class-specific limit break is listed among grants" {
        val catalog = validated(baseCatalog)
        val cloudClass = catalog.classDefinition(catalog.character(CharacterId("cloud")).classId)
        cloudClass.limitBreaks shouldBe setOf(LimitBreakId("braver"))
    }

    "scenario 4: swapping the class reference swaps capabilities, nothing else (job change)" {
        val swapped = baseCatalog.copy(
            characters = baseCatalog.characters.map {
                if (it.id == CharacterId("cloud")) it.copy(classId = ClassId("mage")) else it
            }
        )
        val catalog = validated(swapped)
        val cloud = catalog.character(CharacterId("cloud"))
        cloud.displayName shouldBe "Cloud"
        cloud.baseStats.getValue(CoreStats.HP) shouldBe 10
        val newClass = catalog.classDefinition(cloud.classId)
        newClass.id shouldBe ClassId("mage")
        newClass.skills shouldBe setOf(SkillId("fira"))
    }
})
