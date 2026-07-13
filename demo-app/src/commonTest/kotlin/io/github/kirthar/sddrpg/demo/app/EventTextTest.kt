package io.github.kirthar.sddrpg.demo.app

import io.github.kirthar.sddrpg.content.demo.buildDemoBattleState
import io.github.kirthar.sddrpg.core.action.ActionError
import io.github.kirthar.sddrpg.core.action.TargetingShape
import io.github.kirthar.sddrpg.core.catalog.CommandKind
import io.github.kirthar.sddrpg.core.event.BattleEvent
import io.github.kirthar.sddrpg.core.event.GaugeFull
import io.github.kirthar.sddrpg.core.event.LimitBreakUsed
import io.github.kirthar.sddrpg.core.event.SummonCast
import io.github.kirthar.sddrpg.core.event.SynergyId
import io.github.kirthar.sddrpg.core.limitbreak.LimitBreakError
import io.github.kirthar.sddrpg.core.limitbreak.SummonError
import io.github.kirthar.sddrpg.core.limitbreak.SummonId
import io.github.kirthar.sddrpg.core.model.LimitBreakId
import io.github.kirthar.sddrpg.core.model.SkillId
import io.github.kirthar.sddrpg.core.status.StatusEffectId
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain

/** spec 009 US2/FR-004: every occurrence and rejection kind renders as plain language. */
class EventTextTest : StringSpec({

    val battle = buildDemoBattleState()
    val cloudId = battle.participants.first { it.combatant.displayName == "Cloud" }.combatant.id
    val aerithId = battle.participants.first { it.combatant.displayName == "Aerith" }.combatant.id
    val bombId = battle.participants.first { it.combatant.displayName == "Bomb" }.combatant.id

    "DamageDealt with an actor names both actor and target, never raw ids" {
        val text = BattleEvent.DamageDealt(cloudId, bombId, 24, 56).toDisplayText(battle)
        text shouldContain "Cloud"
        text shouldContain "Bomb"
        text shouldContain "24"
        text shouldNotContain cloudId.value
    }

    "DamageDealt with no actor (a status tick) is worded actor-lessly" {
        val text = BattleEvent.DamageDealt(null, bombId, 5, 51).toDisplayText(battle)
        text shouldContain "Bomb"
        text shouldContain "5"
    }

    "HealingApplied with and without an actor" {
        BattleEvent.HealingApplied(aerithId, cloudId, 10, 90).toDisplayText(battle) shouldContain "Aerith"
        BattleEvent.HealingApplied(null, bombId, 32, 80).toDisplayText(battle) shouldContain "Bomb"
    }

    "StatusEffectApplied and StatusEffectExpired name combatant and effect" {
        BattleEvent.StatusEffectApplied(bombId, StatusEffectId("poison")).toDisplayText(battle) shouldContain "poison"
        BattleEvent.StatusEffectExpired(bombId, StatusEffectId("poison")).toDisplayText(battle) shouldContain "Bomb"
    }

    "CombatantDefeated, TurnGranted, SynergyTriggered, GaugeFull, LimitBreakUsed, SummonCast all render distinctly" {
        BattleEvent.CombatantDefeated(bombId).toDisplayText(battle) shouldContain "defeated"
        BattleEvent.TurnGranted(cloudId).toDisplayText(battle) shouldContain "Cloud"
        BattleEvent.SynergyTriggered(SynergyId("warriorMage"), cloudId, aerithId, bombId)
            .toDisplayText(battle) shouldContain "combine"
        GaugeFull(cloudId).toDisplayText(battle) shouldContain "gauge"
        LimitBreakUsed(cloudId, LimitBreakId("omnislash")).toDisplayText(battle) shouldContain "omnislash"
        SummonCast(cloudId, SummonId("meteor")).toDisplayText(battle) shouldContain "meteor"
    }

    "every ActionError variant renders as a readable reason" {
        ActionError.DefeatedActor(cloudId).toDisplayText(battle) shouldContain "Cloud"
        ActionError.MissingCommand(cloudId, CommandKind.MAGIC).toDisplayText(battle) shouldContain "MAGIC"
        ActionError.MissingSkill(cloudId, SkillId("fira")).toDisplayText(battle) shouldContain "fira"
        ActionError.UnknownTarget(bombId).toDisplayText(battle) shouldContain "target"
        ActionError.TargetShapeMismatch(TargetingShape.SINGLE_ENEMY, "already defeated")
            .toDisplayText(battle) shouldContain "already defeated"
    }

    "every LimitBreakError variant renders as a readable reason" {
        LimitBreakError.NoActiveLimitBreak(cloudId).toDisplayText(battle) shouldContain "Cloud"
        LimitBreakError.GaugeNotFull(cloudId, 10, 50).toDisplayText(battle) shouldContain "10/50"
        LimitBreakError.ActionRejected(cloudId, ActionError.MissingCommand(cloudId, CommandKind.SKILL))
            .toDisplayText(battle) shouldContain "SKILL"
    }

    "every SummonError variant renders as a readable reason" {
        SummonError.UnknownSummon(SummonId("bahamut")).toDisplayText(battle) shouldContain "bahamut"
        SummonError.InsufficientResource(aerithId, 15, 3).toDisplayText(battle) shouldContain "15"
        SummonError.ActionRejected(aerithId, ActionError.MissingCommand(aerithId, CommandKind.SUMMON))
            .toDisplayText(battle) shouldContain "SUMMON"
    }
})
