package io.github.kirthar.sddrpg.core.catalog

import io.github.kirthar.sddrpg.core.TestJson
import io.github.kirthar.sddrpg.core.model.Affinity
import io.github.kirthar.sddrpg.core.model.ClassId
import io.github.kirthar.sddrpg.core.model.CoreStats
import io.github.kirthar.sddrpg.core.model.ElementId
import io.github.kirthar.sddrpg.core.model.EquipmentCategoryId
import io.github.kirthar.sddrpg.core.model.GrowthCurve
import io.github.kirthar.sddrpg.core.model.LimitBreakId
import io.github.kirthar.sddrpg.core.model.SkillId
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.maps.shouldBeEmpty
import io.kotest.matchers.shouldBe
import kotlinx.serialization.SerializationException

/** JSON taken from contracts/catalog-api.md (class + character examples). */
private val classJson = """
{
  "id": "warrior",
  "displayName": "Warrior",
  "skills": ["cleave"],
  "commands": ["ATTACK", "SKILL", "DEFEND", "ITEM"],
  "growth": {
    "hp":     { "type": "linear", "base": 120, "perLevel": 11 },
    "attack": { "type": "table", "values": [10, 12, 15, 19] }
  },
  "equipmentCategories": ["swords"],
  "limitBreaks": ["braver"]
}
""".trimIndent()

private val characterJson = """
{
  "id": "cloud",
  "displayName": "Cloud",
  "classId": "warrior",
  "baseStats": { "hp": 100, "mp": 20, "attack": 12, "defense": 10,
                  "magic": 6, "resistance": 8, "speed": 9, "luck": 5 },
  "affinities": { "ice": "RESISTANCE" }
}
""".trimIndent()

class DefinitionSerializationTest : StringSpec({

    "ClassDefinition round-trips the contract example" {
        val def = TestJson.decodeFromString(ClassDefinition.serializer(), classJson)
        def.id shouldBe ClassId("warrior")
        def.displayName shouldBe "Warrior"
        def.skills shouldBe setOf(SkillId("cleave"))
        def.commands shouldBe setOf(
            CommandKind.ATTACK, CommandKind.SKILL, CommandKind.DEFEND, CommandKind.ITEM
        )
        def.growth.getValue(CoreStats.HP) shouldBe GrowthCurve.Linear(120, 11)
        def.growth.getValue(CoreStats.ATTACK) shouldBe GrowthCurve.Table(listOf(10, 12, 15, 19))
        def.equipmentCategories shouldBe setOf(EquipmentCategoryId("swords"))
        def.limitBreaks shouldBe setOf(LimitBreakId("braver"))

        val reencoded = TestJson.encodeToString(ClassDefinition.serializer(), def)
        TestJson.decodeFromString(ClassDefinition.serializer(), reencoded) shouldBe def
    }

    "CharacterDefinition round-trips the contract example" {
        val def = TestJson.decodeFromString(CharacterDefinition.serializer(), characterJson)
        def.classId shouldBe ClassId("warrior")
        def.baseStats.getValue(CoreStats.HP) shouldBe 100
        def.affinities shouldBe mapOf(ElementId("ice") to Affinity.RESISTANCE)

        val reencoded = TestJson.encodeToString(CharacterDefinition.serializer(), def)
        TestJson.decodeFromString(CharacterDefinition.serializer(), reencoded) shouldBe def
    }

    "optional sections default to empty (affinities => neutral to everything)" {
        val minimal = """
            {
              "id": "npc",
              "displayName": "Npc",
              "classId": "warrior",
              "baseStats": { "hp": 1, "mp": 1, "attack": 1, "defense": 1,
                              "magic": 1, "resistance": 1, "speed": 1, "luck": 1 }
            }
        """.trimIndent()
        val def = TestJson.decodeFromString(CharacterDefinition.serializer(), minimal)
        def.affinities.shouldBeEmpty()
    }

    "strict parsing rejects unknown keys" {
        val withTypo = characterJson.replace("\"affinities\"", "\"afinities\"")
        shouldThrow<SerializationException> {
            TestJson.decodeFromString(CharacterDefinition.serializer(), withTypo)
        }
    }
})
