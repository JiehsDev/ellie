package com.example.jikan.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface EarnRuleDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(rule: EarnRule): Long

    @Query("SELECT * FROM earn_rules WHERE enabled = 1 ORDER BY packageName")
    fun observeEnabled(): Flow<List<EarnRule>>

    @Query("SELECT * FROM earn_rules ORDER BY packageName")
    fun observeAll(): Flow<List<EarnRule>>

    @Query("SELECT * FROM earn_rules WHERE packageName = :packageName")
    suspend fun getForPackage(packageName: String): EarnRule?

    @Query("UPDATE earn_rules SET enabled = :enabled WHERE id = :id")
    suspend fun setEnabled(id: Long, enabled: Boolean)

    @Query("DELETE FROM earn_rules WHERE id = :id")
    suspend fun deleteById(id: Long)
}
