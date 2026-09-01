package com.targetcrafter.haalarmclock.ui.stopwatch

import android.os.SystemClock
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** A single recorded lap: [lapMillis] is the time since the *previous* lap (or since start, for
 * the first one), [totalMillis] is the cumulative elapsed time at the moment it was recorded. */
data class Lap(val number: Int, val lapMillis: Long, val totalMillis: Long)

private const val TICK_INTERVAL_MILLIS = 16L

/**
 * A simple count-up stopwatch: Start/Pause/Resume, Lap (while running), Reset (while paused).
 * Backed by [SystemClock.elapsedRealtime], which only ever moves forward and is unaffected by the
 * wall clock changing (NTP sync, timezone, DST, the user editing the time) — unlike
 * [System.currentTimeMillis], which would make the stopwatch jump if the clock changed mid-run.
 *
 * Deliberately in-app only, not backed by a foreground service — unlike a timer, a stopwatch has
 * no completion to notify about, so there's nothing worth showing a permanent notification for.
 * Scoped as a normal ViewModel, it keeps counting across tab swipes and a trip to Settings and
 * back (both stay under the same `ROUTE_TABS` back-stack entry), but not past the app process
 * being killed.
 */
class StopwatchViewModel : ViewModel() {

    private val _isRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

    private val _elapsedMillis = MutableStateFlow(0L)
    val elapsedMillis: StateFlow<Long> = _elapsedMillis.asStateFlow()

    private val _laps = MutableStateFlow<List<Lap>>(emptyList())
    val laps: StateFlow<List<Lap>> = _laps.asStateFlow()

    private var runStartRealtime = 0L
    private var accumulatedMillis = 0L
    private var tickJob: Job? = null

    fun start() {
        if (_isRunning.value) return
        runStartRealtime = SystemClock.elapsedRealtime()
        _isRunning.value = true
        tickJob = viewModelScope.launch {
            while (true) {
                _elapsedMillis.value = currentElapsed()
                delay(TICK_INTERVAL_MILLIS)
            }
        }
    }

    fun pause() {
        if (!_isRunning.value) return
        accumulatedMillis = currentElapsed()
        _elapsedMillis.value = accumulatedMillis
        _isRunning.value = false
        tickJob?.cancel()
        tickJob = null
    }

    fun reset() {
        if (_isRunning.value) return
        accumulatedMillis = 0L
        _elapsedMillis.value = 0L
        _laps.value = emptyList()
    }

    fun lap() {
        if (!_isRunning.value) return
        val total = currentElapsed()
        val previousTotal = _laps.value.firstOrNull()?.totalMillis ?: 0L
        val newLap = Lap(number = _laps.value.size + 1, lapMillis = total - previousTotal, totalMillis = total)
        _laps.value = listOf(newLap) + _laps.value
    }

    private fun currentElapsed(): Long =
        accumulatedMillis + if (_isRunning.value) SystemClock.elapsedRealtime() - runStartRealtime else 0L

    override fun onCleared() {
        tickJob?.cancel()
        super.onCleared()
    }
}
