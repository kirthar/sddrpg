package io.github.kirthar.sddrpg.core.status

import io.github.kirthar.sddrpg.core.model.CombatantId
import io.github.kirthar.sddrpg.core.model.CoreStats
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

/** US5 scenarios 1-2: reapplying an already-active effect refreshes duration without stacking magnitude. */
class ReapplicationTest : StringSpec({

    fun idOf(state: io.github.kirthar.sddrpg.core.action.BattleState, name: String): CombatantId =
        state.participants.first { it.combatant.displayName == name }.combatant.id

    "scenario 1: reapplying an effect at partial remaining duration resets it to full" {
        val battle = freshStatusBattleState()
        val cloud = idOf(battle, "Cloud")
        var state = applyStatusEffect(StatusEffectState(), cloud, StatusEffectId("defenseBuff"), statusEffectFixtureCatalog)
        state = tickStatusEffects(state, battle, statusEffectFixtureCatalog).newEffects // remaining = 2
        state = tickStatusEffects(state, battle, statusEffectFixtureCatalog).newEffects // remaining = 1

        state = applyStatusEffect(state, cloud, StatusEffectId("defenseBuff"), statusEffectFixtureCatalog)
        state.active[cloud] shouldBe listOf(ActiveEffect(StatusEffectId("defenseBuff"), remainingDuration = 3))
    }

    "scenario 2: a reapplied stat modifier's magnitude is unchanged, not doubled" {
        val battle = freshStatusBattleState()
        val bomb = idOf(battle, "Bomb")
        var state = applyStatusEffect(StatusEffectState(), bomb, StatusEffectId("defenseBuff"), statusEffectFixtureCatalog)
        state = applyStatusEffect(state, bomb, StatusEffectId("defenseBuff"), statusEffectFixtureCatalog)

        val effectiveBattle = deriveEffectiveBattleState(battle, state, statusEffectFixtureCatalog)
        val originalDefense = battle.find(bomb)!!.combatant.stats[CoreStats.DEFENSE]
        effectiveBattle.find(bomb)!!.combatant.stats[CoreStats.DEFENSE] shouldBe originalDefense + 5 // not +10
    }
})
