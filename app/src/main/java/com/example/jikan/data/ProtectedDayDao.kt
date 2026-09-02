package com.example.jikan.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ProtectedDayDao {
    /** Paired with [addMinutes]; SQLite UPSERT needs API 30 and this app supports 26. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIfAbsent(day: ProtectedDay)

    @Query("UPDATE protected_days SET observedMinutes = observedMinutes + :minutes WHERE epochDay = :epochDay")
    suspend fun addMinutes(epochDay: Long, minutes: Int)

    @Query("SELECT * FROM protected_days WHERE epochDay >= :epochDay")
    fun observeSince(epochDay: Long): Flow<List<ProtectedDay>>
}
