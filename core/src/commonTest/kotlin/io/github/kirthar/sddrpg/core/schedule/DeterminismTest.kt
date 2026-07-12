package io.github.kirthar.sddrpg.core.schedule

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf

/** FR-006/SC-003: the same battle configuration and turn-consumption sequence always replays identically. */
class DeterminismTest : StringSpec({

    fun runSequence(steps: Int): List<io.github.kirthar.sddrpg.core.model.CombatantId> {
        val state = freshScheduleBattleState()
        var schedule = ActiveTimeBattleScheduler.initialSchedule(state)
        val order = mutableListOf<io.github.kirthar.sddrpg.core.model.CombatantId>()
        repeat(steps) {
            val ready = ActiveTimeBattleScheduler.nextTurn(schedule, state)
                .shouldBeInstanceOf<ScheduleResult.Ready<AtbScheduleState>>()
            order += ready.combatantId
            schedule = ActiveTimeBattleScheduler.markSpent(ready.advancedSchedule, ready.combatantId)
        }
        return order
    }

    "replaying the same battle and consumption sequence yields the identical turn order" {
        val first = runSequence(100)
        val second = runSequence(100)
        first shouldBe second
    }
})
