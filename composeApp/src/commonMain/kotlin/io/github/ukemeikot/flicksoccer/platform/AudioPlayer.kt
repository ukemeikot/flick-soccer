package io.github.ukemeikot.flicksoccer.platform

/** The set of sound effects the game can play (assets bundled per §7). */
enum class SoundEffect {
    KICK, WALL_BOUNCE, GROUND_BOUNCE, CROSSBAR, GOAL, WHISTLE, CLICK,
}

/**
 * Thin platform audio wrapper. Real backends land in **M6**: Android SoundPool, iOS AVAudioPlayer,
 * Desktop javax.sound.sampled. M0 actuals are no-ops so the app runs silently.
 */
expect class AudioPlayer() {
    fun play(effect: SoundEffect)
    fun setEnabled(enabled: Boolean)
    fun release()
}
