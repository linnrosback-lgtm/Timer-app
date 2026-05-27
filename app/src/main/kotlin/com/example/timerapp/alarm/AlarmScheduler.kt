package com.example.timerapp.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.example.timerapp.MainActivity
import com.example.timerapp.data.PresetEntity

class AlarmScheduler(private val context: Context) {

    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun schedule(preset: PresetEntity) {
        val durationMs = preset.durationSeconds * 1_000L
        val fireTime = System.currentTimeMillis() + durationMs
        setAlarmClock(fireTime)
        prefs.edit()
            .putLong(PREF_KEY_FIRE_TIME, fireTime)
            .putLong(PREF_KEY_PRESET_ID, preset.id.toLong())
            .remove(PREF_KEY_PAUSED_REMAINING_MS)
            .apply()
        context.startForegroundService(
            TimerService.startRunningIntent(context, preset.label, fireTime)
        )
    }

    fun pause(label: String) {
        val fireTime = prefs.getLong(PREF_KEY_FIRE_TIME, -1L)
        if (fireTime <= 0L) return
        val remaining = (fireTime - System.currentTimeMillis()).coerceAtLeast(0L)
        alarmManager.cancel(buildPendingIntent())
        prefs.edit()
            .putLong(PREF_KEY_PAUSED_REMAINING_MS, remaining)
            .remove(PREF_KEY_FIRE_TIME)
            .apply()
        context.startService(TimerService.updatePausedIntent(context, label, remaining))
    }

    fun resume(label: String) {
        val remaining = prefs.getLong(PREF_KEY_PAUSED_REMAINING_MS, -1L)
        if (remaining <= 0L) return
        val fireTime = System.currentTimeMillis() + remaining
        setAlarmClock(fireTime)
        prefs.edit()
            .putLong(PREF_KEY_FIRE_TIME, fireTime)
            .remove(PREF_KEY_PAUSED_REMAINING_MS)
            .apply()
        context.startService(TimerService.updateRunningIntent(context, label, fireTime))
    }

    fun restartPaused(label: String, durationMs: Long) {
        alarmManager.cancel(buildPendingIntent())
        prefs.edit()
            .putLong(PREF_KEY_PAUSED_REMAINING_MS, durationMs)
            .remove(PREF_KEY_FIRE_TIME)
            .apply()
        context.startService(TimerService.updatePausedIntent(context, label, durationMs))
    }

    fun cancel() {
        alarmManager.cancel(buildPendingIntent())
        prefs.edit()
            .remove(PREF_KEY_FIRE_TIME)
            .remove(PREF_KEY_PRESET_ID)
            .remove(PREF_KEY_PAUSED_REMAINING_MS)
            .apply()
        context.startService(TimerService.stopIntent(context))
    }

    fun rescheduleExisting() {
        val fireTime = prefs.getLong(PREF_KEY_FIRE_TIME, -1L)
        if (fireTime > System.currentTimeMillis()) {
            setAlarmClock(fireTime)
        }
    }

    fun getScheduledFireTime(): Long = prefs.getLong(PREF_KEY_FIRE_TIME, -1L)
    fun getScheduledPresetId(): Long = prefs.getLong(PREF_KEY_PRESET_ID, -1L)
    fun getPausedRemainingMs(): Long = prefs.getLong(PREF_KEY_PAUSED_REMAINING_MS, -1L)

    private fun setAlarmClock(fireTime: Long) {
        val showIntent = PendingIntent.getActivity(
            context, REQUEST_CODE_SHOW,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.setAlarmClock(
            AlarmManager.AlarmClockInfo(fireTime, showIntent),
            buildPendingIntent()
        )
    }

    private fun buildPendingIntent(): PendingIntent {
        val intent = Intent(context, AlarmReceiver::class.java)
        return PendingIntent.getBroadcast(
            context, REQUEST_CODE, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    companion object {
        const val PREF_KEY_FIRE_TIME = "alarm_fire_time"
        const val PREF_KEY_PRESET_ID = "alarm_preset_id"
        const val PREF_KEY_PAUSED_REMAINING_MS = "alarm_paused_remaining_ms"
        private const val PREFS_NAME = "timer_prefs"
        private const val REQUEST_CODE = 1001
        private const val REQUEST_CODE_SHOW = 1002
    }
}
