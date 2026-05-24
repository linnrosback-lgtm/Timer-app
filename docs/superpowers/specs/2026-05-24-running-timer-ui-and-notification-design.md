# Running Timer: Inline Countdown + Chronometer Notification

Date: 2026-05-24
Status: Approved (design phase)

## Problem

After testing the current build in Android Studio, two gaps remain:

1. When a preset's **Start** is tapped, nothing in the UI confirms a timer is running. The button stays labeled "Start" and there is no visible countdown on the home screen.
2. The running timer is invisible in the system notification shade. The user cannot glance at the notifications to see how much time remains.

## Goals

- Show a live countdown inline on the preset card whose timer is running.
- Replace the running preset's single **Start** button with two buttons: **Pause** + **Stop** while counting down, and **Resume** + **Stop** while paused.
- Pause freezes the remaining time; Resume re-schedules the alarm for that exact remaining; Stop cancels everything and returns the card to idle.
- Show a persistent notification: a system-ticking chronometer counting down while running, and a static "Paused — `mm:ss` remaining" notification while paused.
- Survive process death: if the app is killed and **reopened** while a timer is scheduled or paused, the home screen and notification both reflect the correct state. (While the process is dead, the notification may temporarily disappear — see Open risks.)

## Non-goals

- Multiple concurrent timers. The existing `AlarmScheduler` uses a single `REQUEST_CODE` and a single fire-time pref slot; we preserve that one-timer-at-a-time model.
- Resuming after Stop. Stop is terminal; there is no "undo stop" — the user must tap Start to begin a fresh countdown from the preset's full duration.
- Notification action buttons (e.g., Pause/Stop from the shade). Tap on the notification opens the app; no action buttons.
- Lock-screen full-screen countdown (the alarm-fired screen handled by `AlarmActivity` is unchanged).

## User experience

### Home screen, no timer running
Unchanged from today: list of preset cards, each with label, "N min" subtitle, edit/delete icons, and a **Start** button.

### Home screen, timer running on preset X
- Preset X card: subtitle line is replaced by a live `mm:ss` countdown (e.g., `04:37`). The trailing **Start** button is replaced by two buttons side-by-side: **Pause** (tonal/primary) and **Stop** (error/red tonal).
- All other preset cards: their **Start** buttons are disabled (greyed out). Edit and delete remain enabled — editing or deleting a non-running preset is allowed.
- Editing or deleting the running preset itself is allowed but does **not** cancel the running timer. (The timer is bound to the scheduled fire time, not the preset row.)

### Home screen, timer paused on preset X
- Preset X card: subtitle shows the **frozen** `mm:ss` remaining (no ticking). A small "Paused" label or pause-icon prefix indicates the state visually.
- The two buttons become **Resume** (tonal/primary) and **Stop** (error/red tonal).
- Other preset cards: their **Start** buttons remain disabled (a paused timer still owns the single timer slot).

### Tapping Pause
- Reads `remainingMs = fireTimeMillis - now`.
- Calls `AlarmScheduler.pause()` which cancels the pending alarm and writes `paused_remaining_ms = remainingMs` (clears `fire_time`).
- ViewModel stops the tick coroutine and sets `isPaused = true`, `remainingSeconds = remainingMs / 1000`.
- Foreground service swaps its notification to the static "Paused" variant (same notification id, replaced via `notify()`).

### Tapping Resume
- Reads stored `pausedRemainingMs`.
- Calls `AlarmScheduler.resume()` which schedules a new alarm at `now + pausedRemainingMs`, writes `fire_time`, clears `paused_remaining_ms`.
- ViewModel restarts the tick coroutine; foreground service swaps the notification back to the chronometer variant.

### Tapping Stop
- Cancels the alarm via `AlarmScheduler.cancel()` (works whether running or paused — clears both pref keys).
- Stops the foreground service and dismisses the notification.
- Clears running/paused state in the ViewModel; all preset cards return to their idle state.

