package io.github.kirthar.sddrpg.core.catalog

import io.github.kirthar.sddrpg.core.TestJson
import io.github.kirthar.sddrpg.core.model.Affinity
import io.github.kirthar.sddrpg.core.model.AiProfileId
import io.github.kirthar.sddrpg.core.model.ArchetypeId
import io.github.kirthar.sddrpg.core.model.CoreStats
import io.github.kirthar.sddrpg.core.model.ElementId
import io.github.kirthar.sddrpg.core.model.SkillId
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe

/** JSON taken from contracts/catalog-api.md (archetype + enemy examples). */
private val archetypeJson = """
{ "id": "boss", "displayName": "Boss", "aiProfile": "aggressive", "rewardTier": 3 }
""".trimIndent()

private val enemyJson = """
{
  "id": "bomb",
  "displayName": "Bomb",
  "archetypeId": "boss",
  "skills": ["fira"],
  "stats": { "hp": 300, "mp": 50, "attack": 14, "defense": 8,
              "magic": 12, "resistance": 6, "speed": 7, "luck": 3 },
  "affinities": { "fire": "ABSORPTION", "ice": "WEAKNESS" }
}
""".trimIndent()

class EnemySerializationTest : StringSpec({

    "ArchetypeDefinition round-trips the contract example" {
        val def = TestJson.decodeFromString(ArchetypeDefinition.serializer(), archetypeJson)
        def.id shouldBe ArchetypeId("boss")
        def.aiProfile shouldBe AiProfileId("aggressive")
        def.rewardTier shouldBe 3
        val reencoded = TestJson.encodeToString(ArchetypeDefinition.serializer(), def)
        TestJson.decodeFromString(ArchetypeDefinition.serializer(), reencoded) shouldBe def
    }

    "EnemyDefinition round-trips the contract example" {
        val def = TestJson.decodeFromString(EnemyDefinition.serializer(), enemyJson)
        def.archetypeId shouldBe ArchetypeId("boss")
        def.skills shouldBe setOf(SkillId("fira"))
        def.stats.getValue(CoreStats.HP) shouldBe 300
        def.affinities shouldBe mapOf(
            ElementId("fire") to Affinity.ABSORPTION,
            ElementId("ice") to Affinity.WEAKNESS,
        )
        val reencoded = TestJson.encodeToString(EnemyDefinition.serializer(), def)
        TestJson.decodeFromString(EnemyDefinition.serializer(), reencoded) shouldBe def
    }

    "enemy skills are optional and default to empty" {
        val withoutSkills = """
            {
              "id": "goblin",
              "displayName": "Goblin",
              "archetypeId": "boss",
              "stats": { "hp": 10, "mp": 0, "attack": 3, "defense": 2,
                          "magic": 0, "resistance": 1, "speed": 4, "luck": 1 }
            }
        """.trimIndent()
        val def = TestJson.decodeFromString(EnemyDefinition.serializer(), withoutSkills)
        def.skills.shouldBeEmpty()
        def.affinities shouldBe emptyMap()
    }
})
