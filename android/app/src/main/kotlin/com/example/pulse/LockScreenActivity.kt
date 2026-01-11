package com.example.pulse

import android.app.Activity
import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import android.view.WindowManager

class LockScreenActivity : Activity() {

    private val dismissReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == ACTION_DISMISS) {
                finish()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Show over lock screen
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }
        
        // Dismiss keyguard temporarily
        val keyguardManager = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            keyguardManager.requestDismissKeyguard(this, null)
        }
        
        // Keep screen on while this activity is visible
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        
        // Minimal UI - just a simple layout
        setContentView(R.layout.activity_lock_screen)
        
        // Register receiver to dismiss this activity
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(dismissReceiver, IntentFilter(ACTION_DISMISS), Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(dismissReceiver, IntentFilter(ACTION_DISMISS))
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        
        // Clear keep screen on flag to allow screen to turn off
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        
        try {
            unregisterReceiver(dismissReceiver)
        } catch (e: Exception) {
            // Receiver might not be registered
        }
    }

    companion object {
        const val ACTION_DISMISS = "com.example.pulse.DISMISS_LOCK_SCREEN"
    }
}
