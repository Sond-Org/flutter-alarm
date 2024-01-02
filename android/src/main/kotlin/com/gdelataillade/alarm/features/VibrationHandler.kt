package com.gdelataillade.alarm.features

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator

class VibrationHandler(private val context: Context) {
    private val vibrator: Vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator

    fun startVibrating(
        pattern: LongArray,
        repeat: Int,
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // For API 26 and above
            val vibrationEffect = VibrationEffect.createWaveform(pattern, repeat)
            vibrator.vibrate(vibrationEffect)
        } else {
            // For older versions
            vibrator.vibrate(pattern, repeat)
        }
    }

    fun stopVibrating() {
        vibrator.cancel()
    }
}
