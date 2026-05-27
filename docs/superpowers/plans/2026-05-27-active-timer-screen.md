# Active Timer Screen Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a full-screen active-timer view (countdown ring, label chips, FAB controls) that replaces inline list controls while a timer runs.

**Architecture:** State-driven screen swap in `MainActivity` — a new `isViewingActiveTimer` flag in `HomeUiState` controls which composable is rendered; no Navigation Compose dependency added. `ActiveTimerScreen` is a pure composable reading from `PresetViewModel`. All timer logic stays in `PresetViewModel` + `AlarmScheduler`.

**Tech Stack:** Kotlin, Jetpack Compose, Material3, `Canvas` for ring, MockK + Turbine for unit tests, Gradle `./gradlew test` for local JVM tests.

---

## File Map

| File | Action | Responsibility |
|------|--------|----------------|
| `app/src/main/res/values/strings.xml` | Modify | Swedish content descriptions |
| `app/src/main/kotlin/com/example/timerapp/util/TimeFormat.kt` | Modify | `formatClockTime`, `formatCountdown` |
| `app/src/test/kotlin/com/example/timerapp/TimeFormatTest.kt` | Modify | Tests for the two new format functions |
| `app/src/main/kotlin/com/example/timerapp/alarm/AlarmScheduler.kt` | Modify | Add `restartPaused` |
| `app/src/test/kotlin/com/example/timerapp/AlarmSchedulerTest.kt` | Modify | Test for `restartPaused` |
| `app/src/main/kotlin/com/example/timerapp/ui/home/PresetViewModel.kt` | Modify | `isViewingActiveTimer`, `viewActiveTimer`, `exitActiveTimer`, `restartTimer` |
| `app/src/test/kotlin/com/example/timerapp/PresetViewModelTest.kt` | Modify | Tests for new ViewModel methods |
| `app/src/main/kotlin/com/example/timerapp/ui/home/ActiveTimerScreen.kt` | Create | Full-screen active-timer composable |
| `app/src/main/kotlin/com/example/timerapp/MainActivity.kt` | Modify | Screen-swap logic |

---

## Task 1: Add Swedish string resources

**Files:**
- Modify: `app/src/main/res/values/strings.xml`

- [ ] **Step 1: Add the content-description strings**

Replace the full file content with:

```xml
<resources>
    <string name="app_name">Timer App</string>
    <string name="preset_label_hint">Label</string>
    <string name="ringtone">Ringtone</string>
    <string name="ringtone_default">Default</string>
    <string name="action_add">Add</string>
    <string name="action_save">Save</string>
    <string name="active_timer_overflow_menu">Fler alternativ</string>
    <string name="active_timer_restart">Starta om</string>
    <string name="active_timer_pause">Pausa</string>
    <string name="active_timer_play">Återuppta</string>
    <string name="active_timer_stop">Stoppa</string>
    <string name="active_timer_rings_at">Ringer kl.</string>
</resources>
```

- [ ] **Step 2: Commit**

```
git add app/src/main/res/values/strings.xml
git commit -m "feat: add Swedish content-description strings for active timer screen"
```

---

## Task 2: Add `formatClockTime` and `formatCountdown` to TimeFormat

**Files:**
- Modify: `app/src/main/kotlin/com/example/timerapp/util/TimeFormat.kt`
- Modify: `app/src/test/kotlin/com/example/timerapp/TimeFormatTest.kt`

- [ ] **Step 1: Write the failing tests**

Append to `TimeFormatTest.kt` (keep all existing tests unchanged, add a new class):

