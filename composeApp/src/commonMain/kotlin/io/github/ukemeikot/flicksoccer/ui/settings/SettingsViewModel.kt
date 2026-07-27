package io.github.ukemeikot.flicksoccer.ui.settings

import androidx.lifecycle.ViewModel
import io.github.ukemeikot.flicksoccer.data.MatchHistoryRepository
import io.github.ukemeikot.flicksoccer.data.SettingsRepository
import io.github.ukemeikot.flicksoccer.domain.model.Difficulty
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class SettingsUiState(
    val soundEnabled: Boolean = true,
    val hapticsEnabled: Boolean = true,
    val defaultDifficulty: Difficulty = Difficulty.MEDIUM,
    val teamPalette: Int = 0,
)

class SettingsViewModel(
    private val settingsRepo: SettingsRepository,
    private val historyRepo: MatchHistoryRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(read())
    val state: StateFlow<SettingsUiState> = _state.asStateFlow()

    private fun read() = SettingsUiState(
        soundEnabled = settingsRepo.soundEnabled,
        hapticsEnabled = settingsRepo.hapticsEnabled,
        defaultDifficulty = settingsRepo.defaultDifficulty,
        teamPalette = settingsRepo.teamPalette,
    )

    fun setSound(enabled: Boolean) {
        settingsRepo.soundEnabled = enabled
        _state.value = read()
    }

    fun setHaptics(enabled: Boolean) {
        settingsRepo.hapticsEnabled = enabled
        _state.value = read()
    }

    fun setDifficulty(difficulty: Difficulty) {
        settingsRepo.defaultDifficulty = difficulty
        _state.value = read()
    }

    fun setPalette(index: Int) {
        settingsRepo.teamPalette = index
        _state.value = read()
    }

    fun resetHistory() {
        historyRepo.clear()
        _state.value = read()
    }
}
