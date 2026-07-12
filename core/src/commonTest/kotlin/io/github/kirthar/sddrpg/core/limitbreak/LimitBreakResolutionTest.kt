package io.github.kirthar.sddrpg.core.limitbreak

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf

class LimitBreakResolutionTest : FunSpec({
    val catalog = LimitBreakCatalog(listOf(omnislashDefinition))

    test("a below-threshold attempt is rejected as GaugeNotFull (US2 scenario 1)") {
        val battle = freshLimitBreakBattleState()
        val gauges = LimitGaugeState(mapOf(cloudId to omnislashDefinition.threshold - 1))
        val result = resolveLimitBreak(battle, gauges, cloudId, setOf(bombId), catalog)
        val rejected = result.shouldBeInstanceOf<LimitBreakResolutionResult.Rejected>()
        rejected.error shouldBe LimitBreakError.GaugeNotFull(cloudId, omnislashDefinition.threshold - 1, omnislashDefinition.threshold)
    }

    test("a threshold-reached use resolves within [0, maximum] health bounds (US2 scenario 2)") {
        val battle = freshLimitBreakBattleState()
        val gauges = LimitGaugeState(mapOf(cloudId to omnislashDefinition.threshold))
        val result = resolveLimitBreak(battle, gauges, cloudId, setOf(bombId), catalog)
        val resolved = result.shouldBeInstanceOf<LimitBreakResolutionResult.Resolved>()
        val outcome = resolved.outcomes.single()
        (outcome.resultingHealth in 0..battle.find(bombId)!!.health.maximum) shouldBe true
    }

    test("the gauge is reset to zero immediately after a successful use (US2 scenario 3)") {
        val battle = freshLimitBreakBattleState()
        val gauges = LimitGaugeState(mapOf(cloudId to omnislashDefinition.threshold))
        val result = resolveLimitBreak(battle, gauges, cloudId, setOf(bombId), catalog).shouldBeInstanceOf<LimitBreakResolutionResult.Resolved>()
        result.newGauges.gauges[cloudId] shouldBe 0
    }

    test("exact-at-threshold boundary is usable (gauge == threshold, not just gauge > threshold)") {
        val battle = freshLimitBreakBattleState()
        val gauges = LimitGaugeState(mapOf(cloudId to omnislashDefinition.threshold))
        resolveLimitBreak(battle, gauges, cloudId, setOf(bombId), catalog).shouldBeInstanceOf<LimitBreakResolutionResult.Resolved>()
    }

    test("a combatant with no active limit break is rejected as NoActiveLimitBreak") {
        val battle = freshLimitBreakBattleState()
        val result = resolveLimitBreak(battle, LimitGaugeState(), aerithId, setOf(bombId), catalog)
        val rejected = result.shouldBeInstanceOf<LimitBreakResolutionResult.Rejected>()
        rejected.error shouldBe LimitBreakError.NoActiveLimitBreak(aerithId)
    }

    test("a rejected attempt leaves the gauge untouched") {
        val battle = freshLimitBreakBattleState()
        val gauges = LimitGaugeState(mapOf(cloudId to 5))
        resolveLimitBreak(battle, gauges, cloudId, setOf(bombId), catalog)
        gauges.gauges[cloudId] shouldBe 5
    }
})
