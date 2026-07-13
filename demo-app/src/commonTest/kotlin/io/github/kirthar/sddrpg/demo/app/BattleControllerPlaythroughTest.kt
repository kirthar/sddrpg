package io.github.kirthar.sddrpg.demo.app

import io.github.kirthar.sddrpg.content.ContentLoadResult
import io.github.kirthar.sddrpg.content.ContentPack
import io.github.kirthar.sddrpg.content.demo.DEMO_CONTENT_JSON
import io.github.kirthar.sddrpg.content.loadContentPack
import io.github.kirthar.sddrpg.core.limitbreak.LimitGaugeState
import io.github.kirthar.sddrpg.core.model.CombatantId
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf

/**
 * spec 009 US3: the same battle everywhere is backed by the shared controller --
 * this proves every prior mechanic (status tick, limit break, synergy, summon) is
 * reachable through it, mirroring spec 008's playthrough proof for the console.
 */
class BattleControllerPlaythroughTest : StringSpec({

    fun demoContent(): ContentPack = (loadContentPack(DEMO_CONTENT_JSON) as ContentLoadResult.Valid).pack

    fun BattleController.playPreferring(prefer: (List<OfferedAction>) -> OfferedAction?): BattlePhase.BattleOver {
        var guard = 0
        while (uiState.phase is BattlePhase.AwaitingPlayerAction && guard++ < 200) {
            val phase = uiState.phase as BattlePhase.AwaitingPlayerAction
            val action = prefer(phase.actions)?.takeIf { it.targets.isNotEmpty() }
                ?: phase.actions.first { it.targets.isNotEmpty() }
            submit(PlayerChoice(action, action.targets.first()))
        }
        return uiState.phase.shouldBeInstanceOf<BattlePhase.BattleOver>()
    }

    "a status effect tick occurs during play (cleave applies poison)" {
        val c = BattleController(demoContent())
        c.playPreferring { actions -> actions.filterIsInstance<OfferedAction.UseSkill>().firstOrNull { it.definition.skillId?.value == "cleave" } }
        c.uiState.logLines.any { it.contains("afflicted with poison") }.shouldBeTrue()
        c.uiState.logLines.any { it.contains("takes 5 damage") }.shouldBeTrue()
    }

    "a limit break resolves during play (omnislash, gauge pre-charged as in a longer battle)" {
        val session = newDemoSession().copy(gauges = LimitGaugeState(mapOf(CombatantId("c1") to 50)))
        val c = BattleController(demoContent(), session)
        val over = c.playPreferring { actions -> actions.filterIsInstance<OfferedAction.UseLimitBreak>().firstOrNull() }
        c.uiState.logLines.any { it.contains("unleashes omnislash") }.shouldBeTrue()
        over.victory shouldBe true // omnislash's 200 fixed damage always finishes the 80-HP bomb
    }

    "a summon is cast during play (meteor)" {
        val c = BattleController(demoContent())
        c.playPreferring { actions -> actions.filterIsInstance<OfferedAction.UseSummon>().firstOrNull() }
        c.uiState.logLines.any { it.contains("calls forth meteor") }.shouldBeTrue()
    }

    "a synergy triggers during play (cleave + fira on the same target within the window)" {
        val c = BattleController(demoContent())
        c.playPreferring { actions ->
            actions.filterIsInstance<OfferedAction.UseSkill>().firstOrNull()
        }
        c.uiState.logLines.any { it.contains("combine their attacks") }.shouldBeTrue()
    }
})
