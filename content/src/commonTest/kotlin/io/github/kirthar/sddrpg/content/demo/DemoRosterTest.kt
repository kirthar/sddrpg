package io.github.kirthar.sddrpg.content.demo

import io.github.kirthar.sddrpg.core.catalog.CommandKind
import io.github.kirthar.sddrpg.core.combatant.CombatantKind
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldNotContain

/**
 * spec 008 research.md R5: `RosterBuilder.addEnemy` always produces an empty
 * `capabilities.commands`, so this feature grants the shipped demo's enemy the
 * commands its declared skills actually need -- without touching any spec 001-007 file.
 */
class DemoRosterTest : StringSpec({

    "the bomb enemy is granted CommandKind.ATTACK" {
        val battle = buildDemoBattleState()
        val bomb = battle.participants.first { it.combatant.kind == CombatantKind.ENEMY }.combatant
        bomb.capabilities.commands shouldContain CommandKind.ATTACK
    }

    "an enemy with no declared skills is NOT granted CommandKind.SKILL" {
        val battle = buildDemoBattleState()
        val bomb = battle.participants.first { it.combatant.kind == CombatantKind.ENEMY }.combatant
        bomb.capabilities.skills shouldBe emptySet()
        bomb.capabilities.commands shouldNotContain CommandKind.SKILL
    }

    "non-enemy participants are unaffected: cloud's capabilities are unchanged from the raw roster" {
        val battle = buildDemoBattleState()
        val cloud = battle.participants.first { it.combatant.displayName == "Cloud" }.combatant
        cloud.capabilities.commands shouldBe setOf(CommandKind.ATTACK, CommandKind.SKILL, CommandKind.SUMMON)
    }

    "repeated calls produce structurally-equal battle states (deterministic assembly)" {
        val first = buildDemoBattleState()
        val second = buildDemoBattleState()
        first shouldBe second
    }

    "the demo roster has exactly cloud, aerith, and the bomb" {
        val battle = buildDemoBattleState()
        battle.participants.map { it.combatant.displayName }.toSet() shouldBe setOf("Cloud", "Aerith", "Bomb")
    }
})
