package io.github.kirthar.sddrpg.core.schedule

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.types.shouldBeInstanceOf

/** Edge case: a combatant with Speed <= 0 never becomes ready, and nextTurn never crashes. */
class ZeroSpeedTest : StringSpec({

    "a Speed-0 combatant is never reported ready, and nextTurn never throws" {
        val state = freshZeroSpeedBattleState()
        val statueId = state.participants.first { it.combatant.displayName == "Statue" }.combatant.id
        var schedule = ActiveTimeBattleScheduler.initialSchedule(state)

        repeat(50) {
            val ready = ActiveTimeBattleScheduler.nextTurn(schedule, state)
                .shouldBeInstanceOf<ScheduleResult.Ready<AtbScheduleState>>()
            ready.combatantId shouldNotBe statueId
            schedule = ActiveTimeBattleScheduler.markSpent(ready.advancedSchedule, ready.combatantId)
        }
    }

    "when every combatant has Speed 0, NoOneReady is reported rather than a crash" {
        val state = freshAllZeroSpeedBattleState()
        val schedule = ActiveTimeBattleScheduler.initialSchedule(state)
        ActiveTimeBattleScheduler.nextTurn(schedule, state).shouldBeInstanceOf<ScheduleResult.NoOneReady>()
    }
})
