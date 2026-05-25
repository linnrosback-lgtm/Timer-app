package com.example.timerapp.ui.home

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.timerapp.data.PresetEntity
import com.example.timerapp.util.formatMmSs
import androidx.compose.foundation.layout.Arrangement

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: PresetViewModel,
    showFullScreenBanner: Boolean = false,
    onGrantFullScreen: () -> Unit = {}
) {
    val state by viewModel.uiState.collectAsState()

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(onClick = { viewModel.openAddSheet() }) {
                Icon(Icons.Default.Add, contentDescription = "Add preset")
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item { Spacer(Modifier.height(8.dp)) }
            if (showFullScreenBanner) {
                item { FullScreenPermissionBanner(onGrantFullScreen) }
            }
            items(state.presets, key = { it.id }) { preset ->
                val isActive = state.activePresetId == preset.id.toLong()
                val isPaused = isActive && state.pausedRemainingMs != null
                val isRunning = isActive && state.fireTimeMillis != null
                val anyTimerOwned = state.activePresetId != null
                PresetItem(
                    preset = preset,
                    isRunning = isRunning,
                    isPaused = isPaused,
                    startEnabled = !anyTimerOwned,
                    remainingSeconds = if (isActive) state.remainingSeconds else 0L,
                    onStart = { viewModel.startTimer(preset) },
                    onPause = { viewModel.pauseTimer() },
                    onResume = { viewModel.resumeTimer() },
                    onStop = { viewModel.stopTimer() },
                    onEdit = { viewModel.openEditSheet(preset) },
                    onDelete = { viewModel.deletePreset(preset) }
                )
            }
            item { Spacer(Modifier.height(80.dp)) }
        }
    }

    if (state.isBottomSheetOpen) {
        PresetBottomSheet(
            editing = state.editingPreset,
            onDismiss = { viewModel.dismissSheet() },
            onSave = { label, minutes -> viewModel.savePreset(label, minutes) }
        )
    }
}

@Composable
private fun FullScreenPermissionBanner(onGrant: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "Grant \"pop-up\" permission so the alarm screen appears automatically.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(8.dp))
            FilledTonalButton(onClick = onGrant) { Text("Grant") }
        }
    }
}

@Composable
private fun PresetItem(
    preset: PresetEntity,
    isRunning: Boolean,
    isPaused: Boolean,
    startEnabled: Boolean,
    remainingSeconds: Long,
    onStart: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onStop: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = preset.label, style = MaterialTheme.typography.titleMedium)
                val subtitle = when {
                    isRunning -> formatMmSs(remainingSeconds * 1000L)
                    isPaused -> "Paused — ${formatMmSs(remainingSeconds * 1000L)}"
                    else -> "${preset.durationMinutes} min"
                }
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = onEdit) {
                Icon(Icons.Default.Edit, contentDescription = "Edit")
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = "Delete")
            }
            Spacer(Modifier.width(4.dp))
            when {
                isRunning -> {
                    FilledTonalButton(onClick = onPause) { Text("Pause") }
                    Spacer(Modifier.width(4.dp))
                    Button(
                        onClick = onStop,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error,
                            contentColor = MaterialTheme.colorScheme.onError
                        )
                    ) { Text("Stop") }
                }
                isPaused -> {
                    FilledTonalButton(onClick = onResume) { Text("Resume") }
                    Spacer(Modifier.width(4.dp))
                    Button(
                        onClick = onStop,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error,
                            contentColor = MaterialTheme.colorScheme.onError
                        )
                    ) { Text("Stop") }
                }
                else -> {
                    Button(onClick = onStart, enabled = startEnabled) { Text("Start") }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PresetBottomSheet(
    editing: PresetEntity?,
    onDismiss: () -> Unit,
    onSave: (String, Int) -> Unit
) {
    var label by remember(editing) { mutableStateOf(editing?.label ?: "") }
    var durationText by remember(editing) { mutableStateOf(editing?.durationMinutes?.toString() ?: "") }
    val isValid = label.isNotBlank() && durationText.toIntOrNull()?.let { it > 0 } == true

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = if (editing == null) "Add preset" else "Edit preset",
                style = MaterialTheme.typography.headlineSmall
            )
            OutlinedTextField(
                value = label,
                onValueChange = { label = it },
                label = { Text("Label") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            OutlinedTextField(
                value = durationText,
                onValueChange = { durationText = it },
                label = { Text("Duration (minutes)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
            )
            Button(
                onClick = { onSave(label.trim(), durationText.toIntOrNull() ?: 0) },
                modifier = Modifier.fillMaxWidth(),
                enabled = isValid
            ) {
                Text("Save")
            }
        }
    }
}
