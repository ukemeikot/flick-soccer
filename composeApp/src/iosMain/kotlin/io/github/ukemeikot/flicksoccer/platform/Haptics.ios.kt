package io.github.ukemeikot.flicksoccer.platform

/**
 * iOS haptics. M0 is a no-op; M6 wires UIImpactFeedbackGenerator.
 */
actual class Haptics actual constructor() {
    private var enabled = true
    actual fun tick() { /* TODO(M6): UIImpactFeedbackGenerator */ }
    actual fun setEnabled(enabled: Boolean) { this.enabled = enabled }
}
