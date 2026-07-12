package io.github.kirthar.sddrpg.core.schedule

import io.github.kirthar.sddrpg.core.action.BattleState
import io.github.kirthar.sddrpg.core.model.CombatantId
import io.github.kirthar.sddrpg.core.model.CoreStats

/** ATB bookkeeping: accrued readiness per participant (research R2). */
data class AtbScheduleState(val readiness: Map<CombatantId, Int>)

/**
 * Ceiling division for non-negative divisors: how many whole [d]-sized steps are
 * needed to cover [n]. Correct for the small negative [n] this scheduler produces
 * (bounded overshoot past the ready threshold, research R2) — it evaluates to `0`,
 * meaning "already there, no further steps needed."
 */
internal fun ceilDiv(n: Int, d: Int): Int = (n + d - 1) / d

/**
 * The demo's classic ATB scheduler (spec FR-002): readiness accrues from each
 * combatant's Speed via tick-jump integer arithmetic (research R2), no simulated
 * per-tick loop and no floating point anywhere.
 */
object ActiveTimeBattleScheduler : TurnScheduler<AtbScheduleState> {
    const val READY_THRESHOLD: Int = 1000

    override fun initialSchedule(battle: BattleState): AtbScheduleState =
        AtbScheduleState(battle.participants.associate { it.combatant.id to 0 })

    override fun nextTurn(schedule: AtbScheduleState, battle: BattleState): ScheduleResult<AtbScheduleState> {
        val candidates = battle.participants.filter { participant ->
            !participant.isDefeated && participant.combatant.stats[CoreStats.SPEED] > 0
        }
        if (candidates.isEmpty()) return ScheduleResult.NoOneReady

        val ticksNeeded = candidates.associate { participant ->
            val id = participant.combatant.id
            val speed = participant.combatant.stats[CoreStats.SPEED]
            id to ceilDiv(READY_THRESHOLD - schedule.readiness.getValue(id), speed)
        }
        val minTicks = ticksNeeded.values.min()

        val advancedReadiness = schedule.readiness.mapValues { (id, current) ->
            val participant = battle.participants.firstOrNull { it.combatant.id == id }
            val defeated = participant == null || participant.isDefeated
            if (defeated) current else current + minTicks * participant!!.combatant.stats[CoreStats.SPEED]
        }

        // Tie-break: among candidates that just reached the threshold, the one
        // earliest in battle.participants wins (research R3) — this is the only
        // selection rule; it applies identically whether one or several qualify.
        val winnerId = battle.participants
            .filter { it.combatant.id in ticksNeeded && ticksNeeded.getValue(it.combatant.id) == minTicks }
            .first()
            .combatant.id

        return ScheduleResult.Ready(winnerId, AtbScheduleState(advancedReadiness))
    }

    override fun markSpent(schedule: AtbScheduleState, combatantId: CombatantId): AtbScheduleState {
        val current = schedule.readiness.getValue(combatantId)
        return AtbScheduleState(schedule.readiness + (combatantId to current - READY_THRESHOLD))
    }
}
