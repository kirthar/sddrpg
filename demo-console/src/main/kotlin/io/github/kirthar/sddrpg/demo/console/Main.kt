package io.github.kirthar.sddrpg.demo.console

import io.github.kirthar.sddrpg.content.ContentLoadResult
import io.github.kirthar.sddrpg.content.demo.DEMO_CONTENT_JSON
import io.github.kirthar.sddrpg.content.loadContentPack
import io.github.kirthar.sddrpg.core.battle.BattleOutcome

/** Wires the real console: `System.in`/`println`. All loop logic lives in `BattleLoop.kt`. */
fun main() {
    val loaded = loadContentPack(DEMO_CONTENT_JSON)
    check(loaded is ContentLoadResult.Valid) { "shipped demo content failed to load: $loaded" }

    println("sddrpg console demo -- Cloud and Aerith vs. a Bomb.")
    val outcome = runBattleLoop(
        session = newBattleSession(),
        content = loaded.pack,
        readInput = ::readlnOrNull,
        emit = ::println,
    )

    when (outcome) {
        BattleOutcome.Victory -> println("Victory!")
        BattleOutcome.Defeat -> println("Defeat...")
        BattleOutcome.Ongoing -> println("The battle ends here (no more input).")
    }
}
