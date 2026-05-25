package com.example.timerapp

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import com.example.timerapp.alarm.AlarmScheduler
import com.example.timerapp.data.PresetEntity
import io.mockk.*
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class AlarmSchedulerTest {
    private val context = mockk<Context>(relaxed = true)
    private val alarmManager = mockk<AlarmManager>(relaxed = true)
    private val prefs = mockk<SharedPreferences>(relaxed = true)
    private val prefsEditor = mockk<SharedPreferences.Editor>(relaxed = true)

    private val preset = PresetEntity(id = 42, label = "Tea", durationMinutes = 3, isDefault = false)

    @After
    fun teardown() { unmockkAll() }

    @Before
    fun setup() {
        every { context.getSystemService(Context.ALARM_SERVICE) } returns alarmManager
        every { context.getSharedPreferences(any(), any()) } returns prefs
        every { prefs.edit() } returns prefsEditor
        every { prefsEditor.putLong(any(), any()) } returns prefsEditor
        every { prefsEditor.remove(any()) } returns prefsEditor
        mockkStatic(PendingIntent::class)
        every { PendingIntent.getBroadcast(any(), any(), any(), any()) } returns mockk()
        every { PendingIntent.getActivity(any(), any(), any(), any()) } returns mockk()
        mockkConstructor(Intent::class)
        every { anyConstructed<Intent>().setAction(any()) } returns mockk(relaxed = true)
        every { anyConstructed<Intent>().setFlags(any()) } returns mockk(relaxed = true)
        every { anyConstructed<Intent>().putExtra(any<String>(), any<String>()) } returns mockk(relaxed = true)
        every { anyConstructed<Intent>().putExtra(any<String>(), any<Long>()) } returns mockk(relaxed = true)
        every { context.startService(any()) } returns null
        every { context.startForegroundService(any()) } returns null
    }

    @Test
    fun schedulePersistsFireTimeAndPresetIdAndClearsPaused() {
        val scheduler = AlarmScheduler(context)
        scheduler.schedule(preset)
        verify { prefsEditor.putLong(AlarmScheduler.PREF_KEY_FIRE_TIME, any()) }
        verify { prefsEditor.putLong(AlarmScheduler.PREF_KEY_PRESET_ID, 42L) }
        verify { prefsEditor.remove(AlarmScheduler.PREF_KEY_PAUSED_REMAINING_MS) }
        verify { prefsEditor.apply() }
    }

    @Test
    fun scheduleSetsAlarmClock() {
        val scheduler = AlarmScheduler(context)
        scheduler.schedule(preset)
        verify { alarmManager.setAlarmClock(any(), any()) }
    }

    @Test
    fun pauseStoresRemainingClearsFireTimeKeepsPresetIdCancelsAlarm() {
        val now = System.currentTimeMillis()
        every { prefs.getLong(AlarmScheduler.PREF_KEY_FIRE_TIME, -1L) } returns now + 120_000L
        every { prefs.getLong(AlarmScheduler.PREF_KEY_PRESET_ID, -1L) } returns 42L

        val scheduler = AlarmScheduler(context)
        scheduler.pause("Tea")

        verify { alarmManager.cancel(any<PendingIntent>()) }
        verify { prefsEditor.putLong(eq(AlarmScheduler.PREF_KEY_PAUSED_REMAINING_MS), more(0L)) }
        verify { prefsEditor.remove(AlarmScheduler.PREF_KEY_FIRE_TIME) }
        verify(exactly = 0) { prefsEditor.remove(AlarmScheduler.PREF_KEY_PRESET_ID) }
        verify { prefsEditor.apply() }
    }

    @Test
    fun pauseWhenNoFireTimeIsNoOp() {
        every { prefs.getLong(AlarmScheduler.PREF_KEY_FIRE_TIME, -1L) } returns -1L
        val scheduler = AlarmScheduler(context)
        scheduler.pause("Tea")
        verify(exactly = 0) { prefsEditor.putLong(eq(AlarmScheduler.PREF_KEY_PAUSED_REMAINING_MS), any()) }
    }

    @Test
    fun resumeSchedulesNewAlarmAndClearsPausedRemaining() {
        every { prefs.getLong(AlarmScheduler.PREF_KEY_PAUSED_REMAINING_MS, -1L) } returns 60_000L
        every { prefs.getLong(AlarmScheduler.PREF_KEY_PRESET_ID, -1L) } returns 42L

        val scheduler = AlarmScheduler(context)
        scheduler.resume("Tea")

        verify { alarmManager.setAlarmClock(any(), any()) }
        verify { prefsEditor.putLong(eq(AlarmScheduler.PREF_KEY_FIRE_TIME), more(0L)) }
        verify { prefsEditor.remove(AlarmScheduler.PREF_KEY_PAUSED_REMAINING_MS) }
        verify { prefsEditor.apply() }
    }

    @Test
    fun resumeWhenNoPausedRemainingIsNoOp() {
        every { prefs.getLong(AlarmScheduler.PREF_KEY_PAUSED_REMAINING_MS, -1L) } returns -1L
        val scheduler = AlarmScheduler(context)
        scheduler.resume("Tea")
        verify(exactly = 0) { alarmManager.setAlarmClock(any(), any()) }
    }

    @Test
    fun cancelClearsAllThreeKeysAndCancelsAlarm() {
        val scheduler = AlarmScheduler(context)
        scheduler.cancel()
        verify { alarmManager.cancel(any<PendingIntent>()) }
        verify { prefsEditor.remove(AlarmScheduler.PREF_KEY_FIRE_TIME) }
        verify { prefsEditor.remove(AlarmScheduler.PREF_KEY_PRESET_ID) }
        verify { prefsEditor.remove(AlarmScheduler.PREF_KEY_PAUSED_REMAINING_MS) }
        verify { prefsEditor.apply() }
    }

    @Test
    fun getScheduledPresetIdReturnsMinusOneWhenUnset() {
        every { prefs.getLong(AlarmScheduler.PREF_KEY_PRESET_ID, -1L) } returns -1L
        val scheduler = AlarmScheduler(context)
        assertEquals(-1L, scheduler.getScheduledPresetId())
    }

    @Test
    fun getPausedRemainingMsReturnsMinusOneWhenUnset() {
        every { prefs.getLong(AlarmScheduler.PREF_KEY_PAUSED_REMAINING_MS, -1L) } returns -1L
        val scheduler = AlarmScheduler(context)
        assertEquals(-1L, scheduler.getPausedRemainingMs())
    }

    @Test
    fun getScheduledFireTimeReturnsMinusOneWhenUnset() {
        every { prefs.getLong(AlarmScheduler.PREF_KEY_FIRE_TIME, -1L) } returns -1L
        val scheduler = AlarmScheduler(context)
        assertEquals(-1L, scheduler.getScheduledFireTime())
    }
}
