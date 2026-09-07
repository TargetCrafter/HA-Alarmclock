package com.targetcrafter.haalarmclock.util

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.PrintWriter
import java.io.StringWriter

/** One recorded crash. */
@Serializable
data class RecordedCrash(val atMillis: Long, val threadName: String, val stackTrace: String)

/**
 * Keeps the stack traces of recent crashes where the user can actually read them.
 *
 * This app is sideloaded, so there is no store console collecting crashes, and reading logcat needs
 * a computer and a cable — which leaves a background crash invisible from the phone itself. That
 * matters more here than in most apps: repeated crashes get an app force-stopped, and a force-stop
 * cancels every alarm it had scheduled. A crash nobody can see is therefore a plausible cause of an
 * alarm that silently never rings.
 *
 * Keeps a list rather than only the newest, because the failure mode worth diagnosing is a *loop* —
 * and the first crash in one is usually the informative one, while later entries are often
 * knock-on damage. The handler chains to whatever was installed before it, so the system still
 * shows its dialog and kills the process exactly as it would have.
 */
class CrashRecorder(context: Context) {

    private val prefs = context.getSharedPreferences("crash_recorder", Context.MODE_PRIVATE)

    private val _crashes = MutableStateFlow(read())

    /** Newest first. */
    val crashes: StateFlow<List<RecordedCrash>> = _crashes.asStateFlow()

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
        val crash = RecordedCrash(
            atMillis = System.currentTimeMillis(),
            threadName = thread.name,
            stackTrace = trace.take(MAX_TRACE_CHARS),
        )
        val updated = (listOf(crash) + _crashes.value).take(MAX_CRASHES)
        // commit(), not apply(): the process is about to be killed, and an async write would be
        // lost — which would leave exactly the invisible crash this exists to prevent.
        prefs.edit().putString(KEY_CRASHES, Json.encodeToString(updated)).commit()
        _crashes.value = updated
    }

    fun clear() {
        prefs.edit().clear().apply()
        _crashes.value = emptyList()
    }

    private fun read(): List<RecordedCrash> {
        val stored = prefs.getString(KEY_CRASHES, null) ?: return emptyList()
        // Never throw out of a constructor over unreadable debug data.
        return runCatching { Json.decodeFromString<List<RecordedCrash>>(stored) }.getOrDefault(emptyList())
    }

    companion object {
        /** Enough to see a loop start without filling preferences with knock-on failures. */
        private const val MAX_CRASHES = 10
        private const val MAX_TRACE_CHARS = 8_000
        private const val KEY_CRASHES = "crashes"
    }
}
