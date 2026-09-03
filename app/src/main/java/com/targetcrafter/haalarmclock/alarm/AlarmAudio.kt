package com.targetcrafter.haalarmclock.alarm

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.util.Log
import androidx.core.content.getSystemService

private const val TAG = "AlarmAudio"

/** The audio configuration every alarm sound plays with. Shared so the "test the alarm sound"
 * button in Settings exercises exactly what a real alarm does, rather than something that merely
 * resembles it — a test that can pass while the real thing stays silent is worse than no test. */
fun alarmAudioAttributes(): AudioAttributes = AudioAttributes.Builder()
    .setUsage(AudioAttributes.USAGE_ALARM)
    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
    .build()

/** Ringtone candidates to try in order: the alarm's own choice first, then the device default
 * alarm sound, then any valid ringtone — so a stale or revoked URI (a ringtone from an app since
 * uninstalled, a file that moved) can't leave an alarm silent. Deduplicated, since the first
 * candidate is often already the default. */
fun candidateRingtoneUris(context: Context, ringtoneUri: String?): List<Uri> =
    listOfNotNull(
        ringtoneUri?.let(Uri::parse),
        RingtoneManager.getActualDefaultRingtoneUri(context, RingtoneManager.TYPE_ALARM),
        RingtoneManager.getValidRingtoneUri(context),
    ).distinct()

/**
 * The device's alarm-stream volume. Alarms play on `STREAM_ALARM`, which has its own volume
 * separate from ringtone and media — so it can sit at zero while the phone is otherwise perfectly
 * loud, and every alarm is then silent no matter how correctly the rest of the ring path runs.
 * Neither Android nor this app used to say anything about that.
 */
data class AlarmVolume(val current: Int, val max: Int) {
    val isSilent: Boolean get() = current == 0
    val percent: Int get() = if (max <= 0) 0 else current * 100 / max
}

fun alarmVolume(context: Context): AlarmVolume {
    val audioManager = context.getSystemService<AudioManager>() ?: return AlarmVolume(0, 0)
    return AlarmVolume(
        current = audioManager.getStreamVolume(AudioManager.STREAM_ALARM),
        max = audioManager.getStreamMaxVolume(AudioManager.STREAM_ALARM),
    )
}

sealed interface AlarmSoundTestResult {
    data class Playing(val player: MediaPlayer, val uri: Uri) : AlarmSoundTestResult
    data class Failed(val reason: String) : AlarmSoundTestResult
}

/**
 * Starts the alarm sound on demand, through the same candidate list and audio attributes a real
 * alarm uses. Deliberately does *not* touch [RingingState] or start the ringing service: a test
 * must not push "an alarm is ringing" to Home Assistant and set off whatever the user has
 * automated against it. The caller owns the returned player and must release it.
 */
fun startAlarmSoundTest(context: Context, ringtoneUri: String? = null): AlarmSoundTestResult {
    val candidates = candidateRingtoneUris(context, ringtoneUri)
    if (candidates.isEmpty()) {
        return AlarmSoundTestResult.Failed("No ringtone is available on this device at all.")
    }
    for (uri in candidates) {
        val player = MediaPlayer()
        player.setAudioAttributes(alarmAudioAttributes())
        player.isLooping = true
        try {
            player.setDataSource(context, uri)
            player.prepare()
            player.start()
            return AlarmSoundTestResult.Playing(player, uri)
        } catch (e: Exception) {
            Log.e(TAG, "Test playback failed for $uri, trying the next candidate", e)
            player.release()
        }
    }
    return AlarmSoundTestResult.Failed("Every ringtone on this device failed to play.")
}
