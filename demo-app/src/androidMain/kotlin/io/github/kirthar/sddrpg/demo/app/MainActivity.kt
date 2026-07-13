package io.github.kirthar.sddrpg.demo.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.remember
import io.github.kirthar.sddrpg.content.ContentLoadResult
import io.github.kirthar.sddrpg.content.demo.DEMO_CONTENT_JSON
import io.github.kirthar.sddrpg.content.loadContentPack
import io.github.kirthar.sddrpg.demo.app.ui.BattleScreen

/** Android entry point: the same shared screen, touch-driven (spec 009 US3). */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val controller = remember {
                val loaded = loadContentPack(DEMO_CONTENT_JSON)
                check(loaded is ContentLoadResult.Valid) { "shipped demo content failed to load: $loaded" }
                BattleController(loaded.pack)
            }
            BattleScreen(controller)
        }
    }
}
