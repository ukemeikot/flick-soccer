package io.github.ukemeikot.flicksoccer.di

import io.github.ukemeikot.flicksoccer.data.MatchHistoryRepository
import io.github.ukemeikot.flicksoccer.data.MatchHistoryRepositoryImpl
import io.github.ukemeikot.flicksoccer.data.SettingsRepository
import io.github.ukemeikot.flicksoccer.data.SettingsRepositoryImpl
import io.github.ukemeikot.flicksoccer.domain.ai.AiPlanner
import io.github.ukemeikot.flicksoccer.platform.AudioPlayer
import io.github.ukemeikot.flicksoccer.platform.Haptics
import io.github.ukemeikot.flicksoccer.ui.game.GameViewModel
import io.github.ukemeikot.flicksoccer.ui.menu.MenuViewModel
import io.github.ukemeikot.flicksoccer.ui.settings.SettingsViewModel
import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

/** The single Koin module for the whole app. No network, no auth — nothing gates startup. */
val appModule: Module = module {
    single<SettingsRepository> { SettingsRepositoryImpl() }
    single<MatchHistoryRepository> { MatchHistoryRepositoryImpl() }
    single { AudioPlayer() }
    single { Haptics() }
    single { AiPlanner() }

    viewModelOf(::MenuViewModel)
    viewModelOf(::SettingsViewModel)
    viewModelOf(::GameViewModel)
}
