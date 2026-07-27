package io.github.ukemeikot.flicksoccer.platform

import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

/**
 * Procedurally synthesizes short sound effects in pure Kotlin — no asset files, no resource loading.
 * Exposes 16-bit mono PCM (for Android AudioTrack) and a WAV-wrapped variant (for Desktop
 * javax.sound and iOS AVAudioPlayer). Everything is generated once and cached.
 */
object SoundSynth {
    const val SAMPLE_RATE = 22050

    private val pcmCache = HashMap<SoundEffect, ShortArray>()
    private val wavCache = HashMap<SoundEffect, ByteArray>()

    fun pcm16(effect: SoundEffect): ShortArray = pcmCache.getOrPut(effect) { synth(effect) }

    fun wav(effect: SoundEffect): ByteArray = wavCache.getOrPut(effect) { wrapWav(pcm16(effect)) }

    private fun synth(effect: SoundEffect): ShortArray = when (effect) {
        SoundEffect.KICK -> tone(freq = 130f, seconds = 0.12f, decay = 30f, gain = 0.9f)
        SoundEffect.WALL_BOUNCE -> tone(freq = 320f, seconds = 0.06f, decay = 45f, gain = 0.6f)
        SoundEffect.GROUND_BOUNCE -> tone(freq = 180f, seconds = 0.08f, decay = 35f, gain = 0.6f)
        SoundEffect.CROSSBAR -> mix(seconds = 0.18f, decay = 22f, gain = 0.7f, freqs = floatArrayOf(820f, 1240f, 1600f))
        SoundEffect.GOAL -> sweep(from = 420f, to = 860f, seconds = 0.45f, gain = 0.7f)
        SoundEffect.WHISTLE -> vibrato(base = 2100f, depth = 60f, rate = 9f, seconds = 0.4f, gain = 0.55f)
        SoundEffect.CLICK -> tone(freq = 1000f, seconds = 0.03f, decay = 80f, gain = 0.5f)
    }

    private fun tone(freq: Float, seconds: Float, decay: Float, gain: Float): ShortArray {
        val n = (seconds * SAMPLE_RATE).toInt()
        return ShortArray(n) { i ->
            val t = i.toFloat() / SAMPLE_RATE
            sample(gain * exp(-t * decay) * sin(2f * PI.toFloat() * freq * t))
        }
    }

    private fun mix(seconds: Float, decay: Float, gain: Float, freqs: FloatArray): ShortArray {
        val n = (seconds * SAMPLE_RATE).toInt()
        return ShortArray(n) { i ->
            val t = i.toFloat() / SAMPLE_RATE
            var v = 0f
            for (f in freqs) v += sin(2f * PI.toFloat() * f * t)
            sample(gain / freqs.size * exp(-t * decay) * v)
        }
    }

    private fun sweep(from: Float, to: Float, seconds: Float, gain: Float): ShortArray {
        val n = (seconds * SAMPLE_RATE).toInt()
        return ShortArray(n) { i ->
            val t = i.toFloat() / SAMPLE_RATE
            val f = from + (to - from) * (t / seconds)
            val env = (1f - t / seconds).coerceAtLeast(0f)
            sample(gain * env * sin(2f * PI.toFloat() * f * t))
        }
    }

    private fun vibrato(base: Float, depth: Float, rate: Float, seconds: Float, gain: Float): ShortArray {
        val n = (seconds * SAMPLE_RATE).toInt()
        return ShortArray(n) { i ->
            val t = i.toFloat() / SAMPLE_RATE
            val f = base + depth * sin(2f * PI.toFloat() * rate * t)
            val env = if (t < 0.02f) t / 0.02f else (1f - (t - 0.02f) / (seconds - 0.02f)).coerceAtLeast(0f)
            sample(gain * env * sin(2f * PI.toFloat() * f * t))
        }
    }

    private fun sample(v: Float): Short {
        val clamped = v.coerceIn(-1f, 1f)
        return (clamped * 32767f).toInt().toShort()
    }

    /** Wrap 16-bit mono PCM in a minimal canonical WAV (RIFF) container. */
    private fun wrapWav(pcm: ShortArray): ByteArray {
        val dataSize = pcm.size * 2
        val out = ByteArray(44 + dataSize)
        var p = 0
        fun str(s: String) { for (c in s) out[p++] = c.code.toByte() }
        fun le32(v: Int) { out[p++] = v.toByte(); out[p++] = (v shr 8).toByte(); out[p++] = (v shr 16).toByte(); out[p++] = (v shr 24).toByte() }
        fun le16(v: Int) { out[p++] = v.toByte(); out[p++] = (v shr 8).toByte() }

        str("RIFF"); le32(36 + dataSize); str("WAVE")
        str("fmt "); le32(16); le16(1); le16(1)            // PCM, mono
        le32(SAMPLE_RATE); le32(SAMPLE_RATE * 2)           // byte rate (mono, 2 bytes/sample)
        le16(2); le16(16)                                  // block align, bits/sample
        str("data"); le32(dataSize)
        for (s in pcm) { out[p++] = s.toByte(); out[p++] = (s.toInt() shr 8).toByte() }
        return out
    }
}
