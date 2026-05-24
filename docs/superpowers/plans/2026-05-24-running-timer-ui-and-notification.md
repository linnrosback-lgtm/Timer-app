# Running Timer: Inline Countdown + Chronometer Notification — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** When a preset's timer is running, the preset card shows a live `mm:ss` countdown with **Pause** + **Stop** buttons (or **Resume** + **Stop** while paused), and a persistent chronometer notification reflects the same state in the system shade.

**Architecture:** A new `TimerService` (foreground, low-importance channel) owns the notification. `AlarmScheduler` gains `pause()` / `resume()` plus persistence of `preset_id` and `paused_remaining_ms` alongside the existing `fire_time`. `PresetViewModel` tracks `activePresetId` + mutually-exclusive `fireTimeMillis` / `pausedRemainingMs`, runs a per-second tick coroutine while running, and restores state on init so process death is survivable. `HomeScreen` reads the new state to render the inline countdown and the Pause/Resume/Stop buttons.

**Tech Stack:** Kotlin, Jetpack Compose + Material3, AlarmManager, foreground `Service`, NotificationCompat (Chronometer), kotlinx.coroutines, JUnit4 + mockk + Turbine for tests.

**Spec:** [2026-05-24-running-timer-ui-and-notification-design.md](../specs/2026-05-24-running-timer-ui-and-notification-design.md)

---

## File map

**Created:**
- `app/src/main/kotlin/com/example/timerapp/util/TimeFormat.kt` — `mm:ss` formatter (pure function, easy to test, reused by VM + service).
- `app/src/test/kotlin/com/example/timerapp/TimeFormatTest.kt`
- `app/src/main/kotlin/com/example/timerapp/alarm/TimerService.kt` — foreground service that owns the running/paused notification.
- `app/src/main/res/drawable/ic_stat_timer.xml` — small mono icon for the running notification.
- `app/src/main/res/drawable/ic_stat_pause.xml` — small mono icon for the paused notification.

**Modified:**
- `app/src/main/kotlin/com/example/timerapp/alarm/AlarmScheduler.kt` — new API: `schedule(preset)`, `pause()`, `resume()`, `getScheduledPresetId()`, `getPausedRemainingMs()`; wires `TimerService`.
- `app/src/main/kotlin/com/example/timerapp/alarm/AlarmReceiver.kt` — stop `TimerService` when alarm fires.
- `app/src/main/kotlin/com/example/timerapp/ui/home/PresetViewModel.kt` — extended `HomeUiState`, pause/resume/stop actions, tick coroutine, init-time restore.
- `app/src/main/kotlin/com/example/timerapp/ui/home/HomeScreen.kt` — inline running/paused UI on the active card, disabled Start on idle cards.
- `app/src/main/kotlin/com/example/timerapp/MainActivity.kt` — runtime `POST_NOTIFICATIONS` permission request (API 33+).
- `app/src/main/AndroidManifest.xml` — `POST_NOTIFICATIONS`, `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_SPECIAL_USE`, `<service>` declaration.
- `app/src/test/kotlin/com/example/timerapp/AlarmSchedulerTest.kt` — replace `schedule(Int)` tests, add pause/resume tests.
- `app/src/test/kotlin/com/example/timerapp/PresetViewModelTest.kt` — replace `startTimer` test, add pause/resume/stop/init-restore tests.

---

## Conventions

- Build + test command for the JVM unit suite: `./gradlew :app:testDebugUnitTest` (or `gradlew.bat :app:testDebugUnitTest` on Windows).
- Single targeted test: `./gradlew :app:testDebugUnitTest --tests "com.example.timerapp.ClassName.testName"`.
- After each task: run the relevant test(s), then `./gradlew :app:assembleDebug` once at the end of the task to confirm the app still compiles.
- Commit at the end of each task with a conventional-commits message.

---

## Task 1: `mm:ss` formatter utility

**Files:**
- Create: `app/src/main/kotlin/com/example/timerapp/util/TimeFormat.kt`
- Test: `app/src/test/kotlin/com/example/timerapp/TimeFormatTest.kt`

- [ ] **Step 1: Write the failing test**

Create `app/src/test/kotlin/com/example/timerapp/TimeFormatTest.kt`:

```kotlin
package com.example.timerapp

import com.example.timerapp.util.formatMmSs
import org.junit.Assert.assertEquals
import org.junit.Test

class TimeFormatTest {
    @Test
    fun zeroMillisFormatsAsZeroZero() {
        assertEquals("00:00", formatMmSs(0L))
    }

    @Test
    fun oneSecondFormatsAsZeroOne() {
        assertEquals("00:01", formatMmSs(1_000L))
    }

    @Test
    fun oneMinuteFormatsAsOneZero() {
        assertEquals("01:00", formatMmSs(60_000L))
    }

    @Test
    fun fiveMinutesThirtySevenSeconds() {
        assertEquals("05:37", formatMmSs(5 * 60_000L + 37_000L))
    }

    @Test
    fun tenMinutesShowsTwoDigitMinutes() {
        assertEquals("10:00", formatMmSs(10 * 60_000L))
    }

    @Test
    fun ninetyNineMinutesFiftyNineSeconds() {
        assertEquals("99:59", formatMmSs(99 * 60_000L + 59_000L))
    }

    @Test
    fun negativeMillisClampsToZero() {
        assertEquals("00:00", formatMmSs(-5_000L))
    }

    @Test
    fun subSecondMillisTruncatesDown() {
        assertEquals("00:00", formatMmSs(999L))
        assertEquals("00:01", formatMmSs(1_999L))
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "com.example.timerapp.TimeFormatTest"`
Expected: FAIL — compile error, `formatMmSs` unresolved.

- [ ] **Step 3: Implement the formatter**

Create `app/src/main/kotlin/com/example/timerapp/util/TimeFormat.kt`:

