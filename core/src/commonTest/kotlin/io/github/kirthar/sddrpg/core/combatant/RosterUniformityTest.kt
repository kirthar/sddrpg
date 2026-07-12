package io.github.kirthar.sddrpg.core.combatant

import io.github.kirthar.sddrpg.core.model.AiProfileId
import io.github.kirthar.sddrpg.core.catalog.CommandKind
import io.github.kirthar.sddrpg.core.model.CharacterId
import io.github.kirthar.sddrpg.core.model.CoreStats
import io.github.kirthar.sddrpg.core.model.ElementId
import io.github.kirthar.sddrpg.core.model.EnemyId
import io.github.kirthar.sddrpg.core.model.SkillId
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

/** US3 scenarios 1–3: one uniform battle surface for all combatant kinds. */
class RosterUniformityTest : StringSpec({

    fun mixedRoster(): Roster = RosterBuilder(fixtureCatalog)
        .addPartyMember(CharacterId("cloud"), level = 3)
        .addEnemy(EnemyId("bomb"))
        .addTemporaryAlly(CharacterId("ifrit"), level = 5, aiProfile = AiProfileId("guardian"))
        .build()

    "scenario 1: every combatant kind exposes the same attribute surface" {
        val roster = mixedRoster()
        roster.combatants.size shouldBe 3
        roster.combatants.forEach { combatant ->
            // Uniformity: every attribute of the shared surface is readable for every kind.
            combatant.displayName.isNotEmpty() shouldBe true
            (combatant.stats[CoreStats.HP] >= 0) shouldBe true
            combatant.affinityTo(ElementId("fire")) // total, never throws
            combatant.capabilities // derived, never null
            combatant.decisionSource // configured, never null
        }
        roster.combatants.map { it.kind } shouldBe listOf(
            CombatantKind.PARTY_MEMBER, CombatantKind.ENEMY, CombatantKind.TEMPORARY_ALLY,
        )
    }

    "scenario 2: a temporary ally fights on the player's side but decides via AI" {
        val ally = mixedRoster().combatants[2]
        ally.kind shouldBe CombatantKind.TEMPORARY_ALLY
        ally.allegiance shouldBe Allegiance.PLAYER
        ally.decisionSource shouldBe DecisionSource.AiProfile(AiProfileId("guardian"))
    }

    "scenario 3 / SC-004: swapping the decision source changes nothing else" {
        val cloud = mixedRoster().combatants[0]
        val asAi = cloud.withDecisionSource(DecisionSource.AiProfile(AiProfileId("aggressive")))
        asAi.decisionSource shouldBe DecisionSource.AiProfile(AiProfileId("aggressive"))
        asAi.id shouldBe cloud.id
        asAi.kind shouldBe cloud.kind
        asAi.allegiance shouldBe cloud.allegiance
        asAi.stats shouldBe cloud.stats
        asAi.capabilities shouldBe cloud.capabilities
    }

    "capabilities derive from class for characters and from definition+archetype for enemies" {
        val roster = mixedRoster()
        val cloud = roster.combatants[0]
        cloud.capabilities.skills shouldBe setOf(SkillId("cleave"))
        cloud.capabilities.commands shouldBe setOf(CommandKind.ATTACK, CommandKind.DEFEND)

        val bomb = roster.combatants[1]
        bomb.kind shouldBe CombatantKind.ENEMY
        bomb.allegiance shouldBe Allegiance.OPPONENT
        bomb.capabilities.skills shouldBe setOf(SkillId("self-destruct"))
        // Enemies default to their archetype's AI profile.
        bomb.decisionSource shouldBe DecisionSource.AiProfile(AiProfileId("aggressive"))
    }

    "character stats are level-derived through the class growth curve (FR-008a)" {
        val roster = mixedRoster()
        roster.combatants[0].stats[CoreStats.HP] shouldBe 120 // warrior: 100 + 10 * (3-1)
        roster.combatants[2].stats[CoreStats.HP] shouldBe 80  // mage:    60 + 5 * (5-1)
        roster.combatants[1].stats[CoreStats.HP] shouldBe 50  // enemy: fixed definition stats
    }
})
