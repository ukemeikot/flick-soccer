package io.github.ukemeikot.flicksoccer.platform

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import io.github.ukemeikot.flicksoccer.AndroidAppContext

/** Android haptics via the system Vibrator (context obtained from the Application holder). */
actual class Haptics actual constructor() {
    private var enabled = true

    private val vibrator: Vibrator? by lazy {
        val ctx = AndroidAppContext.context ?: return@lazy null
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val mgr = ctx.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            mgr?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            ctx.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    }

    actual fun tick() {
        if (!enabled) return
        val v = vibrator ?: return
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                v.vibrate(VibrationEffect.createOneShot(18, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                v.vibrate(18)
            }
        }
    }

    actual fun setEnabled(enabled: Boolean) { this.enabled = enabled }
}
