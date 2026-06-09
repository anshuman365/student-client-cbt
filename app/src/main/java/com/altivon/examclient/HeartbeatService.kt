package com.altivon.examclient

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

class HeartbeatService : Service() {

    companion object {
        const val ACTION_STOP = "com.altivon.examclient.STOP_SERVICE"
        private const val CHANNEL_ID = "exam_heartbeat"
        private const val NOTIFICATION_ID = 1001
        private const val HEARTBEAT_INTERVAL_MS = 30_000L
        private const val SECURITY_CHECK_INTERVAL_MS = 5_000L
        private const val MAX_CONSECUTIVE_FAILURES = 3

        var examActive = false
        var autoSubmitCallback: (() -> Unit)? = null
    }

    private val handler = Handler(Looper.getMainLooper())
    private val executor = Executors.newSingleThreadExecutor()
    private var consecutiveHeartbeatFailures = 0

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification())
        scheduleHeartbeat()
        scheduleSecurityChecks()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
        }
        return START_STICKY
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        triggerAutoSubmit("App removed from recents")
        super.onTaskRemoved(rootIntent)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        executor.shutdownNow()
        super.onDestroy()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Exam Session",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Keeps your exam session active"
                setShowBadge(false)
            }
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Exam in Progress")
            .setContentText("Do not close or switch apps during the exam.")
            .setSmallIcon(R.drawable.ic_exam_notification)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun scheduleHeartbeat() {
        handler.postDelayed(heartbeatRunnable, HEARTBEAT_INTERVAL_MS)
    }

    private val heartbeatRunnable = object : Runnable {
        override fun run() {
            if (examActive) {
                executor.execute { sendHeartbeat() }
            }
            handler.postDelayed(this, HEARTBEAT_INTERVAL_MS)
        }
    }

    private fun sendHeartbeat() {
        val url = AppPreferences.getHeartbeatUrl(this)
        val hwId = AppPreferences.getHardwareId(this)
        val compNum = AppPreferences.getComputerNumber(this)
        val systemId = "ANDROID-$compNum"

        val payload = JSONObject().apply {
            put("system_id", systemId)
            put("hardware_signature", hwId)
        }.toString()

        try {
            val conn = URL(url).openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.setRequestProperty("Content-Type", "application/json")
            conn.doOutput = true
            conn.connectTimeout = 10_000
            conn.readTimeout = 10_000

            OutputStreamWriter(conn.outputStream).use { it.write(payload) }
            val code = conn.responseCode
            conn.disconnect()

            if (code == 200) {
                consecutiveHeartbeatFailures = 0
            } else {
                handleHeartbeatFailure()
            }
        } catch (e: Exception) {
            handleHeartbeatFailure()
        }
    }

    private fun handleHeartbeatFailure() {
        consecutiveHeartbeatFailures++
        if (consecutiveHeartbeatFailures >= MAX_CONSECUTIVE_FAILURES) {
            triggerAutoSubmit("Network disconnected – heartbeat failed")
        }
    }

    private fun scheduleSecurityChecks() {
        handler.postDelayed(securityCheckRunnable, SECURITY_CHECK_INTERVAL_MS)
    }

    private val securityCheckRunnable = object : Runnable {
        override fun run() {
            if (examActive) {
                executor.execute { runSecurityChecks() }
            }
            handler.postDelayed(this, SECURITY_CHECK_INTERVAL_MS)
        }
    }

    private fun runSecurityChecks() {
        if (SecurityChecker.isAdbEnabled(this)) {
            triggerAutoSubmit("USB debugging detected during exam")
            return
        }
        if (SecurityChecker.isDeveloperOptionsEnabled(this)) {
            triggerAutoSubmit("Developer options detected during exam")
            return
        }
    }

    private fun triggerAutoSubmit(reason: String) {
        if (!examActive) return
        examActive = false
        handler.post {
            autoSubmitCallback?.invoke()
        }
    }
}
