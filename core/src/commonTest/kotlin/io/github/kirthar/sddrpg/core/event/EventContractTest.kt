package io.github.kirthar.sddrpg.core.event

import io.github.kirthar.sddrpg.core.action.ActionResolutionResult
import io.github.kirthar.sddrpg.core.action.resolveAction
import io.github.kirthar.sddrpg.core.schedule.ActiveTimeBattleScheduler
import io.github.kirthar.sddrpg.core.status.StatusEffectId
import io.github.kirthar.sddrpg.core.status.StatusEffectState
import io.github.kirthar.sddrpg.core.status.applyStatusEffect
import io.github.kirthar.sddrpg.core.status.tickStatusEffects
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

/**
 * FR-011: specs 001-004's resolveAction/tickStatusEffects/TurnScheduler are never
 * modified -- involving the event package produces the exact same BattleState/
 * StatusEffectState/ScheduleResult sequence as calling spec 002-004 directly. This
 * test, plus the fact that everything else in this module compiles against
 * unmodified spec 001-004 files, is the evidence.
 */
class EventContractTest : FunSpec({
    test("resolving/ticking/scheduling with the event package involved produces identical results to calling spec 002-004 directly") {
        // Run A: spec 002-004 only, no event package.
        val battleA = freshEventBattleState()
        val resultA = resolveAction(battleA, cleaveAction, setOf(bombId)) as ActionResolutionResult.Resolved
        var effectsA = applyStatusEffect(StatusEffectState(), bombId, StatusEffectId("poison"), eventFixtureStatusCatalog)
        val tickA = tickStatusEffects(effectsA, resultA.newState, eventFixtureStatusCatalog)
        val scheduleA = ActiveTimeBattleScheduler.initialSchedule(tickA.newBattle)
        val nextA = ActiveTimeBattleScheduler.nextTurn(scheduleA, tickA.newBattle)

        // Run B: identical calls, plus deriving events (with an empty SynergyCatalog) alongside.
        val battleB = freshEventBattleState()
        val resultB = resolveAction(battleB, cleaveAction, setOf(bombId)) as ActionResolutionResult.Resolved
        var log = EventLog().append(eventsFromResolution(battleB, cleaveAction, resultB))
        var effectsB = applyStatusEffect(StatusEffectState(), bombId, StatusEffectId("poison"), eventFixtureStatusCatalog)
        log = log.append(eventsFromApply(bombId, StatusEffectId("poison")))
        val tickB = tickStatusEffects(effectsB, resultB.newState, eventFixtureStatusCatalog)
        log = log.append(eventsFromTick(effectsB, resultB.newState, eventFixtureStatusCatalog, tickB))
        detectSynergyTriggers(log.entries.map { it.event }, log, tickB.newBattle, SynergyCatalog()) shouldBe emptyList()
        val scheduleB = ActiveTimeBattleScheduler.initialSchedule(tickB.newBattle)
        val nextB = ActiveTimeBattleScheduler.nextTurn(scheduleB, tickB.newBattle)
        log = log.append(eventsFromSchedule(nextB))

        resultA shouldBe resultB
        tickA shouldBe tickB
        nextA shouldBe nextB
    }
})
