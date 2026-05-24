package com.example.timerapp

import com.example.timerapp.data.PresetDao
import com.example.timerapp.data.PresetEntity
import com.example.timerapp.data.PresetRepository
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class PresetRepositoryTest {
    private val dao = mockk<PresetDao>(relaxed = true)
    private val repository = PresetRepository(dao)

    @Test
    fun getAllDelegatesToDao() = runTest {
        val presets = listOf(PresetEntity(label = "Test", durationMinutes = 5, isDefault = false))
        every { dao.getAll() } returns flowOf(presets)
        repository.getAll().collect { assertEquals(presets, it) }
    }

    @Test
    fun insertDelegatesToDao() = runTest {
        val preset = PresetEntity(label = "Test", durationMinutes = 5, isDefault = false)
        repository.insert(preset)
        coVerify { dao.insert(preset) }
    }

    @Test
    fun updateDelegatesToDao() = runTest {
        val preset = PresetEntity(id = 1, label = "Test", durationMinutes = 5, isDefault = false)
        repository.update(preset)
        coVerify { dao.update(preset) }
    }

    @Test
    fun deleteDelegatesToDao() = runTest {
        val preset = PresetEntity(id = 1, label = "Test", durationMinutes = 5, isDefault = false)
        repository.delete(preset)
        coVerify { dao.delete(preset) }
    }
}
