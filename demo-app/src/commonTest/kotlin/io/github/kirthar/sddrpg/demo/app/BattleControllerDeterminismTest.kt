package io.github.kirthar.sddrpg.demo.app

import io.github.kirthar.sddrpg.content.ContentLoadResult
import io.github.kirthar.sddrpg.content.ContentPack
import io.github.kirthar.sddrpg.content.demo.DEMO_CONTENT_JSON
import io.github.kirthar.sddrpg.content.loadContentPack
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

/** spec 009 FR-009/SC-002: same submissions => identical uiState sequence and outcome. */
class BattleControllerDeterminismTest : StringSpec({

    fun demoContent(): ContentPack = (loadContentPack(DEMO_CONTENT_JSON) as ContentLoadResult.Valid).pack

    "two controllers fed the identical submission strategy produce identical states at every step" {
        val content = demoContent()
        val first = BattleController(content, newDemoSession())
        val second = BattleController(content, newDemoSession())

        second.uiState shouldBe first.uiState

        var guard = 0
        while (first.uiState.phase is BattlePhase.AwaitingPlayerAction && guard++ < 200) {
            val phase = first.uiState.phase as BattlePhase.AwaitingPlayerAction
            val action = phase.actions.first { it.targets.isNotEmpty() }
            val choice = PlayerChoice(action, action.targets.first())
            first.submit(choice)
            second.submit(choice)
            second.uiState shouldBe first.uiState
        }

        (first.uiState.phase is BattlePhase.BattleOver) shouldBe true
    }
})
