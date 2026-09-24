package app.vigil.system

import android.content.Context
import android.os.VibrationEffect
import android.os.VibratorManager

object Haptics {
    private fun vibrator(context: Context) = context.getSystemService(VibratorManager::class.java)?.defaultVibrator

    /** Lub-dub. Played on every successful check-in. */
    fun heartbeat(context: Context) {
        val v = vibrator(context) ?: return
        if (!v.hasVibrator()) return
        val composed = VibrationEffect.startComposition()
        if (v.areAllPrimitivesSupported(VibrationEffect.Composition.PRIMITIVE_THUD, VibrationEffect.Composition.PRIMITIVE_CLICK)) {
            composed.addPrimitive(VibrationEffect.Composition.PRIMITIVE_THUD, 0.9f)
                .addPrimitive(VibrationEffect.Composition.PRIMITIVE_CLICK, 0.6f, 110)
            v.vibrate(composed.compose())
        } else {
            v.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 40, 90, 28), intArrayOf(0, 255, 0, 150), -1))
        }
    }

    fun confirm(context: Context) {
        vibrator(context)?.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_HEAVY_CLICK))
    }
}
