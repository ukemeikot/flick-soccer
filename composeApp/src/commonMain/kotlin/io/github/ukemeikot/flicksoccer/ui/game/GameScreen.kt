package io.github.ukemeikot.flicksoccer.ui.game

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.ukemeikot.flicksoccer.domain.model.Difficulty
import io.github.ukemeikot.flicksoccer.domain.model.ShotType
import io.github.ukemeikot.flicksoccer.domain.model.Team
import io.github.ukemeikot.flicksoccer.platform.gl.GameGlSurface
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun GameScreen(
    vsAi: Boolean,
    difficulty: Difficulty,
    onExit: () -> Unit,
    viewModel: GameViewModel = koinViewModel(),
) {
    LaunchedEffect(vsAi, difficulty) { viewModel.startMatch(vsAi, difficulty) }
    val state by viewModel.state.collectAsState()

    Column(modifier = Modifier.fillMaxSize()) {
        // HUD bar sits ABOVE the GL surface (never overlaid) — §8.2.
        HudBar(
            scoreA = state.match.scoreA,
            scoreB = state.match.scoreB,
            turn = state.match.turn,
            turnNumber = state.match.turnNumber,
            shotType = state.shotType,
            onToggleShot = {
                viewModel.setShotType(if (state.shotType == ShotType.GROUND) ShotType.CHIP else ShotType.GROUND)
            },
            onPause = onExit,
        )
        Box(modifier = Modifier.fillMaxSize()) {
            GameGlSurface(
                modifier = Modifier.fillMaxSize(),
                snapshot = null,
                onPointer = { /* wired to aiming in M4 */ },
            )
        }
    }
}

@Composable
private fun HudBar(
    scoreA: Int,
    scoreB: Int,
    turn: Team,
    turnNumber: Int,
    shotType: ShotType,
    onToggleShot: () -> Unit,
    onPause: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("A $scoreA – $scoreB B", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
        Text(
            "Turn ${turn.name} · $turnNumber",
            color = if (turn == Team.A) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary,
            style = MaterialTheme.typography.labelLarge,
        )
        TextButton(onClick = onToggleShot) {
            Text(if (shotType == ShotType.GROUND) "⚽ Ground" else "🪁 Chip")
        }
        TextButton(onClick = onPause) { Text("Pause") }
    }
}
