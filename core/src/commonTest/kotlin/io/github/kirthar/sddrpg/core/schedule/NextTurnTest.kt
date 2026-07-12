package io.github.kirthar.sddrpg.core.schedule

import io.github.kirthar.sddrpg.core.model.CombatantId
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf

/** US1 scenarios 1 and 4: a single ready combatant, available from the very start. */
class NextTurnTest : StringSpec({

    fun idOf(state: io.github.kirthar.sddrpg.core.action.BattleState, name: String): CombatantId =
        state.participants.first { it.combatant.displayName == name }.combatant.id

    "scenario 1: nextTurn reports exactly one ready combatant" {
        val state = freshScheduleBattleState()
        val schedule = ActiveTimeBattleScheduler.initialSchedule(state)
        val result = ActiveTimeBattleScheduler.nextTurn(schedule, state)
        result.shouldBeInstanceOf<ScheduleResult.Ready<AtbScheduleState>>()
    }

    "scenario 1: the fastest combatant is ready first from a fresh schedule" {
        val state = freshScheduleBattleState()
        val schedule = ActiveTimeBattleScheduler.initialSchedule(state)
        val result = ActiveTimeBattleScheduler.nextTurn(schedule, state)
            .shouldBeInstanceOf<ScheduleResult.Ready<AtbScheduleState>>()
        result.combatantId shouldBe idOf(state, "Fast")
    }

    "scenario 4: readiness is available before any turn has ever been granted" {
        val state = freshScheduleBattleState()
        val schedule = ActiveTimeBattleScheduler.initialSchedule(state)
        // No markSpent has been called yet -- this is the very first query.
        ActiveTimeBattleScheduler.nextTurn(schedule, state)
            .shouldBeInstanceOf<ScheduleResult.Ready<AtbScheduleState>>()
    }

    "querying twice without consuming reports the same combatant both times" {
        val state = freshScheduleBattleState()
        val schedule = ActiveTimeBattleScheduler.initialSchedule(state)
        val first = ActiveTimeBattleScheduler.nextTurn(schedule, state)
            .shouldBeInstanceOf<ScheduleResult.Ready<AtbScheduleState>>()
        val second = ActiveTimeBattleScheduler.nextTurn(schedule, state)
            .shouldBeInstanceOf<ScheduleResult.Ready<AtbScheduleState>>()
        first.combatantId shouldBe second.combatantId
    }
})
