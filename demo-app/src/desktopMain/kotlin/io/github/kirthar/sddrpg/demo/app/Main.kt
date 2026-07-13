package io.github.kirthar.sddrpg.demo.app

import androidx.compose.runtime.remember
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import io.github.kirthar.sddrpg.content.ContentLoadResult
import io.github.kirthar.sddrpg.content.demo.DEMO_CONTENT_JSON
import io.github.kirthar.sddrpg.content.loadContentPack
import io.github.kirthar.sddrpg.demo.app.ui.BattleScreen

/** Desktop entry point: the same shared screen in a window (dev app / test host target). */
fun main() = application {
    Window(onCloseRequest = ::exitApplication, title = "sddrpg demo") {
        val controller = remember {
            val loaded = loadContentPack(DEMO_CONTENT_JSON)
            check(loaded is ContentLoadResult.Valid) { "shipped demo content failed to load: $loaded" }
            BattleController(loaded.pack)
        }
        BattleScreen(controller)
    }
}
