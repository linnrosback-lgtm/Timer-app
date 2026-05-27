package com.example.timerapp.ui.home

import android.app.Activity
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.timerapp.R
import com.example.timerapp.data.PresetEntity
import com.example.timerapp.util.formatHhMmSs
import com.example.timerapp.util.formatMmSs
import androidx.compose.foundation.layout.Arrangement

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: PresetViewModel,
    showFullScreenBanner: Boolean = false,
    onGrantFullScreen: () -> Unit = {},
    onViewActive: () -> Unit = {}
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
                    onDelete = { viewModel.deletePreset(preset) },
                    onViewActive = onViewActive
                )
            }
            item { Spacer(Modifier.height(80.dp)) }
        }
    }

    if (state.isBottomSheetOpen) {
        PresetBottomSheet(
            editing = state.editingPreset,
            onDismiss = { viewModel.dismissSheet() },
            onSave = { label, seconds, uri -> viewModel.savePreset(label, seconds, uri) }
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
    onDelete: () -> Unit,
    onViewActive: () -> Unit = {}
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (isRunning || isPaused)
                    Modifier.clickable(onClick = onViewActive)
                else Modifier
            )
    ) {
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
                    else -> formatHhMmSs(preset.durationSeconds)
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
    onSave: (String, Int, String?) -> Unit
) {
    var label by remember(editing) { mutableStateOf(editing?.label ?: "") }
    var durationSeconds by remember(editing) { mutableStateOf(editing?.durationSeconds ?: 0) }
    var ringtoneUri by remember(editing) { mutableStateOf(editing?.ringtoneUri) }
    val isValid = label.isNotBlank() && durationSeconds > 0

    val context = LocalContext.current
    val defaultRingtoneLabel = stringResource(R.string.ringtone_default)
    val ringtoneLabel = remember(ringtoneUri, defaultRingtoneLabel) {
        ringtoneUri?.let {
            runCatching { RingtoneManager.getRingtone(context, Uri.parse(it))?.getTitle(context) }.getOrNull()
        } ?: defaultRingtoneLabel
    }
    val ringtonePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            @Suppress("DEPRECATION")
            val picked: Uri? = result.data?.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
            ringtoneUri = picked?.toString()
        }
    }
    fun launchRingtonePicker() {
        val intent = Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
            putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALARM)
            putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, context.getString(R.string.ringtone))
            putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
            putExtra(
                RingtoneManager.EXTRA_RINGTONE_DEFAULT_URI,
                RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            )
            putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false)
            putExtra(
                RingtoneManager.EXTRA_RINGTONE_EXISTING_URI,
                ringtoneUri?.let { Uri.parse(it) }
            )
        }
        ringtonePicker.launch(intent)
    }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .navigationBarsPadding()
                .imePadding()
                .padding(bottom = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Spacer(Modifier.height(24.dp))
            DurationWheelPicker(
                totalSeconds = durationSeconds,
                onValueChange = { durationSeconds = it }
            )
            TextField(
                value = label,
                onValueChange = { label = it },
                label = { Text(stringResource(R.string.preset_label_hint)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent
                )
            )
            RingtoneRow(value = ringtoneLabel, onClick = { launchRingtonePicker() })
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = { onSave(label.trim(), durationSeconds, ringtoneUri) },
                enabled = isValid,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                    contentColor = MaterialTheme.colorScheme.onTertiaryContainer
                )
            ) {
                Text(stringResource(if (editing == null) R.string.action_add else R.string.action_save))
            }
        }
    }
}

@Composable
private fun RingtoneRow(value: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.ringtone),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = value,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
