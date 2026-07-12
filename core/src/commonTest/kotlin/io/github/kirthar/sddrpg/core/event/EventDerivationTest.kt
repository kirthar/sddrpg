package io.github.kirthar.sddrpg.core.event

import io.github.kirthar.sddrpg.core.action.ActionResolutionResult
import io.github.kirthar.sddrpg.core.action.CombatAction
import io.github.kirthar.sddrpg.core.action.DamageFormula
import io.github.kirthar.sddrpg.core.action.EffectKind
import io.github.kirthar.sddrpg.core.action.TargetingShape
import io.github.kirthar.sddrpg.core.action.resolveAction
import io.github.kirthar.sddrpg.core.catalog.CommandKind
import io.github.kirthar.sddrpg.core.model.SkillId
import io.github.kirthar.sddrpg.core.schedule.ActiveTimeBattleScheduler
import io.github.kirthar.sddrpg.core.schedule.ScheduleResult
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf

class EventDerivationTest : FunSpec({
    test("resolving a damaging action produces a DamageDealt event identifying the target and the amount (US1 scenario 2)") {
        val battle = freshEventBattleState()
        val result = resolveAction(battle, cleaveAction, setOf(bombId))
        val resolved = result.shouldBeInstanceOf<ActionResolutionResult.Resolved>()
        val events = eventsFromResolution(battle, cleaveAction, result)

        val outcome = resolved.outcomes.single()
        events shouldBe listOf(
            BattleEvent.DamageDealt(cloudId, bombId, -outcome.appliedDelta, outcome.resultingHealth)
        )
    }

    test("resolving a heal produces a HealingApplied event") {
        val battle = freshEventBattleState()
        val healAction = CombatAction(
            actorId = cloudId,
            command = CommandKind.ATTACK,
            effectKind = EffectKind.HEAL,
            formula = DamageFormula.Fixed(5),
            targeting = TargetingShape.SINGLE_ALLY,
        )
        val result = resolveAction(battle, healAction, setOf(aerithId))
        val resolved = result.shouldBeInstanceOf<ActionResolutionResult.Resolved>()
        val outcome = resolved.outcomes.single()
        val events = eventsFromResolution(battle, healAction, result)

        events shouldBe listOf(
            BattleEvent.HealingApplied(cloudId, aerithId, outcome.appliedDelta, outcome.resultingHealth)
        )
    }

    test("a rejected resolution produces no events") {
        val battle = freshEventBattleState()
        val badAction = cleaveAction.copy(skillId = SkillId("unknown"))
        val result = resolveAction(battle, badAction, setOf(bombId))
        result.shouldBeInstanceOf<ActionResolutionResult.Rejected>()
        eventsFromResolution(battle, badAction, result) shouldBe emptyList()
    }

    test("the scheduler granting a turn produces a TurnGranted event naming that combatant (US1 scenario 6)") {
        val battle = freshEventBattleState()
        val schedule = ActiveTimeBattleScheduler.initialSchedule(battle)
        val result = ActiveTimeBattleScheduler.nextTurn(schedule, battle)
        val ready = result.shouldBeInstanceOf<ScheduleResult.Ready<*>>()
        eventsFromSchedule(result) shouldBe listOf(BattleEvent.TurnGranted(ready.combatantId))
    }

    test("NoOneReady produces no event") {
        eventsFromSchedule(ScheduleResult.NoOneReady) shouldBe emptyList()
    }
})
