package com.example.timerapp

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.SharedPreferences
import com.example.timerapp.alarm.AlarmScheduler
import io.mockk.*
import org.junit.Before
import org.junit.Test

class AlarmSchedulerTest {
    private val context = mockk<Context>(relaxed = true)
    private val alarmManager = mockk<AlarmManager>(relaxed = true)
    private val prefs = mockk<SharedPreferences>(relaxed = true)
    private val prefsEditor = mockk<SharedPreferences.Editor>(relaxed = true)

    @Before
    fun setup() {
        every { context.getSystemService(Context.ALARM_SERVICE) } returns alarmManager
        every { context.getSharedPreferences(any(), any()) } returns prefs
        every { prefs.edit() } returns prefsEditor
        every { prefsEditor.putLong(any(), any()) } returns prefsEditor
        every { prefsEditor.remove(any()) } returns prefsEditor
        mockkStatic(PendingIntent::class)
        every { PendingIntent.getBroadcast(any(), any(), any(), any()) } returns mockk()
    }

    @Test
    fun scheduleCallsSetExactAndAllowWhileIdle() {
        val scheduler = AlarmScheduler(context)
        scheduler.schedule(10)
        verify { alarmManager.setExactAndAllowWhileIdle(any(), any(), any()) }
    }

    @Test
    fun scheduleStoresFireTimeInPrefs() {
        val scheduler = AlarmScheduler(context)
        scheduler.schedule(5)
        verify { prefsEditor.putLong(AlarmScheduler.PREF_KEY_FIRE_TIME, any()) }
        verify { prefsEditor.apply() }
    }

    @Test
    fun cancelClearsPrefsAndCancelsAlarm() {
        val scheduler = AlarmScheduler(context)
        scheduler.cancel()
        verify { alarmManager.cancel(any<PendingIntent>()) }
        verify { prefsEditor.remove(AlarmScheduler.PREF_KEY_FIRE_TIME) }
        verify { prefsEditor.apply() }
    }

    @Test
    fun getScheduledFireTimeReturnsMinusOneWhenNotSet() {
        every { prefs.getLong(AlarmScheduler.PREF_KEY_FIRE_TIME, -1L) } returns -1L
        val scheduler = AlarmScheduler(context)
        val result = scheduler.getScheduledFireTime()
        assert(result == -1L)
    }
}
