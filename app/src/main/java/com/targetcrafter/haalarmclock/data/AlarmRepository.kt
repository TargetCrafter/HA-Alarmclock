package com.targetcrafter.haalarmclock.data

import com.targetcrafter.haalarmclock.alarm.AlarmScheduler
import kotlinx.coroutines.flow.Flow

class AlarmRepository(
    private val dao: AlarmDao,
    private val scheduler: AlarmScheduler,
) {
    val alarms: Flow<List<Alarm>> = dao.observeAll()

    suspend fun getById(id: Long): Alarm? = dao.getById(id)

    /** Inserts or updates [alarm] and (re)schedules or cancels it as appropriate. Any pending
     * snooze is invalidated, since the alarm's own settings just changed underneath it — use
     * [markSnoozed]/[clearSnoozed] instead if you specifically want to touch snooze state. */
    suspend fun save(alarm: Alarm): Alarm {
        val toSave = alarm.copy(snoozedUntilMillis = null)
        val id = dao.upsert(toSave)
        val saved = if (toSave.id == 0L) toSave.copy(id = id) else toSave
        scheduler.cancelSnooze(saved.id)
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

    /** Updates just the time, leaving every other setting untouched — used by the quick time-edit popup. */
    suspend fun updateTime(id: Long, hour: Int, minute: Int) {
        val alarm = dao.getById(id) ?: return
        save(alarm.copy(hour = hour, minute = minute))
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

    /** Marks [id] as snoozed until [untilMillis], for the list UI to show a "Snoozed" badge. */
    suspend fun markSnoozed(id: Long, untilMillis: Long) {
        val alarm = dao.getById(id) ?: return
        dao.update(alarm.copy(snoozedUntilMillis = untilMillis))
    }

    /** Clears the "snoozed" badge — called whenever the alarm actually starts ringing again. */
    suspend fun clearSnoozed(id: Long) {
        val alarm = dao.getById(id) ?: return
        if (alarm.snoozedUntilMillis != null) {
            dao.update(alarm.copy(snoozedUntilMillis = null))
        }
    }
}
