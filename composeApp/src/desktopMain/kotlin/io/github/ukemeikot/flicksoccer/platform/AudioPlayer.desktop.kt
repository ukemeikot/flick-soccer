package io.github.ukemeikot.flicksoccer.platform

/**
 * Desktop audio. M0 is a silent no-op; M6 wires javax.sound.sampled.Clip playback of the bundled
 * WAV assets.
 */
actual class AudioPlayer actual constructor() {
    private var enabled = true
    actual fun play(effect: SoundEffect) { /* TODO(M6): javax.sound.sampled */ }
    actual fun setEnabled(enabled: Boolean) { this.enabled = enabled }
    actual fun release() { /* no-op */ }
}
