package io.github.kirthar.sddrpg.core.ai

import io.github.kirthar.sddrpg.core.action.BattleState
import io.github.kirthar.sddrpg.core.action.toBattleState
import io.github.kirthar.sddrpg.core.catalog.ArchetypeDefinition
import io.github.kirthar.sddrpg.core.catalog.Catalog
import io.github.kirthar.sddrpg.core.catalog.CatalogResult
import io.github.kirthar.sddrpg.core.catalog.ClassDefinition
import io.github.kirthar.sddrpg.core.catalog.CommandKind
import io.github.kirthar.sddrpg.core.catalog.EnemyDefinition
import io.github.kirthar.sddrpg.core.catalog.ValidatedCatalog
import io.github.kirthar.sddrpg.core.catalog.validateCatalog
import io.github.kirthar.sddrpg.core.combatant.Allegiance
import io.github.kirthar.sddrpg.core.combatant.Combatant
import io.github.kirthar.sddrpg.core.combatant.RosterBuilder
import io.github.kirthar.sddrpg.core.combatant.fixtureCatalog
import io.github.kirthar.sddrpg.core.model.AiProfileId
import io.github.kirthar.sddrpg.core.model.ArchetypeId
import io.github.kirthar.sddrpg.core.model.CharacterId
import io.github.kirthar.sddrpg.core.model.CombatantId
import io.github.kirthar.sddrpg.core.model.EnemyId
import io.github.kirthar.sddrpg.core.model.SkillId
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

/**
 * spec.md's "Automatic Action Rule": first usable command in a fixed preference
 * order, target the living opposing combatant with lowest current health (ties by
 * participant order), skill selection is deterministic regardless of Set order.
 */
