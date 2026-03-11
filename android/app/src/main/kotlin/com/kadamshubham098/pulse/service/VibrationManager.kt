package com.kadamshubham098.pulse.service

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

class VibrationManager(context: Context) {
    private val vibrator: Vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val manager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
        manager.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
    }

    fun vibrateStartConfirmation() {
        if (!vibrator.hasVibrator()) return
        
        // Short blip: 300ms
        play(300)
    }

    fun startAlarm() {
        if (!vibrator.hasVibrator()) return

        // Pattern: 0 wait, 500 vibrate, 500 sleep...
        val pattern = longArrayOf(0, 500, 500)
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // Repeat index 1
            vibrator.vibrate(VibrationEffect.createWaveform(pattern, 1))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(pattern, 1)
        }
    }

    fun stop() {
        vibrator.cancel()
    }

    private fun play(ms: Long) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(ms)
        }
    }
}
