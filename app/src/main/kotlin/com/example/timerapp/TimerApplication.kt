package com.example.timerapp

import android.app.Application
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.timerapp.alarm.AlarmScheduler
import com.example.timerapp.data.AppDatabase
import com.example.timerapp.data.PresetRepository
import com.example.timerapp.ui.home.PresetViewModel

class TimerApplication : Application() {
    val database by lazy { AppDatabase.getInstance(this) }
    val repository by lazy { PresetRepository(database.presetDao()) }
    val scheduler by lazy { AlarmScheduler(this) }

    val viewModelFactory: ViewModelProvider.Factory = viewModelFactory {
        initializer {
            PresetViewModel(repository, scheduler)
        }
    }
}