```kotlin
package com.example.timerapp.util

fun formatMmSs(millis: Long): String {
    val safe = millis.coerceAtLeast(0L)
    val totalSeconds = safe / 1000L
    val minutes = totalSeconds / 60L
    val seconds = totalSeconds % 60L
    return "%02d:%02d".format(minutes, seconds)
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests "com.example.timerapp.TimeFormatTest"`
Expected: PASS, 8 tests.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/kotlin/com/example/timerapp/util/TimeFormat.kt app/src/test/kotlin/com/example/timerapp/TimeFormatTest.kt
git commit -m "feat: add formatMmSs time formatting utility"
```

---

## Task 2: `AlarmScheduler` — preset id + pause/resume persistence

**Files:**
- Modify: `app/src/main/kotlin/com/example/timerapp/alarm/AlarmScheduler.kt`
- Modify: `app/src/test/kotlin/com/example/timerapp/AlarmSchedulerTest.kt`

In this task we extend the scheduler API. Service wiring is added in **Task 4** — for now `schedule`/`pause`/`resume`/`cancel` only touch prefs + AlarmManager, so we can unit-test them with the existing mockk setup.

- [ ] **Step 1: Update existing tests to reflect new API, add new test cases**

Replace the entire file `app/src/test/kotlin/com/example/timerapp/AlarmSchedulerTest.kt` with:

```kotlin
package com.example.timerapp

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
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

    private val preset = PresetEntity(id = 42L, label = "Tea", durationMinutes = 3, isDefault = false)

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
    fun scheduleSetsExactAlarm() {
        val scheduler = AlarmScheduler(context)
        scheduler.schedule(preset)
        verify { alarmManager.setExactAndAllowWhileIdle(any(), any(), any()) }
    }

    @Test
    fun pauseStoresRemainingClearsFireTimeKeepsPresetIdCancelsAlarm() {
        val now = System.currentTimeMillis()
        every { prefs.getLong(AlarmScheduler.PREF_KEY_FIRE_TIME, -1L) } returns now + 120_000L
        every { prefs.getLong(AlarmScheduler.PREF_KEY_PRESET_ID, -1L) } returns 42L

        val scheduler = AlarmScheduler(context)
        scheduler.pause()

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
        scheduler.pause()
        verify(exactly = 0) { prefsEditor.putLong(eq(AlarmScheduler.PREF_KEY_PAUSED_REMAINING_MS), any()) }
    }

    @Test
    fun resumeSchedulesNewAlarmAndClearsPausedRemaining() {
        every { prefs.getLong(AlarmScheduler.PREF_KEY_PAUSED_REMAINING_MS, -1L) } returns 60_000L
        every { prefs.getLong(AlarmScheduler.PREF_KEY_PRESET_ID, -1L) } returns 42L

        val scheduler = AlarmScheduler(context)
        scheduler.resume()

        verify { alarmManager.setExactAndAllowWhileIdle(any(), any(), any()) }
        verify { prefsEditor.putLong(eq(AlarmScheduler.PREF_KEY_FIRE_TIME), more(0L)) }
        verify { prefsEditor.remove(AlarmScheduler.PREF_KEY_PAUSED_REMAINING_MS) }
        verify { prefsEditor.apply() }
    }

    @Test
    fun resumeWhenNoPausedRemainingIsNoOp() {
        every { prefs.getLong(AlarmScheduler.PREF_KEY_PAUSED_REMAINING_MS, -1L) } returns -1L
        val scheduler = AlarmScheduler(context)
        scheduler.resume()
        verify(exactly = 0) { alarmManager.setExactAndAllowWhileIdle(any(), any(), any()) }
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
```

- [ ] **Step 2: Run tests to verify they fail to compile**

Run: `./gradlew :app:testDebugUnitTest --tests "com.example.timerapp.AlarmSchedulerTest"`
Expected: FAIL — compile errors on `schedule(preset)`, `pause`, `resume`, `PREF_KEY_PRESET_ID`, `PREF_KEY_PAUSED_REMAINING_MS`, `getScheduledPresetId`, `getPausedRemainingMs`.

- [ ] **Step 3: Implement the extended scheduler**

Replace the entire file `app/src/main/kotlin/com/example/timerapp/alarm/AlarmScheduler.kt` with:

```kotlin
package com.example.timerapp.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.example.timerapp.data.PresetEntity

class AlarmScheduler(private val context: Context) {

    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun schedule(preset: PresetEntity) {
        val durationMs = preset.durationMinutes * 60_000L
        val fireTime = System.currentTimeMillis() + durationMs
        alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, fireTime, buildPendingIntent())
        prefs.edit()
            .putLong(PREF_KEY_FIRE_TIME, fireTime)
            .putLong(PREF_KEY_PRESET_ID, preset.id)
            .remove(PREF_KEY_PAUSED_REMAINING_MS)
            .apply()
    }

    fun pause() {
        val fireTime = prefs.getLong(PREF_KEY_FIRE_TIME, -1L)
        if (fireTime <= 0L) return
        val remaining = (fireTime - System.currentTimeMillis()).coerceAtLeast(0L)
        alarmManager.cancel(buildPendingIntent())
        prefs.edit()
            .putLong(PREF_KEY_PAUSED_REMAINING_MS, remaining)
            .remove(PREF_KEY_FIRE_TIME)
            .apply()
    }

    fun resume() {
        val remaining = prefs.getLong(PREF_KEY_PAUSED_REMAINING_MS, -1L)
        if (remaining <= 0L) return
        val fireTime = System.currentTimeMillis() + remaining
        alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, fireTime, buildPendingIntent())
        prefs.edit()
            .putLong(PREF_KEY_FIRE_TIME, fireTime)
            .remove(PREF_KEY_PAUSED_REMAINING_MS)
            .apply()
    }

    fun cancel() {
        alarmManager.cancel(buildPendingIntent())
        prefs.edit()
            .remove(PREF_KEY_FIRE_TIME)
            .remove(PREF_KEY_PRESET_ID)
            .remove(PREF_KEY_PAUSED_REMAINING_MS)
            .apply()
    }

    fun getScheduledFireTime(): Long = prefs.getLong(PREF_KEY_FIRE_TIME, -1L)
    fun getScheduledPresetId(): Long = prefs.getLong(PREF_KEY_PRESET_ID, -1L)
    fun getPausedRemainingMs(): Long = prefs.getLong(PREF_KEY_PAUSED_REMAINING_MS, -1L)

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
    }
}
```

- [ ] **Step 4: Run tests to verify pass**

Run: `./gradlew :app:testDebugUnitTest --tests "com.example.timerapp.AlarmSchedulerTest"`
Expected: PASS, 10 tests.

The project will not yet compile end-to-end because `PresetViewModel.startTimer` still calls the old `schedule(Int)` signature. That's fixed in Task 5. For now, run only the targeted tests above; do **not** run the full `:app:assembleDebug` until Task 5.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/kotlin/com/example/timerapp/alarm/AlarmScheduler.kt app/src/test/kotlin/com/example/timerapp/AlarmSchedulerTest.kt
git commit -m "feat(scheduler): add preset id + paused-remaining persistence and pause/resume"
```

