package com.example.jikan.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface BankingAllowlistDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entry: BankingAllowlistEntry)

    @Query("DELETE FROM banking_allowlist WHERE packageName = :packageName")
    suspend fun delete(packageName: String)

    @Query("DELETE FROM banking_allowlist WHERE expiresAtMs <= :nowMs")
    suspend fun pruneExpired(nowMs: Long)

    @Query("SELECT * FROM banking_allowlist WHERE expiresAtMs > :nowMs")
    suspend fun active(nowMs: Long): List<BankingAllowlistEntry>

    @Query("SELECT * FROM banking_allowlist WHERE expiresAtMs > :nowMs")
    fun observeActive(nowMs: Long): Flow<List<BankingAllowlistEntry>>
}
