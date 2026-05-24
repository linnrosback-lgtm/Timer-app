# Running Timer: Inline Countdown + Chronometer Notification

Date: 2026-05-24
Status: Approved (design phase)

## Problem

After testing the current build in Android Studio, two gaps remain:

1. When a preset's **Start** is tapped, nothing in the UI confirms a timer is running. The button stays labeled "Start" and there is no visible countdown on the home screen.
2. The running timer is invisible in the system notification shade. The user cannot glance at the notifications to see how much time remains.

## Goals

- Show a live countdown inline on the preset card whose timer is running.
- Toggle the running preset's button from **Start** to **Stop**.
- Show a persistent notification with a system-ticking chronometer that counts down to zero while the timer is running.
- Survive process death: if the app is killed and **reopened** while a timer is scheduled, the home screen inline countdown resumes and the chronometer notification is re-posted. (While the process is dead, the notification may temporarily disappear — see Open risks.)

## Non-goals

- Multiple concurrent timers. The existing `AlarmScheduler` uses a single `REQUEST_CODE` and a single fire-time pref slot; we preserve that one-timer-at-a-time model.
- Pause/resume. Only Start and Stop.
- Notification action buttons (e.g., Stop from the shade). Tap on the notification opens the app; no action buttons.
- Lock-screen full-screen countdown (the alarm-fired screen handled by `AlarmActivity` is unchanged).

## User experience

### Home screen, no timer running
Unchanged from today: list of preset cards, each with label, "N min" subtitle, edit/delete icons, and a **Start** button.

### Home screen, timer running on preset X
- Preset X card: subtitle line is replaced by a live `mm:ss` countdown (e.g., `04:37`). The trailing button reads **Stop** and uses an error/red tonal style to make it visually distinct from a Start button.
- All other preset cards: their **Start** buttons are disabled (greyed out). Edit and delete remain enabled — editing or deleting a non-running preset is allowed.
- Editing or deleting the running preset itself is allowed but does **not** cancel the running timer. (The timer is bound to the scheduled fire time, not the preset row.)

### Tapping Stop
- Cancels the alarm via `AlarmScheduler.cancel()`.
- Stops the foreground service and dismisses the notification.
- Clears running state in the ViewModel; all preset cards return to their idle state.

### Notification
- Posted as soon as a timer starts; updated/replaced is unnecessary because the chronometer ticks itself.
- Title: the preset's label (e.g., "Tea").
- Body / chronometer: counts down `mm:ss` to zero using `setUsesChronometer(true) + setChronometerCountDown(true) + setWhen(fireTimeMillis)`.
- Ongoing (not swipeable). Low-importance channel — no sound, no vibration.
- Tap action: launches `MainActivity` with `SINGLE_TOP | CLEAR_TOP` so the existing home screen is brought forward rather than a new instance.

### Timer expiry
- Existing flow: `AlarmReceiver` fires, launches `AlarmActivity` (alarm sound + Dismiss UI). No change to that path.
- New: `AlarmReceiver` also stops `TimerService` so the chronometer notification is dismissed when the alarm rings.
- ViewModel observes the cleared scheduler state on next interaction (or via an explicit broadcast — see Implementation notes) and clears `runningPresetId`.

## Architecture

### State

`HomeUiState` gains three fields:

```kotlin
data class HomeUiState(
    val presets: List<PresetEntity> = emptyList(),
    val editingPreset: PresetEntity? = null,
    val isBottomSheetOpen: Boolean = false,
    val runningPresetId: Long? = null,
    val fireTimeMillis: Long? = null,
    val remainingSeconds: Long = 0L,
)
```

`runningPresetId` is the source of truth for "is anything running, and which preset." `fireTimeMillis` is mirrored from `AlarmScheduler` prefs for tick math. `remainingSeconds` is recomputed every second by a ViewModel coroutine and consumed directly by the UI.

### AlarmScheduler changes

Add a second pref key for the preset id and update the API:

```kotlin
fun schedule(preset: PresetEntity)   // was: schedule(durationMinutes: Int)
fun cancel()
fun getScheduledFireTime(): Long
fun getScheduledPresetId(): Long     // -1L when none
```

`schedule` writes both `fire_time` and `preset_id`, then calls `context.startForegroundService(TimerService.startIntent(...))`. `cancel` clears both keys and calls `context.stopService(...)`.

### TimerService (new)

`alarm/TimerService.kt` — a `Service` (not `LifecycleService`; nothing needs lifecycle scope).

Responsibilities:
- On `onStartCommand`, read fire time + preset label from the intent extras, call `startForeground(NOTIF_ID, buildNotification(...))`, return `START_NOT_STICKY` (we do not want the OS to restart it without a pending alarm).
- On `onDestroy`, no extra work — the foreground notification is removed automatically.

Notification builder:
- Channel id `timer_running`, channel name "Running timer", importance `IMPORTANCE_LOW`.
- `setSmallIcon(R.drawable.ic_stat_timer)` (reuse existing launcher mono icon or add a simple vector — pick during implementation).
- `setContentTitle(presetLabel)`.
- `setUsesChronometer(true)`, `setChronometerCountDown(true)`, `setWhen(fireTimeMillis)`.
- `setOngoing(true)`, `setOnlyAlertOnce(true)`.
- `setContentIntent(piToMainActivity)`.

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
        clearRunningState()
    }
}
```

`startTimer(preset)` calls `scheduler.schedule(preset)`, updates state to `(runningPresetId = preset.id, fireTimeMillis = ..., remainingSeconds = ...)`, then `startTicking`.

`stopTimer()` calls `scheduler.cancel()`, cancels `tickJob`, and clears state.

`init` restores state from `scheduler.getScheduledFireTime()` + `scheduler.getScheduledPresetId()` so a process restart immediately resumes the inline countdown.

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
  - Starting a timer sets `runningPresetId`, `fireTimeMillis`, and `remainingSeconds > 0`.
  - Tick updates `remainingSeconds` downward (use `TestDispatcher` + `advanceTimeBy`).
  - Stop clears `runningPresetId` and cancels the scheduler.
  - On expiry (`remaining == 0`), state is cleared automatically.
  - Init after a saved `fireTime` in the future restores running state.
  - Init after a saved `fireTime` in the past clears scheduler state and starts idle.

- `AlarmSchedulerTest`
  - `schedule(preset)` persists both `fire_time` and `preset_id`.
  - `cancel()` clears both keys.
  - `getScheduledPresetId()` returns `-1L` when none.
  - (Service start verification is out of scope for the unit suite; the existing tests already mock/skip framework wiring.)

Manual QA checklist (Android Studio emulator + physical device):
- Start timer → card shows countdown, button reads Stop, notification appears with ticking chronometer.
- Swipe notification shade down → chronometer visibly ticks each second.
- Tap notification → app opens to home, countdown still ticking on the same card.
- Tap Stop → notification gone, card returns to idle.
- Force-stop app while timer running, relaunch → countdown resumes on the right card; notification reappears (after the foreground service is restarted on next interaction — acceptable; see Open risks).
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
