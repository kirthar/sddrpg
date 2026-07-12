package io.github.kirthar.sddrpg.core.limitbreak

import io.github.kirthar.sddrpg.core.action.BattleState
import io.github.kirthar.sddrpg.core.event.BattleEvent
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.element
import io.kotest.property.checkAll

/** FR-011/SC-005: the same sequence of charge/resolve calls always replays identically. */
class LimitBreakDeterminismTest : StringSpec({

    fun runSequence(damageAmounts: List<Int>): Triple<LimitGaugeState, BattleState, ResourceState> {
        val lbCatalog = LimitBreakCatalog(listOf(omnislashDefinition))
        val summonCatalog = SummonCatalog(listOf(meteorDefinition))
        var battle = freshLimitBreakBattleState()
        var gauges = LimitGaugeState()
        var resources = battle.initialResourceState()

        for (amount in damageAmounts) {
            val events = listOf(BattleEvent.DamageDealt(aerithId, cloudId, amount, 20 - amount))
            gauges = chargeLimitGauge(gauges, battle, events, lbCatalog)
        }

        val lbResult = resolveLimitBreak(battle, gauges, cloudId, setOf(bombId), lbCatalog)
        if (lbResult is LimitBreakResolutionResult.Resolved) {
            battle = lbResult.newState
            gauges = lbResult.newGauges
        }

        val summonResult = resolveSummon(battle, resources, cloudId, meteorId, setOf(bombId), summonCatalog)
        if (summonResult is SummonResolutionResult.Resolved) {
            battle = summonResult.newState
            resources = summonResult.newResources
        }

        return Triple(gauges, battle, resources)
    }

    "property: replaying the same charge/resolve sequence twice yields structurally identical results" {
        checkAll(
            Arb.element(listOf(5, 10, 20, 60)),
            Arb.element(listOf(5, 10, 20, 60)),
        ) { a, b ->
            val steps = listOf(a, b)
            val first = runSequence(steps)
            val second = runSequence(steps)
            first shouldBe second
        }
    }
})
