package io.github.kirthar.sddrpg.demo.console

import io.github.kirthar.sddrpg.content.ContentLoadResult
import io.github.kirthar.sddrpg.content.ContentPack
import io.github.kirthar.sddrpg.content.demo.DEMO_CONTENT_JSON
import io.github.kirthar.sddrpg.content.loadContentPack
import io.github.kirthar.sddrpg.core.battle.BattleOutcome
import io.github.kirthar.sddrpg.core.limitbreak.LimitGaugeState
import io.github.kirthar.sddrpg.core.model.CombatantId
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

/**
 * spec 008 US3: a full playthrough of the shipped demo content exercises every
 * mechanic specs 001-007 built at least once -- a status effect tick, a limit break,
 * and a synergy trigger or summon cast. Each is proven via its own scripted run
 * (rather than one combined battle) so the assertion doesn't depend on emergent
 * turn-order/pacing interactions between cloud and aerith -- each run still exercises
 * the real loop wiring (resolveLimitBreak/resolveSummon/tickStatusEffects/
 * detectSynergyTriggers) against the real shipped demo content.
 */
class BattleLoopPlaythroughTest : StringSpec({

    fun demoContent(): ContentPack = (loadContentPack(DEMO_CONTENT_JSON) as ContentLoadResult.Valid).pack

    fun repeating(vararg lines: String): () -> String? {
        var i = 0
        return { lines[(i++) % lines.size] }
    }

    "a status effect tick occurs during play (cleave applies poison)" {
        val session = newBattleSession()
        val content = demoContent()
        val emitted = mutableListOf<String>()
        val outcome = runBattleLoop(session, content, repeating("cleave Bomb", "attack Bomb"), emitted::add)

        (outcome == BattleOutcome.Victory || outcome == BattleOutcome.Defeat) shouldBe true
        emitted.any { it.contains("poison") } shouldBe true
    }

    "a limit break resolves during play (cloud's omnislash, once his gauge is full)" {
        // Cloud (RosterBuilder's insertion-order "c1") starts with his limit gauge
        // already at threshold, simulating that he took hits earlier in a longer
        // battle -- exercises the real resolveLimitBreak/eventsFromLimitBreak
        // pathway against the real shipped demo content without depending on how
        // many turns bomb's counter-attacks would otherwise take to fill it.
        val session = newBattleSession().copy(gauges = LimitGaugeState(mapOf(CombatantId("c1") to 50)))
        val content = demoContent()
        val emitted = mutableListOf<String>()
        val outcome = runBattleLoop(session, content, repeating("omnislash Bomb", "attack Bomb"), emitted::add)

        (outcome == BattleOutcome.Victory || outcome == BattleOutcome.Defeat) shouldBe true
        emitted.any { it.contains("omnislash") } shouldBe true
    }

    "a summon is cast during play (cloud's meteor)" {
        val session = newBattleSession()
        val content = demoContent()
        val emitted = mutableListOf<String>()
        val outcome = runBattleLoop(session, content, repeating("meteor Bomb", "attack Bomb"), emitted::add)

        (outcome == BattleOutcome.Victory || outcome == BattleOutcome.Defeat) shouldBe true
        emitted.any { it.contains("meteor") } shouldBe true
    }

    "a synergy triggers during play (cloud's cleave and aerith's fira on the same target within the window)" {
        val session = newBattleSession()
        val content = demoContent()
        val emitted = mutableListOf<String>()
        // Both cloud and aerith always target the bomb; whichever order they act in,
        // cleave and fira land on the same target within warriorMage's window (2).
        val outcome = runBattleLoop(session, content, repeating("cleave Bomb", "fira Bomb"), emitted::add)

        (outcome == BattleOutcome.Victory || outcome == BattleOutcome.Defeat) shouldBe true
        emitted.any { it.contains("combine their attacks") } shouldBe true
    }
})
