# Timer App — Design Spec

**Date:** 2026-05-24  
**Language:** Kotlin  
**Platform:** Android

---

## Overview

An Android app that replaces the native alarm's "snooze" behaviour with a customizable list of timer presets. When an alarm fires, the user picks a new duration from their preset list (or dismisses). The new alarm starts from the moment they tap.

---

## Architecture

MVVM with a clean layered structure. UI built with Jetpack Compose. UI design handled externally in Figma.

```
App
├── data/         → PresetEntity, PresetDao, AppDatabase, PresetRepository
├── alarm/        → AlarmScheduler, AlarmReceiver
├── ui/
│   ├── home/     → HomeScreen, PresetViewModel
│   └── alarm/    → AlarmActivity, AlarmScreen
```

**Layers:**
- **Data:** Room database + Repository as single source of truth
- **ViewModel:** `StateFlow`-based state, business logic
- **UI:** Compose screens observe ViewModel
- **Alarm:** `AlarmManager` + `BroadcastReceiver` for system-level scheduling

---

## Data Model

```kotlin
@Entity
data class PresetEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val label: String,
    val durationMinutes: Int,
    val isDefault: Boolean
)
```

**Default presets seeded on first launch:** 5 min, 10 min, 15 min, 30 min.

User can add, edit, and delete any preset including defaults. No enforced min/max count.

**PresetDao operations:**
- `getAll(): Flow<List<PresetEntity>>`
- `insert(preset: PresetEntity)`
- `update(preset: PresetEntity)`
- `delete(preset: PresetEntity)`

---

## Alarm Flow

1. **User starts a timer** (from HomeScreen or AlarmScreen) → `AlarmScheduler.schedule(durationMinutes)` calls `AlarmManager.setExactAndAllowWhileIdle()` with a `PendingIntent` targeting `AlarmReceiver`.
2. **Alarm fires** → `AlarmReceiver.onReceive()` launches `AlarmActivity` via a full-screen intent. Device wakes, `AlarmActivity` appears over the lock screen.
3. **AlarmActivity** starts looping sound (`RingtoneManager`) and vibration (`VibrationEffect`). Shows `AlarmScreen` with preset list + dismiss button.
4. **User taps a preset** → new alarm scheduled from now + duration → sound/vibration stops → `AlarmActivity` finishes.
5. **User taps dismiss** → sound/vibration stops → `AlarmActivity` finishes. No new alarm scheduled.
6. **Back button** does nothing — user must explicitly tap dismiss or a preset.
7. Alarm keeps ringing until the user interacts. No auto-dismiss.

### Boot handling
`RECEIVE_BOOT_COMPLETED` permission declared. Active alarm state (scheduled fire time in epoch ms) is persisted to `SharedPreferences` whenever an alarm is set or cancelled. On boot, `BootReceiver` reads this value and reschedules the alarm if the fire time is still in the future.

---

## Permissions

```xml
<uses-permission android:name="android.permission.USE_EXACT_ALARM" />
<uses-permission android:name="android.permission.SCHEDULE_EXACT_ALARM" />
<uses-permission android:name="android.permission.RECEIVE_BOOT_COMPLETED" />
<uses-permission android:name="android.permission.USE_FULL_SCREEN_INTENT" />
<uses-permission android:name="android.permission.VIBRATE" />
<uses-permission android:name="android.permission.WAKE_LOCK" />
```

`SCHEDULE_EXACT_ALARM` requires runtime permission prompt on Android 12+.

---

## Home Screen

Single screen with bottom sheet overlay for add/edit. No navigation library needed.

**Preset list:**
- Scrollable list showing label + duration for each preset
- Edit and delete actions per item (swipe gesture or icon buttons)
- "Add preset" FAB opens bottom sheet with label and duration fields
- Editing a preset opens the same bottom sheet pre-filled

**Starting a timer:**
- Each preset has a start action (tap or button)
- Schedules an alarm immediately for that duration
- Snackbar confirms: "Timer set for X min"

**ViewModel state:**
```kotlin
data class HomeUiState(
    val presets: List<PresetEntity> = emptyList(),
    val editingPreset: PresetEntity? = null,
    val isBottomSheetOpen: Boolean = false
)
```

---

## Alarm Screen

Hosted in `AlarmActivity`. Shown full-screen when alarm fires.

**Layout:**
- Header: "Time's up!"
- Scrollable preset list — same presets from Room
- Tapping a preset schedules new alarm from now, closes screen
- "Dismiss" button — stops alarm, no new timer

**AlarmActivity flags:**
```kotlin
window.addFlags(
    WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
    WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
)
```

**State:** Presets fetched directly from `PresetRepository` via `collectAsState`. No dedicated ViewModel.

---

## Key Components

| Component | Type | Responsibility |
|---|---|---|
| `PresetEntity` | Room entity | Preset data model |
| `PresetDao` | Room DAO | DB queries |
| `AppDatabase` | Room database | DB singleton |
| `PresetRepository` | Repository | Data access abstraction |
| `AlarmScheduler` | Class | Wraps AlarmManager calls |
| `AlarmReceiver` | BroadcastReceiver | Catches fired alarm, launches AlarmActivity |
| `BootReceiver` | BroadcastReceiver | Reschedules alarm after reboot |
| `PresetViewModel` | ViewModel | HomeScreen state + logic |
| `HomeScreen` | Composable | Preset management UI |
| `AlarmActivity` | Activity | Full-screen alarm host |
| `AlarmScreen` | Composable | Alarm UI — preset list + dismiss |

---

## Out of Scope

- Multiple simultaneous alarms
- Alarm history / logs
- Notification actions (alarm interaction is full-screen Activity only)
- Cloud sync or backup of presets
