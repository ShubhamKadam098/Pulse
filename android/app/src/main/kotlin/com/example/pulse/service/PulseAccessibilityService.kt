package com.example.pulse.service

import android.accessibilityservice.AccessibilityService
import android.os.Build
import android.util.Log
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent

class PulseAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "PulseAccessibility"
        
        // Callback: keyCode -> Unit
        var onKeyPress: ((Int) -> Unit)? = null
        
        // Master switch. If false, we do nothing.
        var isInterceptionEnabled: Boolean = false

        private var instance: PulseAccessibilityService? = null

        fun lockScreen() {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                instance?.performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN)
            }
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        Log.d(TAG, "Accessibility Service Connected")
    }

    override fun onKeyEvent(event: KeyEvent): Boolean {
        Log.d(TAG, "onKeyEvent called: keyCode=${event.keyCode}, action=${event.action}, enabled=$isInterceptionEnabled")
        
        // If interception is disabled, behave normally
        if (!isInterceptionEnabled) {
            return super.onKeyEvent(event)
        }

        val keyCode = event.keyCode

        // We only care about Volume Keys
        if (keyCode == KeyEvent.KEYCODE_VOLUME_UP || keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
            Log.d(TAG, "Volume key detected: $keyCode")
            
            // On DOWN, trigger action
            if (event.action == KeyEvent.ACTION_DOWN) {
                Log.d(TAG, "Invoking callback for keyCode: $keyCode")
                onKeyPress?.invoke(keyCode)
            }
            
            // Always return true to CONSUME the event (prevent system volume change)
            // when enabled.
            return true
        }

        return super.onKeyEvent(event)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Not used
    }

    override fun onInterrupt() {
        Log.d(TAG, "Service interrupted")
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "Service destroyed")
    }
}