---

## Task 3: Notification icons (vector drawables)

**Files:**
- Create: `app/src/main/res/drawable/ic_stat_timer.xml`
- Create: `app/src/main/res/drawable/ic_stat_pause.xml`

System status-bar icons must be a white-on-transparent mono vector. Both are simple stock shapes.

- [ ] **Step 1: Create the timer icon**

Create `app/src/main/res/drawable/ic_stat_timer.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp"
    android:height="24dp"
    android:viewportWidth="24"
    android:viewportHeight="24"
    android:tint="#FFFFFF">
    <path
        android:fillColor="#FFFFFF"
        android:pathData="M15,1H9v2h6V1zM11,14h2V8h-2v6zM19.03,7.39l1.42,-1.42c-0.43,-0.51 -0.9,-0.99 -1.41,-1.41l-1.42,1.42C16.07,4.74 14.12,4 12,4c-4.97,0 -9,4.03 -9,9s4.02,9 9,9 9,-4.03 9,-9c0,-2.12 -0.74,-4.07 -1.97,-5.61zM12,20c-3.87,0 -7,-3.13 -7,-7s3.13,-7 7,-7 7,3.13 7,7 -3.13,7 -7,7z"/>
</vector>
```

- [ ] **Step 2: Create the pause icon**

Create `app/src/main/res/drawable/ic_stat_pause.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp"
    android:height="24dp"
    android:viewportWidth="24"
    android:viewportHeight="24"
    android:tint="#FFFFFF">
    <path
        android:fillColor="#FFFFFF"
        android:pathData="M6,19h4V5H6v14zM14,5v14h4V5h-4z"/>
</vector>
```

- [ ] **Step 3: Commit**

```bash
git add app/src/main/res/drawable/ic_stat_timer.xml app/src/main/res/drawable/ic_stat_pause.xml
git commit -m "feat: add status-bar icons for running and paused notifications"
```

---

## Task 4: `TimerService` (foreground service + notification + scheduler wiring)

**Files:**
- Create: `app/src/main/kotlin/com/example/timerapp/alarm/TimerService.kt`
- Modify: `app/src/main/kotlin/com/example/timerapp/alarm/AlarmScheduler.kt` (wire service start/update/stop)

This is a framework-heavy class; unit tests are out of scope (see spec § Testing). Verification happens via the manual QA in Task 11.

- [ ] **Step 1: Create the service**

Create `app/src/main/kotlin/com/example/timerapp/alarm/TimerService.kt`:

```kotlin
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
```

- [ ] **Step 2: Wire scheduler to start/stop/update the service**

The scheduler needs a preset label to feed the notification. Change its API to accept the label on `pause`/`resume` (the label is held by the ViewModel, which already has the preset). Modify `app/src/main/kotlin/com/example/timerapp/alarm/AlarmScheduler.kt`:

Replace the existing methods (and only those — leave the companion + pending-intent helper alone) with:

```kotlin
    fun schedule(preset: PresetEntity) {
        val durationMs = preset.durationMinutes * 60_000L
        val fireTime = System.currentTimeMillis() + durationMs
        alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, fireTime, buildPendingIntent())
        prefs.edit()
            .putLong(PREF_KEY_FIRE_TIME, fireTime)
            .putLong(PREF_KEY_PRESET_ID, preset.id)
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
        alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, fireTime, buildPendingIntent())
        prefs.edit()
            .putLong(PREF_KEY_FIRE_TIME, fireTime)
            .remove(PREF_KEY_PAUSED_REMAINING_MS)
            .apply()
        context.startService(TimerService.updateRunningIntent(context, label, fireTime))
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
```

- [ ] **Step 3: Update the scheduler tests for the new pause/resume signatures**

In `app/src/test/kotlin/com/example/timerapp/AlarmSchedulerTest.kt`, change every `scheduler.pause()` call to `scheduler.pause("Tea")` and every `scheduler.resume()` call to `scheduler.resume("Tea")` (the label content is irrelevant to the assertions; the tests only verify pref + alarm behavior).

Also add this to the `@Before setup()` block, just after the `PendingIntent.getBroadcast` stub:

```kotlin
        every { context.startService(any()) } returns null
        every { context.startForegroundService(any()) } returns null
```

- [ ] **Step 4: Run scheduler tests to verify they still pass**

Run: `./gradlew :app:testDebugUnitTest --tests "com.example.timerapp.AlarmSchedulerTest"`
Expected: PASS, 10 tests.

