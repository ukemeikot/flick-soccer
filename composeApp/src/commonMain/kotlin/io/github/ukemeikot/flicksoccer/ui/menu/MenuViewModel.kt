package io.github.ukemeikot.flicksoccer.ui.menu

import androidx.lifecycle.ViewModel
import io.github.ukemeikot.flicksoccer.data.MatchHistory
import io.github.ukemeikot.flicksoccer.data.MatchHistoryRepository
import io.github.ukemeikot.flicksoccer.data.SettingsRepository
import io.github.ukemeikot.flicksoccer.domain.model.Difficulty
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class MenuUiState(
    val history: MatchHistory = MatchHistory(),
    val defaultDifficulty: Difficulty = Difficulty.MEDIUM,
)

class MenuViewModel(
    private val historyRepo: MatchHistoryRepository,
    private val settingsRepo: SettingsRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(
        MenuUiState(
            history = historyRepo.history(),
            defaultDifficulty = settingsRepo.defaultDifficulty,
        ),
    )
    val state: StateFlow<MenuUiState> = _state.asStateFlow()

    fun refresh() {
        _state.value = MenuUiState(
            history = historyRepo.history(),
            defaultDifficulty = settingsRepo.defaultDifficulty,
        )
    }
}
