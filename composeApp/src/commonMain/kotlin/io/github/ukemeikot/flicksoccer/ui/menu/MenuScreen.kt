package io.github.ukemeikot.flicksoccer.ui.menu

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.ukemeikot.flicksoccer.domain.model.Difficulty
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun MenuScreen(
    onPlayVsAi: (Difficulty) -> Unit,
    onTwoPlayer: () -> Unit,
    onSettings: () -> Unit,
    viewModel: MenuViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsState()
    var difficulty by remember { mutableStateOf(state.defaultDifficulty) }

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text("Flick Soccer", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text("Turn-based 3D physics soccer", style = MaterialTheme.typography.bodyMedium)

        Spacer(Modifier.height(32.dp))

        Text("Difficulty", style = MaterialTheme.typography.labelLarge)
        Spacer(Modifier.height(8.dp))
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            androidx.compose.foundation.layout.Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Difficulty.entries.forEach { d ->
                    FilterChip(
                        selected = difficulty == d,
                        onClick = { difficulty = d },
                        label = { Text(d.name.lowercase().replaceFirstChar { it.uppercase() }) },
                    )
                }
            }
        }

        Spacer(Modifier.height(24.dp))

        Button(onClick = { onPlayVsAi(difficulty) }, modifier = Modifier.fillMaxWidth()) {
            Text("Play vs AI")
        }
        Spacer(Modifier.height(12.dp))
        Button(onClick = onTwoPlayer, modifier = Modifier.fillMaxWidth()) {
            Text("2 Players")
        }
        Spacer(Modifier.height(12.dp))
        OutlinedButton(onClick = onSettings, modifier = Modifier.fillMaxWidth()) {
            Text("Settings")
        }

        Spacer(Modifier.height(32.dp))

        val h = state.history
        if (h.played > 0) {
            Text(
                "Played ${h.played} · A ${h.winsA} – ${h.winsB} B · ${h.draws} draws",
                style = MaterialTheme.typography.bodySmall,
            )
            h.last?.let {
                Text("Last: ${it.scoreA} – ${it.scoreB}", style = MaterialTheme.typography.bodySmall)
            }
        } else {
            Text("No matches yet — play your first!", style = MaterialTheme.typography.bodySmall)
        }
    }
}
