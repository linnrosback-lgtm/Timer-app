package com.example.timerapp.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Database(entities = [PresetEntity::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun presetDao(): PresetDao

    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null

        private val DEFAULT_PRESETS = listOf(
            PresetEntity(label = "5 min", durationMinutes = 5, isDefault = true),
            PresetEntity(label = "10 min", durationMinutes = 10, isDefault = true),
            PresetEntity(label = "15 min", durationMinutes = 15, isDefault = true),
            PresetEntity(label = "30 min", durationMinutes = 30, isDefault = true),
        )

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                Room.databaseBuilder(context, AppDatabase::class.java, "timer_db")
                    .addCallback(object : Callback() {
                        override fun onCreate(db: SupportSQLiteDatabase) {
                            super.onCreate(db)
                            INSTANCE?.let { database ->
                                CoroutineScope(Dispatchers.IO).launch {
                                    DEFAULT_PRESETS.forEach { database.presetDao().insert(it) }
                                }
                            }
                        }
                    })
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
