package io.github.kirthar.sddrpg.core.action

import io.github.kirthar.sddrpg.core.catalog.ArchetypeDefinition
import io.github.kirthar.sddrpg.core.catalog.Catalog
import io.github.kirthar.sddrpg.core.catalog.CatalogResult
import io.github.kirthar.sddrpg.core.catalog.CharacterDefinition
import io.github.kirthar.sddrpg.core.catalog.ClassDefinition
import io.github.kirthar.sddrpg.core.catalog.CommandKind
import io.github.kirthar.sddrpg.core.catalog.EnemyDefinition
import io.github.kirthar.sddrpg.core.catalog.ValidatedCatalog
import io.github.kirthar.sddrpg.core.catalog.validateCatalog
import io.github.kirthar.sddrpg.core.combatant.RosterBuilder
import io.github.kirthar.sddrpg.core.model.AiProfileId
import io.github.kirthar.sddrpg.core.model.Affinity
import io.github.kirthar.sddrpg.core.model.ArchetypeId
import io.github.kirthar.sddrpg.core.model.CharacterId
import io.github.kirthar.sddrpg.core.model.ClassId
import io.github.kirthar.sddrpg.core.model.CoreStats
import io.github.kirthar.sddrpg.core.model.ElementId
import io.github.kirthar.sddrpg.core.model.EnemyId
import io.github.kirthar.sddrpg.core.model.SkillId
import io.github.kirthar.sddrpg.core.model.StatId
import io.kotest.matchers.types.shouldBeInstanceOf

/** Shared action-resolution fixture: a validated catalog + a fresh two-party/one-enemy battle state. */

fun completeStats(v: Int = 20): Map<StatId, Int> = CoreStats.ALL.associateWith { v }

val fireElement = ElementId("fire")

val actionFixtureCatalog: ValidatedCatalog by lazy {
    val warrior = ClassDefinition(
        id = ClassId("warrior"),
        displayName = "Warrior",
        skills = setOf(SkillId("cleave")),
        commands = setOf(CommandKind.ATTACK, CommandKind.SKILL, CommandKind.DEFEND, CommandKind.ITEM),
    )
    val mage = ClassDefinition(
        id = ClassId("mage"),
        displayName = "Mage",
        skills = setOf(SkillId("fira"), SkillId("cura")),
        commands = setOf(CommandKind.ATTACK, CommandKind.MAGIC, CommandKind.SKILL),
    )
    val raw = Catalog(
        elements = setOf(fireElement),
        knownSkills = setOf(SkillId("cleave"), SkillId("fira"), SkillId("cura")),
        knownAiProfiles = setOf(AiProfileId("aggressive")),
        classes = listOf(warrior, mage),
        archetypes = listOf(
            ArchetypeDefinition(ArchetypeId("common"), "Common", AiProfileId("aggressive"), rewardTier = 0),
        ),
        characters = listOf(
            CharacterDefinition(CharacterId("cloud"), "Cloud", ClassId("warrior"), completeStats()),
            CharacterDefinition(CharacterId("aerith"), "Aerith", ClassId("mage"), completeStats(10)),
        ),
        enemies = listOf(
            EnemyDefinition(
                id = EnemyId("bomb"),
                displayName = "Bomb",
                archetypeId = ArchetypeId("common"),
                stats = completeStats(15),
                affinities = mapOf(fireElement to Affinity.WEAKNESS),
            ),
            EnemyDefinition(
                id = EnemyId("flan"),
                displayName = "Flan",
                archetypeId = ArchetypeId("common"),
                stats = completeStats(15),
                affinities = mapOf(fireElement to Affinity.ABSORPTION),
            ),
            EnemyDefinition(
                id = EnemyId("pudding"),
                displayName = "Pudding",
                archetypeId = ArchetypeId("common"),
                stats = completeStats(15),
                affinities = mapOf(fireElement to Affinity.RESISTANCE),
            ),
        ),
    )
    validateCatalog(raw).shouldBeInstanceOf<CatalogResult.Valid>().catalog
}

/**
 * cloud (warrior), aerith (mage), bomb (fire-weak enemy), flan (fire-absorbing enemy),
 * pudding (fire-resistant enemy).
 */
fun freshBattleState(): BattleState = RosterBuilder(actionFixtureCatalog)
    .addPartyMember(CharacterId("cloud"), level = 1)
    .addPartyMember(CharacterId("aerith"), level = 1)
    .addEnemy(EnemyId("bomb"))
    .addEnemy(EnemyId("flan"))
    .addEnemy(EnemyId("pudding"))
    .build()
    .toBattleState()
