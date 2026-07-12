package io.github.kirthar.sddrpg.core.limitbreak

import io.github.kirthar.sddrpg.core.action.BattleState
import io.github.kirthar.sddrpg.core.event.BattleEvent
import io.github.kirthar.sddrpg.core.model.LimitBreakId
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class LimitGaugeChargeTest : FunSpec({
    val catalog = LimitBreakCatalog(listOf(omnislashDefinition))

    test("a fresh gauge rises by the exact damage amount from a DamageDealt event (US1 scenario 1)") {
        val battle = freshLimitBreakBattleState()
        val events = listOf(BattleEvent.DamageDealt(aerithId, cloudId, 12, 8))
        val result = chargeLimitGauge(LimitGaugeState(), battle, events, catalog)
        result.gauges[cloudId] shouldBe 12
    }

    test("damage that would push the gauge past threshold clamps it at threshold, never exceeding (US1 scenario 2)") {
        val battle = freshLimitBreakBattleState()
        val nearFull = LimitGaugeState(mapOf(cloudId to 45))
        val events = listOf(BattleEvent.DamageDealt(aerithId, cloudId, 20, 1))
        val result = chargeLimitGauge(nearFull, battle, events, catalog)
        result.gauges[cloudId] shouldBe omnislashDefinition.threshold
    }

    test("a combatant whose class declares no limit break never gets a gauge entry (US1 scenario 3)") {
        val battle = freshLimitBreakBattleState()
        val events = listOf(BattleEvent.DamageDealt(cloudId, aerithId, 12, 8))
        val result = chargeLimitGauge(LimitGaugeState(), battle, events, catalog)
        result.gauges.containsKey(aerithId) shouldBe false
    }

    test("a defeated combatant's gauge still charges from further damage events") {
        val battle = BattleState(
            freshLimitBreakBattleState().participants.map {
                if (it.combatant.id == cloudId) it.copy(health = it.health.copy(current = 0)) else it
            }
        )
        val events = listOf(BattleEvent.DamageDealt(aerithId, cloudId, 5, 0))
        val result = chargeLimitGauge(LimitGaugeState(), battle, events, catalog)
        result.gauges[cloudId] shouldBe 5
    }

    test("a stale LimitBreakId absent from the catalog is skipped gracefully rather than crashing") {
        val battle = freshLimitBreakBattleState()
        val events = listOf(BattleEvent.DamageDealt(aerithId, cloudId, 12, 8))
        val emptyCatalog = LimitBreakCatalog(listOf(io.github.kirthar.sddrpg.core.limitbreak.LimitBreakDefinition(LimitBreakId("other"), 10, io.github.kirthar.sddrpg.core.action.EffectKind.DAMAGE, io.github.kirthar.sddrpg.core.action.DamageFormula.Fixed(1), io.github.kirthar.sddrpg.core.action.TargetingShape.SINGLE_ENEMY)))
        val result = chargeLimitGauge(LimitGaugeState(), battle, events, emptyCatalog)
        result.gauges.containsKey(cloudId) shouldBe false
    }

    test("non-DamageDealt events do not charge any gauge") {
        val battle = freshLimitBreakBattleState()
        val events = listOf(BattleEvent.HealingApplied(aerithId, cloudId, 12, 20), BattleEvent.TurnGranted(cloudId))
        val result = chargeLimitGauge(LimitGaugeState(), battle, events, catalog)
        result.gauges.containsKey(cloudId) shouldBe false
    }
})
