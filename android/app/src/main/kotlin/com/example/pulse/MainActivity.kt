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
                            val timestamps = db.pulseDao().getAllAcknowledgementTimestamps()
                            
                            val sdf = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
                            val todayStr = sdf.format(java.util.Date())
                            
                            val dates = timestamps.map { sdf.format(java.util.Date(it)) }
                            
                            val todayCount = dates.count { it == todayStr }
                            
                            // Weekly
                            val cal = java.util.Calendar.getInstance()
                            val weekly = ArrayList<Int>()
                            // Last 7 days including today? Or 6 days ago + today.
                            // 0..6: 0 is today, 6 is 6 days ago.
                            for (i in 0..6) {
                                cal.time = java.util.Date()
                                cal.add(java.util.Calendar.DAY_OF_YEAR, - (6 - i))
                                val dStr = sdf.format(cal.time)
                                weekly.add(dates.count { it == dStr })
                            }
                            
                            // Streaks - Calculate using sorted unique strings
                            val uniqueDates = dates.distinct().sorted() // Ascending strings works for yyyy-MM-dd
                            
                            var maxStreak = 0
                            var curStreakCalc = 0
                            var prevStr: String? = null
                            
                            // Helper to check if s2 is day after s1
                            fun isNextDay(s1: String, s2: String): Boolean {
                                val d1 = sdf.parse(s1)
                                val d2 = sdf.parse(s2)
                                val diff = d2.time - d1.time
                                // Approx 24 hours + leeway
                                // 86400000 ms
                                // Check if diff is around 1 day
                                val days = java.util.concurrent.TimeUnit.MILLISECONDS.toDays(diff)
                                return days == 1L
                            }

                            for (d in uniqueDates) {
                                if (prevStr != null && isNextDay(prevStr!!, d)) {
                                    curStreakCalc++
                                } else {
                                    maxStreak = Math.max(maxStreak, curStreakCalc)
                                    curStreakCalc = 1
                                }
                                prevStr = d
                            }
                            maxStreak = Math.max(maxStreak, curStreakCalc)
                            
                            // Current Streak
                            var currentStreak = 0
                            if (uniqueDates.isNotEmpty()) {
                                // Check today or yesterday
                                var lastActive = uniqueDates.last() // Last active date
                                
                                val todayDate = sdf.parse(todayStr)
                                val lastActiveDate = sdf.parse(lastActive)
                                
                                val diff = todayDate.time - lastActiveDate.time
                                val daysDiff = java.util.concurrent.TimeUnit.MILLISECONDS.toDays(diff)
                                
                                if (daysDiff <= 1) {
                                    // Streak is alive
                                    // Count backwards from lastActive
                                    currentStreak = 1
                                    var currentCheck = lastActive
                                    for (i in (uniqueDates.size - 2) downTo 0) {
                                        val prev = uniqueDates[i]
                                        if (isNextDay(prev, currentCheck)) {
                                            currentStreak++
                                            currentCheck = prev
                                        } else {
                                            break
                                        }
                                    }
                                }
                            }

                            mapOf(
                                "total" to timestamps.size,
                                "today" to todayCount,
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
        if (s == null) return mapOf("state" to "IDLE", "timeRemaining" to 0)
        
        return mapOf(
            "state" to s.currentState.name,
            "timeRemaining" to s.timeRemaining
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
