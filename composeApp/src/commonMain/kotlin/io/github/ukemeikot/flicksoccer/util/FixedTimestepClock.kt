package io.github.ukemeikot.flicksoccer.util

/**
 * Accumulates real elapsed time and yields fixed physics steps, exposing an interpolation [alpha]
 * (0..1) for smooth rendering between steps. Used by the game loop in the GameViewModel (§9).
 */
class FixedTimestepClock(private val stepSeconds: Float) {
    private var accumulator = 0f

    fun accumulate(deltaSeconds: Float) {
        // Clamp to avoid a spiral of death after a long pause/GC.
        accumulator += deltaSeconds.coerceAtMost(MAX_FRAME_SECONDS)
    }

    fun hasStep(): Boolean = accumulator >= stepSeconds

    fun consumeStep() {
        accumulator -= stepSeconds
    }

    fun alpha(): Float = (accumulator / stepSeconds).coerceIn(0f, 1f)

    companion object {
        const val MAX_FRAME_SECONDS = 0.25f
    }
}
