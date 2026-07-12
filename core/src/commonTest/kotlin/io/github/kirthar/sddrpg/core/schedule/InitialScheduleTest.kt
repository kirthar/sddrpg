package io.github.kirthar.sddrpg.core.schedule

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.maps.shouldHaveSize
import io.kotest.matchers.shouldBe

class InitialScheduleTest : StringSpec({

    "every participant gets a readiness entry of 0" {
        val state = freshScheduleBattleState()
        val schedule = ActiveTimeBattleScheduler.initialSchedule(state)
        schedule.readiness shouldHaveSize state.participants.size
        state.participants.forEach { participant ->
            schedule.readiness[participant.combatant.id] shouldBe 0
        }
    }
})
