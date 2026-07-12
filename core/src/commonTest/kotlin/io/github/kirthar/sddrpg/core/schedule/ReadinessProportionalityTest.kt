package io.github.kirthar.sddrpg.core.schedule

import io.github.kirthar.sddrpg.core.model.CombatantId
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.types.shouldBeInstanceOf

/** US1 scenario 3 / SC-001: a higher-Speed combatant is never offered fewer turns than a slower one. */
class ReadinessProportionalityTest : StringSpec({

    "over many consumed turns, the faster combatant acts more often than the slower one" {
        val state = freshScheduleBattleState()
        val fastId = state.participants.first { it.combatant.displayName == "Fast" }.combatant.id
        val slowId = state.participants.first { it.combatant.displayName == "Slow" }.combatant.id

        var schedule = ActiveTimeBattleScheduler.initialSchedule(state)
        val turnCounts = mutableMapOf<CombatantId, Int>()
        repeat(200) {
            val ready = ActiveTimeBattleScheduler.nextTurn(schedule, state)
                .shouldBeInstanceOf<ScheduleResult.Ready<AtbScheduleState>>()
            turnCounts[ready.combatantId] = (turnCounts[ready.combatantId] ?: 0) + 1
            schedule = ActiveTimeBattleScheduler.markSpent(ready.advancedSchedule, ready.combatantId)
        }

        (turnCounts[fastId] ?: 0) shouldBeGreaterThan (turnCounts[slowId] ?: 0)
    }
})
