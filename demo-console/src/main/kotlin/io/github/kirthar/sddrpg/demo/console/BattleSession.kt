package io.github.kirthar.sddrpg.demo.console

import io.github.kirthar.sddrpg.content.demo.buildDemoBattleState
import io.github.kirthar.sddrpg.core.action.BattleState
import io.github.kirthar.sddrpg.core.event.EventLog
import io.github.kirthar.sddrpg.core.limitbreak.LimitGaugeState
import io.github.kirthar.sddrpg.core.limitbreak.ResourceState
import io.github.kirthar.sddrpg.core.limitbreak.initialResourceState
import io.github.kirthar.sddrpg.core.schedule.ActiveTimeBattleScheduler
import io.github.kirthar.sddrpg.core.schedule.AtbScheduleState
import io.github.kirthar.sddrpg.core.status.StatusEffectState

/**
 * The running state of one playthrough (spec 008 Key Entities: "Battle Session").
 * [battle] is always the *raw*, never-effective-wrapped state -- source of truth for
 * health; `BattleLoop` derives a stat-adjusted view for one turn's use only and never
 * persists it here (would otherwise double-apply status-effect stat modifiers on the
 * next derivation).
 */
data class BattleSession(
    val battle: BattleState,
    val schedule: AtbScheduleState,
    val effects: StatusEffectState,
    val gauges: LimitGaugeState,
    val resources: ResourceState,
    val log: EventLog,
)

/** The shipped demo content's starting session. */
fun newBattleSession(): BattleSession {
    val battle = buildDemoBattleState()
    return BattleSession(
        battle = battle,
        schedule = ActiveTimeBattleScheduler.initialSchedule(battle),
        effects = StatusEffectState(),
        gauges = LimitGaugeState(),
        resources = battle.initialResourceState(),
        log = EventLog(),
    )
}
