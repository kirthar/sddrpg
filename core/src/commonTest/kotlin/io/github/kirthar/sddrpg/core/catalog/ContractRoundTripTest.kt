package io.github.kirthar.sddrpg.core.catalog

import io.github.kirthar.sddrpg.core.TestJson
import io.github.kirthar.sddrpg.core.model.AiProfileId
import io.github.kirthar.sddrpg.core.model.ArchetypeId
import io.github.kirthar.sddrpg.core.model.CharacterId
import io.github.kirthar.sddrpg.core.model.ClassId
import io.github.kirthar.sddrpg.core.model.EnemyId
import io.github.kirthar.sddrpg.core.model.StatId
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.serialization.SerializationException

/**
 * FR-014: the complete catalog document from contracts/catalog-api.md is the canonical
 * interchange example — it must deserialize, validate, and round-trip structurally.
 */
private val contractCatalogJson = """
{
  "customStats": ["bravery"],
  "elements": ["fire", "ice", "thunder"],
  "equipmentCategories": ["swords", "staves"],
  "knownSkills": ["fira", "cura", "cleave"],
  "knownLimitBreaks": ["braver"],
  "knownAiProfiles": ["aggressive", "healer-priority"],
  "classes": [
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
  ],
  "archetypes": [
    { "id": "boss", "displayName": "Boss", "aiProfile": "aggressive", "rewardTier": 3 }
  ],
  "characters": [
    {
      "id": "cloud",
      "displayName": "Cloud",
      "classId": "warrior",
      "baseStats": { "hp": 100, "mp": 20, "attack": 12, "defense": 10,
                      "magic": 6, "resistance": 8, "speed": 9, "luck": 5 },
      "affinities": { "ice": "RESISTANCE" }
    }
  ],
  "enemies": [
    {
      "id": "bomb",
      "displayName": "Bomb",
      "archetypeId": "boss",
      "skills": ["fira"],
      "stats": { "hp": 300, "mp": 50, "attack": 14, "defense": 8,
                  "magic": 12, "resistance": 6, "speed": 7, "luck": 3 },
      "affinities": { "fire": "ABSORPTION", "ice": "WEAKNESS" }
    }
  ]
}
""".trimIndent()

class ContractRoundTripTest : StringSpec({

    "the contract example deserializes, validates, and round-trips structurally" {
        val catalog = TestJson.decodeFromString(Catalog.serializer(), contractCatalogJson)

        catalog.customStats shouldBe setOf(StatId("bravery"))
        catalog.classes.single().id shouldBe ClassId("warrior")
        catalog.characters.single().id shouldBe CharacterId("cloud")
        catalog.enemies.single().id shouldBe EnemyId("bomb")
        catalog.archetypes.single().id shouldBe ArchetypeId("boss")
        catalog.knownAiProfiles shouldBe setOf(AiProfileId("aggressive"), AiProfileId("healer-priority"))

        validateCatalog(catalog).shouldBeInstanceOf<CatalogResult.Valid>()

        val reencoded = TestJson.encodeToString(Catalog.serializer(), catalog)
        TestJson.decodeFromString(Catalog.serializer(), reencoded) shouldBe catalog
    }

    "strict parsing rejects a document with unknown top-level keys" {
        val withExtra = contractCatalogJson.replaceFirst("\"customStats\"", "\"custom_stats\"")
        shouldThrow<SerializationException> {
            TestJson.decodeFromString(Catalog.serializer(), withExtra)
        }
    }
})
