package io.github.ukemeikot.flicksoccer.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.ukemeikot.flicksoccer.domain.model.Difficulty
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    showHaptics: Boolean = true,
    viewModel: SettingsViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsState()

    Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
        Text("Settings", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(24.dp))

        SettingRow("Sound") {
            Switch(checked = state.soundEnabled, onCheckedChange = viewModel::setSound)
        }
        if (showHaptics) {
            SettingRow("Haptics") {
                Switch(checked = state.hapticsEnabled, onCheckedChange = viewModel::setHaptics)
            }
        }

        Spacer(Modifier.height(16.dp))
        Text("Default difficulty", style = MaterialTheme.typography.labelLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Difficulty.entries.forEach { d ->
                FilterChip(
                    selected = state.defaultDifficulty == d,
                    onClick = { viewModel.setDifficulty(d) },
                    label = { Text(d.name.lowercase().replaceFirstChar { it.uppercase() }) },
                )
            }
        }

        Spacer(Modifier.height(16.dp))
        Text("Team colors", style = MaterialTheme.typography.labelLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            (0..3).forEach { i ->
                FilterChip(
                    selected = state.teamPalette == i,
                    onClick = { viewModel.setPalette(i) },
                    label = { Text("Palette ${i + 1}") },
                )
            }
        }

        Spacer(Modifier.height(24.dp))
        OutlinedButton(onClick = viewModel::resetHistory, modifier = Modifier.fillMaxWidth()) {
            Text("Reset match history")
        }

        Spacer(Modifier.height(24.dp))
        Button(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text("Back") }
    }
}

@Composable
private fun SettingRow(label: String, control: @Composable () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge)
        control()
    }
}
