package com.shapemerge.android

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import com.shapemerge.Haptics

/** Android vibration via the system Vibrator service. */
class AndroidHaptics(context: Context) : Haptics {
    private val vibrator =
        context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator

    override fun vibrate(ms: Int) {
        val v = vibrator ?: return
        if (!v.hasVibrator()) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            v.vibrate(VibrationEffect.createOneShot(ms.toLong(), VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            @Suppress("DEPRECATION")
            v.vibrate(ms.toLong())
        }
    }
}
