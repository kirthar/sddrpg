package io.github.kirthar.sddrpg.core.status

import io.github.kirthar.sddrpg.core.model.CombatantId
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe

/** US1 scenarios 1-4: apply -> track -> tick -> expire lifecycle. */
class StatusEffectLifecycleTest : StringSpec({

    fun idOf(state: io.github.kirthar.sddrpg.core.action.BattleState, name: String): CombatantId =
        state.participants.first { it.combatant.displayName == name }.combatant.id

    "scenario 1: applying an effect makes it immediately active" {
        val battle = freshStatusBattleState()
        val cloud = idOf(battle, "Cloud")
        val state = applyStatusEffect(
            StatusEffectState(), cloud, StatusEffectId("poison"), statusEffectFixtureCatalog,
        )
        state.active[cloud] shouldBe listOf(ActiveEffect(StatusEffectId("poison"), remainingDuration = 3))
    }

    "scenario 2: one tick decrements remaining duration by exactly 1" {
        val battle = freshStatusBattleState()
        val cloud = idOf(battle, "Cloud")
        var state = applyStatusEffect(StatusEffectState(), cloud, StatusEffectId("defenseBuff"), statusEffectFixtureCatalog)
        val result = tickStatusEffects(state, battle, statusEffectFixtureCatalog)
        result.newEffects.active[cloud] shouldBe listOf(ActiveEffect(StatusEffectId("defenseBuff"), remainingDuration = 2))
    }

    "scenario 3: an effect at 1 remaining duration is gone after one more tick, no separate removal" {
        val battle = freshStatusBattleState()
        val cloud = idOf(battle, "Cloud")
        var state = applyStatusEffect(StatusEffectState(), cloud, StatusEffectId("attackDebuff"), statusEffectFixtureCatalog)
        // attackDebuff has duration 2: tick twice to exhaust it
        state = tickStatusEffects(state, battle, statusEffectFixtureCatalog).newEffects
        state = tickStatusEffects(state, battle, statusEffectFixtureCatalog).newEffects
        (state.active[cloud] ?: emptyList()).shouldBeEmpty()
    }

    "scenario 4: application after a resolved action needs no special wiring" {
        // Applying is just applyStatusEffect called with the resolved outcome's targetId --
        // no separate follow-up step exists or is needed.
        val battle = freshStatusBattleState()
        val bomb = idOf(battle, "Bomb")
        val state = applyStatusEffect(StatusEffectState(), bomb, StatusEffectId("poison"), statusEffectFixtureCatalog)
        state.active[bomb]?.size shouldBe 1
    }

    "removing an active effect makes it gone immediately" {
        val battle = freshStatusBattleState()
        val cloud = idOf(battle, "Cloud")
        var state = applyStatusEffect(StatusEffectState(), cloud, StatusEffectId("poison"), statusEffectFixtureCatalog)
        state = removeStatusEffect(state, cloud, StatusEffectId("poison"))
        (state.active[cloud] ?: emptyList()).shouldBeEmpty()
    }

    "removing an effect not currently active is an idempotent no-op" {
        val state = StatusEffectState()
        val cloud = CombatantId("c1")
        val result = removeStatusEffect(state, cloud, StatusEffectId("poison"))
        result shouldBe state
    }

    "F2: a stale effectId absent from the catalog is skipped gracefully during tick, not a crash" {
        val battle = freshStatusBattleState()
        val cloud = idOf(battle, "Cloud")
        val staleState = StatusEffectState(
            active = mapOf(cloud to listOf(ActiveEffect(StatusEffectId("does-not-exist"), remainingDuration = 2)))
        )
        val result = tickStatusEffects(staleState, battle, statusEffectFixtureCatalog)
        result.newEffects.active[cloud] shouldBe listOf(ActiveEffect(StatusEffectId("does-not-exist"), remainingDuration = 1))
    }
})
