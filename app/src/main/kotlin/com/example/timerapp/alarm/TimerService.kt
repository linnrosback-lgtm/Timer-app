package com.example.timerapp.alarm

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.timerapp.MainActivity
import com.example.timerapp.R
import com.example.timerapp.util.formatMmSs

class TimerService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        stopForeground(STOP_FOREGROUND_REMOVE)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        ensureChannel()
        val action = intent?.action ?: ACTION_START_RUNNING
        val label = intent?.getStringExtra(EXTRA_LABEL).orEmpty()
        when (action) {
            ACTION_START_RUNNING, ACTION_UPDATE_RUNNING -> {
                val fireTime = intent?.getLongExtra(EXTRA_FIRE_TIME, 0L) ?: 0L
                val notif = buildRunningNotification(label, fireTime)
                if (action == ACTION_START_RUNNING) {
                    startForegroundCompat(notif)
                } else {
                    NotificationManagerCompat.from(this).notify(NOTIF_ID, notif)
                }
            }
            ACTION_UPDATE_PAUSED -> {
                val remainingMs = intent?.getLongExtra(EXTRA_REMAINING_MS, 0L) ?: 0L
                val notif = buildPausedNotification(label, remainingMs)
                NotificationManagerCompat.from(this).notify(NOTIF_ID, notif)
            }
            ACTION_STOP -> {
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    private fun startForegroundCompat(notification: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIF_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(NOTIF_ID, notification)
        }
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = getSystemService(NotificationManager::class.java) ?: return
        if (nm.getNotificationChannel(CHANNEL_ID) != null) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Running timer",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Shows the current running or paused timer."
            setShowBadge(false)
        }
        nm.createNotificationChannel(channel)
    }

    private fun baseBuilder(label: String): NotificationCompat.Builder =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(label.ifBlank { "Timer" })
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(piToMain())

    private fun buildRunningNotification(label: String, fireTime: Long): Notification =
        baseBuilder(label)
            .setSmallIcon(R.drawable.ic_stat_timer)
            .setUsesChronometer(true)
            .setChronometerCountDown(true)
            .setWhen(fireTime)
            .build()

    private fun buildPausedNotification(label: String, remainingMs: Long): Notification =
        baseBuilder(label)
            .setSmallIcon(R.drawable.ic_stat_pause)
            .setContentText("Paused — ${formatMmSs(remainingMs)} remaining")
            .setUsesChronometer(false)
            .build()

    private fun piToMain(): PendingIntent {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    companion object {
        const val ACTION_START_RUNNING = "com.example.timerapp.action.START_RUNNING"
        const val ACTION_UPDATE_RUNNING = "com.example.timerapp.action.UPDATE_RUNNING"
        const val ACTION_UPDATE_PAUSED = "com.example.timerapp.action.UPDATE_PAUSED"
        const val ACTION_STOP = "com.example.timerapp.action.STOP"
        const val EXTRA_LABEL = "label"
        const val EXTRA_FIRE_TIME = "fire_time"
        const val EXTRA_REMAINING_MS = "remaining_ms"

        private const val CHANNEL_ID = "timer_running"
        private const val NOTIF_ID = 2001

        fun startRunningIntent(context: Context, label: String, fireTime: Long): Intent =
            Intent(context, TimerService::class.java)
                .setAction(ACTION_START_RUNNING)
                .putExtra(EXTRA_LABEL, label)
                .putExtra(EXTRA_FIRE_TIME, fireTime)

        fun updateRunningIntent(context: Context, label: String, fireTime: Long): Intent =
            Intent(context, TimerService::class.java)
                .setAction(ACTION_UPDATE_RUNNING)
                .putExtra(EXTRA_LABEL, label)
                .putExtra(EXTRA_FIRE_TIME, fireTime)

        fun updatePausedIntent(context: Context, label: String, remainingMs: Long): Intent =
            Intent(context, TimerService::class.java)
                .setAction(ACTION_UPDATE_PAUSED)
                .putExtra(EXTRA_LABEL, label)
                .putExtra(EXTRA_REMAINING_MS, remainingMs)

        fun stopIntent(context: Context): Intent =
            Intent(context, TimerService::class.java).setAction(ACTION_STOP)
    }
}
