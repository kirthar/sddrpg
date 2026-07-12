package io.github.kirthar.sddrpg.core.schedule

import io.github.kirthar.sddrpg.core.action.BattleState
import io.github.kirthar.sddrpg.core.model.CombatantId
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.types.shouldBeInstanceOf

/** US2 scenarios 1-3 plus FR-009/SC-005: defeated combatants are never scheduled. */
class DefeatedExclusionTest : StringSpec({

    fun idOf(state: BattleState, name: String): CombatantId =
        state.participants.first { it.combatant.displayName == name }.combatant.id

    fun withHealth(state: BattleState, id: CombatantId, current: Int): BattleState = BattleState(
        state.participants.map {
            if (it.combatant.id == id) it.copy(health = it.health.copy(current = current)) else it
        }
    )

    "scenario 1: a defeated combatant is never reported ready" {
        val fresh = freshScheduleBattleState()
        val fastId = idOf(fresh, "Fast")
        val state = withHealth(fresh, fastId, current = 0)
        val schedule = ActiveTimeBattleScheduler.initialSchedule(state)

        repeat(50) {
            val ready = ActiveTimeBattleScheduler.nextTurn(schedule, state)
                .shouldBeInstanceOf<ScheduleResult.Ready<AtbScheduleState>>()
            ready.combatantId shouldNotBe fastId
        }
    }

    "scenario 2: a combatant defeated mid-battle is excluded starting from the next query" {
        val state = freshScheduleBattleState()
        val fastId = idOf(state, "Fast")
        var schedule = ActiveTimeBattleScheduler.initialSchedule(state)

        val first = ActiveTimeBattleScheduler.nextTurn(schedule, state)
            .shouldBeInstanceOf<ScheduleResult.Ready<AtbScheduleState>>()
        first.combatantId shouldBe fastId // fastest combatant, ready first as usual
        schedule = ActiveTimeBattleScheduler.markSpent(first.advancedSchedule, fastId)

        // Fast is defeated right after acting.
        val defeatedState = withHealth(state, fastId, current = 0)
        repeat(50) {
            val ready = ActiveTimeBattleScheduler.nextTurn(schedule, defeatedState)
                .shouldBeInstanceOf<ScheduleResult.Ready<AtbScheduleState>>()
            ready.combatantId shouldNotBe fastId
            schedule = ActiveTimeBattleScheduler.markSpent(ready.advancedSchedule, ready.combatantId)
        }
    }

    "scenario 3: once one side is fully defeated, only the remaining side is ever reported" {
        val fresh = freshScheduleBattleState()
        val goblinId = idOf(fresh, "Goblin")
        val state = withHealth(fresh, goblinId, current = 0)
        var schedule = ActiveTimeBattleScheduler.initialSchedule(state)

        repeat(50) {
            val ready = ActiveTimeBattleScheduler.nextTurn(schedule, state)
                .shouldBeInstanceOf<ScheduleResult.Ready<AtbScheduleState>>()
            ready.combatantId shouldNotBe goblinId
            schedule = ActiveTimeBattleScheduler.markSpent(ready.advancedSchedule, ready.combatantId)
        }
    }

    "FR-009/SC-005: when every combatant is defeated, NoOneReady is reported, not an error" {
        val fresh = freshScheduleBattleState()
        var state = fresh
        for (name in listOf("Fast", "Slow", "Goblin")) {
            state = withHealth(state, idOf(state, name), current = 0)
        }
        val schedule = ActiveTimeBattleScheduler.initialSchedule(state)
        ActiveTimeBattleScheduler.nextTurn(schedule, state).shouldBeInstanceOf<ScheduleResult.NoOneReady>()
    }
})
