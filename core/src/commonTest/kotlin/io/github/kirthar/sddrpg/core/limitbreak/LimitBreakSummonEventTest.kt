package io.github.kirthar.sddrpg.core.limitbreak

import io.github.kirthar.sddrpg.core.event.BattleEvent
import io.github.kirthar.sddrpg.core.event.GaugeFull
import io.github.kirthar.sddrpg.core.event.LimitBreakUsed
import io.github.kirthar.sddrpg.core.event.SummonCast
import io.github.kirthar.sddrpg.core.event.eventsFromGaugeCharge
import io.github.kirthar.sddrpg.core.event.eventsFromLimitBreak
import io.github.kirthar.sddrpg.core.event.eventsFromSummon
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf

class LimitBreakSummonEventTest : FunSpec({
    val lbCatalog = LimitBreakCatalog(listOf(omnislashDefinition))
    val summonCatalog = SummonCatalog(listOf(meteorDefinition))

    test("a gauge crossing from below threshold to at-or-above threshold produces exactly one GaugeFull (US4 scenario 1)") {
        val battle = freshLimitBreakBattleState()
        val before = LimitGaugeState(mapOf(cloudId to 10))
        val after = LimitGaugeState(mapOf(cloudId to omnislashDefinition.threshold))
        eventsFromGaugeCharge(battle, before, after, lbCatalog) shouldBe listOf(GaugeFull(cloudId))
    }

    test("further charging while already at threshold produces no additional GaugeFull") {
        val battle = freshLimitBreakBattleState()
        val before = LimitGaugeState(mapOf(cloudId to omnislashDefinition.threshold))
        val after = LimitGaugeState(mapOf(cloudId to omnislashDefinition.threshold))
        eventsFromGaugeCharge(battle, before, after, lbCatalog) shouldBe emptyList()
    }

    test("charging that stays below threshold produces no GaugeFull") {
        val battle = freshLimitBreakBattleState()
        val before = LimitGaugeState(mapOf(cloudId to 5))
        val after = LimitGaugeState(mapOf(cloudId to 10))
        eventsFromGaugeCharge(battle, before, after, lbCatalog) shouldBe emptyList()
    }

    test("a resolved limit break produces a LimitBreakUsed event alongside its damage event(s) (US4 scenario 2)") {
        val battle = freshLimitBreakBattleState()
        val gauges = LimitGaugeState(mapOf(cloudId to omnislashDefinition.threshold))
        val result = resolveLimitBreak(battle, gauges, cloudId, setOf(bombId), lbCatalog)
            .shouldBeInstanceOf<LimitBreakResolutionResult.Resolved>()

        val events = eventsFromLimitBreak(battle, cloudId, omnislashId, omnislashDefinition.effectKind, result)

        events.first() shouldBe LimitBreakUsed(cloudId, omnislashId)
        events shouldBe listOf(
            LimitBreakUsed(cloudId, omnislashId),
            BattleEvent.DamageDealt(null, bombId, result.outcomes.single().let { kotlin.math.abs(it.appliedDelta) }, result.outcomes.single().resultingHealth),
            BattleEvent.CombatantDefeated(bombId),
        )
    }

    test("a rejected limit break produces no events") {
        val battle = freshLimitBreakBattleState()
        val result = resolveLimitBreak(battle, LimitGaugeState(), cloudId, setOf(bombId), lbCatalog)
            .shouldBeInstanceOf<LimitBreakResolutionResult.Rejected>()
        eventsFromLimitBreak(battle, cloudId, omnislashId, omnislashDefinition.effectKind, result) shouldBe emptyList()
    }

    test("a resolved summon produces a SummonCast event alongside its own damage event(s) (US4 scenario 3)") {
        val battle = freshLimitBreakBattleState()
        val resources = battle.initialResourceState()
        val result = resolveSummon(battle, resources, cloudId, meteorId, setOf(bombId), summonCatalog)
            .shouldBeInstanceOf<SummonResolutionResult.Resolved>()

        val events = eventsFromSummon(battle, cloudId, meteorId, meteorDefinition.effectKind, result)

        events.first() shouldBe SummonCast(cloudId, meteorId)
        events shouldBe listOf(
            SummonCast(cloudId, meteorId),
            BattleEvent.DamageDealt(null, bombId, result.outcomes.single().let { kotlin.math.abs(it.appliedDelta) }, result.outcomes.single().resultingHealth),
            BattleEvent.CombatantDefeated(bombId),
        )
    }

    test("a rejected summon produces no events") {
        val battle = freshLimitBreakBattleState()
        val result = resolveSummon(battle, ResourceState(), cloudId, meteorId, setOf(bombId), summonCatalog)
            .shouldBeInstanceOf<SummonResolutionResult.Rejected>()
        eventsFromSummon(battle, cloudId, meteorId, meteorDefinition.effectKind, result) shouldBe emptyList()
    }
})
