package io.github.ukemeikot.flicksoccer.data

import com.russhwolf.settings.Settings
import io.github.ukemeikot.flicksoccer.domain.model.Difficulty

/** Player preferences, persisted instantly. Backed by multiplatform-settings. */
interface SettingsRepository {
    var soundEnabled: Boolean
    var hapticsEnabled: Boolean
    var defaultDifficulty: Difficulty
    var teamPalette: Int // 0..3
}

class SettingsRepositoryImpl(
    private val settings: Settings = Settings(),
) : SettingsRepository {

    override var soundEnabled: Boolean
        get() = settings.getBoolean(KEY_SOUND, true)
        set(value) = settings.putBoolean(KEY_SOUND, value)

    override var hapticsEnabled: Boolean
        get() = settings.getBoolean(KEY_HAPTICS, true)
        set(value) = settings.putBoolean(KEY_HAPTICS, value)

    override var defaultDifficulty: Difficulty
        get() = runCatching { Difficulty.valueOf(settings.getString(KEY_DIFFICULTY, Difficulty.MEDIUM.name)) }
            .getOrDefault(Difficulty.MEDIUM)
        set(value) = settings.putString(KEY_DIFFICULTY, value.name)

    override var teamPalette: Int
        get() = settings.getInt(KEY_PALETTE, 0)
        set(value) = settings.putInt(KEY_PALETTE, value)

    private companion object {
        const val KEY_SOUND = "sound_enabled"
        const val KEY_HAPTICS = "haptics_enabled"
        const val KEY_DIFFICULTY = "default_difficulty"
        const val KEY_PALETTE = "team_palette"
    }
}
