package com.example.timerapp.data

import kotlinx.coroutines.flow.Flow

class PresetRepository(private val dao: PresetDao) {
    fun getAll(): Flow<List<PresetEntity>> = dao.getAll()
    suspend fun insert(preset: PresetEntity) = dao.insert(preset)
    suspend fun update(preset: PresetEntity) = dao.update(preset)
    suspend fun delete(preset: PresetEntity) = dao.delete(preset)
}
