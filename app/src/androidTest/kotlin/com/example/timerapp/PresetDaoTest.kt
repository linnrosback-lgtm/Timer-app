package com.example.timerapp

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.timerapp.data.AppDatabase
import com.example.timerapp.data.PresetDao
import com.example.timerapp.data.PresetEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PresetDaoTest {
    private lateinit var db: AppDatabase
    private lateinit var dao: PresetDao

    @Before
    fun setup() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java
        ).allowMainThreadQueries().build()
        dao = db.presetDao()
    }

    @After
    fun teardown() {
        db.close()
    }

    @Test
    fun insertAndGetAll() = runTest {
        val preset = PresetEntity(label = "Quick nap", durationMinutes = 20, isDefault = false)
        dao.insert(preset)
        val all = dao.getAll().first()
        assertEquals(1, all.size)
        assertEquals("Quick nap", all[0].label)
        assertEquals(20, all[0].durationMinutes)
    }

    @Test
    fun updatePreset() = runTest {
        val preset = PresetEntity(label = "Old", durationMinutes = 5, isDefault = false)
        dao.insert(preset)
        val inserted = dao.getAll().first()[0]
        val updated = inserted.copy(label = "New", durationMinutes = 10)
        dao.update(updated)
        val all = dao.getAll().first()
        assertEquals(1, all.size)
        assertEquals("New", all[0].label)
        assertEquals(10, all[0].durationMinutes)
    }

    @Test
    fun deletePreset() = runTest {
        val preset = PresetEntity(label = "Delete me", durationMinutes = 5, isDefault = false)
        dao.insert(preset)
        val inserted = dao.getAll().first()[0]
        dao.delete(inserted)
        val all = dao.getAll().first()
        assertTrue(all.isEmpty())
    }

    @Test
    fun multipleInserts() = runTest {
        dao.insert(PresetEntity(label = "A", durationMinutes = 5, isDefault = true))
        dao.insert(PresetEntity(label = "B", durationMinutes = 10, isDefault = false))
        dao.insert(PresetEntity(label = "C", durationMinutes = 15, isDefault = false))
        val all = dao.getAll().first()
        assertEquals(3, all.size)
    }
}
