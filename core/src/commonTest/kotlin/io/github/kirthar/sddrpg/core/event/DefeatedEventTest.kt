package io.github.kirthar.sddrpg.core.event

import io.github.kirthar.sddrpg.core.action.ActionResolutionResult
import io.github.kirthar.sddrpg.core.action.BattleState
import io.github.kirthar.sddrpg.core.action.CombatAction
import io.github.kirthar.sddrpg.core.action.DamageFormula
import io.github.kirthar.sddrpg.core.action.EffectKind
import io.github.kirthar.sddrpg.core.action.TargetingShape
import io.github.kirthar.sddrpg.core.action.resolveAction
import io.github.kirthar.sddrpg.core.catalog.CommandKind
import io.github.kirthar.sddrpg.core.status.StatusEffectId
import io.github.kirthar.sddrpg.core.status.StatusEffectState
import io.github.kirthar.sddrpg.core.status.applyStatusEffect
import io.github.kirthar.sddrpg.core.status.tickStatusEffects
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf

/** A lethal attack that brings bomb (200 HP) to exactly 0 in one hit -- Fixed ignores stats. */
private val lethalAction = CombatAction(
    actorId = cloudId,
    command = CommandKind.ATTACK,
    effectKind = EffectKind.DAMAGE,
    formula = DamageFormula.Fixed(200),
    targeting = TargetingShape.SINGLE_ENEMY,
)

class DefeatedEventTest : FunSpec({
    test("a resolution outcome that brings health to exactly 0 produces a CombatantDefeated event") {
        val battle = freshEventBattleState()
        val result = resolveAction(battle, lethalAction, setOf(bombId))
        result.shouldBeInstanceOf<ActionResolutionResult.Resolved>()

        eventsFromResolution(battle, lethalAction, result) shouldBe listOf(
            BattleEvent.DamageDealt(cloudId, bombId, 200, 0),
            BattleEvent.CombatantDefeated(bombId),
        )
    }

    test("resolveAction itself rejects retargeting an already-defeated SINGLE_ENEMY, so eventsFromResolution can never double-report a defeat through this source") {
        val battle = freshEventBattleState()
        val firstHit = resolveAction(battle, lethalAction, setOf(bombId))
        val defeated = (firstHit as ActionResolutionResult.Resolved).newState

        val secondHit = resolveAction(defeated, lethalAction, setOf(bombId))
        secondHit.shouldBeInstanceOf<ActionResolutionResult.Rejected>()
        eventsFromResolution(defeated, lethalAction, secondHit) shouldBe emptyList()
    }

    test("a resolution outcome that leaves health above 0 produces no CombatantDefeated") {
        val battle = freshEventBattleState()
        val result = resolveAction(battle, cleaveAction, setOf(bombId))
        result.shouldBeInstanceOf<ActionResolutionResult.Resolved>()
        eventsFromResolution(battle, cleaveAction, result).filterIsInstance<BattleEvent.CombatantDefeated>() shouldBe emptyList()
    }

    test("a tick outcome that brings health to exactly 0 produces a CombatantDefeated event") {
        val poisonId = StatusEffectId("poison")
        val nearDeath = BattleState(
            freshEventBattleState().participants.map {
                if (it.combatant.id == bombId) it.copy(health = it.health.copy(current = 3)) else it
            }
        )
        val effects = applyStatusEffect(StatusEffectState(), bombId, poisonId, eventFixtureStatusCatalog)
        val tickResult = tickStatusEffects(effects, nearDeath, eventFixtureStatusCatalog)

        eventsFromTick(effects, nearDeath, eventFixtureStatusCatalog, tickResult) shouldBe listOf(
            BattleEvent.DamageDealt(null, bombId, 5, 0),
            BattleEvent.CombatantDefeated(bombId),
        )
    }

    test("a tick outcome against an already-defeated combatant produces no second CombatantDefeated") {
        val poisonId = StatusEffectId("poison")
        val defeatedAlready = BattleState(
            freshEventBattleState().participants.map {
                if (it.combatant.id == bombId) it.copy(health = it.health.copy(current = 0)) else it
            }
        )
        val effects = applyStatusEffect(StatusEffectState(), bombId, poisonId, eventFixtureStatusCatalog)
        val tickResult = tickStatusEffects(effects, defeatedAlready, eventFixtureStatusCatalog)

        eventsFromTick(effects, defeatedAlready, eventFixtureStatusCatalog, tickResult).filterIsInstance<BattleEvent.CombatantDefeated>() shouldBe emptyList()
    }

    test("a BonusDamage that brings health to exactly 0 produces a CombatantDefeated event via eventsFromSynergyBonus") {
        val nearDeath = BattleState(
            freshEventBattleState().participants.map {
                if (it.combatant.id == bombId) it.copy(health = it.health.copy(current = 8)) else it
            }
        )
        val bonus = io.github.kirthar.sddrpg.core.event.SynergyBonus.BonusDamage(10)
        val newBattle = applySynergyBonus(nearDeath, bombId, bonus)

        eventsFromSynergyBonus(nearDeath, bombId, bonus, newBattle) shouldBe listOf(
            BattleEvent.DamageDealt(null, bombId, 10, 0),
            BattleEvent.CombatantDefeated(bombId),
        )
    }
})
