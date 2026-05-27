package com.example.timerapp.util

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

fun formatMmSs(millis: Long): String {
    val safe = millis.coerceAtLeast(0L)
    val totalSeconds = safe / 1000L
    val minutes = totalSeconds / 60L
    val seconds = totalSeconds % 60L
    return "%02d:%02d".format(minutes, seconds)
}

fun formatHhMmSs(totalSeconds: Int): String {
    val safe = totalSeconds.coerceAtLeast(0)
    val hours = safe / 3600
    val minutes = (safe % 3600) / 60
    val seconds = safe % 60
    return "%02d:%02d:%02d".format(hours, minutes, seconds)
}

fun formatClockTime(millis: Long): String {
    val sdf = SimpleDateFormat("HH:mm", Locale("sv", "SE"))
    return sdf.format(Date(millis))
}

fun formatCountdown(totalSeconds: Long): String {
    val safe = totalSeconds.coerceAtLeast(0L)
    return if (safe >= 3600L) {
        val h = safe / 3600L
        val m = (safe % 3600L) / 60L
        val s = safe % 60L
        "%02d:%02d:%02d".format(h, m, s)
    } else {
        val m = safe / 60L
        val s = safe % 60L
        "%02d:%02d".format(m, s)
    }
}
