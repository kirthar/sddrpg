package io.github.kirthar.sddrpg.core.event

import io.github.kirthar.sddrpg.core.action.ActionResolutionResult
import io.github.kirthar.sddrpg.core.action.BattleState
import io.github.kirthar.sddrpg.core.action.CombatAction
import io.github.kirthar.sddrpg.core.action.resolveAction
import io.github.kirthar.sddrpg.core.model.CombatantId
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf

private fun resolveAndLog3(battle: BattleState, log: EventLog, action: CombatAction, targetId: CombatantId): Triple<BattleState, EventLog, List<BattleEvent>> {
    val result = resolveAction(battle, action, setOf(targetId))
    val resolved = result as ActionResolutionResult.Resolved
    val events = eventsFromResolution(battle, action, result)
    return Triple(resolved.newState, log.append(events), events)
}

class SynergyTriggeredEventTest : FunSpec({
    test("after a synergy triggers, the EventLog contains a SynergyTriggered event distinct from the events its bonus produced (US4 scenario 1)") {
        var battle = freshSynergyBattleState()
        var log = EventLog()
        val r1 = resolveAndLog3(battle, log, cleaveOn(cloudId), synergyBombId)
        battle = r1.first; log = r1.second
        val r2 = resolveAndLog3(battle, log, firaOn(aerithId), synergyBombId)
        battle = r2.first; log = r2.second

        val catalog = SynergyCatalog(listOf(warriorMageSynergy))
        val trigger = detectSynergyTriggers(r2.third, log, battle, catalog).single()

        val beforeBonus = battle
        battle = applySynergyBonus(battle, trigger.targetId, trigger.definition.bonus)
        val bonusEvents = eventsFromSynergyBonus(beforeBonus, trigger.targetId, trigger.definition.bonus, battle)
        val triggerEvent = BattleEvent.SynergyTriggered(trigger.definition.id, trigger.firstActorId, trigger.secondActorId, trigger.targetId)
        log = log.append(listOf(triggerEvent) + bonusEvents)

        val lastTwo = log.entries.takeLast(2).map { it.event }
        lastTwo shouldBe listOf(
            triggerEvent,
            BattleEvent.DamageDealt(null, synergyBombId, 10, battle.find(synergyBombId)!!.health.current),
        )
        lastTwo[0].shouldBeInstanceOf<BattleEvent.SynergyTriggered>()
        lastTwo[1] shouldBe bonusEvents.single()
    }
})
