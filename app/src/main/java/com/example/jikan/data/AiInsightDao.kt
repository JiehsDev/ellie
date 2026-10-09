package com.example.jikan.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface AiInsightDao {
    @Insert
    suspend fun insert(insight: AiInsightEntity)

    @Query("""
        SELECT * FROM ai_insights
        WHERE sessionId = :sessionId
        LIMIT 1
    """)
    suspend fun getForSession(sessionId: Long): AiInsightEntity?

    @Query("SELECT * FROM ai_insights ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<AiInsightEntity>>
}