(The project still won't compile end-to-end until Task 5. Skip `:app:assembleDebug` here.)

- [ ] **Step 5: Commit**

```bash
git add app/src/main/kotlin/com/example/timerapp/alarm/TimerService.kt app/src/main/kotlin/com/example/timerapp/alarm/AlarmScheduler.kt app/src/test/kotlin/com/example/timerapp/AlarmSchedulerTest.kt
git commit -m "feat(alarm): add TimerService foreground notification and wire scheduler"
```

---

## Task 5: `PresetViewModel` — extended state + pause/resume/stop + tick + restore

**Files:**
- Modify: `app/src/main/kotlin/com/example/timerapp/ui/home/PresetViewModel.kt`
- Modify: `app/src/test/kotlin/com/example/timerapp/PresetViewModelTest.kt`

- [ ] **Step 1: Write the failing tests**

Replace the entire file `app/src/test/kotlin/com/example/timerapp/PresetViewModelTest.kt` with:

```kotlin
package com.example.timerapp

import app.cash.turbine.test
import com.example.timerapp.alarm.AlarmScheduler
import com.example.timerapp.data.PresetEntity
import com.example.timerapp.data.PresetRepository
import com.example.timerapp.ui.home.PresetViewModel
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PresetViewModelTest {
    private val testDispatcher = UnconfinedTestDispatcher()
    private val repository = mockk<PresetRepository>(relaxed = true)
    private val scheduler = mockk<AlarmScheduler>(relaxed = true)

    private val tea = PresetEntity(id = 1, label = "Tea", durationMinutes = 3, isDefault = true)
    private val coffee = PresetEntity(id = 2, label = "Coffee", durationMinutes = 5, isDefault = true)
    private val samplePresets = listOf(tea, coffee)

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        every { repository.getAll() } returns flowOf(samplePresets)
        every { scheduler.getScheduledFireTime() } returns -1L
        every { scheduler.getScheduledPresetId() } returns -1L
        every { scheduler.getPausedRemainingMs() } returns -1L
    }

    @After
    fun teardown() { Dispatchers.resetMain() }

    // ---- existing sheet/CRUD behavior preserved ----

    @Test
    fun initialStateHasPresetsFromRepository() = runTest {
        val vm = PresetViewModel(repository, scheduler)
        vm.uiState.test {
            val state = awaitItem()
            assertEquals(samplePresets, state.presets)
            assertFalse(state.isBottomSheetOpen)
            assertNull(state.editingPreset)
            assertNull(state.activePresetId)
            assertNull(state.fireTimeMillis)
            assertNull(state.pausedRemainingMs)
            assertEquals(0L, state.remainingSeconds)
        }
    }

    @Test
    fun openAddSheetSetsBottomSheetOpenAndNullEditing() = runTest {
        val vm = PresetViewModel(repository, scheduler)
        vm.openAddSheet()
        vm.uiState.test {
            val state = awaitItem()
            assertTrue(state.isBottomSheetOpen)
            assertNull(state.editingPreset)
        }
    }

    @Test
    fun openEditSheetSetsEditingPreset() = runTest {
        val vm = PresetViewModel(repository, scheduler)
        vm.openEditSheet(tea)
        vm.uiState.test {
            val state = awaitItem()
            assertTrue(state.isBottomSheetOpen)
            assertEquals(tea, state.editingPreset)
        }
    }

    @Test
    fun dismissSheetClosesBottomSheetAndClearsEditing() = runTest {
        val vm = PresetViewModel(repository, scheduler)
        vm.openEditSheet(tea)
        vm.dismissSheet()
        vm.uiState.test {
            val state = awaitItem()
            assertFalse(state.isBottomSheetOpen)
            assertNull(state.editingPreset)
        }
    }

    @Test
    fun savePresetWithNullEditingInsertsNewPreset() = runTest {
        val vm = PresetViewModel(repository, scheduler)
        vm.openAddSheet()
        vm.savePreset(label = "New", durationMinutes = 20)
        coVerify { repository.insert(PresetEntity(label = "New", durationMinutes = 20, isDefault = false)) }
    }

    @Test
    fun savePresetWithEditingUpdatesExistingPreset() = runTest {
        val vm = PresetViewModel(repository, scheduler)
        vm.openEditSheet(tea)
        vm.savePreset(label = "Updated", durationMinutes = 7)
        coVerify { repository.update(tea.copy(label = "Updated", durationMinutes = 7)) }
    }

    @Test
    fun deletePresetCallsRepositoryDelete() = runTest {
        val vm = PresetViewModel(repository, scheduler)
        vm.deletePreset(tea)
        coVerify { repository.delete(tea) }
    }

    // ---- new running-timer behavior ----

    @Test
    fun startTimerSchedulesAndSetsRunningState() = runTest {
        val now = System.currentTimeMillis()
        every { scheduler.getScheduledFireTime() } returnsMany listOf(-1L, now + 180_000L)
        val vm = PresetViewModel(repository, scheduler)
        vm.startTimer(tea)
        verify { scheduler.schedule(tea) }
        vm.uiState.test {
            val state = awaitItem()
            assertEquals(1L, state.activePresetId)
            assertNotNull(state.fireTimeMillis)
            assertNull(state.pausedRemainingMs)
            assertTrue(state.remainingSeconds > 0L)
        }
    }

    @Test
    fun pauseFreezesRemainingClearsFireTimeAndCallsScheduler() = runTest {
        val now = System.currentTimeMillis()
        every { scheduler.getScheduledFireTime() } returnsMany listOf(-1L, now + 120_000L, -1L)
        val vm = PresetViewModel(repository, scheduler)
        vm.startTimer(tea)
        vm.pauseTimer()
        verify { scheduler.pause(tea.label) }
        vm.uiState.test {
            val state = awaitItem()
            assertEquals(1L, state.activePresetId)
            assertNull(state.fireTimeMillis)
            assertNotNull(state.pausedRemainingMs)
            assertTrue(state.pausedRemainingMs!! > 0L)
        }
    }

    @Test
    fun resumeAfterPauseResumesTickingAndCallsScheduler() = runTest {
        val now = System.currentTimeMillis()
        every { scheduler.getScheduledFireTime() } returnsMany listOf(-1L, now + 120_000L, -1L, now + 60_000L)
        val vm = PresetViewModel(repository, scheduler)
        vm.startTimer(tea)
        vm.pauseTimer()
        vm.resumeTimer()
        verify { scheduler.resume(tea.label) }
        vm.uiState.test {
            val state = awaitItem()
            assertEquals(1L, state.activePresetId)
            assertNotNull(state.fireTimeMillis)
            assertNull(state.pausedRemainingMs)
        }
    }

    @Test
    fun stopWhileRunningClearsAllActiveFields() = runTest {
        val now = System.currentTimeMillis()
        every { scheduler.getScheduledFireTime() } returnsMany listOf(-1L, now + 60_000L)
        val vm = PresetViewModel(repository, scheduler)
        vm.startTimer(tea)
        vm.stopTimer()
        verify { scheduler.cancel() }
        vm.uiState.test {
            val state = awaitItem()
            assertNull(state.activePresetId)
            assertNull(state.fireTimeMillis)
            assertNull(state.pausedRemainingMs)
            assertEquals(0L, state.remainingSeconds)
        }
    }

    @Test
    fun stopWhilePausedClearsAllActiveFields() = runTest {
        val now = System.currentTimeMillis()
        every { scheduler.getScheduledFireTime() } returnsMany listOf(-1L, now + 60_000L, -1L)
        val vm = PresetViewModel(repository, scheduler)
        vm.startTimer(tea)
        vm.pauseTimer()
        vm.stopTimer()
        verify { scheduler.cancel() }
        vm.uiState.test {
            val state = awaitItem()
            assertNull(state.activePresetId)
            assertNull(state.fireTimeMillis)
            assertNull(state.pausedRemainingMs)
        }
    }

    @Test
    fun initRestoresRunningStateWhenFireTimeInFuture() = runTest {
        val now = System.currentTimeMillis()
        every { scheduler.getScheduledFireTime() } returns now + 90_000L
        every { scheduler.getScheduledPresetId() } returns 1L
        every { scheduler.getPausedRemainingMs() } returns -1L
        val vm = PresetViewModel(repository, scheduler)
        vm.uiState.test {
            val state = awaitItem()
            assertEquals(1L, state.activePresetId)
            assertNotNull(state.fireTimeMillis)
            assertNull(state.pausedRemainingMs)
            assertTrue(state.remainingSeconds > 0L)
        }
    }

    @Test
    fun initRestoresPausedStateWhenPausedRemainingPresent() = runTest {
        every { scheduler.getScheduledFireTime() } returns -1L
        every { scheduler.getScheduledPresetId() } returns 1L
        every { scheduler.getPausedRemainingMs() } returns 45_000L
        val vm = PresetViewModel(repository, scheduler)
        vm.uiState.test {
            val state = awaitItem()
            assertEquals(1L, state.activePresetId)
            assertNull(state.fireTimeMillis)
            assertEquals(45_000L, state.pausedRemainingMs)
            assertEquals(45L, state.remainingSeconds)
        }
    }

    @Test
    fun initClearsExpiredSchedulerStateAndStartsIdle() = runTest {
        val past = System.currentTimeMillis() - 10_000L
        every { scheduler.getScheduledFireTime() } returns past
        every { scheduler.getScheduledPresetId() } returns 1L
        every { scheduler.getPausedRemainingMs() } returns -1L
        val vm = PresetViewModel(repository, scheduler)
        verify { scheduler.cancel() }
        vm.uiState.test {
            val state = awaitItem()
            assertNull(state.activePresetId)
        }
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew :app:testDebugUnitTest --tests "com.example.timerapp.PresetViewModelTest"`
Expected: FAIL — compile errors on `state.activePresetId`, `state.fireTimeMillis`, `state.pausedRemainingMs`, `vm.pauseTimer()`, `vm.resumeTimer()`, `vm.stopTimer()`, `scheduler.pause(...)`, `scheduler.resume(...)`, etc.

- [ ] **Step 3: Implement the extended ViewModel**

Replace the entire file `app/src/main/kotlin/com/example/timerapp/ui/home/PresetViewModel.kt` with:

```kotlin
package com.example.timerapp.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.timerapp.alarm.AlarmScheduler
import com.example.timerapp.data.PresetEntity
import com.example.timerapp.data.PresetRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class HomeUiState(
    val presets: List<PresetEntity> = emptyList(),
    val editingPreset: PresetEntity? = null,
    val isBottomSheetOpen: Boolean = false,
    val activePresetId: Long? = null,
    val fireTimeMillis: Long? = null,
    val pausedRemainingMs: Long? = null,
    val remainingSeconds: Long = 0L,
)

class PresetViewModel(
    private val repository: PresetRepository,
    private val scheduler: AlarmScheduler
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    private var tickJob: Job? = null

    init {
        viewModelScope.launch {
            repository.getAll().collect { presets ->
                _uiState.update { it.copy(presets = presets) }
            }
        }
        restoreFromScheduler()
    }

    private fun restoreFromScheduler() {
        val presetId = scheduler.getScheduledPresetId()
        if (presetId == -1L) return
        val fireTime = scheduler.getScheduledFireTime()
        val paused = scheduler.getPausedRemainingMs()
        val now = System.currentTimeMillis()
        when {
            fireTime > now -> {
                _uiState.update {
                    it.copy(
                        activePresetId = presetId,
                        fireTimeMillis = fireTime,
                        pausedRemainingMs = null,
                        remainingSeconds = ((fireTime - now) / 1000L).coerceAtLeast(0L)
                    )
                }
                startTicking(fireTime)
            }
            paused > 0L -> {
                _uiState.update {
                    it.copy(
                        activePresetId = presetId,
                        fireTimeMillis = null,
                        pausedRemainingMs = paused,
                        remainingSeconds = paused / 1000L
                    )
                }
            }
            else -> {
                scheduler.cancel()
            }
        }
    }

    fun openAddSheet() {
        _uiState.update { it.copy(isBottomSheetOpen = true, editingPreset = null) }
    }

    fun openEditSheet(preset: PresetEntity) {
        _uiState.update { it.copy(isBottomSheetOpen = true, editingPreset = preset) }
    }

    fun dismissSheet() {
        _uiState.update { it.copy(isBottomSheetOpen = false, editingPreset = null) }
    }

    fun savePreset(label: String, durationMinutes: Int) {
        val editing = _uiState.value.editingPreset
        viewModelScope.launch {
            if (editing == null) {
                repository.insert(PresetEntity(label = label, durationMinutes = durationMinutes, isDefault = false))
            } else {
                repository.update(editing.copy(label = label, durationMinutes = durationMinutes))
            }
        }
        dismissSheet()
    }

    fun deletePreset(preset: PresetEntity) {
        viewModelScope.launch { repository.delete(preset) }
    }

    fun startTimer(preset: PresetEntity) {
        scheduler.schedule(preset)
        val fireTime = scheduler.getScheduledFireTime()
        _uiState.update {
            it.copy(
                activePresetId = preset.id,
                fireTimeMillis = fireTime,
                pausedRemainingMs = null,
                remainingSeconds = ((fireTime - System.currentTimeMillis()) / 1000L).coerceAtLeast(0L)
            )
        }
        startTicking(fireTime)
    }

    fun pauseTimer() {
        val state = _uiState.value
        val fireTime = state.fireTimeMillis ?: return
        val activeId = state.activePresetId ?: return
        val label = state.presets.firstOrNull { it.id == activeId }?.label ?: ""
        scheduler.pause(label)
        tickJob?.cancel()
        tickJob = null
        val remainingMs = (fireTime - System.currentTimeMillis()).coerceAtLeast(0L)
        _uiState.update {
            it.copy(
                fireTimeMillis = null,
                pausedRemainingMs = remainingMs,
                remainingSeconds = remainingMs / 1000L
            )
        }
    }

    fun resumeTimer() {
        val state = _uiState.value
        if (state.pausedRemainingMs == null) return
        val activeId = state.activePresetId ?: return
        val label = state.presets.firstOrNull { it.id == activeId }?.label ?: ""
        scheduler.resume(label)
        val fireTime = scheduler.getScheduledFireTime()
        _uiState.update {
            it.copy(
                fireTimeMillis = fireTime,
                pausedRemainingMs = null,
                remainingSeconds = ((fireTime - System.currentTimeMillis()) / 1000L).coerceAtLeast(0L)
            )
        }
        startTicking(fireTime)
    }

    fun stopTimer() {
        scheduler.cancel()
        tickJob?.cancel()
        tickJob = null
        _uiState.update {
            it.copy(
                activePresetId = null,
                fireTimeMillis = null,
                pausedRemainingMs = null,
                remainingSeconds = 0L
            )
        }
    }

    private fun startTicking(fireTime: Long) {
        tickJob?.cancel()
        tickJob = viewModelScope.launch {
            while (isActive) {
                val remaining = ((fireTime - System.currentTimeMillis()) / 1000L).coerceAtLeast(0L)
                _uiState.update { it.copy(remainingSeconds = remaining) }
                if (remaining == 0L) {
                    _uiState.update {
                        it.copy(
                            activePresetId = null,
                            fireTimeMillis = null,
                            pausedRemainingMs = null
                        )
                    }
                    break
                }
                delay(1000L)
            }
        }
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew :app:testDebugUnitTest --tests "com.example.timerapp.PresetViewModelTest"`
Expected: PASS, 14 tests.

- [ ] **Step 5: Confirm the project still compiles**

Run: `./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL. (The UI in `HomeScreen` still calls `startTimer`; new actions are not yet wired into the UI but the code compiles.)

- [ ] **Step 6: Commit**

```bash
git add app/src/main/kotlin/com/example/timerapp/ui/home/PresetViewModel.kt app/src/test/kotlin/com/example/timerapp/PresetViewModelTest.kt
git commit -m "feat(vm): extended state + pause/resume/stop actions + init-time restore"
```

---

## Task 6: `HomeScreen` UI — inline countdown, two buttons, disabled idle Starts

**Files:**
- Modify: `app/src/main/kotlin/com/example/timerapp/ui/home/HomeScreen.kt`

There are no unit tests for the Composable layer in this project. Verification of this task is part of the manual QA pass in Task 11.

- [ ] **Step 1: Replace `HomeScreen.kt`**

Replace the entire file `app/src/main/kotlin/com/example/timerapp/ui/home/HomeScreen.kt` with:

```kotlin
package com.example.timerapp.ui.home

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.timerapp.data.PresetEntity
import com.example.timerapp.util.formatMmSs

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(viewModel: PresetViewModel) {
    val state by viewModel.uiState.collectAsState()

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(onClick = { viewModel.openAddSheet() }) {
                Icon(Icons.Default.Add, contentDescription = "Add preset")
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item { Spacer(Modifier.height(8.dp)) }
            items(state.presets, key = { it.id }) { preset ->
                val isActive = state.activePresetId == preset.id
                val isPaused = isActive && state.pausedRemainingMs != null
                val isRunning = isActive && state.fireTimeMillis != null
                val anyTimerOwned = state.activePresetId != null
                PresetItem(
                    preset = preset,
                    isRunning = isRunning,
                    isPaused = isPaused,
                    startEnabled = !anyTimerOwned,
                    remainingSeconds = if (isActive) state.remainingSeconds else 0L,
                    onStart = { viewModel.startTimer(preset) },
                    onPause = { viewModel.pauseTimer() },
                    onResume = { viewModel.resumeTimer() },
                    onStop = { viewModel.stopTimer() },
                    onEdit = { viewModel.openEditSheet(preset) },
                    onDelete = { viewModel.deletePreset(preset) }
                )
            }
            item { Spacer(Modifier.height(80.dp)) }
        }
    }

    if (state.isBottomSheetOpen) {
        PresetBottomSheet(
            editing = state.editingPreset,
            onDismiss = { viewModel.dismissSheet() },
            onSave = { label, minutes -> viewModel.savePreset(label, minutes) }
        )
    }
}

