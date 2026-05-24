package com.example.timerapp.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.timerapp.alarm.AlarmScheduler
import com.example.timerapp.data.PresetEntity
import com.example.timerapp.data.PresetRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class HomeUiState(
    val presets: List<PresetEntity> = emptyList(),
    val editingPreset: PresetEntity? = null,
    val isBottomSheetOpen: Boolean = false
)

class PresetViewModel(
    private val repository: PresetRepository,
    private val scheduler: AlarmScheduler
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            repository.getAll().collect { presets ->
                _uiState.update { it.copy(presets = presets) }
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
        scheduler.schedule(preset.durationMinutes)
    }
}
