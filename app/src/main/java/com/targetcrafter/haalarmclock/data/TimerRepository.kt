package com.targetcrafter.haalarmclock.data

import com.targetcrafter.haalarmclock.timer.TimerNotifications
import com.targetcrafter.haalarmclock.timer.TimerScheduler
import kotlinx.coroutines.flow.Flow

class TimerRepository(
    private val dao: TimerDao,
    private val scheduler: TimerScheduler,
    private val notifications: TimerNotifications,
) {
    val timers: Flow<List<Timer>> = dao.observeAll()

    suspend fun getById(id: Long): Timer? = dao.getById(id)

    suspend fun start(label: String, durationMillis: Long): Timer {
        val timer = Timer(
            label = label,
            durationMillis = durationMillis,
            state = TimerState.RUNNING,
            endAtMillis = System.currentTimeMillis() + durationMillis,
        )
        val saved = timer.copy(id = dao.upsert(timer))
        scheduler.schedule(saved)
        notifications.postRunning(saved)
        return saved
    }

    suspend fun pause(id: Long) {
        val timer = dao.getById(id) ?: return
        if (timer.state != TimerState.RUNNING) return
        val updated = timer.copy(state = TimerState.PAUSED, endAtMillis = null, remainingMillis = timer.remainingMillisNow())
        dao.update(updated)
        scheduler.cancel(id)
        notifications.postPaused(updated)
    }

    suspend fun resume(id: Long) {
        val timer = dao.getById(id) ?: return
        if (timer.state != TimerState.PAUSED) return
        val remaining = timer.remainingMillis ?: timer.durationMillis
        val updated = timer.copy(state = TimerState.RUNNING, endAtMillis = System.currentTimeMillis() + remaining, remainingMillis = null)
        dao.update(updated)
        scheduler.schedule(updated)
        notifications.postRunning(updated)
    }

    suspend fun cancel(id: Long) {
        val timer = dao.getById(id) ?: return
        scheduler.cancel(id)
        notifications.cancel(id)
        dao.delete(timer)
    }

    /** Called once a running timer's AlarmManager trigger fires. */
    suspend fun markFinished(id: Long) {
        val timer = dao.getById(id) ?: return
        dao.update(timer.copy(state = TimerState.FINISHED, endAtMillis = null, remainingMillis = 0))
        notifications.cancel(id)
    }

    /** Re-arms every running timer's AlarmManager entry; timers don't survive a reboot on their own. */
    suspend fun rescheduleAll() {
        dao.getAllOnce().forEach { timer ->
            when (timer.state) {
                TimerState.RUNNING -> {
                    scheduler.schedule(timer)
                    notifications.postRunning(timer)
                }
                TimerState.PAUSED -> notifications.postPaused(timer)
                TimerState.FINISHED -> {}
            }
        }
    }
}
