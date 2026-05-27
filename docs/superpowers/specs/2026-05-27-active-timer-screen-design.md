# Active Timer Screen — Design

**Date:** 2026-05-27
**Branch:** feature/running-timer-ui
**Figma:** https://www.figma.com/design/h5F6UUBFnr893GA6aNvaD3/Timer-app?node-id=20-2795

## Goal

A full-screen "active timer" view shown while a preset's timer runs. Replaces the
current inline list controls as the primary running-timer surface. Built from the
Figma frame `Active timer` (node 20:2795).

## Behavior decisions

- **Navigation:** state-driven screen swap in `MainActivity` (no Navigation Compose
  dependency). Active screen shown when the user is viewing a running/paused timer.
- **Back gesture/button:** returns to the home list; timer keeps running in the
  background. User can re-enter by tapping the running preset.
- **Restart FAB (↺):** resets countdown to the preset's full duration, **paused**.
- **Pause FAB (center):** toggles pause ↔ play (resume).
- **Stop FAB (✕):** cancels the timer and returns to the home list.
- **Top chip "Etikett":** the preset's name/label.
- **Inner chip "5 min":** the preset's configured duration.
- **Bell + "14:54":** wall-clock time the alarm will fire (from `fireTimeMillis`),
  Swedish 24h `HH:mm`.
- **Big countdown:** `MM:SS`, switching to `HH:MM:SS` when remaining ≥ 1 hour.

## Screen visibility model

Visibility cannot be driven purely by `activePresetId` (back must keep the timer
running while leaving the screen). Add a nav flag `isViewingActiveTimer`:

- Set `true` on `startTimer(preset)` and when the user taps a running preset in the list.
- Set `false` on back and on `stopTimer()`.
- `MainActivity` renders `ActiveTimerScreen` when `isViewingActiveTimer && activePresetId != null`,
  else `HomeScreen`.

If the timer completes on its own (`remainingSeconds == 0`), `isViewingActiveTimer`
is cleared alongside the existing reset, returning the user to the list.

## Components

### New: `ui/home/ActiveTimerScreen.kt`

`@Composable fun ActiveTimerScreen(...)` over dark surface (`#141218` → use
`MaterialTheme.colorScheme.surface`). Top→bottom:

1. **App bar** — `TopAppBar` with an overflow (3-dot) icon button. Inert for now
   (no menu items defined yet). Leading icon top-left per Figma.
2. **Top chip** — assistive-chip style showing the preset **name**.
3. **Circle (≈250dp)** — `Box` with a `Canvas` ring behind a centered `Column`:
   - inner chip: preset **duration** label (e.g. "5 min").
   - big countdown text (display-large), from `remainingSeconds`.
   - `Row`: bell icon + ring wall-clock time.
4. **FAB row** — restart (secondary container), pause/play (large, tertiary
   container), stop (secondary container), matching Figma sizes/spacing.

**Ring math:** `progress = remainingSeconds / preset.durationSeconds` (clamp 0..1).
Full track arc + brighter remaining arc depleting clockwise + a leading dot at the
arc head. Colors from `MaterialTheme.colorScheme` tertiary family.

### Changed: `ui/home/PresetViewModel.kt`

- Add `isViewingActiveTimer: Boolean` to `HomeUiState`.
- Add `viewActiveTimer()` / `exitActiveTimer()` (set the flag).
- Set flag in `startTimer`; clear in `stopTimer` and on auto-completion in
  `startTicking`.
- Add `restartTimer()`: cancel running schedule, set `pausedRemainingMs =
  durationSeconds * 1000`, `fireTimeMillis = null`, stop the tick job, keep
  `activePresetId`.
- Expose active preset name + duration for the screen (derive from `presets.first {
  it.id.toLong() == activePresetId }`).

### Changed: `alarm/AlarmScheduler.kt`

- Add `restartPaused(label: String, durationMs: Long)`: cancel the pending alarm,
  write `PREF_KEY_PAUSED_REMAINING_MS = durationMs`, keep `PREF_KEY_PRESET_ID`,
  clear `PREF_KEY_FIRE_TIME`, and update the `TimerService` paused notification.

### Changed: `util/TimeFormat.kt`

- Add `formatClockTime(millis: Long): String` → Swedish `HH:mm` (24h) for the ring
  time. Add `HH:MM:SS`-when-≥1h handling for the big countdown (extend
  `formatMmSs` usage or add `formatCountdown(millis)`).

### Changed: `MainActivity.kt`

- Swap composable on `isViewingActiveTimer && activePresetId != null`.

### Changed: `HomeScreen.kt`

- Tapping a running/paused preset row calls `viewActiveTimer()` to re-enter the
  active screen. (Inline start/pause/stop controls can remain for now.)

### Strings

Swedish entries in `strings.xml` — content descriptions for restart, pause, play,
stop, back/overflow, and bell.

## Testing

- Unit: `restartTimer()` state transitions (running→paused-at-full, tick cancelled,
  `activePresetId` retained).
- Unit: `formatClockTime` and `HH:MM:SS`-vs-`MM:SS` countdown selection.
- On-device: ring rendering, FAB toggle, back-keeps-running, re-enter, auto-complete.

## Out of scope

- Overflow menu contents.
- Editing the timer from the active screen.
