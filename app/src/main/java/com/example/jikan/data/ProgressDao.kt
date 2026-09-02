package com.example.jikan.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ProgressDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(progress: UserCardProgress)

    @Query("SELECT * FROM user_card_progress WHERE cardId = :cardId")
    suspend fun getForCard(cardId: Long): UserCardProgress?

    @Query(
        "SELECT * FROM user_card_progress WHERE dueAt <= :now ORDER BY dueAt ASC LIMIT :limit"
    )
    suspend fun getDueCards(now: Long, limit: Int): List<UserCardProgress>

    @Query("SELECT * FROM user_card_progress")
    fun observeAll(): Flow<List<UserCardProgress>>

    @Query("SELECT COUNT(*) FROM user_card_progress WHERE dueAt <= :now")
    fun observeDueCount(now: Long): Flow<Int>
}
