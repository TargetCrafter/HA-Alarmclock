package com.targetcrafter.haalarmclock.alarm

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
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
import com.targetcrafter.haalarmclock.NOTIFICATION_CHANNEL_ALARM
import com.targetcrafter.haalarmclock.R
import com.targetcrafter.haalarmclock.data.Alarm
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val MAX_RING_DURATION_MILLIS = 10 * 60 * 1000L
private const val NOTIFICATION_ID = 42
private const val FADE_IN_DURATION_MILLIS = 45_000L
private const val FADE_IN_STEP_MILLIS = 500L
private const val FADE_IN_START_VOLUME = 0.05f

class AlarmRingService : LifecycleService() {

    private var mediaPlayer: MediaPlayer? = null
    private var currentAlarm: Alarm? = null

    // Tracks the in-flight "ring, then auto-dismiss after a timeout" coroutine so a second alarm
    // firing while the first is still ringing replaces it cleanly instead of leaking a MediaPlayer
    // or letting the first alarm's delayed auto-dismiss cut off the second alarm's ringing.
    private var ringJob: Job? = null
    private var fadeJob: Job? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        when (intent?.action) {
            ACTION_START -> handleStart(intent.getLongExtra(EXTRA_ALARM_ID, -1L))
            ACTION_SNOOZE -> handleSnooze()
            ACTION_DISMISS -> handleDismiss()
        }
        return START_NOT_STICKY
    }

    private fun handleStart(alarmId: Long) {
        if (alarmId < 0) return
        ringJob?.cancel()
        ringJob = lifecycleScope.launch {
            stopRinging()
            val alarm = HaAlarmClockApp.from(this@AlarmRingService).repository.getById(alarmId) ?: return@launch
            currentAlarm = alarm
            RingingState.setRinging(alarm)
            startForeground(NOTIFICATION_ID, buildNotification(alarm))
            startRinging(alarm)
            startActivity(
                Intent(this@AlarmRingService, RingingActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
            delay(MAX_RING_DURATION_MILLIS)
            handleDismiss()
        }
    }

    private fun handleSnooze() {
        val alarm = currentAlarm ?: return
        val app = HaAlarmClockApp.from(this)
        val snoozeUntil = System.currentTimeMillis() + alarm.snoozeMinutes * 60_000L
        app.scheduler.scheduleSnooze(alarm.id, snoozeUntil)
        // Persist the snooze mark before stopping the service (which cancels lifecycleScope) so the
        // write isn't racing self-destruction.
        lifecycleScope.launch {
            app.repository.markSnoozed(alarm.id, snoozeUntil)
            stopRingingAndFinish()
        }
    }

    private fun handleDismiss() {
        stopRingingAndFinish()
    }

    private fun stopRingingAndFinish() {
        ringJob?.cancel()
        ringJob = null
        stopRinging()
        RingingState.setRinging(null)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
        stopSelf()
    }

    private fun startRinging(alarm: Alarm) {
        val uri: Uri = alarm.ringtoneUri?.let(Uri::parse)
            ?: RingtoneManager.getActualDefaultRingtoneUri(this, RingtoneManager.TYPE_ALARM)
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
                setDataSource(this@AlarmRingService, uri)
                prepare()
                if (alarm.fadeInEnabled) setVolume(FADE_IN_START_VOLUME, FADE_IN_START_VOLUME)
                start()
            } catch (_: Exception) {
                release()
                mediaPlayer = null
            }
        }
        if (alarm.fadeInEnabled && mediaPlayer != null) startFadeIn()
        if (alarm.vibrate) startVibration()
    }

    /** Ramps [mediaPlayer]'s volume from [FADE_IN_START_VOLUME] to full over [FADE_IN_DURATION_MILLIS]. */
    private fun startFadeIn() {
        fadeJob?.cancel()
        fadeJob = lifecycleScope.launch {
            val startedAt = System.currentTimeMillis()
            while (true) {
                val elapsed = System.currentTimeMillis() - startedAt
                if (elapsed >= FADE_IN_DURATION_MILLIS) {
                    mediaPlayer?.setVolume(1f, 1f)
                    break
                }
                val volume = FADE_IN_START_VOLUME + (1f - FADE_IN_START_VOLUME) * (elapsed.toFloat() / FADE_IN_DURATION_MILLIS)
                mediaPlayer?.setVolume(volume, volume)
                delay(FADE_IN_STEP_MILLIS)
            }
        }
    }

    private fun startVibration() {
        val pattern = longArrayOf(0, 800, 600)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = getSystemService<VibratorManager>() ?: return
            vibratorManager.vibrate(
                CombinedVibration.createParallel(VibrationEffect.createWaveform(pattern, 0)),
            )
        } else {
            @Suppress("DEPRECATION")
            val vibrator = getSystemService<Vibrator>() ?: return
            vibrator.vibrate(VibrationEffect.createWaveform(pattern, 0))
        }
    }

    private fun stopRinging() {
        fadeJob?.cancel()
        fadeJob = null
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

    private fun buildNotification(alarm: Alarm): Notification {
        val fullScreenIntent = PendingIntent.getActivity(
            this,
            alarm.id.toInt(),
            Intent(this, RingingActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val label = alarm.label.ifBlank { getString(R.string.app_name) }
        return NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ALARM)
            .setContentTitle(label)
            .setContentText(String.format("%02d:%02d", alarm.hour, alarm.minute))
            .setSmallIcon(R.drawable.ic_notification)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setOngoing(true)
            .setFullScreenIntent(fullScreenIntent, true)
            .addAction(0, getString(R.string.snooze), actionPendingIntent(ACTION_SNOOZE))
            .addAction(0, getString(R.string.dismiss), actionPendingIntent(ACTION_DISMISS))
            .build()
    }

    private fun actionPendingIntent(action: String): PendingIntent {
        val intent = Intent(this, AlarmRingService::class.java).setAction(action)
        return PendingIntent.getService(
            this,
            action.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    override fun onDestroy() {
        stopRinging()
        RingingState.setRinging(null)
        super.onDestroy()
    }

    companion object {
        const val ACTION_START = "com.targetcrafter.haalarmclock.action.START"

        fun startIntent(context: Context, alarmId: Long): Intent =
            Intent(context, AlarmRingService::class.java)
                .setAction(ACTION_START)
                .putExtra(EXTRA_ALARM_ID, alarmId)
    }
}
