package io.github.ukemeikot.flicksoccer.ui.game

import androidx.lifecycle.ViewModel
import io.github.ukemeikot.flicksoccer.domain.engine.GameEngine
import io.github.ukemeikot.flicksoccer.domain.model.AimState
import io.github.ukemeikot.flicksoccer.domain.model.Difficulty
import io.github.ukemeikot.flicksoccer.domain.model.MatchState
import io.github.ukemeikot.flicksoccer.domain.model.ShotType
import io.github.ukemeikot.flicksoccer.platform.AudioPlayer
import io.github.ukemeikot.flicksoccer.platform.Haptics
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

data class GameUiState(
    val match: MatchState,
    val aim: AimState? = null,
    val vsAi: Boolean = false,
    val difficulty: Difficulty = Difficulty.MEDIUM,
    val isPaused: Boolean = false,
    val shotType: ShotType = ShotType.GROUND,
    val interpolationAlpha: Float = 0f,
)

/** One-shot effects the ViewModel emits for the View to play (sound/haptic/navigate). */
sealed interface GameEffect {
    data object KickSound : GameEffect
    data object WallBounceSound : GameEffect
    data object GroundBounceSound : GameEffect
    data object CrossbarClangSound : GameEffect
    data class GoalScored(val by: io.github.ukemeikot.flicksoccer.domain.model.Team) : GameEffect
    data object WhistleEnd : GameEffect
    data object HapticTick : GameEffect
}

/**
 * Owns the match: the fixed-timestep game loop, turn management, effect dispatch and RenderSnapshot
 * publishing (§9). The loop and AI turns are wired in **M4/M5**; M0 exposes a live [GameUiState]
 * built from the engine's kickoff formation so the scene has real bodies to render.
 */
class GameViewModel(
    private val audio: AudioPlayer,
    private val haptics: Haptics,
) : ViewModel() {

    private val engine = GameEngine()

    private val _state = MutableStateFlow(GameUiState(match = engine.state))
    val state: StateFlow<GameUiState> = _state.asStateFlow()

    private val _effects = MutableSharedFlow<GameEffect>(extraBufferCapacity = 16)
    val effects: SharedFlow<GameEffect> = _effects.asSharedFlow()

    fun startMatch(vsAi: Boolean, difficulty: Difficulty) {
        engine.reset()
        _state.value = GameUiState(match = engine.state, vsAi = vsAi, difficulty = difficulty)
    }

    fun setShotType(shotType: ShotType) {
        _state.value = _state.value.copy(shotType = shotType)
    }

    fun setPaused(paused: Boolean) {
        _state.value = _state.value.copy(isPaused = paused)
    }

    override fun onCleared() {
        audio.release()
        super.onCleared()
    }
}
