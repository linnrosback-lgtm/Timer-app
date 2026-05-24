package com.example.timerapp.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "presets")
data class PresetEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val label: String,
    val durationMinutes: Int,
    val isDefault: Boolean
)
