package com.example.timerapp

import android.app.AlarmManager
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import com.example.timerapp.ui.home.HomeScreen
import com.example.timerapp.ui.home.PresetViewModel
import com.example.timerapp.ui.theme.TimerAppTheme

class MainActivity : ComponentActivity() {
    private val viewModel: PresetViewModel by viewModels {
        (application as TimerApplication).viewModelFactory
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestExactAlarmPermissionIfNeeded()
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
}
