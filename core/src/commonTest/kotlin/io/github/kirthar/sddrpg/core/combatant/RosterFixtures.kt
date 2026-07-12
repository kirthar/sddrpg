package io.github.kirthar.sddrpg.core.combatant

import io.github.kirthar.sddrpg.core.catalog.ArchetypeDefinition
import io.github.kirthar.sddrpg.core.catalog.Catalog
import io.github.kirthar.sddrpg.core.catalog.CatalogResult
import io.github.kirthar.sddrpg.core.catalog.CharacterDefinition
import io.github.kirthar.sddrpg.core.catalog.ClassDefinition
import io.github.kirthar.sddrpg.core.catalog.CommandKind
import io.github.kirthar.sddrpg.core.catalog.EnemyDefinition
import io.github.kirthar.sddrpg.core.catalog.ValidatedCatalog
import io.github.kirthar.sddrpg.core.catalog.validateCatalog
import io.github.kirthar.sddrpg.core.model.AiProfileId
import io.github.kirthar.sddrpg.core.model.ArchetypeId
import io.github.kirthar.sddrpg.core.model.CharacterId
import io.github.kirthar.sddrpg.core.model.ClassId
import io.github.kirthar.sddrpg.core.model.CoreStats
import io.github.kirthar.sddrpg.core.model.EnemyId
import io.github.kirthar.sddrpg.core.model.GrowthCurve
import io.github.kirthar.sddrpg.core.model.LimitBreakId
import io.github.kirthar.sddrpg.core.model.SkillId
import io.github.kirthar.sddrpg.core.model.StatId
import io.kotest.matchers.types.shouldBeInstanceOf

/** Shared US3 fixture: one validated catalog with classes, characters and enemies. */

fun completeStats(v: Int = 10): Map<StatId, Int> = CoreStats.ALL.associateWith { v }

val fixtureCatalog: ValidatedCatalog by lazy {
    val warrior = ClassDefinition(
        id = ClassId("warrior"),
        displayName = "Warrior",
        skills = setOf(SkillId("cleave")),
        commands = setOf(CommandKind.ATTACK, CommandKind.DEFEND),
        growth = mapOf(CoreStats.HP to GrowthCurve.Linear(base = 100, perLevel = 10)),
        limitBreaks = setOf(LimitBreakId("braver")),
    )
    val mage = ClassDefinition(
        id = ClassId("mage"),
        displayName = "Mage",
        skills = setOf(SkillId("fira")),
        commands = setOf(CommandKind.ATTACK, CommandKind.MAGIC),
        growth = mapOf(CoreStats.HP to GrowthCurve.Linear(base = 60, perLevel = 5)),
    )
    val raw = Catalog(
        knownSkills = setOf(SkillId("cleave"), SkillId("fira"), SkillId("self-destruct")),
        knownLimitBreaks = setOf(LimitBreakId("braver")),
        knownAiProfiles = setOf(AiProfileId("aggressive"), AiProfileId("guardian")),
        classes = listOf(warrior, mage),
        archetypes = listOf(
            ArchetypeDefinition(ArchetypeId("boss"), "Boss", AiProfileId("aggressive"), rewardTier = 3),
        ),
        characters = listOf(
            CharacterDefinition(CharacterId("cloud"), "Cloud", ClassId("warrior"), completeStats(10)),
            CharacterDefinition(CharacterId("ifrit"), "Ifrit", ClassId("mage"), completeStats(30)),
        ),
        enemies = listOf(
            EnemyDefinition(
                id = EnemyId("bomb"),
                displayName = "Bomb",
                archetypeId = ArchetypeId("boss"),
                skills = setOf(SkillId("self-destruct")),
                stats = completeStats(50),
            ),
        ),
    )
    validateCatalog(raw).shouldBeInstanceOf<CatalogResult.Valid>().catalog
}
