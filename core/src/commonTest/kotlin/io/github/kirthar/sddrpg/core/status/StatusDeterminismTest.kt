package io.github.kirthar.sddrpg.core.status

import io.github.kirthar.sddrpg.core.model.CombatantId
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.element
import io.kotest.property.checkAll

/** SC-007: the same starting state + the same apply/tick/remove sequence always replays identically. */
class StatusDeterminismTest : StringSpec({

    fun runSequence(steps: List<StatusEffectId>): Pair<StatusEffectState, io.github.kirthar.sddrpg.core.action.BattleState> {
        val battle = freshStatusBattleState()
        val cloud = battle.participants.first { it.combatant.displayName == "Cloud" }.combatant.id
        var effects = StatusEffectState()
        var state = battle
        for (effectId in steps) {
            effects = applyStatusEffect(effects, cloud, effectId, statusEffectFixtureCatalog)
            val tick = tickStatusEffects(effects, state, statusEffectFixtureCatalog)
            state = tick.newBattle
            effects = tick.newEffects
        }
        return effects to state
    }

    "property: replaying the same apply/tick sequence twice yields structurally identical results" {
        checkAll(
            Arb.element(listOf("poison", "defenseBuff", "stun", "attackDebuff")),
            Arb.element(listOf("poison", "defenseBuff", "stun", "attackDebuff")),
            Arb.element(listOf("poison", "defenseBuff", "stun", "attackDebuff")),
        ) { a, b, c ->
            val steps = listOf(StatusEffectId(a), StatusEffectId(b), StatusEffectId(c))
            val first = runSequence(steps)
            val second = runSequence(steps)
            first shouldBe second
        }
    }
})
