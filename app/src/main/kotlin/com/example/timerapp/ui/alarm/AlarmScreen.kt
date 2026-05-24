package com.example.timerapp.ui.alarm

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.timerapp.data.PresetEntity
import com.example.timerapp.data.PresetRepository

@Composable
fun AlarmScreen(
    repository: PresetRepository,
    onPresetSelected: (PresetEntity) -> Unit,
    onDismiss: () -> Unit
) {
    val presets by repository.getAll().collectAsState(initial = emptyList())

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(48.dp))
        Text(text = "Time's up!", style = MaterialTheme.typography.displayMedium)
        Spacer(Modifier.height(32.dp))
        Text(text = "Snooze for:", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(16.dp))
        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(presets, key = { it.id }) { preset ->
                OutlinedButton(
                    onClick = { onPresetSelected(preset) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("${preset.label} — ${preset.durationMinutes} min")
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        Button(
            onClick = onDismiss,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
        ) {
            Text("Dismiss")
        }
        Spacer(Modifier.height(24.dp))
    }
}
