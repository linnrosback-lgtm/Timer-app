package com.example.timerapp.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val scheduler = AlarmScheduler(context)
        val fireTime = scheduler.getScheduledFireTime()
        val now = System.currentTimeMillis()
        if (fireTime > now) {
            val remainingMs = fireTime - now
            val remainingMinutes = (remainingMs / 60_000L).toInt().coerceAtLeast(1)
            scheduler.schedule(remainingMinutes)
        }
    }
}
