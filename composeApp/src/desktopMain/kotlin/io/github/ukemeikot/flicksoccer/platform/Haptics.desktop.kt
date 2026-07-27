package io.github.ukemeikot.flicksoccer.platform

/** Desktop has no haptics — no-op on every platform call. */
actual class Haptics actual constructor() {
    actual fun tick() { /* no-op */ }
    actual fun setEnabled(enabled: Boolean) { /* no-op */ }
}
