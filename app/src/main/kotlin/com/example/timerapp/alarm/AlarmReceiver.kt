package com.example.timerapp.alarm

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.timerapp.R
import com.example.timerapp.ui.alarm.AlarmActivity

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        context.stopService(Intent(context, TimerService::class.java))

        ensureAlarmChannel(context)

        val alarmPi = PendingIntent.getActivity(
            context, 0,
            Intent(context, AlarmActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_USER_ACTION)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, ALARM_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_timer)
            .setContentTitle("Timer finished!")
            .setContentText("Tap to dismiss")
            .setContentIntent(alarmPi)
            .setFullScreenIntent(alarmPi, true)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setVibrate(longArrayOf(0, 400, 200, 400, 200, 400))
            .setOngoing(true)
            .setAutoCancel(false)
            .build()

        NotificationManagerCompat.from(context).notify(ALARM_NOTIF_ID, notification)
    }

    private fun ensureAlarmChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        if (nm.getNotificationChannel(ALARM_CHANNEL_ID) != null) return
        val channel = NotificationChannel(
            ALARM_CHANNEL_ID,
            "Alarm",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Timer finished alerts"
            enableVibration(true)
        }
        nm.createNotificationChannel(channel)
    }

    companion object {
        const val ALARM_CHANNEL_ID = "timer_alarm"
        const val ALARM_NOTIF_ID = 2002
    }
}