@Composable
private fun PresetItem(
    preset: PresetEntity,
    isRunning: Boolean,
    isPaused: Boolean,
    startEnabled: Boolean,
    remainingSeconds: Long,
    onStart: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onStop: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = preset.label, style = MaterialTheme.typography.titleMedium)
                val subtitle = when {
                    isRunning -> formatMmSs(remainingSeconds * 1000L)
                    isPaused -> "Paused — ${formatMmSs(remainingSeconds * 1000L)}"
                    else -> "${preset.durationMinutes} min"
                }
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = onEdit) {
                Icon(Icons.Default.Edit, contentDescription = "Edit")
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = "Delete")
            }
            Spacer(Modifier.width(4.dp))
            when {
                isRunning -> {
                    FilledTonalButton(onClick = onPause) { Text("Pause") }
                    Spacer(Modifier.width(4.dp))
                    Button(
                        onClick = onStop,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error,
                            contentColor = MaterialTheme.colorScheme.onError
                        )
                    ) { Text("Stop") }
                }
                isPaused -> {
                    FilledTonalButton(onClick = onResume) { Text("Resume") }
                    Spacer(Modifier.width(4.dp))
                    Button(
                        onClick = onStop,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error,
                            contentColor = MaterialTheme.colorScheme.onError
                        )
                    ) { Text("Stop") }
                }
                else -> {
                    Button(onClick = onStart, enabled = startEnabled) { Text("Start") }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PresetBottomSheet(
    editing: PresetEntity?,
    onDismiss: () -> Unit,
    onSave: (String, Int) -> Unit
) {
    var label by remember(editing) { mutableStateOf(editing?.label ?: "") }
    var durationText by remember(editing) { mutableStateOf(editing?.durationMinutes?.toString() ?: "") }
    val isValid = label.isNotBlank() && durationText.toIntOrNull()?.let { it > 0 } == true

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = if (editing == null) "Add preset" else "Edit preset",
                style = MaterialTheme.typography.headlineSmall
            )
            OutlinedTextField(
                value = label,
                onValueChange = { label = it },
                label = { Text("Label") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            OutlinedTextField(
                value = durationText,
                onValueChange = { durationText = it },
                label = { Text("Duration (minutes)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
            )
            Button(
                onClick = { onSave(label.trim(), durationText.toIntOrNull() ?: 0) },
                modifier = Modifier.fillMaxWidth(),
                enabled = isValid
            ) {
                Text("Save")
            }
        }
    }
}
```

- [ ] **Step 2: Verify the app still compiles**

Run: `./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/kotlin/com/example/timerapp/ui/home/HomeScreen.kt
git commit -m "feat(ui): inline running/paused countdown with Pause/Resume/Stop buttons"
```

---

## Task 7: `AlarmReceiver` — stop the service when the alarm fires

**Files:**
- Modify: `app/src/main/kotlin/com/example/timerapp/alarm/AlarmReceiver.kt`

- [ ] **Step 1: Replace the receiver**

Replace the entire file `app/src/main/kotlin/com/example/timerapp/alarm/AlarmReceiver.kt` with:

```kotlin
package com.example.timerapp.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.timerapp.ui.alarm.AlarmActivity

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        context.startService(TimerService.stopIntent(context))

        val alarmIntent = Intent(context, AlarmActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_USER_ACTION)
        }
        context.startActivity(alarmIntent)
    }
}
```

- [ ] **Step 2: Verify compile**

Run: `./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/kotlin/com/example/timerapp/alarm/AlarmReceiver.kt
git commit -m "feat(alarm): stop TimerService when alarm fires"
```

---

## Task 8: `MainActivity` — runtime `POST_NOTIFICATIONS` request

**Files:**
- Modify: `app/src/main/kotlin/com/example/timerapp/MainActivity.kt`

- [ ] **Step 1: Replace `MainActivity.kt`**

Replace the entire file `app/src/main/kotlin/com/example/timerapp/MainActivity.kt` with:

```kotlin
package com.example.timerapp

