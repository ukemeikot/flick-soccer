package io.github.ukemeikot.flicksoccer.ui.game

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.ukemeikot.flicksoccer.domain.model.Difficulty
import io.github.ukemeikot.flicksoccer.domain.model.MatchPhase
import io.github.ukemeikot.flicksoccer.domain.model.ShotType
import io.github.ukemeikot.flicksoccer.domain.model.Team
import io.github.ukemeikot.flicksoccer.ui.game.render.GameCanvasScene
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
        HudBar(
            scoreA = state.match.scoreA,
            scoreB = state.match.scoreB,
            turn = state.match.turn,
            turnNumber = state.match.turnNumber,
            shotType = state.shotType,
            onToggleShot = {
                viewModel.setShotType(if (state.shotType == ShotType.GROUND) ShotType.CHIP else ShotType.GROUND)
            },
            onPause = { viewModel.setPaused(true) },
        )
        Box(modifier = Modifier.fillMaxSize()) {
            GameCanvasScene(
                modifier = Modifier.fillMaxSize(),
                snapshotProvider = viewModel::renderSnapshot,
                onPointer = viewModel::onPointer,
                paletteIndex = state.paletteIndex,
            )

            when (state.match.phase) {
                MatchPhase.GOAL_SCORED -> CenterBanner("GOAL!")
                MatchPhase.MATCH_OVER -> MatchOverOverlay(
                    scoreA = state.match.scoreA,
                    scoreB = state.match.scoreB,
                    winner = state.match.winner,
                    onRematch = { viewModel.rematch() },
                    onMenu = onExit,
                )
                else -> Unit
            }

            if (state.isPaused) {
                PauseOverlay(
                    onResume = { viewModel.setPaused(false) },
                    onRestart = { viewModel.rematch() },
                    onQuit = onExit,
                )
            }
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

@Composable
private fun CenterBanner(text: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text, style = MaterialTheme.typography.displayMedium, fontWeight = FontWeight.Black, color = Color.White)
    }
}

@Composable
private fun PauseOverlay(onResume: () -> Unit, onRestart: () -> Unit, onQuit: () -> Unit) {
    OverlayScrim {
        Text("Paused", style = MaterialTheme.typography.headlineMedium, color = Color.White)
        Spacer(Modifier.height(20.dp))
        Button(onClick = onResume, modifier = Modifier.fillMaxWidth()) { Text("Resume") }
        Spacer(Modifier.height(10.dp))
        Button(onClick = onRestart, modifier = Modifier.fillMaxWidth()) { Text("Restart") }
        Spacer(Modifier.height(10.dp))
        OutlinedButton(onClick = onQuit, modifier = Modifier.fillMaxWidth()) { Text("Quit to menu") }
    }
}

@Composable
private fun MatchOverOverlay(scoreA: Int, scoreB: Int, winner: Team?, onRematch: () -> Unit, onMenu: () -> Unit) {
    OverlayScrim {
        val title = when (winner) {
            Team.A -> "Blue wins!"
            Team.B -> "Red wins!"
            null -> "Draw"
        }
        Text(title, style = MaterialTheme.typography.headlineMedium, color = Color.White)
        Spacer(Modifier.height(8.dp))
        Text("$scoreA – $scoreB", style = MaterialTheme.typography.titleLarge, color = Color.White)
        Spacer(Modifier.height(20.dp))
        Button(onClick = onRematch, modifier = Modifier.fillMaxWidth()) { Text("Rematch") }
        Spacer(Modifier.height(10.dp))
        OutlinedButton(onClick = onMenu, modifier = Modifier.fillMaxWidth()) { Text("Menu") }
    }
}

@Composable
private fun OverlayScrim(content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    Box(
        modifier = Modifier.fillMaxSize().background(Color(0xCC000000)),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(0.7f).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            content = content,
        )
    }
}
