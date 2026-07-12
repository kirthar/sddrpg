package io.github.kirthar.sddrpg.core.status

import io.github.kirthar.sddrpg.core.model.CombatantId
import io.github.kirthar.sddrpg.core.model.CoreStats
import io.github.kirthar.sddrpg.core.schedule.ActiveTimeBattleScheduler
import io.github.kirthar.sddrpg.core.schedule.ScheduleResult
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.types.shouldBeInstanceOf

/** US3 scenarios 1-3: incapacitation removes a combatant from the turn order. */
class IncapacitateTest : StringSpec({

    fun idOf(state: io.github.kirthar.sddrpg.core.action.BattleState, name: String): CombatantId =
        state.participants.first { it.combatant.displayName == name }.combatant.id

    "scenario 1: an incapacitated combatant's effective Speed is 0 and is never scheduled" {
        val battle = freshStatusBattleState()
        val cloud = idOf(battle, "Cloud")
        val effects = applyStatusEffect(StatusEffectState(), cloud, StatusEffectId("stun"), statusEffectFixtureCatalog)
        val effectiveBattle = deriveEffectiveBattleState(battle, effects, statusEffectFixtureCatalog)

        effectiveBattle.find(cloud)!!.combatant.stats[CoreStats.SPEED] shouldBe 0

        val schedule = ActiveTimeBattleScheduler.initialSchedule(effectiveBattle)
        repeat(30) {
            val ready = ActiveTimeBattleScheduler.nextTurn(schedule, effectiveBattle)
                .shouldBeInstanceOf<ScheduleResult.Ready<*>>()
            ready.combatantId shouldNotBe cloud
        }
    }

    "scenario 2: normal scheduling resumes once the effect ends" {
        val battle = freshStatusBattleState()
        val cloud = idOf(battle, "Cloud")
        var effects = applyStatusEffect(StatusEffectState(), cloud, StatusEffectId("stun"), statusEffectFixtureCatalog)

        // Tick past the stun's full duration (3).
        repeat(3) { effects = tickStatusEffects(effects, battle, statusEffectFixtureCatalog).newEffects }
        (effects.active[cloud] ?: emptyList()).isEmpty() shouldBe true

        val effectiveBattle = deriveEffectiveBattleState(battle, effects, statusEffectFixtureCatalog)
        effectiveBattle.find(cloud)!!.combatant.stats[CoreStats.SPEED] shouldBe battle.find(cloud)!!.combatant.stats[CoreStats.SPEED]
    }

    "scenario 3: among otherwise-eligible combatants, only non-incapacitated ones are ever reported" {
        val battle = freshStatusBattleState()
        val cloud = idOf(battle, "Cloud")
        val effects = applyStatusEffect(StatusEffectState(), cloud, StatusEffectId("stun"), statusEffectFixtureCatalog)
        val effectiveBattle = deriveEffectiveBattleState(battle, effects, statusEffectFixtureCatalog)
        val schedule = ActiveTimeBattleScheduler.initialSchedule(effectiveBattle)

        val seen = mutableSetOf<CombatantId>()
        var s = schedule
        repeat(30) {
            val ready = ActiveTimeBattleScheduler.nextTurn(s, effectiveBattle)
                .shouldBeInstanceOf<ScheduleResult.Ready<*>>()
            seen += ready.combatantId
            @Suppress("UNCHECKED_CAST")
            s = ActiveTimeBattleScheduler.markSpent(
                ready.advancedSchedule as io.github.kirthar.sddrpg.core.schedule.AtbScheduleState, ready.combatantId,
            )
        }
        seen shouldNotContain cloud
    }

    "F1 (analyze): a simultaneous Speed-boosting modifier cannot offset an active Incapacitate effect" {
        val battle = freshStatusBattleState()
        val cloud = idOf(battle, "Cloud")
        var effects = applyStatusEffect(StatusEffectState(), cloud, StatusEffectId("stun"), statusEffectFixtureCatalog)
        effects = applyStatusEffect(effects, cloud, StatusEffectId("speedBoost"), statusEffectFixtureCatalog)

        val effectiveBattle = deriveEffectiveBattleState(battle, effects, statusEffectFixtureCatalog)
        effectiveBattle.find(cloud)!!.combatant.stats[CoreStats.SPEED] shouldBe 0
    }
})
