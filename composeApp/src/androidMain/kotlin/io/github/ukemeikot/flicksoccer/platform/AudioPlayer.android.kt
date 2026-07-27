package io.github.ukemeikot.flicksoccer.platform

/**
 * Android audio. M0 is a silent no-op; M6 wires SoundPool (obtaining the app Context via a Koin
 * platform module or androidx.startup) to play the bundled WAV assets.
 */
actual class AudioPlayer actual constructor() {
    private var enabled = true
    actual fun play(effect: SoundEffect) { /* TODO(M6): SoundPool */ }
    actual fun setEnabled(enabled: Boolean) { this.enabled = enabled }
    actual fun release() { /* TODO(M6): release SoundPool */ }
}
