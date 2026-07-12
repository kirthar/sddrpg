package io.github.kirthar.sddrpg.core.event

import io.github.kirthar.sddrpg.core.status.StatusEffectId
import io.github.kirthar.sddrpg.core.status.StatusEffectState
import io.github.kirthar.sddrpg.core.status.applyStatusEffect
import io.github.kirthar.sddrpg.core.status.tickStatusEffects
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class StatusTickEventTest : FunSpec({
    val poisonId = StatusEffectId("poison")

    test("applying a status effect produces a StatusEffectApplied event, including on a refresh (US1 scenario 4)") {
        eventsFromApply(bombId, poisonId) shouldBe listOf(BattleEvent.StatusEffectApplied(bombId, poisonId))
    }

    test("a damage-over-time tick produces a DamageDealt event indistinguishable in shape from a resolved action's except actorId is null (US1 scenario 3)") {
        val battle = freshEventBattleState()
        val effects = applyStatusEffect(StatusEffectState(), bombId, poisonId, eventFixtureStatusCatalog)
        val tickResult = tickStatusEffects(effects, battle, eventFixtureStatusCatalog)

        val events = eventsFromTick(effects, battle, eventFixtureStatusCatalog, tickResult)

        events shouldBe listOf(BattleEvent.DamageDealt(null, bombId, 5, tickResult.newBattle.find(bombId)!!.health.current))
    }

    test("a tick whose post-decrement remainingDuration reaches 0 produces a StatusEffectExpired event for that effect") {
        val battle = freshEventBattleState()
        var effects = applyStatusEffect(StatusEffectState(), bombId, poisonId, eventFixtureStatusCatalog)
        var currentBattle = battle
        repeat(2) {
            val tickResult = tickStatusEffects(effects, currentBattle, eventFixtureStatusCatalog)
            eventsFromTick(effects, currentBattle, eventFixtureStatusCatalog, tickResult).none { it is BattleEvent.StatusEffectExpired } shouldBe true
            effects = tickResult.newEffects
            currentBattle = tickResult.newBattle
        }

        val finalTick = tickStatusEffects(effects, currentBattle, eventFixtureStatusCatalog)
        val finalEvents = eventsFromTick(effects, currentBattle, eventFixtureStatusCatalog, finalTick)
        finalEvents shouldBe listOf(
            BattleEvent.DamageDealt(null, bombId, 5, finalTick.newBattle.find(bombId)!!.health.current),
            BattleEvent.StatusEffectExpired(bombId, poisonId),
        )
    }

    test("a combatant with two simultaneous damage-over-time effects gets one DamageDealt event per effect, in deterministic order") {
        val secondPoisonId = StatusEffectId("poison2")
        val catalog = io.github.kirthar.sddrpg.core.status.StatusEffectCatalog(
            eventFixtureStatusCatalog.effects + io.github.kirthar.sddrpg.core.status.StatusEffectDefinition(
                secondPoisonId, "Poison 2", io.github.kirthar.sddrpg.core.status.EffectKind.DamageOverTime(3), duration = 3,
            )
        )
        val battle = freshEventBattleState()
        var effects = applyStatusEffect(StatusEffectState(), bombId, poisonId, catalog)
        effects = applyStatusEffect(effects, bombId, secondPoisonId, catalog)

        val tickResult = tickStatusEffects(effects, battle, catalog)
        val events = eventsFromTick(effects, battle, catalog, tickResult)

        events.filterIsInstance<BattleEvent.DamageDealt>().map { it.amount } shouldBe listOf(5, 3)
        events.filterIsInstance<BattleEvent.DamageDealt>().last().resultingHealth shouldBe tickResult.newBattle.find(bombId)!!.health.current
    }
})
