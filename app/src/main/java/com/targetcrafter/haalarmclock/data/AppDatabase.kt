package com.targetcrafter.haalarmclock.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [Alarm::class, Timer::class], version = 4, exportSchema = true)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun alarmDao(): AlarmDao
    abstract fun timerDao(): TimerDao

    companion object {
        @Volatile private var instance: AppDatabase? = null

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE alarms ADD COLUMN fadeInEnabled INTEGER NOT NULL DEFAULT 1")
                db.execSQL("ALTER TABLE alarms ADD COLUMN snoozedUntilMillis INTEGER")
            }
        }

        // Replaces the flat snoozeMinutes column with a nullable snoozeMinutesOverride (an alarm's
        // existing value carries over as its override, so behavior doesn't change for existing
        // alarms) plus a new nullable fadeInSecondsOverride. SQLite can't drop/rename a column
        // reliably across all supported versions, so this recreates the table instead.
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE alarms_new (
                        id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
                        hour INTEGER NOT NULL,
                        minute INTEGER NOT NULL,
                        label TEXT NOT NULL,
                        enabled INTEGER NOT NULL,
                        repeatDaysMask INTEGER NOT NULL,
                        vibrate INTEGER NOT NULL,
                        ringtoneUri TEXT,
                        fadeInEnabled INTEGER NOT NULL DEFAULT 1,
                        snoozedUntilMillis INTEGER,
                        snoozeMinutesOverride INTEGER,
                        fadeInSecondsOverride INTEGER
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    """
                    INSERT INTO alarms_new
                        (id, hour, minute, label, enabled, repeatDaysMask, vibrate, ringtoneUri,
                         fadeInEnabled, snoozedUntilMillis, snoozeMinutesOverride)
                    SELECT
                        id, hour, minute, label, enabled, repeatDaysMask, vibrate, ringtoneUri,
                        fadeInEnabled, snoozedUntilMillis, snoozeMinutes
                    FROM alarms
                    """.trimIndent(),
                )
                db.execSQL("DROP TABLE alarms")
                db.execSQL("ALTER TABLE alarms_new RENAME TO alarms")
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE timers (
                        id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
                        label TEXT NOT NULL,
                        durationMillis INTEGER NOT NULL,
                        state TEXT NOT NULL,
                        endAtMillis INTEGER,
                        remainingMillis INTEGER,
                        createdAtMillis INTEGER NOT NULL
                    )
                    """.trimIndent(),
                )
            }
        }

        fun get(context: Context): AppDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                "ha-alarmclock.db",
            ).addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4).build().also { instance = it }
        }
    }
}