```kotlin
// At the top of the file add these imports if not already present:
// import com.example.timerapp.util.formatClockTime
// import com.example.timerapp.util.formatCountdown
// import java.util.TimeZone

class FormatClockTimeTest {
    @Test
    fun formatClockTimeReturnsHhMm() {
        // Use a known epoch offset: 1970-01-01 14:54 UTC
        val millis = (14 * 3600 + 54 * 60) * 1_000L
        // Force UTC so test is timezone-independent
        val tz = TimeZone.getDefault()
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
        try {
            assertEquals("14:54", formatClockTime(millis))
        } finally {
            TimeZone.setDefault(tz)
        }
    }

    @Test
    fun formatClockTimeZeroIsZeroZero() {
        val tz = TimeZone.getDefault()
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
        try {
            assertEquals("00:00", formatClockTime(0L))
        } finally {
            TimeZone.setDefault(tz)
        }
    }
}

class FormatCountdownTest {
    @Test
    fun underOneHourFormatsMmSs() {
        assertEquals("05:37", formatCountdown(5 * 60 + 37))
    }

    @Test
    fun exactlyOneHourFormatsHhMmSs() {
        assertEquals("01:00:00", formatCountdown(3600))
    }

    @Test
    fun overOneHourFormatsHhMmSs() {
        assertEquals("01:02:03", formatCountdown(3600 + 2 * 60 + 3))
    }

    @Test
    fun zeroSecondsFormatsAsMmSs() {
        assertEquals("00:00", formatCountdown(0))
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

```
./gradlew :app:test --tests "com.example.timerapp.FormatClockTimeTest" --tests "com.example.timerapp.FormatCountdownTest" -q
```

Expected: FAIL — `formatClockTime` and `formatCountdown` not found.

- [ ] **Step 3: Implement the two functions in `TimeFormat.kt`**

Add to the bottom of `app/src/main/kotlin/com/example/timerapp/util/TimeFormat.kt`:

```kotlin
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

fun formatClockTime(millis: Long): String {
    val sdf = SimpleDateFormat("HH:mm", Locale("sv", "SE"))
    return sdf.format(Date(millis))
}

fun formatCountdown(totalSeconds: Long): String {
    val safe = totalSeconds.coerceAtLeast(0L)
    return if (safe >= 3600L) {
        val h = safe / 3600L
        val m = (safe % 3600L) / 60L
        val s = safe % 60L
        "%02d:%02d:%02d".format(h, m, s)
    } else {
        val m = safe / 60L
        val s = safe % 60L
        "%02d:%02d".format(m, s)
    }
}
```

Note: `formatCountdown` takes **seconds** (not millis), matching how `remainingSeconds` is stored in `HomeUiState`.

- [ ] **Step 4: Run tests to verify they pass**

```
./gradlew :app:test --tests "com.example.timerapp.FormatClockTimeTest" --tests "com.example.timerapp.FormatCountdownTest" -q
```

Expected: PASS

- [ ] **Step 5: Commit**

```
git add app/src/main/kotlin/com/example/timerapp/util/TimeFormat.kt \
        app/src/test/kotlin/com/example/timerapp/TimeFormatTest.kt
