package com.example.timerapp.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent

class AlarmScheduler(private val context: Context) {

    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun schedule(durationMinutes: Int) {
        val fireTime = System.currentTimeMillis() + durationMinutes * 60_000L
        alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, fireTime, buildPendingIntent())
        prefs.edit().putLong(PREF_KEY_FIRE_TIME, fireTime).apply()
    }

    fun cancel() {
        alarmManager.cancel(buildPendingIntent())
        prefs.edit().remove(PREF_KEY_FIRE_TIME).apply()
    }

    fun getScheduledFireTime(): Long = prefs.getLong(PREF_KEY_FIRE_TIME, -1L)

    private fun buildPendingIntent(): PendingIntent {
        val intent = Intent(context, AlarmReceiver::class.java)
        return PendingIntent.getBroadcast(
            context, REQUEST_CODE, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    companion object {
        const val PREF_KEY_FIRE_TIME = "alarm_fire_time"
        private const val PREFS_NAME = "timer_prefs"
        private const val REQUEST_CODE = 1001
    }
}
