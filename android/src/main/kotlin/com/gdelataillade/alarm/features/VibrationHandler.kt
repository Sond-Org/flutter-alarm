package com.gdelataillade.alarm.features

import android.content.Context
import android.os.VibrationEffect
import android.os.Vibrator

class VibrationHandler(private val context: Context) {
    private val vibrator: Vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator

    fun startVibrating(
        pattern: LongArray,
        repeat: Int,
    ) {
        val vibrationEffect = VibrationEffect.createWaveform(pattern, repeat)
        vibrator.vibrate(vibrationEffect)
    }

    fun stopVibrating() {
        vibrator.cancel()
    }
}
