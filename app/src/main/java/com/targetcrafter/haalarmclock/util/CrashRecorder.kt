package com.targetcrafter.haalarmclock.util

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.PrintWriter
import java.io.StringWriter

/** The last crash the app recorded, or null if it hasn't crashed since this was last cleared. */
data class RecordedCrash(val atMillis: Long, val threadName: String, val stackTrace: String)

/**
 * Keeps the stack trace of the last crash where the user can actually read it.
 *
 * This app is sideloaded, so there is no Play Console collecting crashes, and reading logcat needs
 * a computer and a cable — which means a background crash is completely invisible from the phone.
 * That matters more here than in most apps: repeated crashes get an app force-stopped, and a
 * force-stop cancels every alarm it had scheduled. A crash nobody can see is therefore a plausible
 * cause of an alarm that silently never rings, and "the app crashed" alone doesn't say where.
 *
 * The handler chains to whatever was installed before it, so the system still shows its dialog and
 * kills the process exactly as it would have.
 */
class CrashRecorder(context: Context) {

    private val prefs = context.getSharedPreferences("crash_recorder", Context.MODE_PRIVATE)

    private val _lastCrash = MutableStateFlow(read())
    val lastCrash: StateFlow<RecordedCrash?> = _lastCrash.asStateFlow()

    fun install() {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            // Never let recording a crash become a second crash that hides the first.
            runCatching { record(thread, throwable) }
            previous?.uncaughtException(thread, throwable)
        }
    }

    private fun record(thread: Thread, throwable: Throwable) {
        val trace = StringWriter().also { throwable.printStackTrace(PrintWriter(it)) }.toString()
        // commit(), not apply(): the process is about to be killed, and an async write would be
        // lost — which would leave exactly the invisible crash this exists to prevent.
        prefs.edit()
            .putLong(KEY_AT, System.currentTimeMillis())
            .putString(KEY_THREAD, thread.name)
            .putString(KEY_TRACE, trace.take(MAX_TRACE_CHARS))
            .commit()
    }

    fun clear() {
        prefs.edit().clear().apply()
        _lastCrash.value = null
    }

    private fun read(): RecordedCrash? {
        val at = prefs.getLong(KEY_AT, 0L)
        val trace = prefs.getString(KEY_TRACE, null) ?: return null
        if (at <= 0L) return null
        return RecordedCrash(at, prefs.getString(KEY_THREAD, "?").orEmpty(), trace)
    }

    companion object {
        /** Enough for the frames that matter; a full trace with dozens of "caused by" chains would
         * be unreadable on a phone and is not worth the preference bloat. */
        private const val MAX_TRACE_CHARS = 8_000
        private const val KEY_AT = "crashed_at"
        private const val KEY_THREAD = "thread"
        private const val KEY_TRACE = "stack_trace"
    }
}
