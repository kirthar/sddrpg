package io.github.kirthar.sddrpg.demo.console

import io.github.kirthar.sddrpg.content.ContentLoadResult
import io.github.kirthar.sddrpg.content.ContentPack
import io.github.kirthar.sddrpg.content.demo.DEMO_CONTENT_JSON
import io.github.kirthar.sddrpg.content.loadContentPack
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

/** spec 008 FR-010/SC-002: same content and same scripted inputs, replayed twice, are identical. */
class BattleLoopDeterminismTest : StringSpec({

    fun demoContent(): ContentPack = (loadContentPack(DEMO_CONTENT_JSON) as ContentLoadResult.Valid).pack

    fun repeating(vararg lines: String): () -> String? {
        var i = 0
        return { lines[(i++) % lines.size] }
    }

    "replaying the identical scripted input sequence produces the identical outcome and emitted text" {
        val content = demoContent()

        val firstEmitted = mutableListOf<String>()
        val firstOutcome = runBattleLoop(newBattleSession(), content, repeating("cleave Bomb", "attack Bomb"), firstEmitted::add)

        val secondEmitted = mutableListOf<String>()
        val secondOutcome = runBattleLoop(newBattleSession(), content, repeating("cleave Bomb", "attack Bomb"), secondEmitted::add)

        secondOutcome shouldBe firstOutcome
        secondEmitted shouldBe firstEmitted
    }
})
