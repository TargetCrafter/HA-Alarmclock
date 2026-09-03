package com.targetcrafter.haalarmclock.alarm

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.MediaPlayer
import android.os.Build
import android.os.CombinedVibration
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
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

private const val TAG = "AlarmRingService"
private const val MAX_RING_DURATION_MILLIS = 10 * 60 * 1000L
private const val NOTIFICATION_ID = 42
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

        // FIRST, synchronously, before anything that can block. The OS gives a service five
        // seconds from the startForegroundService() call that got us here to reach
        // startForeground(), and kills the app instead if it doesn't. This used to run inside the
        // coroutine below, *after* loading the alarm out of Room — fine when the app had been open
        // recently and everything was warm, but that read comes off cold storage when the alarm is
        // what woke a process Android killed hours earlier, which is exactly the "set it at
        // bedtime, no sound in the morning" case. Posting the notification here also gets its
        // full-screen intent in front of the user sooner. currentAlarm is null on a fresh start
        // and set for a snooze/dismiss arriving mid-ring, so this reposts the same notification
        // rather than flickering a placeholder over it.
        startForeground(NOTIFICATION_ID, buildNotification(currentAlarm))

        when (intent?.action) {
            ACTION_START -> handleStart(
                intent.getLongExtra(EXTRA_ALARM_ID, -1L),
                intent.getBooleanExtra(EXTRA_IS_SNOOZE, false),
            )
            ACTION_SNOOZE -> handleSnooze()
            ACTION_DISMISS -> handleDismiss()
            // Nothing to do, but we're a foreground service now and mustn't just sit there.
            else -> stopRingingAndFinish()
        }
        return START_NOT_STICKY
    }

    private fun handleStart(alarmId: Long, isSnooze: Boolean) {
        if (alarmId < 0) {
            Log.w(TAG, "handleStart: missing alarm id, nothing to ring")
            stopRingingAndFinish()
            return
        }
        ringJob?.cancel()
        ringJob = lifecycleScope.launch {
            stopRinging()
            val alarm = HaAlarmClockApp.from(this@AlarmRingService).repository.getById(alarmId)
            if (alarm == null) {
                Log.w(TAG, "handleStart: alarm $alarmId no longer exists, not ringing")
                stopRingingAndFinish()
                return@launch
            }
            // A disabled alarm still rings for a snooze trigger: the snooze was armed while it was
            // enabled, and turning the alarm off afterwards shouldn't strand a snooze already due.
            if (!isSnooze && !alarm.enabled) {
                Log.w(TAG, "handleStart: alarm $alarmId is disabled, not ringing")
                stopRingingAndFinish()
                return@launch
            }
            currentAlarm = alarm
            RingingState.setRinging(alarm)
            // Replace the placeholder from onStartCommand now that we know which alarm this is.
            // The notification's fullScreenIntent is the primary, OS-sanctioned way to bring up
            // RingingActivity over the lock screen — a plain startActivity() from a background
            // service is subject to Android 10+'s background-activity-launch restrictions and
            // isn't reliably honored. But Android 14+ can itself refuse to honor fullScreenIntent
            // (see launchRingingActivityIfFullScreenIntentUnusable), so that path attempts the
            // direct launch as a fallback when the OS has told us it won't work.
            NotificationManagerCompat.from(this@AlarmRingService).notify(NOTIFICATION_ID, buildNotification(alarm))
            launchRingingActivityIfFullScreenIntentUnusable()
            startRinging(alarm)
            delay(MAX_RING_DURATION_MILLIS)
            handleDismiss()
        }
    }

    private fun handleSnooze() {
        val alarm = currentAlarm ?: run {
            // Nothing is ringing, so there's nothing to snooze — but this service is foreground now.
            stopRingingAndFinish()
            return
        }
        val app = HaAlarmClockApp.from(this)
        val snoozeMinutes = alarm.effectiveSnoozeMinutes(app.appDefaultsStore.defaults.value)
        val snoozeUntil = System.currentTimeMillis() + snoozeMinutes * 60_000L
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
        // Logged on every alarm: an alarm-stream volume of zero silences the alarm no matter how
        // correctly everything else here runs, and it is invisible from inside the app otherwise.
        val volume = alarmVolume(this)
        if (volume.isSilent) {
            Log.e(TAG, "startRinging: alarm ${alarm.id} is ringing but the device alarm volume is 0 — it will be inaudible")
        } else {
            Log.i(TAG, "startRinging: alarm ${alarm.id}, alarm volume ${volume.current}/${volume.max}")
        }

        val candidates = candidateRingtoneUris(this, alarm.ringtoneUri)
        if (candidates.isEmpty()) {
            Log.e(TAG, "startRinging: no ringtone URI available at all (not even a system default)")
        }
        for ((index, uri) in candidates.withIndex()) {
            val player = MediaPlayer()
            player.setAudioAttributes(alarmAudioAttributes())
            player.isLooping = true
            try {
                player.setDataSource(this, uri)
                player.prepare()
                if (alarm.fadeInEnabled) player.setVolume(FADE_IN_START_VOLUME, FADE_IN_START_VOLUME)
                player.start()
                mediaPlayer = player
                break
            } catch (e: Exception) {
                Log.e(TAG, "startRinging: candidate $index/$uri failed to play, trying next", e)
                player.release()
            }
        }
        if (mediaPlayer == null) {
            Log.e(TAG, "startRinging: every ringtone candidate failed — alarm ${alarm.id} will ring silently, vibration only")
        }
        if (alarm.fadeInEnabled && mediaPlayer != null) {
            val defaults = HaAlarmClockApp.from(this).appDefaultsStore.defaults.value
            startFadeIn(alarm.effectiveFadeInSeconds(defaults) * 1_000L)
        }
        if (alarm.vibrate) startVibration()
    }

    /** Android 14+ can silently demote a notification's fullScreenIntent to a plain heads-up
     * notification instead of launching the activity, e.g. when the app hasn't been granted the
     * "Full screen intent" special app access. [buildNotification]'s fullScreenIntent is still the
     * primary mechanism (see its comment), but when the OS has told us up front it won't honor
     * one, fall back to a direct launch — attempted from the same background-start exemption
     * window this service is already running in as a direct result of the alarm broadcast. */
    private fun launchRingingActivityIfFullScreenIntentUnusable() {
        if (NotificationManagerCompat.from(this).canUseFullScreenIntent()) return
        Log.w(TAG, "launchRingingActivityIfFullScreenIntentUnusable: full-screen intent not permitted, launching RingingActivity directly")
        try {
            startActivity(Intent(this, RingingActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: Exception) {
            Log.e(TAG, "launchRingingActivityIfFullScreenIntentUnusable: direct launch also failed", e)
        }
    }

    /** Ramps [mediaPlayer]'s volume from [FADE_IN_START_VOLUME] to full over [durationMillis]. */
    private fun startFadeIn(durationMillis: Long) {
        fadeJob?.cancel()
        fadeJob = lifecycleScope.launch {
            val startedAt = System.currentTimeMillis()
            while (true) {
                val elapsed = System.currentTimeMillis() - startedAt
                if (elapsed >= durationMillis) {
                    mediaPlayer?.setVolume(1f, 1f)
                    break
                }
                val volume = FADE_IN_START_VOLUME + (1f - FADE_IN_START_VOLUME) * (elapsed.toFloat() / durationMillis)
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

    /** [alarm] is null for the placeholder posted before the alarm has been loaded — see
     * [onStartCommand]. It carries the same channel, category and full-screen intent, so the
     * ringing screen still comes up from it; only the title and time fill in afterwards. */
    private fun buildNotification(alarm: Alarm?): Notification {
        val fullScreenIntent = PendingIntent.getActivity(
            this,
            alarm?.id?.toInt() ?: 0,
            Intent(this, RingingActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val label = alarm?.label?.ifBlank { getString(R.string.app_name) } ?: getString(R.string.app_name)
        return NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ALARM)
            .setContentTitle(label)
            .setContentText(alarm?.let { String.format("%02d:%02d", it.hour, it.minute) }.orEmpty())
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
