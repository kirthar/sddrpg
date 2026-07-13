package io.github.kirthar.sddrpg.demo.app

import androidx.compose.runtime.remember
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport
import io.github.kirthar.sddrpg.content.ContentLoadResult
import io.github.kirthar.sddrpg.content.demo.DEMO_CONTENT_JSON
import io.github.kirthar.sddrpg.content.loadContentPack
import io.github.kirthar.sddrpg.demo.app.ui.BattleScreen
import kotlinx.browser.document

/** Web entry point: the same shared screen in the browser viewport (spec 009 US3). */
@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    ComposeViewport(document.body!!) {
        val controller = remember {
            val loaded = loadContentPack(DEMO_CONTENT_JSON)
            check(loaded is ContentLoadResult.Valid) { "shipped demo content failed to load: $loaded" }
            BattleController(loaded.pack)
        }
        BattleScreen(controller)
    }
}
