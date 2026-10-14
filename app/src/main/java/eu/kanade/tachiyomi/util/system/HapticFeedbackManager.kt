package eu.kanade.tachiyomi.util.system

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

object HapticFeedbackManager {

    enum class Event {
        PAGE_FLIP,
        CATEGORY_UNLOCK,
    }

    fun perform(context: Context, event: Event) {
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(VibratorManager::class.java)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Vibrator::class.java)
        } ?: return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val effect = when (event) {
                Event.PAGE_FLIP -> VibrationEffect.EFFECT_TICK
                Event.CATEGORY_UNLOCK -> VibrationEffect.EFFECT_CLICK
            }
            vibrator.vibrate(VibrationEffect.createPredefined(effect))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(12L)
        }
    }
}