git commit -m "feat: add formatClockTime and formatCountdown utilities"
```

---

## Task 3: Add `restartPaused` to `AlarmScheduler`

**Files:**
- Modify: `app/src/main/kotlin/com/example/timerapp/alarm/AlarmScheduler.kt`
- Modify: `app/src/test/kotlin/com/example/timerapp/AlarmSchedulerTest.kt`

- [ ] **Step 1: Write the failing test**

Append inside the `AlarmSchedulerTest` class (before the closing `}`):

```kotlin
@Test
fun restartPausedCancelsAlarmWritesDurationAndKeepsPresetId() {
    every { prefs.getLong(AlarmScheduler.PREF_KEY_PRESET_ID, -1L) } returns 42L

    val scheduler = AlarmScheduler(context)
    scheduler.restartPaused(label = "Tea", durationMs = 180_000L)

    verify { alarmManager.cancel(any<PendingIntent>()) }
    verify { prefsEditor.putLong(AlarmScheduler.PREF_KEY_PAUSED_REMAINING_MS, 180_000L) }
    verify { prefsEditor.remove(AlarmScheduler.PREF_KEY_FIRE_TIME) }
    verify(exactly = 0) { prefsEditor.remove(AlarmScheduler.PREF_KEY_PRESET_ID) }
    verify { prefsEditor.apply() }
}
```

- [ ] **Step 2: Run test to verify it fails**

```
./gradlew :app:test --tests "com.example.timerapp.AlarmSchedulerTest.restartPausedCancelsAlarmWritesDurationAndKeepsPresetId" -q
```

Expected: FAIL — `restartPaused` not found.

- [ ] **Step 3: Implement `restartPaused` in `AlarmScheduler.kt`**

Add after the `resume` function (around line 41 of the current file):

```kotlin
fun restartPaused(label: String, durationMs: Long) {
    alarmManager.cancel(buildPendingIntent())
    prefs.edit()
        .putLong(PREF_KEY_PAUSED_REMAINING_MS, durationMs)
        .remove(PREF_KEY_FIRE_TIME)
        .apply()
    context.startService(TimerService.updatePausedIntent(context, label, durationMs))
}
```

- [ ] **Step 4: Run test to verify it passes**

```
./gradlew :app:test --tests "com.example.timerapp.AlarmSchedulerTest.restartPausedCancelsAlarmWritesDurationAndKeepsPresetId" -q
```

Expected: PASS

- [ ] **Step 5: Run full test suite to confirm nothing broken**

```
./gradlew :app:test -q
```

Expected: all tests PASS.

- [ ] **Step 6: Commit**

```
git add app/src/main/kotlin/com/example/timerapp/alarm/AlarmScheduler.kt \
        app/src/test/kotlin/com/example/timerapp/AlarmSchedulerTest.kt
git commit -m "feat: add AlarmScheduler.restartPaused for restart-and-pause behavior"
```

---

## Task 4: Extend `HomeUiState` and `PresetViewModel`

**Files:**
- Modify: `app/src/main/kotlin/com/example/timerapp/ui/home/PresetViewModel.kt`
- Modify: `app/src/test/kotlin/com/example/timerapp/PresetViewModelTest.kt`

- [ ] **Step 1: Write the failing tests**

Append inside `PresetViewModelTest` (before the closing `}`):

```kotlin
// ---- active-timer navigation flag ----

@Test
fun startTimerSetsIsViewingActiveTimer() = runTest {
    val now = System.currentTimeMillis()
    every { scheduler.getScheduledFireTime() } returns now + 60_000L
    val vm = PresetViewModel(repository, scheduler)
    vm.startTimer(tea)
    vm.uiState.test {
        val state = awaitItem()
        assertTrue(state.isViewingActiveTimer)
    }
}

@Test
fun exitActiveTimerClearsFlag() = runTest {
    val now = System.currentTimeMillis()
    every { scheduler.getScheduledFireTime() } returns now + 60_000L
    val vm = PresetViewModel(repository, scheduler)
    vm.startTimer(tea)
    vm.exitActiveTimer()
    vm.uiState.test {
        val state = awaitItem()
        assertFalse(state.isViewingActiveTimer)
    }
}

@Test
fun viewActiveTimerSetsFlag() = runTest {
    val vm = PresetViewModel(repository, scheduler)
    vm.viewActiveTimer()
    vm.uiState.test {
        val state = awaitItem()
        assertTrue(state.isViewingActiveTimer)
    }
}

@Test
fun stopTimerClearsIsViewingActiveTimer() = runTest {
    val now = System.currentTimeMillis()
    every { scheduler.getScheduledFireTime() } returns now + 60_000L
    val vm = PresetViewModel(repository, scheduler)
    vm.startTimer(tea)
    vm.stopTimer()
    vm.uiState.test {
        val state = awaitItem()
        assertFalse(state.isViewingActiveTimer)
    }
}

// ---- restartTimer ----

