package com.example.timerapp.ui.alarm

import android.content.Context
import android.media.Ringtone
import android.media.RingtoneManager
import android.os.*
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.example.timerapp.TimerApplication
import com.example.timerapp.ui.theme.TimerAppTheme

class AlarmActivity : ComponentActivity() {
    private var ringtone: Ringtone? = null
    private var vibrator: Vibrator? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
            WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
            WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
        )

        startSound()
        startVibration()

        val app = application as TimerApplication

        setContent {
            TimerAppTheme {
                AlarmScreen(
                    repository = app.repository,
                    onPresetSelected = { preset ->
                        app.scheduler.schedule(preset)
                        stopAlarm()
                        finish()
                    },
                    onDismiss = {
                        stopAlarm()
                        finish()
                    }
                )
            }
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        // intentionally blocked — user must tap dismiss or a preset
    }

    override fun onDestroy() {
        super.onDestroy()
        stopAlarm()
    }

    private fun startSound() {
        val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
        ringtone = RingtoneManager.getRingtone(this, uri)?.also { it.play() }
    }

    private fun startVibration() {
        val v = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
        vibrator = v
        v.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 500, 500), 0))
    }

    private fun stopAlarm() {
        ringtone?.stop()
        ringtone = null
        vibrator?.cancel()
        vibrator = null
    }
}
