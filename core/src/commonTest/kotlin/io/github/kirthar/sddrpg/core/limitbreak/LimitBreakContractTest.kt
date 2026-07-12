package io.github.kirthar.sddrpg.core.limitbreak

import io.github.kirthar.sddrpg.core.action.resolveAction
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

/**
 * FR-012: specs 001-005's resolveAction/CommandKind/BattleEvent are never modified --
 * resolving the same action produces the exact same result whether the limitbreak
 * package is involved at all. This test, plus the fact that everything else in this
 * module compiles against unmodified spec 001-005 files, is the evidence.
 */
class LimitBreakContractTest : FunSpec({
    test("resolving a normal action is identical whether limitbreak state is tracked alongside or not") {
        // Run A: spec 002 only, no limitbreak package involved.
        val battleA = freshLimitBreakBattleState()
        val resultA = resolveAction(battleA, cleaveOn(cloudId, bombId), setOf(bombId))

        // Run B: identical call, plus tracking (empty) limitbreak/resource state alongside.
        val battleB = freshLimitBreakBattleState()
        var gauges = LimitGaugeState()
        var resources = battleB.initialResourceState()
        gauges = chargeLimitGauge(gauges, battleB, emptyList(), LimitBreakCatalog())
        val resultB = resolveAction(battleB, cleaveOn(cloudId, bombId), setOf(bombId))

        resultA shouldBe resultB
        gauges shouldBe LimitGaugeState()
    }

    test("an empty LimitBreakCatalog never lets any gauge reach threshold, regardless of damage taken") {
        val battle = freshLimitBreakBattleState()
        val events = listOf(io.github.kirthar.sddrpg.core.event.BattleEvent.DamageDealt(aerithId, cloudId, 9999, 1))
        chargeLimitGauge(LimitGaugeState(), battle, events, LimitBreakCatalog()) shouldBe LimitGaugeState()
    }

    test("resolveLimitBreak against an unknown/empty catalog rejects without mutating battle") {
        val battle = freshLimitBreakBattleState()
        val result = resolveLimitBreak(battle, LimitGaugeState(mapOf(cloudId to 999)), cloudId, setOf(bombId), LimitBreakCatalog())
        result shouldBe LimitBreakResolutionResult.Rejected(LimitBreakError.NoActiveLimitBreak(cloudId))
    }
})

private fun cleaveOn(actorId: io.github.kirthar.sddrpg.core.model.CombatantId, targetId: io.github.kirthar.sddrpg.core.model.CombatantId) =
    io.github.kirthar.sddrpg.core.action.CombatAction(
        actorId = actorId,
        command = io.github.kirthar.sddrpg.core.catalog.CommandKind.ATTACK,
        effectKind = io.github.kirthar.sddrpg.core.action.EffectKind.DAMAGE,
        formula = io.github.kirthar.sddrpg.core.action.DamageFormula.Physical(5),
        targeting = io.github.kirthar.sddrpg.core.action.TargetingShape.SINGLE_ENEMY,
    )
