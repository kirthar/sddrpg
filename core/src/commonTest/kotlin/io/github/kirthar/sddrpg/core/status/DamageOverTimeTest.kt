package io.github.kirthar.sddrpg.core.status

import io.github.kirthar.sddrpg.core.model.CombatantId
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

/** US4 scenarios 1-3: damage-over-time harms its target automatically, no actor involved. */
class DamageOverTimeTest : StringSpec({

    fun idOf(state: io.github.kirthar.sddrpg.core.action.BattleState, name: String): CombatantId =
        state.participants.first { it.combatant.displayName == name }.combatant.id

    "scenario 1: each tick reduces health by the documented per-tick amount, no action submitted" {
        val battle = freshStatusBattleState()
        val cloud = idOf(battle, "Cloud")
        val effects = applyStatusEffect(StatusEffectState(), cloud, StatusEffectId("poison"), statusEffectFixtureCatalog)
        val before = battle.find(cloud)!!.health.current

        val result = tickStatusEffects(effects, battle, statusEffectFixtureCatalog)
        result.newBattle.find(cloud)!!.health.current shouldBe before - 5
    }

    "scenario 2: a tick that would go below zero clamps at zero" {
        val fresh = freshStatusBattleState()
        val cloud = idOf(fresh, "Cloud")
        val nearDeath = io.github.kirthar.sddrpg.core.action.BattleState(
            fresh.participants.map {
                if (it.combatant.id == cloud) it.copy(health = it.health.copy(current = 3)) else it
            }
        )
        val effects = applyStatusEffect(StatusEffectState(), cloud, StatusEffectId("poison"), statusEffectFixtureCatalog)
        val result = tickStatusEffects(effects, nearDeath, statusEffectFixtureCatalog)
        result.newBattle.find(cloud)!!.health.current shouldBe 0
    }

    "scenario 3: a tick while already at zero health has no further effect" {
        val fresh = freshStatusBattleState()
        val cloud = idOf(fresh, "Cloud")
        val defeated = io.github.kirthar.sddrpg.core.action.BattleState(
            fresh.participants.map {
                if (it.combatant.id == cloud) it.copy(health = it.health.copy(current = 0)) else it
            }
        )
        val effects = applyStatusEffect(StatusEffectState(), cloud, StatusEffectId("poison"), statusEffectFixtureCatalog)
        val result = tickStatusEffects(effects, defeated, statusEffectFixtureCatalog)
        result.newBattle.find(cloud)!!.health.current shouldBe 0
    }

    "research R7: the tick that brings duration to 0 still deals its damage" {
        val battle = freshStatusBattleState()
        val cloud = idOf(battle, "Cloud")
        var effects = applyStatusEffect(StatusEffectState(), cloud, StatusEffectId("poison"), statusEffectFixtureCatalog) // duration 3
        var currentBattle = battle

        repeat(3) {
            val result = tickStatusEffects(effects, currentBattle, statusEffectFixtureCatalog)
            currentBattle = result.newBattle
            effects = result.newEffects
        }

        val expectedHealth = battle.find(cloud)!!.health.current - (5 * 3)
        currentBattle.find(cloud)!!.health.current shouldBe expectedHealth
        (effects.active[cloud] ?: emptyList()).isEmpty() shouldBe true
    }
})
