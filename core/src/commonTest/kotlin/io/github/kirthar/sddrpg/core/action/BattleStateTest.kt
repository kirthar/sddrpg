package io.github.kirthar.sddrpg.core.action

import io.github.kirthar.sddrpg.core.combatant.CombatantKind
import io.github.kirthar.sddrpg.core.model.CoreStats
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe

class BattleStateTest : StringSpec({

    "toBattleState wraps every roster combatant with current = maximum health" {
        val state = freshBattleState()
        state.participants shouldHaveSize 5
        state.participants.forEach { participant ->
            val maxHp = participant.combatant.stats[CoreStats.HP]
            participant.health.maximum shouldBe maxHp
            participant.health.current shouldBe maxHp
        }
    }

    "isDefeated is false when current health is above zero" {
        val state = freshBattleState()
        state.participants.forEach { it.isDefeated shouldBe false }
    }

    "isDefeated is true when current health is exactly zero" {
        val participant = freshBattleState().participants.first()
        val defeated = participant.copy(health = participant.health.copy(current = 0))
        defeated.isDefeated shouldBe true
    }

    "party members and enemies are wrapped identically" {
        val state = freshBattleState()
        val kinds = state.participants.map { it.combatant.kind }
        kinds shouldBe listOf(
            CombatantKind.PARTY_MEMBER, CombatantKind.PARTY_MEMBER,
            CombatantKind.ENEMY, CombatantKind.ENEMY, CombatantKind.ENEMY,
        )
    }
})
