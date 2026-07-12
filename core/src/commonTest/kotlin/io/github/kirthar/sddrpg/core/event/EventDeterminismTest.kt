package io.github.kirthar.sddrpg.core.event

import io.github.kirthar.sddrpg.core.action.ActionResolutionResult
import io.github.kirthar.sddrpg.core.action.BattleState
import io.github.kirthar.sddrpg.core.action.resolveAction
import io.github.kirthar.sddrpg.core.model.CombatantId
import io.github.kirthar.sddrpg.core.status.StatusEffectId
import io.github.kirthar.sddrpg.core.status.StatusEffectState
import io.github.kirthar.sddrpg.core.status.applyStatusEffect
import io.github.kirthar.sddrpg.core.status.tickStatusEffects
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.element
import io.kotest.property.checkAll

/** FR-010/SC-005: the same sequence of resolve/tick/schedule/detect/apply calls always replays identically. */
class EventDeterminismTest : StringSpec({

    fun runSequence(secondActor: CombatantId): Triple<EventLog, BattleState, List<SynergyTrigger>> {
        var battle = freshSynergyBattleState()
        var log = EventLog()
        val catalog = SynergyCatalog(listOf(warriorMageSynergy))

        val r1 = resolveAction(battle, cleaveOn(cloudId), setOf(synergyBombId)) as ActionResolutionResult.Resolved
        val e1 = eventsFromResolution(battle, cleaveOn(cloudId), r1)
        battle = r1.newState
        log = log.append(e1)

        var effects = applyStatusEffect(StatusEffectState(), synergyBombId, StatusEffectId("poison"), eventFixtureStatusCatalog)
        log = log.append(eventsFromApply(synergyBombId, StatusEffectId("poison")))
        val tick = tickStatusEffects(effects, battle, eventFixtureStatusCatalog)
        log = log.append(eventsFromTick(effects, battle, eventFixtureStatusCatalog, tick))
        battle = tick.newBattle
        effects = tick.newEffects

        val action = firaOn(secondActor)
        val r2 = resolveAction(battle, action, setOf(synergyBombId)) as ActionResolutionResult.Resolved
        val e2 = eventsFromResolution(battle, action, r2)
        battle = r2.newState
        log = log.append(e2)

        val triggers = detectSynergyTriggers(e2, log, battle, catalog)
        return Triple(log, battle, triggers)
    }

    "property: replaying the same resolve/tick/detect sequence twice yields structurally identical results" {
        checkAll(Arb.element(listOf(aerithId, tifaId))) { actor ->
            val first = runSequence(actor)
            val second = runSequence(actor)
            first shouldBe second
        }
    }
})