class AutomaticActionRuleTest : StringSpec({

    val preference = listOf(CommandKind.ATTACK, CommandKind.SKILL, CommandKind.MAGIC)

    fun freshState(): BattleState = RosterBuilder(fixtureCatalog)
        .addPartyMember(CharacterId("cloud"), level = 1)
        .addEnemy(EnemyId("bomb"))
        .build()
        .toBattleState()

    fun withHealth(state: BattleState, id: CombatantId, current: Int): BattleState = BattleState(
        state.participants.map {
            if (it.combatant.id == id) it.copy(health = it.health.copy(current = current)) else it
        }
    )

    fun idOf(state: BattleState, displayName: String): CombatantId =
        state.participants.first { it.combatant.displayName == displayName }.combatant.id

    "selects the first command in preference order that the actor's capabilities grant" {
        val state = freshState()
        val cloud = state.participants.first { it.combatant.displayName == "Cloud" }.combatant
        selectAutomaticCommand(cloud, preference) shouldBe CommandKind.ATTACK
    }

    "selects a later preferred command when an earlier one isn't granted" {
        val state = freshState()
        val cloud = state.participants.first { it.combatant.displayName == "Cloud" }.combatant
        selectAutomaticCommand(cloud, listOf(CommandKind.MAGIC, CommandKind.ATTACK)) shouldBe CommandKind.ATTACK
    }

    "returns null when the actor's capabilities grant none of the preferred commands" {
        val state = freshState()
        val cloud = state.participants.first { it.combatant.displayName == "Cloud" }.combatant
        selectAutomaticCommand(cloud, listOf(CommandKind.ITEM, CommandKind.SUMMON)) shouldBe null
    }

    "selects the opposing combatant with the lowest current health" {
        val state = freshState()
        val cloudId = idOf(state, "Cloud")
        val bombId = idOf(state, "Bomb")
        val damaged = withHealth(state, bombId, current = 1)
        selectAutomaticTarget(damaged, cloudId) shouldBe bombId
    }

    "ties among equal-health opposing combatants break by earliest participant order" {
        val threeWay = RosterBuilder(fixtureCatalog)
            .addPartyMember(CharacterId("cloud"), level = 1)
            .addEnemy(EnemyId("bomb"))
            .addEnemy(EnemyId("bomb"))
            .build()
            .toBattleState()
        val cloudId = threeWay.participants.first { it.combatant.displayName == "Cloud" }.combatant.id
        val firstBombId = threeWay.participants.first { it.combatant.displayName == "Bomb" }.combatant.id
        selectAutomaticTarget(threeWay, cloudId) shouldBe firstBombId
    }

    "excludes already-defeated opposing combatants from target selection" {
        val threeWay = RosterBuilder(fixtureCatalog)
            .addPartyMember(CharacterId("cloud"), level = 1)
            .addEnemy(EnemyId("bomb"))
            .addEnemy(EnemyId("bomb"))
            .build()
            .toBattleState()
        val cloudId = threeWay.participants.first { it.combatant.displayName == "Cloud" }.combatant.id
        val firstBombId = threeWay.participants.first { it.combatant.displayName == "Bomb" }.combatant.id
        val secondBombId = threeWay.participants.last { it.combatant.displayName == "Bomb" }.combatant.id
        val firstDefeated = withHealth(threeWay, firstBombId, current = 0)
        selectAutomaticTarget(firstDefeated, cloudId) shouldBe secondBombId
    }

    "returns null when the actor is unknown to the battle" {
        val state = freshState()
        selectAutomaticTarget(state, CombatantId("nonexistent")) shouldBe null
    }

    "returns null when no living opposing combatant remains" {
        val state = freshState()
        val cloudId = idOf(state, "Cloud")
        val bombId = idOf(state, "Bomb")
        val allEnemiesDown = withHealth(state, bombId, current = 0)
        selectAutomaticTarget(allEnemiesDown, cloudId) shouldBe null
    }

    "skill selection is deterministic (lexicographically least skill id) regardless of Set iteration order" {
        val ranger = ClassDefinition(
            id = io.github.kirthar.sddrpg.core.model.ClassId("ranger"),
            displayName = "Ranger",
            skills = setOf(SkillId("snipe"), SkillId("quickshot")),
            commands = setOf(CommandKind.SKILL),
        )
        val raw = Catalog(
            knownSkills = setOf(SkillId("snipe"), SkillId("quickshot")),
            knownAiProfiles = setOf(AiProfileId("aggressive")),
            classes = listOf(ranger),
            characters = listOf(
                io.github.kirthar.sddrpg.core.catalog.CharacterDefinition(
                    CharacterId("rango"), "Rango", io.github.kirthar.sddrpg.core.model.ClassId("ranger"),
                    io.github.kirthar.sddrpg.core.combatant.completeStats(10),
                ),
            ),
            archetypes = listOf(ArchetypeDefinition(ArchetypeId("common"), "Common", AiProfileId("aggressive"), rewardTier = 0)),
            enemies = listOf(EnemyDefinition(EnemyId("bomb"), "Bomb", ArchetypeId("common"), stats = io.github.kirthar.sddrpg.core.combatant.completeStats(10))),
        )
        val catalog: ValidatedCatalog = validateCatalog(raw).let { it as CatalogResult.Valid }.catalog
        val rango: Combatant = RosterBuilder(catalog).addPartyMember(CharacterId("rango"), level = 1).build().combatants.first()

        selectAutomaticSkill(rango) shouldBe SkillId("quickshot")
    }

    "skill selection returns null when the actor has no skills" {
        val skillless = ClassDefinition(
            id = io.github.kirthar.sddrpg.core.model.ClassId("commoner"),
            displayName = "Commoner",
            commands = setOf(CommandKind.ATTACK),
        )
        val raw = Catalog(
            classes = listOf(skillless),
            characters = listOf(
                io.github.kirthar.sddrpg.core.catalog.CharacterDefinition(
                    CharacterId("nobody"), "Nobody", io.github.kirthar.sddrpg.core.model.ClassId("commoner"),
                    io.github.kirthar.sddrpg.core.combatant.completeStats(10),
                ),
            ),
        )
        val catalog: ValidatedCatalog = validateCatalog(raw).let { it as CatalogResult.Valid }.catalog
        val nobody: Combatant = RosterBuilder(catalog).addPartyMember(CharacterId("nobody"), level = 1).build().combatants.first()

        selectAutomaticSkill(nobody) shouldBe null
    }

    "target selection is pure and deterministic: same battle and actor always return the same result" {
        val state = freshState()
        val cloudId = idOf(state, "Cloud")
        selectAutomaticTarget(state, cloudId) shouldBe selectAutomaticTarget(state, cloudId)
    }
})
