package com.example.jikan.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface CardDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(cards: List<Card>)

    @Query("SELECT * FROM cards ORDER BY tier, sortOrder")
    fun observeAll(): Flow<List<Card>>

    @Query("SELECT * FROM cards WHERE tier = :tier ORDER BY sortOrder")
    fun observeByTier(tier: CardTier): Flow<List<Card>>

    @Query("SELECT * FROM cards WHERE id = :id")
    suspend fun getById(id: Long): Card?

    @Query("SELECT * FROM cards")
    suspend fun getAllOnce(): List<Card>

    @Query(
        "SELECT * FROM cards WHERE id NOT IN (SELECT cardId FROM user_card_progress) ORDER BY tier, sortOrder LIMIT :limit"
    )
    suspend fun getNewCards(limit: Int): List<Card>

    @Query("SELECT COUNT(*) FROM cards")
    suspend fun count(): Int
}
