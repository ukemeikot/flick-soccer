package io.github.ukemeikot.flicksoccer.platform

/**
 * iOS audio. M0 is a silent no-op; M6 wires AVAudioPlayer to play the bundled WAV assets.
 */
actual class AudioPlayer actual constructor() {
    private var enabled = true
    actual fun play(effect: SoundEffect) { /* TODO(M6): AVAudioPlayer */ }
    actual fun setEnabled(enabled: Boolean) { this.enabled = enabled }
    actual fun release() { /* no-op */ }
}
