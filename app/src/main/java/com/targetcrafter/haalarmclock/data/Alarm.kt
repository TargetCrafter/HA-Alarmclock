package com.targetcrafter.haalarmclock.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * A single alarm. [repeatDaysMask] is a bitmask over [DayOfWeek.getValue] (Monday=1 .. Sunday=7),
 * bit (value - 1) set means the alarm repeats on that day. A mask of 0 means "one-off": the alarm
 * fires once at the next occurrence of [hour]:[minute] and then disables itself.
 */
@Entity(tableName = "alarms")
data class Alarm(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val hour: Int,
    val minute: Int,
    val label: String = "",
    val enabled: Boolean = true,
    val repeatDaysMask: Int = 0,
    val vibrate: Boolean = true,
    val ringtoneUri: String? = null,
    val snoozeMinutes: Int = 10,
    val fadeInEnabled: Boolean = true,
    /** Set while this alarm is snoozed; cleared when the snooze fires, or the alarm is dismissed/edited/deleted. */
    val snoozedUntilMillis: Long? = null,
) {
    val repeatDays: Set<DayOfWeek>
        get() = DayOfWeek.values().filter { repeatDaysMask and (1 shl (it.value - 1)) != 0 }.toSet()

    val isRepeating: Boolean get() = repeatDaysMask != 0

    /** Computes the next epoch-millis this alarm should fire at, strictly after [from]. */
    fun nextTriggerAtMillis(from: ZonedDateTime = ZonedDateTime.now(), zone: ZoneId = ZoneId.systemDefault()): Long {
        val nowInZone = from.withZoneSameInstant(zone)
        val todayCandidate = nowInZone.toLocalDate().atTime(hour, minute).atZone(zone)
        if (!isRepeating) {
            val candidate = if (todayCandidate.isAfter(nowInZone)) todayCandidate else todayCandidate.plusDays(1)
            return candidate.toInstant().toEpochMilli()
        }
        for (offset in 0..7) {
            val day: LocalDate = nowInZone.toLocalDate().plusDays(offset.toLong())
            val candidate: ZonedDateTime = day.atTime(hour, minute).atZone(zone)
            if (repeatDays.contains(day.dayOfWeek) && candidate.isAfter(nowInZone)) {
                return candidate.toInstant().toEpochMilli()
            }
        }
        error("A repeating alarm must have a next trigger within 8 days")
    }

    companion object {
        fun maskOf(days: Set<DayOfWeek>): Int = days.fold(0) { acc, d -> acc or (1 shl (d.value - 1)) }
    }
}

fun daysMaskLabel(mask: Int): String {
    if (mask == 0) return "One-off"
    val order = listOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY, DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)
    if (order.all { mask and (1 shl (it.value - 1)) != 0 }) return "Every day"
    val weekdays = listOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY)
    if (weekdays.all { mask and (1 shl (it.value - 1)) != 0 } && mask and (1 shl (DayOfWeek.SATURDAY.value - 1)) == 0 && mask and (1 shl (DayOfWeek.SUNDAY.value - 1)) == 0) {
        return "Weekdays"
    }
    return order.filter { mask and (1 shl (it.value - 1)) != 0 }
        .joinToString(", ") { it.name.take(3).lowercase().replaceFirstChar(Char::uppercase) }
}
