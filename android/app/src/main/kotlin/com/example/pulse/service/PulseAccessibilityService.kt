package com.example.pulse.service

import android.accessibilityservice.AccessibilityService
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent

class PulseAccessibilityService : AccessibilityService() {

    companion object {
        // Callback: keyCode -> Boolean (Handled?)
        var onKeyPress: ((Int) -> Unit)? = null
        
        // Master switch. If false, we do nothing.
        var isInterceptionEnabled: Boolean = false
    }

    override fun onKeyEvent(event: KeyEvent): Boolean {
        // If interception is disabled, behave normally
        if (!isInterceptionEnabled) {
            return super.onKeyEvent(event)
        }

        val keyCode = event.keyCode

        // We only care about Volume Keys
        if (keyCode == KeyEvent.KEYCODE_VOLUME_UP || keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
            
            // On DOWN, trigger action
            if (event.action == KeyEvent.ACTION_DOWN) {
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
        // Not used
    }
}