### Notification
Two variants, posted with the same `NOTIF_ID` on the same low-importance channel `timer_running`. Switching state replaces the notification in place — no flicker, no extra alert (use `setOnlyAlertOnce(true)`).

**Running variant**
- Title: the preset's label (e.g., "Tea").
- Chronometer: `setUsesChronometer(true) + setChronometerCountDown(true) + setWhen(fireTimeMillis)`.
- Ongoing, no sound, no vibration.

**Paused variant**
- Title: the preset's label.
- Content text: `"Paused — mm:ss remaining"` (computed once at pause time, not ticking).
- `setUsesChronometer(false)`.
- Ongoing, no sound, no vibration. A small pause icon (e.g., `R.drawable.ic_stat_pause`) replaces the running icon to make the state glanceable.

Both variants:
- Tap action: launches `MainActivity` with `SINGLE_TOP | CLEAR_TOP` so the existing home screen is brought forward rather than a new instance.

### Timer expiry
- Existing flow: `AlarmReceiver` fires, launches `AlarmActivity` (alarm sound + Dismiss UI). No change to that path.
- New: `AlarmReceiver` also stops `TimerService` so the chronometer notification is dismissed when the alarm rings.
- ViewModel observes the cleared scheduler state on next interaction (or via an explicit broadcast — see Implementation notes) and clears `runningPresetId`.

## Architecture

### State

`HomeUiState` gains four fields:

```kotlin
data class HomeUiState(
    val presets: List<PresetEntity> = emptyList(),
    val editingPreset: PresetEntity? = null,
    val isBottomSheetOpen: Boolean = false,
    val activePresetId: Long? = null,        // preset that owns the running OR paused timer
    val fireTimeMillis: Long? = null,        // non-null only while running
    val pausedRemainingMs: Long? = null,     // non-null only while paused
    val remainingSeconds: Long = 0L,         // displayed value; ticks while running, frozen while paused
)
```

State invariants:
- `activePresetId == null` ⇔ idle ⇔ both `fireTimeMillis` and `pausedRemainingMs` are null.
- Running ⇔ `activePresetId != null && fireTimeMillis != null && pausedRemainingMs == null`.
- Paused ⇔ `activePresetId != null && fireTimeMillis == null && pausedRemainingMs != null`.

`remainingSeconds` is recomputed by a ViewModel coroutine each second while running, and set once at pause time (then left untouched).

### AlarmScheduler changes

New pref keys: `preset_id`, `paused_remaining_ms`. Updated API:

```kotlin
fun schedule(preset: PresetEntity)              // was: schedule(durationMinutes: Int)
fun pause()                                     // running → paused
fun resume()                                    // paused → running
fun cancel()                                    // any state → idle
fun getScheduledFireTime(): Long                // -1L when not running
fun getScheduledPresetId(): Long                // -1L when idle
fun getPausedRemainingMs(): Long                // -1L when not paused
```

Behavior:
- `schedule(preset)` — writes `fire_time = now + durationMs`, `preset_id = preset.id`, clears `paused_remaining_ms`. Calls `startForegroundService(TimerService.startRunning(...))`.
- `pause()` — reads current `fire_time`, computes `remainingMs`, writes `paused_remaining_ms = remainingMs`, clears `fire_time`, cancels the AlarmManager alarm. Sends `TimerService.updatePaused(...)` to swap notification.
- `resume()` — reads `paused_remaining_ms`, writes `fire_time = now + remainingMs`, clears `paused_remaining_ms`, re-arms the AlarmManager alarm. Sends `TimerService.updateRunning(...)` to swap notification back.
- `cancel()` — clears all three keys, cancels alarm, calls `stopService(...)`.

`TimerService` is the single owner of the notification across states; it never gets two pending intents fighting each other.

### TimerService (new)

`alarm/TimerService.kt` — a `Service` (not `LifecycleService`; nothing needs lifecycle scope).

