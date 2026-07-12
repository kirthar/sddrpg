package io.github.kirthar.sddrpg.core.schedule

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.types.shouldBeInstanceOf

/** US1 scenario 2: marking a turn spent advances the schedule so it isn't offered again immediately. */
class MarkSpentTest : StringSpec({

    "immediately after markSpent, the untouched equal-speed combatant is reported ready next" {
        // Two combatants of equal speed reach the threshold together; the winner's
        // readiness drops well below threshold after markSpent while the other
        // combatant's readiness (never touched) is still at/above it -- so the other
        // one must win the very next query. (A single fast-vs-slow-vs-mid roster does
        // NOT universally guarantee this -- a combatant fast enough can legitimately
        // win two queries in a row, which is exactly what ReadinessProportionalityTest
        // demonstrates -- so this scenario needs an equal-speed pair to be unambiguous.)
        val state = freshEqualSpeedBattleState()
        var schedule = ActiveTimeBattleScheduler.initialSchedule(state)
        val first = ActiveTimeBattleScheduler.nextTurn(schedule, state)
            .shouldBeInstanceOf<ScheduleResult.Ready<AtbScheduleState>>()
        schedule = ActiveTimeBattleScheduler.markSpent(first.advancedSchedule, first.combatantId)

        val second = ActiveTimeBattleScheduler.nextTurn(schedule, state)
            .shouldBeInstanceOf<ScheduleResult.Ready<AtbScheduleState>>()
        second.combatantId shouldNotBe first.combatantId
    }

    "markSpent subtracts the threshold rather than resetting readiness to zero" {
        val state = freshScheduleBattleState()
        val schedule = ActiveTimeBattleScheduler.initialSchedule(state)
        val ready = ActiveTimeBattleScheduler.nextTurn(schedule, state)
            .shouldBeInstanceOf<ScheduleResult.Ready<AtbScheduleState>>()
        val readinessBeforeSpend = ready.advancedSchedule.readiness.getValue(ready.combatantId)
        val spent = ActiveTimeBattleScheduler.markSpent(ready.advancedSchedule, ready.combatantId)
        spent.readiness.getValue(ready.combatantId) shouldBe
            readinessBeforeSpend - ActiveTimeBattleScheduler.READY_THRESHOLD
    }
})
