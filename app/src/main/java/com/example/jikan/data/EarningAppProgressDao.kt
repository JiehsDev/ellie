package com.example.jikan.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface EarningAppProgressDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(progress: EarningAppProgress)

    @Query("SELECT * FROM earning_app_progress WHERE ruleId = :ruleId AND epochDay = :epochDay LIMIT 1")
    suspend fun get(ruleId: Long, epochDay: Long): EarningAppProgress?
}
