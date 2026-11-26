package com.atonai.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.atonai.R

/**
 * Foreground service that keeps a long agent turn alive with the screen off
 * (M8). It shares the app's single [com.atonai.engine.Engine] via the app graph.
 *
 * This milestone only owns the lifecycle and the notification; the run loop is
 * driven by the shared engine, so the service just keeps the process warm and
 * shows progress.
 */
class AgentService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        startForeground(NOTIFICATION_ID, buildNotification("AtonAI is working…"))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val text = intent?.getStringExtra(EXTRA_TEXT) ?: "AtonAI is working…"
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, buildNotification(text))
        return START_STICKY
    }

    private fun buildNotification(text: String): Notification {
        ensureChannel()
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("AtonAI")
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_stat_agent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(NotificationManager::class.java)
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Agent activity", NotificationManager.IMPORTANCE_LOW),
        )
    }

    companion object {
        private const val CHANNEL_ID = "aton_agent"
        private const val NOTIFICATION_ID = 42
        const val EXTRA_TEXT = "text"

        fun start(context: Context, text: String) {
            val intent = Intent(context, AgentService::class.java).putExtra(EXTRA_TEXT, text)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, AgentService::class.java))
        }
    }
}
