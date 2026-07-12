package io.github.kirthar.sddrpg.core.catalog

import io.github.kirthar.sddrpg.core.model.AiProfileId
import io.github.kirthar.sddrpg.core.model.ArchetypeId
import io.github.kirthar.sddrpg.core.model.CoreStats
import io.github.kirthar.sddrpg.core.model.ElementId
import io.github.kirthar.sddrpg.core.model.EnemyId
import io.github.kirthar.sddrpg.core.model.SkillId
import io.github.kirthar.sddrpg.core.model.StatId
import io.kotest.core.spec.style.StringSpec
import io.kotest.inspectors.forOne
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf

/** US2 acceptance scenarios plus enemy-side validation rules. */

private fun completeStats(v: Int = 10): Map<StatId, Int> = CoreStats.ALL.associateWith { v }

private val boss = ArchetypeDefinition(
    id = ArchetypeId("boss"),
    displayName = "Boss",
    aiProfile = AiProfileId("aggressive"),
    rewardTier = 3,
)

private val commonMob = ArchetypeDefinition(
    id = ArchetypeId("common"),
    displayName = "Common",
    aiProfile = AiProfileId("healer-priority"),
    rewardTier = 0,
)

private fun enemy(id: String, archetype: String = "boss", stats: Map<StatId, Int> = completeStats()) =
    EnemyDefinition(
        id = EnemyId(id),
        displayName = id,
        archetypeId = ArchetypeId(archetype),
        stats = stats,
    )

private val baseCatalog = Catalog(
    elements = setOf(ElementId("fire")),
    knownSkills = setOf(SkillId("fira")),
    knownAiProfiles = setOf(AiProfileId("aggressive"), AiProfileId("healer-priority")),
    archetypes = listOf(boss, commonMob),
    enemies = listOf(enemy("bomb"), enemy("goblin", archetype = "common")),
)

private fun validated(catalog: Catalog): ValidatedCatalog =
    validateCatalog(catalog).shouldBeInstanceOf<CatalogResult.Valid>().catalog

class ArchetypeTest : StringSpec({

    "scenario 1: an enemy exposes its archetype's AI profile and reward tier" {
        val catalog = validated(baseCatalog)
        val bomb = catalog.enemy(EnemyId("bomb"))
        val archetype = catalog.archetype(bomb.archetypeId)
        archetype.aiProfile shouldBe AiProfileId("aggressive")
        archetype.rewardTier shouldBe 3
    }

    "scenario 2: adding an enemy referencing an existing archetype is data-only" {
        val extended = baseCatalog.copy(enemies = baseCatalog.enemies + enemy("tonberry"))
        val catalog = validated(extended)
        catalog.enemy(EnemyId("tonberry")).archetypeId shouldBe ArchetypeId("boss")
    }

    "scenario 3: same archetype shares profile/tier, individual stats stay individual" {
        val two = baseCatalog.copy(
            enemies = listOf(enemy("bomb", stats = completeStats(30)), enemy("bomb-king", stats = completeStats(99))),
        )
        val catalog = validated(two)
        val a = catalog.enemy(EnemyId("bomb"))
        val b = catalog.enemy(EnemyId("bomb-king"))
        a.archetypeId shouldBe b.archetypeId
        a.stats.getValue(CoreStats.HP) shouldBe 30
        b.stats.getValue(CoreStats.HP) shouldBe 99
    }

    "unknown enemy archetype, enemy skill and archetype aiProfile are reported" {
        val result = validateCatalog(
            baseCatalog.copy(
                archetypes = baseCatalog.archetypes + boss.copy(id = ArchetypeId("weird"), aiProfile = AiProfileId("nope")),
                enemies = listOf(
                    enemy("lost", archetype = "ghost-archetype"),
                    enemy("caster").copy(skills = setOf(SkillId("unknown-skill"))),
                ),
            )
        )
        val errors = result.shouldBeInstanceOf<CatalogResult.Invalid>().errors
        errors.forOne {
            val e = it.shouldBeInstanceOf<CatalogError.UnknownReference>()
            e.fromDefinition shouldBe "lost"
            e.field shouldBe "archetypeId"
        }
        errors.forOne {
            val e = it.shouldBeInstanceOf<CatalogError.UnknownReference>()
            e.fromDefinition shouldBe "caster"
            e.field shouldBe "skills"
        }
        errors.forOne {
            val e = it.shouldBeInstanceOf<CatalogError.UnknownReference>()
            e.fromDefinition shouldBe "weird"
            e.field shouldBe "aiProfile"
        }
    }

    "duplicate archetype/enemy ids and negative reward tier are reported" {
        val result = validateCatalog(
            baseCatalog.copy(
                archetypes = listOf(boss, boss, commonMob.copy(rewardTier = -1)),
                enemies = listOf(enemy("bomb"), enemy("bomb")),
            )
        )
        val errors = result.shouldBeInstanceOf<CatalogResult.Invalid>().errors
        errors.forOne {
            val e = it.shouldBeInstanceOf<CatalogError.DuplicateId>()
            e.namespace shouldBe "archetypes"
            e.id shouldBe "boss"
        }
        errors.forOne {
            val e = it.shouldBeInstanceOf<CatalogError.DuplicateId>()
            e.namespace shouldBe "enemies"
            e.id shouldBe "bomb"
        }
        errors.forOne {
            val e = it.shouldBeInstanceOf<CatalogError.NegativeValue>()
            e.field shouldBe "rewardTier"
            e.value shouldBe -1
        }
    }
})
