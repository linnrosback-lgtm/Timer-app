package com.example.timerapp.ui.home

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.timerapp.R
import com.example.timerapp.util.formatClockTime
import com.example.timerapp.util.formatCountdown
import kotlin.math.cos
import kotlin.math.sin

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActiveTimerScreen(
    presetName: String,
    presetDurationSeconds: Int,
    remainingSeconds: Long,
    fireTimeMillis: Long?,
    isPaused: Boolean,
    onBack: () -> Unit,
    onPauseResume: () -> Unit,
    onRestart: () -> Unit,
    onStop: () -> Unit,
) {
    BackHandler(onBack = onBack)

    Scaffold(
        topBar = {
            TopAppBar(
                title = {},
                actions = {
                    IconButton(onClick = {}) {
                        Icon(
                            Icons.Default.MoreVert,
                            contentDescription = stringResource(R.string.active_timer_overflow_menu)
                        )
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceEvenly
        ) {
            // Top label chip — preset name
            SuggestionChip(
                onClick = {},
                label = { Text(presetName) }
            )

            // Ring + countdown
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(250.dp)
            ) {
                TimerRing(
                    progress = if (presetDurationSeconds > 0)
                        (remainingSeconds.toFloat() / presetDurationSeconds.toFloat()).coerceIn(0f, 1f)
                    else 0f,
                    modifier = Modifier.fillMaxSize()
                )
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    // Inner duration chip
                    val durationLabel = formatCountdown(presetDurationSeconds.toLong())
                    SuggestionChip(
                        onClick = {},
                        label = { Text(durationLabel) }
                    )
                    // Big countdown
                    Text(
                        text = formatCountdown(remainingSeconds),
                        style = MaterialTheme.typography.displayLarge
                    )
                    // Bell + ring time (only when running)
                    if (fireTimeMillis != null) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                Icons.Default.Notifications,
                                contentDescription = stringResource(R.string.active_timer_rings_at),
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = formatClockTime(fireTimeMillis),
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }
            }

            // FAB row: restart | pause/play (large) | stop
            Row(
                horizontalArrangement = Arrangement.spacedBy(17.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Restart FAB (small)
                FloatingActionButton(
                    onClick = onRestart,
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    modifier = Modifier.size(56.dp)
                ) {
                    Icon(
                        painter = painterResource(android.R.drawable.ic_menu_rotate),
                        contentDescription = stringResource(R.string.active_timer_restart),
                        tint = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
                // Pause / Play FAB (large)
                LargeFloatingActionButton(
                    onClick = onPauseResume,
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer
                ) {
                    Icon(
                        painter = painterResource(
                            if (isPaused) android.R.drawable.ic_media_play
                            else android.R.drawable.ic_media_pause
                        ),
                        contentDescription = stringResource(
                            if (isPaused) R.string.active_timer_play
                            else R.string.active_timer_pause
                        ),
                        tint = MaterialTheme.colorScheme.onTertiaryContainer,
                        modifier = Modifier.size(28.dp)
                    )
                }
                // Stop FAB (small)
                FloatingActionButton(
                    onClick = onStop,
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    modifier = Modifier.size(56.dp)
                ) {
                    Icon(
                        painter = painterResource(android.R.drawable.ic_menu_close_clear_cancel),
                        contentDescription = stringResource(R.string.active_timer_stop),
                        tint = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }
        }
    }
}

@Composable
private fun TimerRing(progress: Float, modifier: Modifier = Modifier) {
    val trackColor = MaterialTheme.colorScheme.surfaceVariant
    val arcColor = MaterialTheme.colorScheme.tertiary
    val dotColor = MaterialTheme.colorScheme.tertiary

    Canvas(modifier = modifier) {
        val stroke = Stroke(width = 8.dp.toPx(), cap = StrokeCap.Round)
        val inset = stroke.width / 2f
        val arcSize = Size(size.width - stroke.width, size.height - stroke.width)
        val topLeft = Offset(inset, inset)

        // Full track
        drawArc(
            color = trackColor,
            startAngle = -90f,
            sweepAngle = 360f,
            useCenter = false,
            topLeft = topLeft,
            size = arcSize,
            style = stroke
        )
        // Remaining arc (depletes clockwise)
        if (progress > 0f) {
            drawArc(
                color = arcColor,
                startAngle = -90f,
                sweepAngle = 360f * progress,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = stroke
            )
            // Leading dot
            val sweepRad = Math.toRadians((-90f + 360f * progress).toDouble())
            val cx = size.width / 2f + (arcSize.width / 2f) * cos(sweepRad).toFloat()
            val cy = size.height / 2f + (arcSize.height / 2f) * sin(sweepRad).toFloat()
            drawCircle(color = dotColor, radius = stroke.width / 2f, center = Offset(cx, cy))
        }
    }
}
