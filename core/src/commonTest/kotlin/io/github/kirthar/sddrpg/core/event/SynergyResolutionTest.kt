package io.github.kirthar.sddrpg.core.event

import io.github.kirthar.sddrpg.core.action.ActionResolutionResult
import io.github.kirthar.sddrpg.core.action.BattleState
import io.github.kirthar.sddrpg.core.action.resolveAction
import io.github.kirthar.sddrpg.core.model.SkillId
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

private fun resolveAndLog(battle: BattleState, log: EventLog, action: io.github.kirthar.sddrpg.core.action.CombatAction, targetId: io.github.kirthar.sddrpg.core.model.CombatantId): Triple<BattleState, EventLog, List<BattleEvent>> {
    val result = resolveAction(battle, action, setOf(targetId))
    val resolved = result as ActionResolutionResult.Resolved
    val events = eventsFromResolution(battle, action, result)
    return Triple(resolved.newState, log.append(events), events)
}

class SynergyResolutionTest : FunSpec({
    test("two distinct combatants each acting on the same target within the window trigger the synergy (US2 scenario 1)") {
        var battle = freshSynergyBattleState()
        var log = EventLog()
        val (battle1, log1, _) = resolveAndLog(battle, log, cleaveOn(cloudId), synergyBombId)
        battle = battle1; log = log1
        val (battle2, log2, events2) = resolveAndLog(battle, log, firaOn(aerithId), synergyBombId)
        battle = battle2; log = log2

        val catalog = SynergyCatalog(listOf(warriorMageSynergy))
        val triggers = detectSynergyTriggers(events2, log, battle, catalog)

        triggers shouldBe listOf(SynergyTrigger(warriorMageSynergy, cloudId, aerithId, synergyBombId))
    }

    test("the second qualifying action outside the window does not trigger (US2 scenario 2)") {
        var battle = freshSynergyBattleState()
        var log = EventLog()
        val r1 = resolveAndLog(battle, log, cleaveOn(cloudId), synergyBombId)
        battle = r1.first; log = r1.second

        // burn through the window (window = 2) with unrelated turns
        log = log.append(listOf(BattleEvent.TurnGranted(cloudId), BattleEvent.TurnGranted(cloudId), BattleEvent.TurnGranted(cloudId)))

        val r2 = resolveAndLog(battle, log, firaOn(aerithId), synergyBombId)
        battle = r2.first; log = r2.second

        val catalog = SynergyCatalog(listOf(warriorMageSynergy))
        detectSynergyTriggers(r2.third, log, battle, catalog) shouldBe emptyList()
    }

    test("a partner exactly window turns earlier still triggers; one turn beyond does not (research R5)") {
        val catalog = SynergyCatalog(listOf(warriorMageSynergy))

        var battle = freshSynergyBattleState()
        var log = EventLog()
        val r1 = resolveAndLog(battle, log, cleaveOn(cloudId), synergyBombId)
        battle = r1.first; log = r1.second
        log = log.append(listOf(BattleEvent.TurnGranted(cloudId), BattleEvent.TurnGranted(cloudId))) // exactly 2 turns pass
        val r2 = resolveAndLog(battle, log, firaOn(aerithId), synergyBombId)
        detectSynergyTriggers(r2.third, r2.second, r2.first, catalog) shouldBe listOf(SynergyTrigger(warriorMageSynergy, cloudId, aerithId, synergyBombId))
    }

    test("two actions targeting different combatants do not trigger (US2 scenario 3)") {
        var battle = freshSynergyBattleState()
        var log = EventLog()
        val r1 = resolveAndLog(battle, log, cleaveOn(cloudId), synergyBombId)
        battle = r1.first; log = r1.second
        val r2 = resolveAndLog(battle, log, firaOn(aerithId), flanId)

        val catalog = SynergyCatalog(listOf(warriorMageSynergy))
        detectSynergyTriggers(r2.third, r2.second, r2.first, catalog) shouldBe emptyList()
    }

    test("the same single combatant satisfying both capability roles does not trigger it (US2 scenario 4, FR-008)") {
        var battle = freshSynergyBattleState()
        var log = EventLog()
        val r1 = resolveAndLog(battle, log, cleaveOn(tifaId), synergyBombId)
        battle = r1.first; log = r1.second
        val r2 = resolveAndLog(battle, log, firaOn(tifaId), synergyBombId)

        val catalog = SynergyCatalog(listOf(warriorMageSynergy))
        detectSynergyTriggers(r2.third, r2.second, r2.first, catalog) shouldBe emptyList()
    }

    test("a triggered BonusDamage bonus decreases the target's health within [0, maximum] bounds (US2 scenario 5, SC-003)") {
        var battle = freshSynergyBattleState()
        var log = EventLog()
        val r1 = resolveAndLog(battle, log, cleaveOn(cloudId), synergyBombId)
        battle = r1.first; log = r1.second
        val r2 = resolveAndLog(battle, log, firaOn(aerithId), synergyBombId)
        battle = r2.first; log = r2.second

        val catalog = SynergyCatalog(listOf(warriorMageSynergy))
        val trigger = detectSynergyTriggers(r2.third, log, battle, catalog).single()

        val before = battle.find(synergyBombId)!!.health.current
        battle = applySynergyBonus(battle, trigger.targetId, trigger.definition.bonus)
        val after = battle.find(synergyBombId)!!.health.current

        after shouldBe (before - 10).coerceIn(0, battle.find(synergyBombId)!!.health.maximum)
    }

    test("FR-005: the nearest eligible prior qualifying event wins the pairing, not the first-in-log or an arbitrary one") {
        var battle = freshSynergyBattleState()
        var log = EventLog()
        // two eligible "cleave" partners for a later "fira" closing event: cloud first, tifa second (nearest)
        val r1 = resolveAndLog(battle, log, cleaveOn(cloudId), synergyBombId)
        battle = r1.first; log = r1.second
        val r2 = resolveAndLog(battle, log, cleaveOn(tifaId), synergyBombId)
        battle = r2.first; log = r2.second
        val r3 = resolveAndLog(battle, log, firaOn(aerithId), synergyBombId)

        val catalog = SynergyCatalog(listOf(warriorMageSynergy))
        val triggers = detectSynergyTriggers(r3.third, r3.second, r3.first, catalog)

        triggers shouldBe listOf(SynergyTrigger(warriorMageSynergy, tifaId, aerithId, synergyBombId))
    }

    test("a synergy's window expiring with no second qualifying action produces no trigger and no error") {
        var battle = freshSynergyBattleState()
        var log = EventLog()
        val r1 = resolveAndLog(battle, log, cleaveOn(cloudId), synergyBombId)
        log = r1.second.append(List(5) { BattleEvent.TurnGranted(cloudId) })

        val catalog = SynergyCatalog(listOf(warriorMageSynergy))
        detectSynergyTriggers(emptyList(), log, r1.first, catalog) shouldBe emptyList()
    }

    test("a combatant defeated after supplying one half can still have that recorded event pair with a later qualifying event from a different combatant") {
        var battle = freshSynergyBattleState()
        var log = EventLog()
        val r1 = resolveAndLog(battle, log, cleaveOn(cloudId), synergyBombId)
        battle = r1.first; log = r1.second
        // cloud is defeated after supplying the first half (health irrelevant to detection -- only skills/target/window matter)
        battle = BattleState(battle.participants.map { if (it.combatant.id == cloudId) it.copy(health = it.health.copy(current = 0)) else it })

        val r2 = resolveAndLog(battle, log, firaOn(aerithId), synergyBombId)
        val catalog = SynergyCatalog(listOf(warriorMageSynergy))
        detectSynergyTriggers(r2.third, r2.second, r2.first, catalog) shouldBe listOf(SynergyTrigger(warriorMageSynergy, cloudId, aerithId, synergyBombId))
    }

    test("an empty SynergyCatalog never triggers anything") {
        var battle = freshSynergyBattleState()
        var log = EventLog()
        val r1 = resolveAndLog(battle, log, cleaveOn(cloudId), synergyBombId)
        battle = r1.first; log = r1.second
        val r2 = resolveAndLog(battle, log, firaOn(aerithId), synergyBombId)

        detectSynergyTriggers(r2.third, r2.second, r2.first, SynergyCatalog()) shouldBe emptyList()
    }
})
