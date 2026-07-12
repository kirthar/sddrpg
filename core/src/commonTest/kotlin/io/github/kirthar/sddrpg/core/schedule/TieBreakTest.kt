package io.github.kirthar.sddrpg.core.schedule

import io.github.kirthar.sddrpg.core.model.CombatantId
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf

/** SC-004 / research R3: ties are broken by battle.participants order, every time. */
class TieBreakTest : StringSpec({

    fun idOf(state: io.github.kirthar.sddrpg.core.action.BattleState, name: String): CombatantId =
        state.participants.first { it.combatant.displayName == name }.combatant.id

    "two combatants of identical Speed reaching readiness together are resolved the same way every time" {
        val state = freshEqualSpeedBattleState()
        val alpha = idOf(state, "Alpha") // first in battle.participants order

        repeat(20) {
            val schedule = ActiveTimeBattleScheduler.initialSchedule(state)
            val result = ActiveTimeBattleScheduler.nextTurn(schedule, state)
                .shouldBeInstanceOf<ScheduleResult.Ready<AtbScheduleState>>()
            result.combatantId shouldBe alpha
        }
    }

    "the tie-break rule is battle.participants index order, not CombatantId string order" {
        // Alpha's CombatantId ("c1") happens to sort first lexicographically too, so this
        // test also exercises a roster where the id order and the participants order
        // could diverge for larger rosters (spec 001 R7: "c10" < "c2" lexicographically) --
        // the implementation must key off participants order, never the id string.
        val state = freshEqualSpeedBattleState()
        state.participants.map { it.combatant.id } shouldBe listOf(idOf(state, "Alpha"), idOf(state, "Beta"))
    }
})
