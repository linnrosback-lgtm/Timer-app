package com.example.timerapp.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface PresetDao {
    @Query("SELECT * FROM presets ORDER BY id ASC")
    fun getAll(): Flow<List<PresetEntity>>

    @Insert
    suspend fun insert(preset: PresetEntity)

    @Update
    suspend fun update(preset: PresetEntity)

    @Delete
    suspend fun delete(preset: PresetEntity)
}
