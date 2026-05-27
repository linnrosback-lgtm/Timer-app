package com.example.timerapp

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationManager
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import com.example.timerapp.ui.home.ActiveTimerScreen
import com.example.timerapp.ui.home.HomeScreen
import com.example.timerapp.ui.home.PresetViewModel
import com.example.timerapp.ui.theme.TimerAppTheme

class MainActivity : ComponentActivity() {
    private val viewModel: PresetViewModel by viewModels {
        (application as TimerApplication).viewModelFactory
    }

    private val notifPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* result ignored */ }

    private var needsFullScreenPermission by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        requestExactAlarmPermissionIfNeeded()
        requestNotificationPermissionIfNeeded()
        requestBatteryOptimizationExemptionIfNeeded()
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
                        onGrantFullScreen = { openFullScreenIntentSettings() },
                        onViewActive = { viewModel.viewActiveTimer() }
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        needsFullScreenPermission = checkNeedsFullScreenPermission()
    }

    private fun checkNeedsFullScreenPermission(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) return false
        val nm = getSystemService(NotificationManager::class.java) ?: return false
        return !nm.canUseFullScreenIntent()
    }

    private fun openFullScreenIntentSettings() {
        startActivity(
            Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT).apply {
                data = Uri.parse("package:$packageName")
            }
        )
    }

    private fun requestBatteryOptimizationExemptionIfNeeded() {
        val pm = getSystemService(PowerManager::class.java) ?: return
        if (!pm.isIgnoringBatteryOptimizations(packageName)) {
            startActivity(
                Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                    data = Uri.parse("package:$packageName")
                }
            )
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
