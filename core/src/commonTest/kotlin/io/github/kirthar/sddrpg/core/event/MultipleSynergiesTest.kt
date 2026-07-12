package io.github.kirthar.sddrpg.core.event

import io.github.kirthar.sddrpg.core.action.ActionResolutionResult
import io.github.kirthar.sddrpg.core.action.BattleState
import io.github.kirthar.sddrpg.core.action.CombatAction
import io.github.kirthar.sddrpg.core.action.resolveAction
import io.github.kirthar.sddrpg.core.model.CombatantId
import io.github.kirthar.sddrpg.core.model.SkillId
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe

private fun resolveAndLog2(battle: BattleState, log: EventLog, action: CombatAction, targetId: CombatantId): Triple<BattleState, EventLog, List<BattleEvent>> {
    val result = resolveAction(battle, action, setOf(targetId))
    val resolved = result as ActionResolutionResult.Resolved
    val events = eventsFromResolution(battle, action, result)
    return Triple(resolved.newState, log.append(events), events)
}

class MultipleSynergiesTest : FunSpec({
    val secondSynergy = SynergyDefinition(
        id = SynergyId("warriorMageBonus2"),
        firstSkillId = SkillId("cleave"),
        secondSkillId = SkillId("fira"),
        window = 2,
        bonus = SynergyBonus.BonusDamage(3),
    )
    val neverSatisfiedSynergy = SynergyDefinition(
        id = SynergyId("neverSatisfied"),
        firstSkillId = SkillId("cleave"),
        secondSkillId = SkillId("unobtainium"),
        window = 2,
        bonus = SynergyBonus.BonusDamage(99),
    )

    test("two independently-defined synergies whose conditions are both satisfied by the same qualifying actions both trigger (US3 scenario 1)") {
        var battle = freshSynergyBattleState()
        var log = EventLog()
        val r1 = resolveAndLog2(battle, log, cleaveOn(cloudId), synergyBombId)
        battle = r1.first; log = r1.second
        val r2 = resolveAndLog2(battle, log, firaOn(aerithId), synergyBombId)

        val catalog = SynergyCatalog(listOf(warriorMageSynergy, secondSynergy))
        val triggers = detectSynergyTriggers(r2.third, r2.second, r2.first, catalog)

        triggers shouldContainExactlyInAnyOrder listOf(
            SynergyTrigger(warriorMageSynergy, cloudId, aerithId, synergyBombId),
            SynergyTrigger(secondSynergy, cloudId, aerithId, synergyBombId),
        )
    }

    test("a catalog of several synergies where only one's condition is satisfied triggers only that one (US3 scenario 2)") {
        var battle = freshSynergyBattleState()
        var log = EventLog()
        val r1 = resolveAndLog2(battle, log, cleaveOn(cloudId), synergyBombId)
        battle = r1.first; log = r1.second
        val r2 = resolveAndLog2(battle, log, firaOn(aerithId), synergyBombId)

        val catalog = SynergyCatalog(listOf(warriorMageSynergy, neverSatisfiedSynergy))
        detectSynergyTriggers(r2.third, r2.second, r2.first, catalog) shouldBe listOf(
            SynergyTrigger(warriorMageSynergy, cloudId, aerithId, synergyBombId)
        )
    }
})
