package com.example.pulse.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.view.KeyEvent
import androidx.core.app.NotificationCompat
import com.example.pulse.MainActivity
import com.example.pulse.R
import com.example.pulse.data.AcknowledgementEntity
import com.example.pulse.data.AppDatabase
import com.example.pulse.data.SessionEntity
import kotlinx.coroutines.*
import java.util.concurrent.atomic.AtomicBoolean

class PulseForegroundService : Service() {

    enum class State {
        IDLE, RUNNING, PAUSED, VIBRATING
    }

    inner class LocalBinder : Binder() {
        fun getService(): PulseForegroundService = this@PulseForegroundService
    }

    private val binder = LocalBinder()
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var wakeLock: PowerManager.WakeLock? = null

    private lateinit var database: AppDatabase
    private lateinit var vibrationManager: VibrationManager
    private lateinit var notificationManager: NotificationManager

    // State
    var currentState = State.IDLE
        private set
    var intervalSeconds: Int = 0
        private set
    private var currentSessionId: Long = -1

    // Timer Job
    private var timerJob: Job? = null
    private var safetyTimeoutJob: Job? = null
    
    // For UI updates
    var timeRemaining: Int = 0
        private set
    var onUpdate: (() -> Unit)? = null // Callback to updating UI

    override fun onCreate() {
        super.onCreate()
        database = AppDatabase.getDatabase(this)
        vibrationManager = VibrationManager(this)
        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        
        createNotificationChannel()

        // Setup Accessibility Hook
        PulseAccessibilityService.onKeyPress = { keyCode ->
             handleKeyPress(keyCode)
        }

        // WakeLock
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Pulse::TimerWakeLock")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "STOP") {
            stopTimer()
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent): IBinder {
        return binder
    }

    // --- Public Control Methods ---

    fun startTimer(seconds: Int) {
        if (seconds <= 0) return
        
        stopTimer() // Reset if running
        
        intervalSeconds = seconds
        timeRemaining = seconds
        
        // Create Session
        scope.launch(Dispatchers.IO) {
            val session = SessionEntity(startTime = System.currentTimeMillis())
            currentSessionId = database.pulseDao().insertSession(session)
        }

        vibrationManager.vibrateStartConfirmation()
        
        startForeground(NOTIFICATION_ID, buildNotification("Focus started"))
        wakeLock?.acquire(100L * 60 * 60 * 1000) // Max timeout just in case

        currentState = State.RUNNING
        startLoop()
        updateUI()
    }

    fun stopTimer() {
        currentState = State.IDLE
        
        timerJob?.cancel()
        safetyTimeoutJob?.cancel()
        vibrationManager.stop()
        PulseAccessibilityService.isInterceptionEnabled = false
        
        // Close Session
        if (currentSessionId != -1L) {
            val sessionId = currentSessionId
            scope.launch(Dispatchers.IO) {
                // Not strictly tracking end time in this simplified version
            }
        }
        
        stopForeground(true)
        if (wakeLock?.isHeld == true) wakeLock?.release()
        
        updateUI()
    }
    
    fun togglePause() {
        when (currentState) {
            State.RUNNING -> pause()
            State.VIBRATING -> pause() // Pause during vibration stops vibration
            State.PAUSED -> resume()
            State.IDLE -> {}
        }
    }

    private fun pause() {
        if (currentState == State.IDLE) return
        
        currentState = State.PAUSED
        timerJob?.cancel()
        safetyTimeoutJob?.cancel()
        vibrationManager.stop()
        PulseAccessibilityService.isInterceptionEnabled = false
        
        updateNotification("Paused")
        updateUI()
    }

    private fun resume() {
        if (currentState != State.PAUSED) return
        
        currentState = State.RUNNING
        startLoop() // Continues from timeRemaining
        updateUI()
    }

    // --- internal logic ---

    private fun startLoop() {
        timerJob = scope.launch {
            updateNotification("Running")
            
            // Countdown
            while (isActive && timeRemaining > 0) {
                delay(1000)
                timeRemaining--
                // We'll update UI
                if (timeRemaining % 5 == 0) updateNotification("Focusing...")
                updateUI()
            }
            
            if (!isActive) return@launch
            
            // Time's up -> Vibrate
            enterVibrationMode()
        }
    }

    private fun enterVibrationMode() {
        currentState = State.VIBRATING
        timeRemaining = 0
        updateUI()
        
        vibrationManager.startAlarm()
        PulseAccessibilityService.isInterceptionEnabled = true
        updateNotification("Time's up! Acknowledge.", vibrating = true)

        // Safety Timeout 90s
        safetyTimeoutJob = scope.launch {
            delay(90_000)
            // Auto pause
            pause()
            updateNotification("Auto-paused due to inactivity")
        }
    }

    private fun acknowledge() {
        if (currentState != State.VIBRATING) return
        
        safetyTimeoutJob?.cancel()
        vibrationManager.stop()
        PulseAccessibilityService.isInterceptionEnabled = false
        
        // Record Stat
        scope.launch(Dispatchers.IO) {
            database.pulseDao().insertAcknowledgement(
                AcknowledgementEntity(
                    sessionId = currentSessionId,
                    timestamp = System.currentTimeMillis()
                )
            )
        }

        // Reset for next loop
        timeRemaining = intervalSeconds
        currentState = State.RUNNING
        
        // Loop again
        startLoop()
        updateUI()
    }

    private fun handleKeyPress(keyCode: Int) {
        if (currentState != State.VIBRATING) return
        
        scope.launch {
            if (keyCode == KeyEvent.KEYCODE_VOLUME_UP) {
                acknowledge()
            } else if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
                pause()
            }
        }
    }

    private fun updateUI() {
        onUpdate?.invoke()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Pulse Service",
                NotificationManager.IMPORTANCE_LOW
            )
            channel.setSound(null, null)
            channel.enableVibration(false)
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(text: String, vibrating: Boolean = false): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE)
        
        val stopIntent = Intent(this, PulseForegroundService::class.java).apply { action = "STOP" }
        val stopPendingIntent = PendingIntent.getService(this, 1, stopIntent, PendingIntent.FLAG_IMMUTABLE)

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Pulse Focus")
            .setContentText(text)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .addAction(android.R.drawable.ic_media_pause, "Stop", stopPendingIntent)
            .setOngoing(true)
            .build()
    }

    private fun updateNotification(text: String, vibrating: Boolean = false) {
        notificationManager.notify(NOTIFICATION_ID, buildNotification(text, vibrating))
    }

    override fun onDestroy() {
        stopTimer()
        super.onDestroy()
    }

    companion object {
        const val CHANNEL_ID = "pulse_service_channel"
        const val NOTIFICATION_ID = 1001
    }
}