Intent actions:
- `ACTION_START_RUNNING` — extras: `presetLabel`, `fireTimeMillis`. Posts the chronometer notification.
- `ACTION_UPDATE_PAUSED` — extras: `presetLabel`, `remainingMs`. Replaces the notification with the paused variant.
- `ACTION_UPDATE_RUNNING` — extras: `presetLabel`, `fireTimeMillis`. Replaces with the chronometer variant.

`onStartCommand` dispatches on the action and calls `startForeground(NOTIF_ID, build(...))` on the first call, then `NotificationManagerCompat.notify(NOTIF_ID, build(...))` on subsequent updates. Returns `START_NOT_STICKY`.

`onDestroy` removes the foreground notification automatically.

Notification builder (shared base):
- Channel id `timer_running`, channel name "Running timer", importance `IMPORTANCE_LOW`.
- `setContentTitle(presetLabel)`, `setOngoing(true)`, `setOnlyAlertOnce(true)`, `setContentIntent(piToMainActivity)`.

Running variant adds:
- `setSmallIcon(R.drawable.ic_stat_timer)`.
- `setUsesChronometer(true)`, `setChronometerCountDown(true)`, `setWhen(fireTimeMillis)`.

Paused variant adds:
- `setSmallIcon(R.drawable.ic_stat_pause)`.
- `setContentText("Paused — " + formatMmSs(remainingMs))`.
- `setUsesChronometer(false)`.

(Icon names are placeholders to be created/aliased during implementation.)

### ViewModel ticking

In `PresetViewModel`:

```kotlin
private var tickJob: Job? = null

private fun startTicking(fireTime: Long) {
    tickJob?.cancel()
    tickJob = viewModelScope.launch {
        while (isActive) {
            val remaining = ((fireTime - System.currentTimeMillis()) / 1000L).coerceAtLeast(0L)
            _uiState.update { it.copy(remainingSeconds = remaining) }
            if (remaining == 0L) break
            delay(1000L)
        }
        clearActiveState()
    }
}
```

Public actions:
- `startTimer(preset)` — `scheduler.schedule(preset)`; state → `(activePresetId = preset.id, fireTimeMillis = ..., pausedRemainingMs = null)`; `startTicking`.
- `pauseTimer()` — read `fireTime - now`, `scheduler.pause()`; `tickJob?.cancel()`; state → `(fireTimeMillis = null, pausedRemainingMs = remainingMs, remainingSeconds = remainingMs/1000)`.
- `resumeTimer()` — `scheduler.resume()`; read new `fireTime`; state → `(fireTimeMillis = newFireTime, pausedRemainingMs = null)`; `startTicking`.
- `stopTimer()` — `scheduler.cancel()`; `tickJob?.cancel()`; clear all active fields.

`init` restores state from the scheduler:
- If `getScheduledPresetId() == -1L` → idle.
- Else if `getScheduledFireTime() > now` → running, start ticking.
- Else if `getScheduledFireTime() in past` → expired while killed; call `scheduler.cancel()`, stay idle.
- Else if `getPausedRemainingMs() > 0` → paused, do not tick; service must be re-started in paused variant (re-post notification on next user interaction or immediately from `init` via context held by the scheduler).

### Alarm fired path

`AlarmReceiver.onReceive`:
- Existing: launch `AlarmActivity`.
- New: `context.stopService(Intent(context, TimerService::class.java))` so the chronometer notification disappears.
- The ViewModel's tick loop naturally hits `remaining == 0` and clears `runningPresetId` on its own — no broadcast needed for the in-process case.
- For the process-killed-then-reopened case after expiry: on `init`, if `fireTime <= now`, clear the scheduler's stored values and start with no running state.

## Permissions and manifest

Additions to `AndroidManifest.xml`:

```xml
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_SPECIAL_USE" />

<service
    android:name=".alarm.TimerService"
    android:foregroundServiceType="specialUse"
    android:exported="false">
    <property
        android:name="android.app.PROPERTY_SPECIAL_USE_FGS_SUBTYPE"
        android:value="timer countdown notification" />
</service>
```

