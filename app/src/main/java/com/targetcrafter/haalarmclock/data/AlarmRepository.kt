package com.targetcrafter.haalarmclock.data

import android.util.Log
import com.targetcrafter.haalarmclock.alarm.AlarmScheduler
import kotlinx.coroutines.flow.Flow

private const val TAG = "AlarmRepository"

class AlarmRepository(
    private val dao: AlarmDao,
    private val scheduler: AlarmScheduler,
) {
    val alarms: Flow<List<Alarm>> = dao.observeAll()

    suspend fun getById(id: Long): Alarm? = dao.getById(id)

    /** Inserts or updates [alarm] and (re)schedules or cancels it as appropriate. Any pending
     * snooze is invalidated, since the alarm's own settings just changed underneath it — use
     * [markSnoozed]/[clearSnoozed] instead if you specifically want to touch snooze state.
     *
     * Rejects an out-of-range time before touching the database: the row is written before it's
     * scheduled, so persisting one would mean [rescheduleAll] hits it again on every launch and
     * every boot, not just once here. */
    suspend fun save(alarm: Alarm): Alarm {
        require(alarm.hasValidTime) { "Alarm time ${alarm.hour}:${alarm.minute} is out of range" }
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
        // A row with an out-of-range time can't be scheduled, so enabling it would only throw out
        // of [save] — and this is reachable straight from the list's toggle, so it has to no-op
        // rather than crash. Disabling one is still allowed, since that needs no scheduling.
        if (enabled && !alarm.hasValidTime) {
            Log.e(TAG, "Refusing to enable alarm $id: time ${alarm.hour}:${alarm.minute} is out of range")
            return
        }
        save(alarm.copy(enabled = enabled))
    }

    /** Updates just the time, leaving every other setting untouched — used by the quick time-edit popup. */
    suspend fun updateTime(id: Long, hour: Int, minute: Int) {
        val alarm = dao.getById(id) ?: return
        save(alarm.copy(hour = hour, minute = minute))
    }

    /** Re-arms every enabled alarm's AlarmManager entry; alarms don't survive a reboot on their own.
     *
     * A row with an out-of-range time is disabled instead of scheduled. This runs on boot and after
     * an app update, so it doubles as the repair path for a bad row written by a build before [save]
     * rejected them: scheduling one throws, which would otherwise crash the app every single time
     * the phone starts and take every *other* alarm's scheduling down with it. Disabling it leaves
     * it visible in the list (switched off, showing its nonsense time) for the user to fix or
     * delete, rather than deleting something they created out from under them. */
    suspend fun rescheduleAll() {
        dao.getAllOnce().filter { it.enabled }.forEach { alarm ->
            if (alarm.hasValidTime) {
                scheduler.schedule(alarm)
            } else {
                Log.e(TAG, "Disabling alarm ${alarm.id}: time ${alarm.hour}:${alarm.minute} is out of range")
                dao.update(alarm.copy(enabled = false))
            }
        }
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
