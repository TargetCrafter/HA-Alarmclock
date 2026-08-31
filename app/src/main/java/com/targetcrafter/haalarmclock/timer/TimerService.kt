package com.targetcrafter.haalarmclock.timer

import android.app.Notification
import android.app.PendingIntent
import android.content.Intent
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Build
import android.os.CombinedVibration
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.NotificationCompat
import androidx.core.content.getSystemService
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.targetcrafter.haalarmclock.HaAlarmClockApp
import com.targetcrafter.haalarmclock.NOTIFICATION_CHANNEL_TIMER
import com.targetcrafter.haalarmclock.R
import com.targetcrafter.haalarmclock.data.Timer
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val MAX_RING_DURATION_MILLIS = 5 * 60 * 1000L

/** Plays/vibrates and shows the "ringing" notification once a timer hits zero — the counterpart
 * to [com.targetcrafter.haalarmclock.alarm.AlarmRingService], simplified (no fade-in, no snooze).
 * Only one timer rings at a time: a second one finishing while this is already ringing replaces it,
 * same tradeoff AlarmRingService makes for concurrent alarms.
 */
class TimerService : LifecycleService() {

    private var mediaPlayer: MediaPlayer? = null
    private var currentTimerId: Long? = null
    private var ringJob: Job? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        val timerId = intent?.getLongExtra(EXTRA_TIMER_ID, -1L) ?: -1L
        if (timerId >= 0) {
            when (intent?.action) {
                ACTION_FINISH -> handleFinish(timerId)
                ACTION_DISMISS -> handleDismiss(timerId)
            }
        }
        return START_NOT_STICKY
    }

    private fun handleFinish(timerId: Long) {
        ringJob?.cancel()
        ringJob = lifecycleScope.launch {
            stopRinging()
            val timer = HaAlarmClockApp.from(this@TimerService).timerRepository.getById(timerId) ?: return@launch
            currentTimerId = timerId
            startForeground(timerNotificationId(timerId), buildNotification(timer))
            startRinging()
            startVibration()
            delay(MAX_RING_DURATION_MILLIS)
            handleDismiss(timerId)
        }
    }

    /** Stops the ringing (whether triggered by the notification's Dismiss action, the UI's
     * Dismiss button, or the ring-duration timeout) and deletes the now-finished timer — a
     * finished timer has served its purpose, unlike an alarm, which stays around to fire again.
     */
    private fun handleDismiss(timerId: Long) {
        ringJob?.cancel()
        ringJob = null
        stopRinging()
        currentTimerId = null
        lifecycleScope.launch { HaAlarmClockApp.from(this@TimerService).timerRepository.cancel(timerId) }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
        stopSelf()
    }

    private fun startRinging() {
        val uri = RingtoneManager.getActualDefaultRingtoneUri(this, RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getValidRingtoneUri(this)
        mediaPlayer = MediaPlayer().apply {
            setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build(),
            )
            isLooping = true
            try {
                setDataSource(this@TimerService, uri)
                prepare()
                start()
            } catch (_: Exception) {
                release()
                mediaPlayer = null
            }
        }
    }

    private fun startVibration() {
        val pattern = longArrayOf(0, 500, 300)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = getSystemService<VibratorManager>() ?: return
            vibratorManager.vibrate(CombinedVibration.createParallel(VibrationEffect.createWaveform(pattern, 0)))
        } else {
            @Suppress("DEPRECATION")
            val vibrator = getSystemService<Vibrator>() ?: return
            vibrator.vibrate(VibrationEffect.createWaveform(pattern, 0))
        }
    }

    private fun stopRinging() {
        mediaPlayer?.apply {
            if (isPlaying) stop()
            release()
        }
        mediaPlayer = null
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            getSystemService<VibratorManager>()?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService<Vibrator>()
        }
        vibrator?.cancel()
    }

    private fun buildNotification(timer: Timer): Notification {
        val label = timer.label.ifBlank { getString(R.string.timer_default_label) }
        return NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_TIMER)
            .setContentTitle(getString(R.string.timer_finished_title, label))
            .setSmallIcon(R.drawable.ic_notification)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setOngoing(true)
            .addAction(0, getString(R.string.dismiss), actionPendingIntent(ACTION_DISMISS, timer.id))
            .build()
    }

    private fun actionPendingIntent(action: String, timerId: Long): PendingIntent {
        val intent = Intent(this, TimerService::class.java).setAction(action).putExtra(EXTRA_TIMER_ID, timerId)
        return PendingIntent.getService(
            this,
            timerNotificationId(timerId),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    override fun onDestroy() {
        stopRinging()
        super.onDestroy()
    }

    companion object {
        const val ACTION_FINISH = "com.targetcrafter.haalarmclock.action.TIMER_FINISH"
        const val ACTION_DISMISS = "com.targetcrafter.haalarmclock.action.TIMER_DISMISS"
    }
}
