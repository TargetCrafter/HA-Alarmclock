package com.targetcrafter.haalarmclock.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface TimerDao {
    @Query("SELECT * FROM timers ORDER BY createdAtMillis")
    fun observeAll(): Flow<List<Timer>>

    @Query("SELECT * FROM timers WHERE id = :id")
    suspend fun getById(id: Long): Timer?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(timer: Timer): Long

    @Update
    suspend fun update(timer: Timer)

    @Delete
    suspend fun delete(timer: Timer)

    @Query("SELECT * FROM timers")
    suspend fun getAllOnce(): List<Timer>
}
