package io.github.ukemeikot.flicksoccer.platform

import java.io.ByteArrayInputStream
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.Clip

/** Desktop audio via javax.sound.sampled, playing procedurally synthesized WAVs (§SoundSynth). */
actual class AudioPlayer actual constructor() {
    private var enabled = true
    private val clips = HashMap<SoundEffect, Clip>()

    private fun clipFor(effect: SoundEffect): Clip? = clips[effect] ?: runCatching {
        val stream = AudioSystem.getAudioInputStream(ByteArrayInputStream(SoundSynth.wav(effect)))
        AudioSystem.getClip().apply { open(stream) }
    }.getOrNull()?.also { clips[effect] = it }

    actual fun play(effect: SoundEffect) {
        if (!enabled) return
        runCatching {
            val clip = clipFor(effect) ?: return
            clip.stop()
            clip.framePosition = 0
            clip.start()
        }
    }

    actual fun setEnabled(enabled: Boolean) { this.enabled = enabled }

    actual fun release() {
        clips.values.forEach { runCatching { it.close() } }
        clips.clear()
    }
}
