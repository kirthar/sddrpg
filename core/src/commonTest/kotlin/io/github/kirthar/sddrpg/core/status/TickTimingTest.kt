package io.github.kirthar.sddrpg.core.status

import io.github.kirthar.sddrpg.core.model.CombatantId
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

/** FR-007: every active effect on every combatant ticks once per call, regardless of whose turn it was. */
class TickTimingTest : StringSpec({

    "an incapacitated combatant's own effect still ticks down even though it never gets a turn" {
        val battle = freshStatusBattleState()
        val cloud = battle.participants.first { it.combatant.displayName == "Cloud" }.combatant.id
        var state = applyStatusEffect(StatusEffectState(), cloud, StatusEffectId("stun"), statusEffectFixtureCatalog)

        // Nothing about "whose turn it was" is passed to tickStatusEffects -- a single
        // call ticks every active effect on every combatant uniformly (the clarified rule).
        state = tickStatusEffects(state, battle, statusEffectFixtureCatalog).newEffects
        state.active[cloud] shouldBe listOf(ActiveEffect(StatusEffectId("stun"), remainingDuration = 2))

        state = tickStatusEffects(state, battle, statusEffectFixtureCatalog).newEffects
        state = tickStatusEffects(state, battle, statusEffectFixtureCatalog).newEffects
        (state.active[cloud] ?: emptyList()).size shouldBe 0
    }
})
