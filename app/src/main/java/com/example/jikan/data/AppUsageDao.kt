package com.example.jikan.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface AppUsageDao {
    /**
     * Seeds the row for a (day, app) pair. Paired with [addMinutes] rather than a
     * single UPSERT because SQLite's ON CONFLICT DO UPDATE only exists from API 30,
     * and this app supports API 26.
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIfAbsent(usage: AppUsage)

    @Query(
        "UPDATE app_usage SET minutes = minutes + :minutes " +
            "WHERE epochDay = :epochDay AND packageName = :packageName"
    )
    suspend fun addMinutes(epochDay: Long, packageName: String, minutes: Int)

    @Query("SELECT COALESCE(SUM(minutes), 0) FROM app_usage WHERE epochDay = :epochDay")
    fun observeTotalMinutes(epochDay: Long): Flow<Int>
}
