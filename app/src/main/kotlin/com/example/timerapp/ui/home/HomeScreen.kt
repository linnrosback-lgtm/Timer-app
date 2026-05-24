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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(viewModel: PresetViewModel) {
    val state by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    var pendingSnackbar by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(pendingSnackbar) {
        pendingSnackbar?.let {
            snackbarHostState.showSnackbar(it)
            pendingSnackbar = null
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
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
            items(state.presets, key = { it.id }) { preset ->
                PresetItem(
                    preset = preset,
                    onStart = {
                        viewModel.startTimer(preset)
                        pendingSnackbar = "Timer set for ${preset.durationMinutes} min"
                    },
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
private fun PresetItem(
    preset: PresetEntity,
    onStart: () -> Unit,
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
                Text(
                    text = "${preset.durationMinutes} min",
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
            Button(onClick = onStart) {
                Text("Start")
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
