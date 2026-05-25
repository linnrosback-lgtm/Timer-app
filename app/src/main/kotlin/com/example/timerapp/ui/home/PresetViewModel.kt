package com.example.timerapp.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.timerapp.alarm.AlarmScheduler
import com.example.timerapp.data.PresetEntity
import com.example.timerapp.data.PresetRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class HomeUiState(
    val presets: List<PresetEntity> = emptyList(),
    val editingPreset: PresetEntity? = null,
    val isBottomSheetOpen: Boolean = false,
    val activePresetId: Long? = null,
    val fireTimeMillis: Long? = null,
    val pausedRemainingMs: Long? = null,
    val remainingSeconds: Long = 0L,
)

class PresetViewModel(
    private val repository: PresetRepository,
    private val scheduler: AlarmScheduler
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    private var tickJob: Job? = null

    init {
        viewModelScope.launch {
            repository.getAll().collect { presets ->
                _uiState.update { it.copy(presets = presets) }
            }
        }
        restoreFromScheduler()
    }

    private fun restoreFromScheduler() {
        val presetId = scheduler.getScheduledPresetId()
        if (presetId == -1L) return
        val fireTime = scheduler.getScheduledFireTime()
        val paused = scheduler.getPausedRemainingMs()
        val now = System.currentTimeMillis()
        when {
            fireTime > now -> {
                _uiState.update {
                    it.copy(
                        activePresetId = presetId,
                        fireTimeMillis = fireTime,
                        pausedRemainingMs = null,
                        remainingSeconds = ((fireTime - now) / 1000L).coerceAtLeast(0L)
                    )
                }
                startTicking(fireTime)
            }
            paused > 0L -> {
                _uiState.update {
                    it.copy(
                        activePresetId = presetId,
                        fireTimeMillis = null,
                        pausedRemainingMs = paused,
                        remainingSeconds = paused / 1000L
                    )
                }
            }
            else -> {
                scheduler.cancel()
            }
        }
    }

    fun openAddSheet() {
        _uiState.update { it.copy(isBottomSheetOpen = true, editingPreset = null) }
    }

    fun openEditSheet(preset: PresetEntity) {
        _uiState.update { it.copy(isBottomSheetOpen = true, editingPreset = preset) }
    }

    fun dismissSheet() {
        _uiState.update { it.copy(isBottomSheetOpen = false, editingPreset = null) }
    }

    fun savePreset(label: String, durationMinutes: Int) {
        val editing = _uiState.value.editingPreset
        viewModelScope.launch {
            if (editing == null) {
                repository.insert(PresetEntity(label = label, durationMinutes = durationMinutes, isDefault = false))
            } else {
                repository.update(editing.copy(label = label, durationMinutes = durationMinutes))
            }
        }
        dismissSheet()
    }

    fun deletePreset(preset: PresetEntity) {
        viewModelScope.launch { repository.delete(preset) }
    }

    fun startTimer(preset: PresetEntity) {
        scheduler.schedule(preset)
        val fireTime = scheduler.getScheduledFireTime()
        _uiState.update {
            it.copy(
                activePresetId = preset.id.toLong(),
                fireTimeMillis = fireTime,
                pausedRemainingMs = null,
                remainingSeconds = ((fireTime - System.currentTimeMillis()) / 1000L).coerceAtLeast(0L)
            )
        }
        startTicking(fireTime)
    }

    fun pauseTimer() {
        val state = _uiState.value
        val fireTime = state.fireTimeMillis ?: return
        val activeId = state.activePresetId ?: return
        val label = state.presets.firstOrNull { it.id.toLong() == activeId }?.label ?: ""
        scheduler.pause(label)
        tickJob?.cancel()
        tickJob = null
        val remainingMs = (fireTime - System.currentTimeMillis()).coerceAtLeast(0L)
        _uiState.update {
            it.copy(
                fireTimeMillis = null,
                pausedRemainingMs = remainingMs,
                remainingSeconds = remainingMs / 1000L
            )
        }
    }

    fun resumeTimer() {
        val state = _uiState.value
        if (state.pausedRemainingMs == null) return
        val activeId = state.activePresetId ?: return
        val label = state.presets.firstOrNull { it.id.toLong() == activeId }?.label ?: ""
        scheduler.resume(label)
        val fireTime = scheduler.getScheduledFireTime()
        _uiState.update {
            it.copy(
                fireTimeMillis = fireTime,
                pausedRemainingMs = null,
                remainingSeconds = ((fireTime - System.currentTimeMillis()) / 1000L).coerceAtLeast(0L)
            )
        }
        startTicking(fireTime)
    }

    fun stopTimer() {
        scheduler.cancel()
        tickJob?.cancel()
        tickJob = null
        _uiState.update {
            it.copy(
                activePresetId = null,
                fireTimeMillis = null,
                pausedRemainingMs = null,
                remainingSeconds = 0L
            )
        }
    }

    private fun startTicking(fireTime: Long) {
        tickJob?.cancel()
        tickJob = viewModelScope.launch {
            while (isActive) {
                val remaining = ((fireTime - System.currentTimeMillis()) / 1000L).coerceAtLeast(0L)
                _uiState.update { it.copy(remainingSeconds = remaining) }
                if (remaining == 0L) {
                    _uiState.update {
                        it.copy(
                            activePresetId = null,
                            fireTimeMillis = null,
                            pausedRemainingMs = null
                        )
                    }
                    break
                }
                delay(1000L)
            }
        }
    }
}
