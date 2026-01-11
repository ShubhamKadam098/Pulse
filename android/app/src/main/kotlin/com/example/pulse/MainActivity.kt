package com.example.pulse

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import com.example.pulse.data.AppDatabase
import com.example.pulse.service.PulseForegroundService
import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.MethodChannel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity: FlutterActivity() {
    private val CHANNEL = "com.pulse.app/channel"
    private var pulseService: PulseForegroundService? = null
    private var isBound = false
    private val scope = CoroutineScope(Dispatchers.Main)

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(className: ComponentName, service: android.os.IBinder) {
            val binder = service as PulseForegroundService.LocalBinder
            pulseService = binder.getService()
            isBound = true
            
            pulseService?.onUpdate = {
                pushStatusUpdate()
            }
        }

        override fun onServiceDisconnected(arg0: ComponentName) {
            isBound = false
            pulseService = null
        }
    }

    override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)
        
        MethodChannel(flutterEngine.dartExecutor.binaryMessenger, CHANNEL).setMethodCallHandler { call, result ->
            when (call.method) {
                "startTimer" -> {
                    val seconds = call.argument<Int>("seconds") ?: 60
                    startServiceIntent()
                    pulseService?.startTimer(seconds)
                    result.success(true)
                }
                "stopTimer" -> {
                    pulseService?.stopTimer()
                    result.success(true)
                }
                "togglePause" -> {
                    pulseService?.togglePause()
                    result.success(true)
                }
                "getStatus" -> {
                    result.success(getStatusMap())
                }
                "checkAccessibility" -> {
                    result.success(isAccessibilityServiceEnabled())
                }
                "openAccessibilitySettings" -> {
                    val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                    startActivity(intent)
                    result.success(true)
                }
                "checkDeviceAdmin" -> {
                    result.success(isDeviceAdminEnabled())
                }
                "openDeviceAdminSettings" -> {
                    val adminComponent = ComponentName(this, PulseDeviceAdminReceiver::class.java)
                    val intent = Intent(android.app.admin.DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
                        putExtra(android.app.admin.DevicePolicyManager.EXTRA_DEVICE_ADMIN, adminComponent)
                        putExtra(android.app.admin.DevicePolicyManager.EXTRA_ADD_EXPLANATION, "Pulse requires this to turn off the screen after you acknowledge reminders.")
                    }
                    startActivity(intent)
                    result.success(true)
                }
                "getStats" -> {
                    scope.launch {
                        val stats = withContext(Dispatchers.IO) {
                            val db = AppDatabase.getDatabase(this@MainActivity)
                            val allAcks = db.pulseDao().getAllAcknowledgements()
                            
                            val sdf = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
                            val todayStr = sdf.format(java.util.Date())
                            
                            val dateToSeconds = mutableMapOf<String, Int>()
                            var totalSeconds = 0
                            
                            for (ack in allAcks) {
                                val dStr = sdf.format(java.util.Date(ack.timestamp))
                                val duration = if (ack.durationSeconds > 0) ack.durationSeconds else 0
                                dateToSeconds[dStr] = (dateToSeconds[dStr] ?: 0) + duration
                                totalSeconds += duration
                            }
                            
                            val todayMinutes = (dateToSeconds[todayStr] ?: 0) / 60
                            val totalMinutes = totalSeconds / 60
                            
                            // Weekly Minutes
                            val cal = java.util.Calendar.getInstance()
                            val weekly = ArrayList<Int>()
                            for (i in 0..6) {
                                cal.time = java.util.Date()
                                cal.add(java.util.Calendar.DAY_OF_YEAR, - (6 - i))
                                val dStr = sdf.format(cal.time)
                                weekly.add((dateToSeconds[dStr] ?: 0) / 60)
                            }
                            
                            // Streaks - based on unique dates with any activity
                            val activeDates = dateToSeconds.keys.filter { (dateToSeconds[it] ?: 0) > 0 }.sorted()
                            
                            var maxStreak = 0
                            var curStreakCalc = 0
                            var prevStr: String? = null
                            
                            fun isNextDay(s1: String, s2: String): Boolean {
                                try {
                                    val d1 = sdf.parse(s1)
                                    val d2 = sdf.parse(s2)
                                    if (d1 == null || d2 == null) return false
                                    val diff = d2.time - d1.time
                                    val days = java.util.concurrent.TimeUnit.MILLISECONDS.toDays(diff + 3600000) // Leeway for DST
                                    return days == 1L
                                } catch (e: Exception) { return false }
                            }

                            for (d in activeDates) {
                                if (prevStr != null && isNextDay(prevStr!!, d)) {
                                    curStreakCalc++
                                } else {
                                    maxStreak = Math.max(maxStreak, curStreakCalc)
                                    curStreakCalc = 1
                                }
                                prevStr = d
                            }
                            maxStreak = Math.max(maxStreak, curStreakCalc)
                            
                            var currentStreak = 0
                            if (activeDates.isNotEmpty()) {
                                val lastActive = activeDates.last()
                                val todayDate = sdf.parse(todayStr)
                                val lastActiveDate = sdf.parse(lastActive)
                                if (todayDate != null && lastActiveDate != null) {
                                    val diff = todayDate.time - lastActiveDate.time
                                    val daysDiff = java.util.concurrent.TimeUnit.MILLISECONDS.toDays(diff + 3600000)
                                    if (daysDiff <= 1) {
                                        currentStreak = 1
                                        var currentCheck = lastActive
                                        for (i in (activeDates.size - 2) downTo 0) {
                                            val prev = activeDates[i]
                                            if (isNextDay(prev, currentCheck)) {
                                                currentStreak++
                                                currentCheck = prev
                                            } else break
                                        }
                                    }
                                }
                            }

                            mapOf(
                                "total" to totalMinutes,
                                "today" to todayMinutes,
                                "streak" to currentStreak,
                                "longestStreak" to maxStreak,
                                "weekly" to weekly
                            )
                        }
                        result.success(stats)
                    }
                }
                else -> result.notImplemented()
            }
        }
    }

    override fun onStart() {
        super.onStart()
        val intent = Intent(this, PulseForegroundService::class.java)
        bindService(intent, connection, Context.BIND_AUTO_CREATE)
    }

    override fun onStop() {
        super.onStop()
        if (isBound) {
            unbindService(connection)
            isBound = false
        }
    }

    private fun startServiceIntent() {
        val intent = Intent(this, PulseForegroundService::class.java)
        startService(intent)
    }

    private fun pushStatusUpdate() {
        runOnUiThread {
             flutterEngine?.dartExecutor?.binaryMessenger?.let { messenger -> 
                 MethodChannel(messenger, CHANNEL).invokeMethod("onStateChanged", getStatusMap())
             }
        }
    }

    private fun getStatusMap(): Map<String, Any> {
        val s = pulseService
        if (s == null) return mapOf("state" to "IDLE", "timeRemaining" to 0, "acknowledgements" to 0)
        
        return mapOf(
            "state" to s.currentState.name,
            "timeRemaining" to s.timeRemaining,
            "acknowledgements" to s.sessionAcknowledgements
        )
    }

    private fun isAccessibilityServiceEnabled(): Boolean {
        val am = getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
        val enabledServices = am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
        for (service in enabledServices) {
            if (service.id.contains("PulseAccessibilityService")) {
                return true
            }
        }
        return false
    }

    private fun isDeviceAdminEnabled(): Boolean {
        val dpm = getSystemService(Context.DEVICE_POLICY_SERVICE) as android.app.admin.DevicePolicyManager
        val adminComponent = ComponentName(this, PulseDeviceAdminReceiver::class.java)
        return dpm.isAdminActive(adminComponent)
    }
}
