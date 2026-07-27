package io.github.ukemeikot.flicksoccer.platform

/**
 * Thin platform haptics wrapper. Real backends land in **M6**: Android Vibrator, iOS
 * UIImpactFeedbackGenerator, Desktop no-op. M0 actuals are no-ops.
 */
expect class Haptics() {
    fun tick()
    fun setEnabled(enabled: Boolean)
}
