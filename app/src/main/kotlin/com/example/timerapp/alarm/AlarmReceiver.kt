package com.example.timerapp.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.timerapp.ui.alarm.AlarmActivity

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val alarmIntent = Intent(context, AlarmActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_USER_ACTION)
        }
        context.startActivity(alarmIntent)
    }
}