`SCHEDULE_EXACT_ALARM` / `USE_EXACT_ALARM` are already present (existing feature).

`MainActivity` requests `POST_NOTIFICATIONS` on first launch on API 33+ using the standard `ActivityResultContracts.RequestPermission` API. If the user denies, timers still work — only the notification is suppressed; the inline UI is unaffected.

## Testing

Unit tests (existing infra, JVM-only, no Robolectric):

- `PresetViewModelTest`
  - Starting a timer sets `activePresetId`, `fireTimeMillis`, and `remainingSeconds > 0`.
  - Tick updates `remainingSeconds` downward (use `TestDispatcher` + `advanceTimeBy`).
  - Pause freezes `remainingSeconds`, sets `pausedRemainingMs`, clears `fireTimeMillis`, and stops further ticks.
  - Resume after pause sets a new `fireTimeMillis`, clears `pausedRemainingMs`, and resumes ticking from the frozen value.
  - Stop while running clears all active fields and cancels the scheduler.
  - Stop while paused clears all active fields and cancels the scheduler.
  - On expiry (`remaining == 0`), state is cleared automatically.
  - Init with `fireTime` in the future restores running state.
  - Init with `paused_remaining_ms > 0` restores paused state (no tick).
  - Init with `fireTime` in the past clears scheduler state and starts idle.

- `AlarmSchedulerTest`
  - `schedule(preset)` persists `fire_time` and `preset_id`, clears `paused_remaining_ms`.
  - `pause()` clears `fire_time`, persists `paused_remaining_ms`, keeps `preset_id`.
  - `resume()` clears `paused_remaining_ms`, persists `fire_time`, keeps `preset_id`.
  - `cancel()` clears all three keys.
  - `getScheduledPresetId()` returns `-1L` when idle, the id when running or paused.
  - `getPausedRemainingMs()` returns `-1L` when not paused.
  - (Service start verification is out of scope for the unit suite; the existing tests already mock/skip framework wiring.)

Manual QA checklist (Android Studio emulator + physical device):
- Start timer → card shows countdown, buttons are [Pause][Stop], notification appears with ticking chronometer.
- Swipe notification shade down → chronometer visibly ticks each second.
- Tap Pause → card shows frozen `mm:ss` and buttons [Resume][Stop]; notification swaps to "Paused — mm:ss remaining" with no ticking.
- Tap Resume → countdown continues from the frozen value; notification swaps back to ticking chronometer.
- Tap Stop while running → notification gone, card idle.
- Tap Stop while paused → notification gone, card idle.
- Tap notification (running or paused) → app opens to home in the matching state.
- Force-stop app while running, relaunch → countdown resumes on the right card; notification reappears.
- Force-stop app while paused, relaunch → card shows paused state with the same `mm:ss`; notification reappears as paused variant.
- Let timer expire → `AlarmActivity` opens, chronometer notification disappears, home returns to idle on next view.

## Open risks

- **Process-death notification gap.** Android does not let a killed app auto-restart a foreground service on its own. If the OS kills the process, the chronometer notification disappears until the user reopens the app (which re-runs `init` and can re-`startForegroundService`). The alarm itself still fires via `AlarmManager`. This is an accepted limitation; alternatives (a persistent service kept alive by `START_STICKY`, or rescheduling via `BootReceiver`) add complexity beyond this scope.
- **POST_NOTIFICATIONS denied.** Inline UI keeps working. Notification simply doesn't appear. No retry prompt.
- **Special-use foreground service type.** On API 34+, `specialUse` must justify its use in Play Console. Acceptable for a personal/dev build. If publishing, revisit type choice.

## Out of scope (explicit deferrals)

- Multiple concurrent timers
- Notification action buttons (Stop / +1 min)
- Tile / quick-settings integration
- Wear OS companion