import android.Manifest
import android.app.AlarmManager
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import com.example.timerapp.ui.home.HomeScreen
import com.example.timerapp.ui.home.PresetViewModel
import com.example.timerapp.ui.theme.TimerAppTheme

class MainActivity : ComponentActivity() {
    private val viewModel: PresetViewModel by viewModels {
        (application as TimerApplication).viewModelFactory
    }

    private val notifPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* result ignored */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestExactAlarmPermissionIfNeeded()
        requestNotificationPermissionIfNeeded()
        setContent {
            TimerAppTheme {
                HomeScreen(viewModel = viewModel)
            }
        }
    }

    private fun requestExactAlarmPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val alarmManager = getSystemService(AlarmManager::class.java)
            if (alarmManager?.canScheduleExactAlarms() == false) {
                startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM))
            }
        }
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val granted = ContextCompat.checkSelfPermission(
            this, Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
        if (!granted) {
            notifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}
```

- [ ] **Step 2: Verify compile**

Run: `./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/kotlin/com/example/timerapp/MainActivity.kt
git commit -m "feat(main): request POST_NOTIFICATIONS at runtime on API 33+"
```

---

## Task 9: `AndroidManifest.xml` — permissions + service declaration

**Files:**
- Modify: `app/src/main/AndroidManifest.xml`

- [ ] **Step 1: Replace the manifest**

Replace the entire file `app/src/main/AndroidManifest.xml` with:

```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">

    <uses-permission android:name="android.permission.USE_EXACT_ALARM" />
    <uses-permission android:name="android.permission.SCHEDULE_EXACT_ALARM" />
    <uses-permission android:name="android.permission.RECEIVE_BOOT_COMPLETED" />
    <uses-permission android:name="android.permission.USE_FULL_SCREEN_INTENT" />
    <uses-permission android:name="android.permission.VIBRATE" />
    <uses-permission android:name="android.permission.WAKE_LOCK" />
    <uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE_SPECIAL_USE" />

    <application
        android:name=".TimerApplication"
        android:allowBackup="true"
        android:icon="@mipmap/ic_launcher"
        android:label="@string/app_name"
        android:roundIcon="@mipmap/ic_launcher_round"
        android:supportsRtl="true"
        android:theme="@style/Theme.TimerApp">

        <activity
            android:name=".MainActivity"
            android:exported="true">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>

        <activity
            android:name=".ui.alarm.AlarmActivity"
            android:exported="false"
            android:showOnLockScreen="true"
            android:turnScreenOn="true"
            android:launchMode="singleInstance" />

        <service
            android:name=".alarm.TimerService"
            android:exported="false"
            android:foregroundServiceType="specialUse">
            <property
                android:name="android.app.PROPERTY_SPECIAL_USE_FGS_SUBTYPE"
                android:value="timer countdown notification" />
        </service>

        <receiver
            android:name=".alarm.AlarmReceiver"
            android:exported="false" />

        <receiver
            android:name=".alarm.BootReceiver"
            android:exported="true">
            <intent-filter>
                <action android:name="android.intent.action.BOOT_COMPLETED" />
            </intent-filter>
        </receiver>

    </application>
</manifest>
```

- [ ] **Step 2: Build the debug APK**

Run: `./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/AndroidManifest.xml
git commit -m "feat(manifest): declare TimerService and POST_NOTIFICATIONS / FGS permissions"
```

---

## Task 10: Full test suite green check

- [ ] **Step 1: Run the entire JVM unit test suite**

Run: `./gradlew :app:testDebugUnitTest`
Expected: BUILD SUCCESSFUL, all tests pass (including the existing `PresetDaoTest`, `PresetRepositoryTest`, `TimeFormatTest`, `AlarmSchedulerTest`, `PresetViewModelTest`).

If anything red, fix it before moving on — no skipping.

- [ ] **Step 2: Lint / build the full debug app**

Run: `./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL.

(No commit step — nothing changed if tests passed cleanly.)

---

## Task 11: Manual QA on Android Studio emulator (or device)

Install the debug build (`adb install -r app/build/outputs/apk/debug/app-debug.apk`) and walk through this checklist. If any item fails, capture which step failed and stop to debug before declaring the feature done.

- [ ] **Start running**
  - From home, tap **Start** on the "Tea" preset (3 min).
  - Card subtitle should read a live `mm:ss` countdown ticking each second.
  - Buttons on that card: [Pause] [Stop]. Other preset cards: their **Start** buttons are visibly disabled.
  - Swipe the notification shade down — a notification with the preset label and a ticking countdown should be visible.

- [ ] **Pause**
  - Tap **Pause** on the running card.
  - Subtitle now reads `Paused — mm:ss`. Buttons become [Resume] [Stop]. Countdown stops ticking on both the card and the notification.
  - Notification text changes to `Paused — mm:ss remaining`; the small icon swaps to a pause icon.

- [ ] **Resume**
  - Tap **Resume**.
  - Subtitle goes back to live ticking from the frozen value (not from 3:00). Buttons return to [Pause] [Stop]. Notification swaps back to the chronometer variant.

- [ ] **Tap notification**
  - With timer running, tap the notification.
  - The app should come to the foreground at the home screen, with the same preset still ticking inline. No second instance of the activity should be created.

- [ ] **Stop while running**
  - Tap **Stop** on the running card.
  - Notification disappears immediately. All preset cards return to idle. Start buttons re-enable.

- [ ] **Stop while paused**
  - Start a timer, pause it, then tap **Stop**.
  - Same expectations as the previous step.

- [ ] **Process death — running**
  - Start a 3-minute timer. From the Android Studio "Logcat / Devices" panel use *Stop process* (or `adb shell am force-stop com.example.timerapp`).
  - Reopen the app from the launcher.
  - Inline countdown should resume on the right preset at the correct remaining value; the notification should reappear as the chronometer variant.

- [ ] **Process death — paused**
  - Start, then pause a timer. Force-stop the process and reopen.
  - The same preset should display as Paused with the same `mm:ss` value, and the notification should reappear as the paused variant.

- [ ] **Expiry**
  - Start a short preset (1 min) and wait it out.
  - The alarm screen (`AlarmActivity`) should open. The chronometer notification should disappear.
  - After dismissing the alarm, the home screen should be idle on next view.

- [ ] **POST_NOTIFICATIONS denied**
  - Re-install the app fresh, deny the notification permission when prompted.
  - Start a timer — inline UI must still work (countdown ticks, Pause/Resume/Stop function). No crash. No notification appears, but the alarm itself still fires.

- [ ] **No commit; this is a verification gate.** If everything above passes, the feature is complete. Otherwise, stop and debug the failing step.

---

## Self-review notes (already addressed inline)

- All steps include the exact code or command needed; no "TBD" placeholders.
- Pause/resume signatures (`pause(label: String)`, `resume(label: String)`) are consistent between `AlarmScheduler` and `PresetViewModel`.
- `formatMmSs(millis)` takes milliseconds throughout (VM converts seconds → millis at the call site in `HomeScreen`).
- Spec coverage:
  - Inline countdown UI → Task 6
  - Two buttons (Pause/Resume + Stop) → Task 6
  - Chronometer notification → Task 4 (`buildRunningNotification`)
  - Paused notification variant → Task 4 (`buildPausedNotification`)
  - Pause/Resume scheduler API → Task 2 + Task 4 wiring
  - State invariants in ViewModel → Task 5
  - Init-time restore (running / paused / expired) → Task 5
  - `AlarmReceiver` stops service on fire → Task 7
  - `POST_NOTIFICATIONS` runtime request → Task 8
  - Manifest service + permissions → Task 9
  - Unit tests for VM + scheduler → Tasks 2 + 5
  - Manual QA checklist → Task 11
