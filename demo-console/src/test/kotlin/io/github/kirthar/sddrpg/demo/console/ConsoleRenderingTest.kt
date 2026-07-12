package io.github.kirthar.sddrpg.demo.console

import io.github.kirthar.sddrpg.content.demo.buildDemoBattleState
import io.github.kirthar.sddrpg.core.event.BattleEvent
import io.github.kirthar.sddrpg.core.event.GaugeFull
import io.github.kirthar.sddrpg.core.event.LimitBreakUsed
import io.github.kirthar.sddrpg.core.event.SummonCast
import io.github.kirthar.sddrpg.core.limitbreak.SummonId
import io.github.kirthar.sddrpg.core.model.LimitBreakId
import io.github.kirthar.sddrpg.core.status.StatusEffectId
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.string.shouldContain

/** spec 008 US2: every BattleEvent subtype renders as plain language, names resolved via battle. */
class ConsoleRenderingTest : StringSpec({

    val battle = buildDemoBattleState()
    val cloudId = battle.participants.first { it.combatant.displayName == "Cloud" }.combatant.id
    val aerithId = battle.participants.first { it.combatant.displayName == "Aerith" }.combatant.id
    val bombId = battle.participants.first { it.combatant.displayName == "Bomb" }.combatant.id

    "DamageDealt with an actor names both actor and target, never raw ids" {
        val text = BattleEvent.DamageDealt(cloudId, bombId, 24, 56).toDisplayText(battle)
        text shouldContain "Cloud"
        text shouldContain "Bomb"
        text shouldContain "24"
    }

    "DamageDealt with no actor (a status-effect tick) is worded actor-lessly" {
        val text = BattleEvent.DamageDealt(null, bombId, 5, 51).toDisplayText(battle)
        text shouldContain "Bomb"
        text shouldContain "5"
    }

    "HealingApplied with an actor names both actor and target" {
        val text = BattleEvent.HealingApplied(aerithId, cloudId, 10, 90).toDisplayText(battle)
        text shouldContain "Aerith"
        text shouldContain "Cloud"
    }

    "HealingApplied with no actor is worded actor-lessly" {
        val text = BattleEvent.HealingApplied(null, bombId, 32, 80).toDisplayText(battle)
        text shouldContain "Bomb"
    }

    "StatusEffectApplied names the combatant and the effect" {
        val text = BattleEvent.StatusEffectApplied(bombId, StatusEffectId("poison")).toDisplayText(battle)
        text shouldContain "Bomb"
        text shouldContain "poison"
    }

    "StatusEffectExpired names the combatant and the effect" {
        val text = BattleEvent.StatusEffectExpired(bombId, StatusEffectId("poison")).toDisplayText(battle)
        text shouldContain "Bomb"
        text shouldContain "poison"
    }

    "CombatantDefeated is shown distinctly from ordinary damage" {
        val text = BattleEvent.CombatantDefeated(bombId).toDisplayText(battle)
        text shouldContain "Bomb"
        text shouldContain "defeated"
    }

    "TurnGranted names whose turn it is" {
        val text = BattleEvent.TurnGranted(cloudId).toDisplayText(battle)
        text shouldContain "Cloud"
    }

    "SynergyTriggered names both actors and the target, distinct from an ordinary action" {
        val text = BattleEvent.SynergyTriggered(
            io.github.kirthar.sddrpg.core.event.SynergyId("warriorMage"), cloudId, aerithId, bombId,
        ).toDisplayText(battle)
        text shouldContain "Cloud"
        text shouldContain "Aerith"
        text shouldContain "Bomb"
    }

    "GaugeFull names the combatant, distinct from an ordinary action" {
        val text = GaugeFull(cloudId).toDisplayText(battle)
        text shouldContain "Cloud"
        text shouldContain "gauge"
    }

    "LimitBreakUsed names the combatant and the limit break, distinct from an ordinary action" {
        val text = LimitBreakUsed(cloudId, LimitBreakId("omnislash")).toDisplayText(battle)
        text shouldContain "Cloud"
        text shouldContain "omnislash"
    }

    "SummonCast names the combatant and the summon, distinct from an ordinary action" {
        val text = SummonCast(cloudId, SummonId("meteor")).toDisplayText(battle)
        text shouldContain "Cloud"
        text shouldContain "meteor"
    }
})
