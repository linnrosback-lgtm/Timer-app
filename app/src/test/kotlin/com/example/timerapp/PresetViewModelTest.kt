package com.example.timerapp

import app.cash.turbine.test
import com.example.timerapp.alarm.AlarmScheduler
import com.example.timerapp.data.PresetEntity
import com.example.timerapp.data.PresetRepository
import com.example.timerapp.ui.home.HomeUiState
import com.example.timerapp.ui.home.PresetViewModel
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PresetViewModelTest {
    private val testDispatcher = UnconfinedTestDispatcher()
    private val repository = mockk<PresetRepository>(relaxed = true)
    private val scheduler = mockk<AlarmScheduler>(relaxed = true)

    private val samplePresets = listOf(
        PresetEntity(id = 1, label = "5 min", durationMinutes = 5, isDefault = true),
        PresetEntity(id = 2, label = "10 min", durationMinutes = 10, isDefault = true),
    )

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        every { repository.getAll() } returns flowOf(samplePresets)
    }

    @After
    fun teardown() {
        Dispatchers.resetMain()
    }

    @Test
    fun initialStateHasPresetsFromRepository() = runTest {
        val vm = PresetViewModel(repository, scheduler)
        vm.uiState.test {
            val state = awaitItem()
            assertEquals(samplePresets, state.presets)
            assertFalse(state.isBottomSheetOpen)
            assertNull(state.editingPreset)
        }
    }

    @Test
    fun openAddSheetSetsBottomSheetOpenAndNullEditing() = runTest {
        val vm = PresetViewModel(repository, scheduler)
        vm.openAddSheet()
        vm.uiState.test {
            val state = awaitItem()
            assertTrue(state.isBottomSheetOpen)
            assertNull(state.editingPreset)
        }
    }

    @Test
    fun openEditSheetSetsEditingPreset() = runTest {
        val vm = PresetViewModel(repository, scheduler)
        val preset = samplePresets[0]
        vm.openEditSheet(preset)
        vm.uiState.test {
            val state = awaitItem()
            assertTrue(state.isBottomSheetOpen)
            assertEquals(preset, state.editingPreset)
        }
    }

    @Test
    fun dismissSheetClosesBottomSheetAndClearsEditing() = runTest {
        val vm = PresetViewModel(repository, scheduler)
        vm.openEditSheet(samplePresets[0])
        vm.dismissSheet()
        vm.uiState.test {
            val state = awaitItem()
            assertFalse(state.isBottomSheetOpen)
            assertNull(state.editingPreset)
        }
    }

    @Test
    fun savePresetWithNullEditingInsertsNewPreset() = runTest {
        val vm = PresetViewModel(repository, scheduler)
        vm.openAddSheet()
        vm.savePreset(label = "New", durationMinutes = 20)
        coVerify { repository.insert(PresetEntity(label = "New", durationMinutes = 20, isDefault = false)) }
    }

    @Test
    fun savePresetWithEditingUpdatesExistingPreset() = runTest {
        val vm = PresetViewModel(repository, scheduler)
        val existing = samplePresets[0]
        vm.openEditSheet(existing)
        vm.savePreset(label = "Updated", durationMinutes = 7)
        coVerify { repository.update(existing.copy(label = "Updated", durationMinutes = 7)) }
    }

    @Test
    fun deletePresetCallsRepositoryDelete() = runTest {
        val vm = PresetViewModel(repository, scheduler)
        val preset = samplePresets[0]
        vm.deletePreset(preset)
        coVerify { repository.delete(preset) }
    }

    @Test
    fun startTimerCallsSchedulerSchedule() = runTest {
        val vm = PresetViewModel(repository, scheduler)
        val preset = samplePresets[0]
        vm.startTimer(preset)
        verify { scheduler.schedule(preset.durationMinutes) }
    }
}
