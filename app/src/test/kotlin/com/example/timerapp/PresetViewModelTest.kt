package com.example.timerapp

import app.cash.turbine.test
import com.example.timerapp.alarm.AlarmScheduler
import com.example.timerapp.data.PresetEntity
import com.example.timerapp.data.PresetRepository
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

    private val tea = PresetEntity(id = 1, label = "Tea", durationMinutes = 3, isDefault = true)
    private val coffee = PresetEntity(id = 2, label = "Coffee", durationMinutes = 5, isDefault = true)
    private val samplePresets = listOf(tea, coffee)

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        every { repository.getAll() } returns flowOf(samplePresets)
        every { scheduler.getScheduledFireTime() } returns -1L
        every { scheduler.getScheduledPresetId() } returns -1L
        every { scheduler.getPausedRemainingMs() } returns -1L
    }

    @After
    fun teardown() { Dispatchers.resetMain() }

    // ---- existing sheet/CRUD behavior preserved ----

    @Test
    fun initialStateHasPresetsFromRepository() = runTest {
        val vm = PresetViewModel(repository, scheduler)
        vm.uiState.test {
            val state = awaitItem()
            assertEquals(samplePresets, state.presets)
            assertFalse(state.isBottomSheetOpen)
            assertNull(state.editingPreset)
            assertNull(state.activePresetId)
            assertNull(state.fireTimeMillis)
            assertNull(state.pausedRemainingMs)
            assertEquals(0L, state.remainingSeconds)
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
        vm.openEditSheet(tea)
        vm.uiState.test {
            val state = awaitItem()
            assertTrue(state.isBottomSheetOpen)
            assertEquals(tea, state.editingPreset)
        }
    }

    @Test
    fun dismissSheetClosesBottomSheetAndClearsEditing() = runTest {
        val vm = PresetViewModel(repository, scheduler)
        vm.openEditSheet(tea)
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
        vm.openEditSheet(tea)
        vm.savePreset(label = "Updated", durationMinutes = 7)
        coVerify { repository.update(tea.copy(label = "Updated", durationMinutes = 7)) }
    }

    @Test
    fun deletePresetCallsRepositoryDelete() = runTest {
        val vm = PresetViewModel(repository, scheduler)
        vm.deletePreset(tea)
        coVerify { repository.delete(tea) }
    }

    // ---- running-timer behavior ----

    @Test
    fun startTimerSchedulesAndSetsRunningState() = runTest {
        val now = System.currentTimeMillis()
        val fakeFireTime = now + 180_000L
        every { scheduler.getScheduledFireTime() } returns fakeFireTime
        val vm = PresetViewModel(repository, scheduler)
        vm.startTimer(tea)
        verify { scheduler.schedule(tea) }
        vm.uiState.test {
            val state = awaitItem()
            assertEquals(1L, state.activePresetId)
            assertNotNull(state.fireTimeMillis)
            assertNull(state.pausedRemainingMs)
            assertTrue(state.remainingSeconds > 0L)
        }
    }

    @Test
    fun pauseFreezesRemainingClearsFireTimeAndCallsScheduler() = runTest {
        val now = System.currentTimeMillis()
        val fakeFireTime = now + 120_000L
        every { scheduler.getScheduledFireTime() } returns fakeFireTime
        val vm = PresetViewModel(repository, scheduler)
        vm.startTimer(tea)
        vm.pauseTimer()
        verify { scheduler.pause("Tea") }
        vm.uiState.test {
            val state = awaitItem()
            assertEquals(1L, state.activePresetId)
            assertNull(state.fireTimeMillis)
            assertNotNull(state.pausedRemainingMs)
            assertTrue(state.pausedRemainingMs!! > 0L)
        }
    }

    @Test
    fun resumeAfterPauseResumesTickingAndCallsScheduler() = runTest {
        val now = System.currentTimeMillis()
        val fakeFireTime = now + 60_000L
        every { scheduler.getScheduledFireTime() } returns fakeFireTime
        val vm = PresetViewModel(repository, scheduler)
        vm.startTimer(tea)
        vm.pauseTimer()
        vm.resumeTimer()
        verify { scheduler.resume("Tea") }
        vm.uiState.test {
            val state = awaitItem()
            assertEquals(1L, state.activePresetId)
            assertNotNull(state.fireTimeMillis)
            assertNull(state.pausedRemainingMs)
        }
    }

    @Test
    fun stopWhileRunningClearsAllActiveFields() = runTest {
        val now = System.currentTimeMillis()
        every { scheduler.getScheduledFireTime() } returns now + 60_000L
        val vm = PresetViewModel(repository, scheduler)
        vm.startTimer(tea)
        vm.stopTimer()
        verify { scheduler.cancel() }
        vm.uiState.test {
            val state = awaitItem()
            assertNull(state.activePresetId)
            assertNull(state.fireTimeMillis)
            assertNull(state.pausedRemainingMs)
            assertEquals(0L, state.remainingSeconds)
        }
    }

    @Test
    fun stopWhilePausedClearsAllActiveFields() = runTest {
        val now = System.currentTimeMillis()
        every { scheduler.getScheduledFireTime() } returns now + 60_000L
        val vm = PresetViewModel(repository, scheduler)
        vm.startTimer(tea)
        vm.pauseTimer()
        vm.stopTimer()
        verify { scheduler.cancel() }
        vm.uiState.test {
            val state = awaitItem()
            assertNull(state.activePresetId)
            assertNull(state.fireTimeMillis)
            assertNull(state.pausedRemainingMs)
        }
    }

    @Test
    fun initRestoresRunningStateWhenFireTimeInFuture() = runTest {
        val now = System.currentTimeMillis()
        every { scheduler.getScheduledFireTime() } returns now + 90_000L
        every { scheduler.getScheduledPresetId() } returns 1L
        every { scheduler.getPausedRemainingMs() } returns -1L
        val vm = PresetViewModel(repository, scheduler)
        vm.uiState.test {
            val state = awaitItem()
            assertEquals(1L, state.activePresetId)
            assertNotNull(state.fireTimeMillis)
            assertNull(state.pausedRemainingMs)
            assertTrue(state.remainingSeconds > 0L)
        }
    }

    @Test
    fun initRestoresPausedStateWhenPausedRemainingPresent() = runTest {
        every { scheduler.getScheduledFireTime() } returns -1L
        every { scheduler.getScheduledPresetId() } returns 1L
        every { scheduler.getPausedRemainingMs() } returns 45_000L
        val vm = PresetViewModel(repository, scheduler)
        vm.uiState.test {
            val state = awaitItem()
            assertEquals(1L, state.activePresetId)
            assertNull(state.fireTimeMillis)
            assertEquals(45_000L, state.pausedRemainingMs)
            assertEquals(45L, state.remainingSeconds)
        }
    }

    @Test
    fun initClearsExpiredSchedulerStateAndStartsIdle() = runTest {
        val past = System.currentTimeMillis() - 10_000L
        every { scheduler.getScheduledFireTime() } returns past
        every { scheduler.getScheduledPresetId() } returns 1L
        every { scheduler.getPausedRemainingMs() } returns -1L
        val vm = PresetViewModel(repository, scheduler)
        verify { scheduler.cancel() }
        vm.uiState.test {
            val state = awaitItem()
            assertNull(state.activePresetId)
        }
    }
}
