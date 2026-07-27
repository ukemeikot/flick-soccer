package io.github.ukemeikot.flicksoccer.platform

/**
 * Android haptics. M0 is a no-op; M6 wires the system Vibrator / HapticFeedback (Context obtained
 * via the Koin platform module).
 */
actual class Haptics actual constructor() {
    private var enabled = true
    actual fun tick() { /* TODO(M6): Vibrator */ }
    actual fun setEnabled(enabled: Boolean) { this.enabled = enabled }
}
