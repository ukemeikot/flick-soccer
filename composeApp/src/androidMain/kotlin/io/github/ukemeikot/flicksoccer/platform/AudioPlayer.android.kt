package io.github.ukemeikot.flicksoccer.platform

import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack

/**
 * Android audio via AudioTrack (no Context required), playing procedurally synthesized 16-bit mono
 * PCM (§SoundSynth). A short pool of tracks is reused/recycled so rapid effects don't leak.
 */
actual class AudioPlayer actual constructor() {
    private var enabled = true
    private val live = ArrayDeque<AudioTrack>()

    actual fun play(effect: SoundEffect) {
        if (!enabled) return
        runCatching {
            recycle()
            val pcm = SoundSynth.pcm16(effect)
            val bytes = pcm.size * 2
            @Suppress("DEPRECATION")
            val track = AudioTrack(
                AudioManager.STREAM_MUSIC,
                SoundSynth.SAMPLE_RATE,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                bytes,
                AudioTrack.MODE_STATIC,
            )
            track.write(pcm, 0, pcm.size)
            track.play()
            live.addLast(track)
            if (live.size > MAX_LIVE) live.removeFirst().also { runCatching { it.release() } }
        }
    }

    private fun recycle() {
        val it = live.iterator()
        while (it.hasNext()) {
            val t = it.next()
            if (t.playState == AudioTrack.PLAYSTATE_STOPPED) { runCatching { t.release() }; it.remove() }
        }
    }

    actual fun setEnabled(enabled: Boolean) { this.enabled = enabled }

    actual fun release() {
        live.forEach { runCatching { it.release() } }
        live.clear()
    }

    private companion object { const val MAX_LIVE = 8 }
}
