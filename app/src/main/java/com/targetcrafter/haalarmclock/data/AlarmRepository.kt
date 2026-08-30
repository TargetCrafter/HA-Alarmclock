package com.targetcrafter.haalarmclock.data

import com.targetcrafter.haalarmclock.alarm.AlarmScheduler
import kotlinx.coroutines.flow.Flow

class AlarmRepository(
    private val dao: AlarmDao,
    private val scheduler: AlarmScheduler,
) {
    val alarms: Flow<List<Alarm>> = dao.observeAll()

    suspend fun getById(id: Long): Alarm? = dao.getById(id)

    /** Inserts or updates [alarm] and (re)schedules or cancels it as appropriate. */
    suspend fun save(alarm: Alarm): Alarm {
        val id = dao.upsert(alarm)
        val saved = if (alarm.id == 0L) alarm.copy(id = id) else alarm
        if (saved.enabled) scheduler.schedule(saved) else scheduler.cancel(saved)
        return saved
    }

    suspend fun delete(alarm: Alarm) {
        scheduler.cancel(alarm)
        dao.delete(alarm)
    }

    suspend fun setEnabled(id: Long, enabled: Boolean) {
        val alarm = dao.getById(id) ?: return
        save(alarm.copy(enabled = enabled))
    }

    /** Re-arms every enabled alarm's AlarmManager entry; alarms don't survive a reboot on their own. */
    suspend fun rescheduleAll() {
        dao.getAllOnce().filter { it.enabled }.forEach { scheduler.schedule(it) }
    }

    /** Called by [AlarmScheduler]'s receiver once a one-off alarm has fired. */
    suspend fun disableAfterOneOffFired(id: Long) {
        val alarm = dao.getById(id) ?: return
        if (!alarm.isRepeating) {
            dao.update(alarm.copy(enabled = false))
        } else {
            scheduler.schedule(alarm)
        }
    }
}