@Test
fun restartTimerSetsFullDurationPausedKeepsActivePresetId() = runTest {
    val now = System.currentTimeMillis()
    every { scheduler.getScheduledFireTime() } returns now + 60_000L
    val vm = PresetViewModel(repository, scheduler)
    vm.startTimer(tea) // tea.durationSeconds = 3
    vm.restartTimer()
    verify { scheduler.restartPaused("Tea", 3_000L) }
    vm.uiState.test {
        val state = awaitItem()
        assertEquals(1L, state.activePresetId)
        assertNull(state.fireTimeMillis)
        assertEquals(3_000L, state.pausedRemainingMs)
        assertEquals(3L, state.remainingSeconds)
    }
}

@Test
fun restartTimerWhilePausedAlsoResets() = runTest {
    val now = System.currentTimeMillis()
    every { scheduler.getScheduledFireTime() } returns now + 60_000L
    val vm = PresetViewModel(repository, scheduler)
    vm.startTimer(tea)
    vm.pauseTimer()
    vm.restartTimer()
    verify { scheduler.restartPaused("Tea", 3_000L) }
    vm.uiState.test {
        val state = awaitItem()
        assertEquals(1L, state.activePresetId)
        assertNull(state.fireTimeMillis)
        assertEquals(3_000L, state.pausedRemainingMs)
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

```
./gradlew :app:test --tests "com.example.timerapp.PresetViewModelTest.startTimerSetsIsViewingActiveTimer" --tests "com.example.timerapp.PresetViewModelTest.restartTimerSetsFullDurationPausedKeepsActivePresetId" -q
```

Expected: FAIL — `isViewingActiveTimer`, `viewActiveTimer`, `exitActiveTimer`, `restartTimer` not found.

- [ ] **Step 3: Update `HomeUiState` and add methods to `PresetViewModel`**

Replace `HomeUiState` data class (lines 14–22 of `PresetViewModel.kt`):

```kotlin
data class HomeUiState(
    val presets: List<PresetEntity> = emptyList(),
    val editingPreset: PresetEntity? = null,
    val isBottomSheetOpen: Boolean = false,
    val activePresetId: Long? = null,
    val fireTimeMillis: Long? = null,
    val pausedRemainingMs: Long? = null,
    val remainingSeconds: Long = 0L,
    val isViewingActiveTimer: Boolean = false,
)
```

Add these three functions after `stopTimer()`:

```kotlin
fun viewActiveTimer() {
    _uiState.update { it.copy(isViewingActiveTimer = true) }
}

fun exitActiveTimer() {
    _uiState.update { it.copy(isViewingActiveTimer = false) }
}

fun restartTimer() {
    val state = _uiState.value
    val activeId = state.activePresetId ?: return
    val preset = state.presets.firstOrNull { it.id.toLong() == activeId } ?: return
    val durationMs = preset.durationSeconds * 1_000L
    tickJob?.cancel()
    tickJob = null
    scheduler.restartPaused(preset.label, durationMs)
    _uiState.update {
        it.copy(
            fireTimeMillis = null,
            pausedRemainingMs = durationMs,
            remainingSeconds = preset.durationSeconds.toLong()
        )
    }
}
```

In `startTimer`, add `isViewingActiveTimer = true` to the `_uiState.update` call:

```kotlin
fun startTimer(preset: PresetEntity) {
    scheduler.schedule(preset)
    val fireTime = scheduler.getScheduledFireTime()
    _uiState.update {
        it.copy(
            activePresetId = preset.id.toLong(),
            fireTimeMillis = fireTime,
            pausedRemainingMs = null,
            remainingSeconds = ((fireTime - System.currentTimeMillis()) / 1000L).coerceAtLeast(0L),
            isViewingActiveTimer = true
        )
    }
    startTicking(fireTime)
}
```

In `stopTimer`, add `isViewingActiveTimer = false`:

```kotlin
fun stopTimer() {
    scheduler.cancel()
    tickJob?.cancel()
    tickJob = null
    _uiState.update {
        it.copy(
            activePresetId = null,
            fireTimeMillis = null,
            pausedRemainingMs = null,
            remainingSeconds = 0L,
            isViewingActiveTimer = false
        )
    }
}
```

In `startTicking`, when `remaining == 0L`, add `isViewingActiveTimer = false`:

```kotlin
if (remaining == 0L) {
    _uiState.update {
        it.copy(
            activePresetId = null,
            fireTimeMillis = null,
            pausedRemainingMs = null,
            isViewingActiveTimer = false
        )
    }
    break
}
```

- [ ] **Step 4: Run tests to verify they pass**

```
./gradlew :app:test --tests "com.example.timerapp.PresetViewModelTest" -q
```

Expected: all PASS (including the 6 new tests + existing ones).

- [ ] **Step 5: Commit**

```
git add app/src/main/kotlin/com/example/timerapp/ui/home/PresetViewModel.kt \
        app/src/test/kotlin/com/example/timerapp/PresetViewModelTest.kt
git commit -m "feat: add isViewingActiveTimer flag, viewActiveTimer/exitActiveTimer, restartTimer to PresetViewModel"
```

---

## Task 5: Create `ActiveTimerScreen`

**Files:**
- Create: `app/src/main/kotlin/com/example/timerapp/ui/home/ActiveTimerScreen.kt`

No unit tests for this composable (ring/layout verified on-device).

- [ ] **Step 1: Create the file**

Create `app/src/main/kotlin/com/example/timerapp/ui/home/ActiveTimerScreen.kt` with:

```kotlin
package com.example.timerapp.ui.home

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.timerapp.R
import com.example.timerapp.util.formatClockTime
import com.example.timerapp.util.formatCountdown
import kotlin.math.cos
import kotlin.math.sin

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActiveTimerScreen(
    presetName: String,
    presetDurationSeconds: Int,
    remainingSeconds: Long,
    fireTimeMillis: Long?,
    isPaused: Boolean,
    onBack: () -> Unit,
    onPauseResume: () -> Unit,
    onRestart: () -> Unit,
    onStop: () -> Unit,
) {
    BackHandler(onBack = onBack)

    Scaffold(
        topBar = {
            TopAppBar(
                title = {},
                actions = {
                    IconButton(onClick = {}) {
                        Icon(
                            Icons.Default.MoreVert,
                            contentDescription = stringResource(R.string.active_timer_overflow_menu)
                        )
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceEvenly
        ) {
            // Top label chip — preset name
            SuggestionChip(
                onClick = {},
                label = { Text(presetName) }
            )

            // Ring + countdown
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(250.dp)
            ) {
                TimerRing(
                    progress = if (presetDurationSeconds > 0)
                        (remainingSeconds.toFloat() / presetDurationSeconds.toFloat()).coerceIn(0f, 1f)
                    else 0f,
                    modifier = Modifier.fillMaxSize()
                )
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    // Inner duration chip
                    val durationLabel = formatCountdown(presetDurationSeconds.toLong())
                    SuggestionChip(
                        onClick = {},
                        label = { Text(durationLabel) }
                    )
                    // Big countdown
                    Text(
                        text = formatCountdown(remainingSeconds),
                        style = MaterialTheme.typography.displayLarge
                    )
                    // Bell + ring time (only when running)
                    if (fireTimeMillis != null) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                Icons.Default.Notifications,
                                contentDescription = stringResource(R.string.active_timer_rings_at),
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = formatClockTime(fireTimeMillis),
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }
            }

            // FAB row: restart | pause/play (large) | stop
            Row(
                horizontalArrangement = Arrangement.spacedBy(17.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Restart FAB (small)
                FloatingActionButton(
                    onClick = onRestart,
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    modifier = Modifier.size(56.dp)
                ) {
                    Icon(
                        painter = painterResource(android.R.drawable.ic_menu_rotate),
                        contentDescription = stringResource(R.string.active_timer_restart),
                        tint = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
                // Pause / Play FAB (large)
                LargeFloatingActionButton(
                    onClick = onPauseResume,
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer
                ) {
                    Icon(
                        painter = painterResource(
                            if (isPaused) android.R.drawable.ic_media_play
                            else android.R.drawable.ic_media_pause
                        ),
                        contentDescription = stringResource(
                            if (isPaused) R.string.active_timer_play
                            else R.string.active_timer_pause
                        ),
                        tint = MaterialTheme.colorScheme.onTertiaryContainer,
                        modifier = Modifier.size(28.dp)
                    )
                }
                // Stop FAB (small)
                FloatingActionButton(
                    onClick = onStop,
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    modifier = Modifier.size(56.dp)
                ) {
                    Icon(
                        painter = painterResource(android.R.drawable.ic_menu_close_clear_cancel),
                        contentDescription = stringResource(R.string.active_timer_stop),
                        tint = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }
        }
    }
}

@Composable
private fun TimerRing(progress: Float, modifier: Modifier = Modifier) {
    val trackColor = MaterialTheme.colorScheme.surfaceVariant
    val arcColor = MaterialTheme.colorScheme.tertiary
    val dotColor = MaterialTheme.colorScheme.tertiary

    Canvas(modifier = modifier) {
        val stroke = Stroke(width = 8.dp.toPx(), cap = StrokeCap.Round)
        val inset = stroke.width / 2f
        val arcSize = Size(size.width - stroke.width, size.height - stroke.width)
        val topLeft = Offset(inset, inset)

        // Full track
        drawArc(
            color = trackColor,
            startAngle = -90f,
            sweepAngle = 360f,
            useCenter = false,
            topLeft = topLeft,
            size = arcSize,
            style = stroke
        )
        // Remaining arc (depletes clockwise)
        if (progress > 0f) {
            drawArc(
                color = arcColor,
                startAngle = -90f,
                sweepAngle = 360f * progress,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = stroke
            )
            // Leading dot
            val sweepRad = Math.toRadians((-90f + 360f * progress).toDouble())
            val cx = size.width / 2f + (arcSize.width / 2f) * cos(sweepRad).toFloat()
            val cy = size.height / 2f + (arcSize.height / 2f) * sin(sweepRad).toFloat()
            drawCircle(color = dotColor, radius = stroke.width / 2f, center = Offset(cx, cy))
        }
    }
}
```

- [ ] **Step 2: Build to verify compilation**

```
./gradlew :app:assembleDebug -q
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```
git add app/src/main/kotlin/com/example/timerapp/ui/home/ActiveTimerScreen.kt
git commit -m "feat: add ActiveTimerScreen composable with ring, chips, FABs"
```

---

## Task 6: Wire screen swap in `MainActivity`

**Files:**
- Modify: `app/src/main/kotlin/com/example/timerapp/MainActivity.kt`

- [ ] **Step 1: Update `MainActivity` to collect state and swap screens**

Replace the `setContent { ... }` block (lines 42–50) with:

```kotlin
setContent {
    TimerAppTheme {
        val state by viewModel.uiState.collectAsState()
        val activePreset = state.activePresetId?.let { id ->
            state.presets.firstOrNull { it.id.toLong() == id }
        }

        if (state.isViewingActiveTimer && activePreset != null) {
            ActiveTimerScreen(
                presetName = activePreset.label,
                presetDurationSeconds = activePreset.durationSeconds,
                remainingSeconds = state.remainingSeconds,
                fireTimeMillis = state.fireTimeMillis,
                isPaused = state.pausedRemainingMs != null,
                onBack = { viewModel.exitActiveTimer() },
                onPauseResume = {
                    if (state.pausedRemainingMs != null) viewModel.resumeTimer()
                    else viewModel.pauseTimer()
                },
                onRestart = { viewModel.restartTimer() },
                onStop = { viewModel.stopTimer() }
            )
        } else {
            HomeScreen(
                viewModel = viewModel,
                showFullScreenBanner = needsFullScreenPermission,
                onGrantFullScreen = { openFullScreenIntentSettings() }
            )
        }
    }
}
```

Add these imports to `MainActivity.kt` if not present:
```kotlin
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.example.timerapp.ui.home.ActiveTimerScreen
```

- [ ] **Step 2: Build to verify compilation**

```
./gradlew :app:assembleDebug -q
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Run full test suite**

```
./gradlew :app:test -q
```

Expected: all tests PASS.

- [ ] **Step 4: Commit**

```
git add app/src/main/kotlin/com/example/timerapp/MainActivity.kt
git commit -m "feat: wire ActiveTimerScreen screen swap in MainActivity"
```

---

## Task 7: Add re-entry from home list

**Files:**
- Modify: `app/src/main/kotlin/com/example/timerapp/ui/home/HomeScreen.kt`

When the user presses back from the active screen, they see the home list. They should be able to tap a running/paused preset row to return to the active screen.

- [ ] **Step 1: Add `onViewActive` callback to `HomeScreen` and pass it through**

Add `onViewActive: () -> Unit = {}` parameter to `HomeScreen`:

```kotlin
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: PresetViewModel,
    showFullScreenBanner: Boolean = false,
    onGrantFullScreen: () -> Unit = {},
    onViewActive: () -> Unit = {}
)
```

Add `onViewActive` parameter to `PresetItem` call in the `items` block (after `onDelete`):

```kotlin
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
    onDelete = { viewModel.deletePreset(preset) },
    onViewActive = onViewActive
)
```

Add `onViewActive: () -> Unit` to `PresetItem`'s parameter list:

```kotlin
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
    onDelete: () -> Unit,
    onViewActive: () -> Unit = {}
)
```

Make the `Card` clickable to call `onViewActive` when a timer is active on this row. Replace the `Card(modifier = Modifier.fillMaxWidth())` line with:

```kotlin
Card(
    modifier = Modifier
        .fillMaxWidth()
        .then(
            if (isRunning || isPaused)
                Modifier.clickable(onClick = onViewActive)
            else Modifier
        )
) {
```

Add `import androidx.compose.foundation.clickable` if not already present.

- [ ] **Step 2: Wire `onViewActive` in `MainActivity`**

In `MainActivity`, pass `onViewActive` into `HomeScreen`:

```kotlin
HomeScreen(
    viewModel = viewModel,
    showFullScreenBanner = needsFullScreenPermission,
    onGrantFullScreen = { openFullScreenIntentSettings() },
    onViewActive = { viewModel.viewActiveTimer() }
)
```

- [ ] **Step 3: Build to verify compilation**

```
./gradlew :app:assembleDebug -q
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Run full test suite**

```
./gradlew :app:test -q
```

Expected: all tests PASS.

- [ ] **Step 5: Commit**

```
git add app/src/main/kotlin/com/example/timerapp/ui/home/HomeScreen.kt \
        app/src/main/kotlin/com/example/timerapp/MainActivity.kt
git commit -m "feat: tap running preset on home list to re-enter active timer screen"
```

---

## On-Device Verification Checklist

After installing the debug APK (`./gradlew installDebug`):

- [ ] Tap play on a preset → active timer screen appears with correct name chip, duration chip, countdown, bell+time.
- [ ] Ring sweeps down from full; dot tracks the arc head.
- [ ] Pause FAB shows pause icon while running; tap it → icon swaps to play, countdown freezes.
- [ ] Tap play icon → countdown resumes, icon swaps back to pause.
- [ ] Tap restart FAB → countdown resets to full duration, timer paused.
- [ ] Tap stop FAB → returns to home list, timer gone.
- [ ] Press system back → returns to home list; timer still counts down (check notification).
- [ ] Tap running preset row → active screen reappears at correct remaining time.
- [ ] Let timer expire naturally → screen returns to home list on its own.
- [ ] Rotate device during countdown → screen and ring stay correct.
