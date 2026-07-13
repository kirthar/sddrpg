package io.github.kirthar.sddrpg.demo.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import io.github.kirthar.sddrpg.demo.app.BattleController
import io.github.kirthar.sddrpg.demo.app.BattlePhase
import io.github.kirthar.sddrpg.demo.app.OfferedAction
import io.github.kirthar.sddrpg.demo.app.ParticipantView
import io.github.kirthar.sddrpg.demo.app.PlayerChoice
import io.github.kirthar.sddrpg.demo.app.label

/**
 * The single battle screen (spec 009 US1/US2): health panels, scrolling log, action
 * buttons on the player's turn, a target-picker second step with cancel (backing out
 * never consumes the turn -- purely local UI state), rejection text, and the
 * Victory/Defeat overlay. Observes [BattleController.uiState] as a plain value.
 */
@Composable
fun BattleScreen(controller: BattleController) {
    var state by remember { mutableStateOf(controller.uiState) }
    var selectedAction by remember { mutableStateOf<OfferedAction?>(null) }

    fun submit(action: OfferedAction, targetId: io.github.kirthar.sddrpg.core.model.CombatantId) {
        controller.submit(PlayerChoice(action, targetId))
        state = controller.uiState
        selectedAction = null
    }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.fillMaxSize().padding(12.dp)) {
                ParticipantPanels(state.participants)

                BattleLog(
                    lines = state.logLines,
                    modifier = Modifier.fillMaxWidth().weight(1f).padding(vertical = 8.dp),
                )

                state.lastRejection?.let { reason ->
                    Text(
                        text = reason,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(bottom = 4.dp),
                    )
                }

                val phase = state.phase
                if (phase is BattlePhase.AwaitingPlayerAction) {
                    val picking = selectedAction
                    if (picking == null) {
                        Text("${phase.actorName}'s turn -- choose an action:", style = MaterialTheme.typography.titleMedium)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                            for (action in phase.actions) {
                                Button(onClick = { selectedAction = action }) { Text(action.label) }
                            }
                        }
                    } else {
                        Text("${picking.label}: choose a target", style = MaterialTheme.typography.titleMedium)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                            for (targetId in picking.targets) {
                                val name = state.participants.firstOrNull { it.id == targetId }?.name ?: targetId.value
                                Button(onClick = { submit(picking, targetId) }) { Text(name) }
                            }
                            OutlinedButton(onClick = { selectedAction = null }) { Text("Back") }
                        }
                    }
                }
            }

            val phase = state.phase
            if (phase is BattlePhase.BattleOver) {
                Box(
                    modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.6f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = if (phase.victory) "Victory!" else "Defeat...",
                        style = MaterialTheme.typography.displayMedium,
                        color = Color.White,
                    )
                }
            }
        }
    }
}

@Composable
private fun ParticipantPanels(participants: List<ParticipantView>) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Column(modifier = Modifier.weight(1f)) {
            Text("Party", style = MaterialTheme.typography.labelLarge)
            for (p in participants.filter { it.isPlayerSide }) ParticipantRow(p)
        }
        Column(modifier = Modifier.weight(1f)) {
            Text("Enemies", style = MaterialTheme.typography.labelLarge)
            for (p in participants.filter { !it.isPlayerSide }) ParticipantRow(p)
        }
    }
}

@Composable
private fun ParticipantRow(p: ParticipantView) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(
            text = if (p.isDefeated) "${p.name} (down)" else p.name,
            style = MaterialTheme.typography.bodyLarge,
        )
        LinearProgressIndicator(
            progress = { if (p.maxHp == 0) 0f else p.currentHp.toFloat() / p.maxHp },
            modifier = Modifier.fillMaxWidth(),
        )
        Text("${p.currentHp} / ${p.maxHp} HP", style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun BattleLog(lines: List<String>, modifier: Modifier = Modifier) {
    val listState = rememberLazyListState()
    LaunchedEffect(lines.size) {
        if (lines.isNotEmpty()) listState.animateScrollToItem(lines.size - 1)
    }
    LazyColumn(state = listState, modifier = modifier) {
        items(lines) { line ->
            Text(line, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(vertical = 1.dp))
        }
    }
}
