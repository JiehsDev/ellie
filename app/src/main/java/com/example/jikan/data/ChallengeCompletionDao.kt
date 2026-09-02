package com.example.jikan.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ChallengeCompletionDao {
    @Insert
    suspend fun insert(completion: ChallengeCompletion): Long

    @Query(
        "SELECT COALESCE(SUM(creditsGranted), 0) FROM challenge_completions " +
            "WHERE type = :type AND epochDay = :epochDay"
    )
    suspend fun creditsGrantedToday(type: ChallengeType, epochDay: Long): Int

    @Query("SELECT * FROM challenge_completions WHERE epochDay >= :epochDay")
    fun observeSince(epochDay: Long): Flow<List<ChallengeCompletion>>
}
