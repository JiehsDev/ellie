package com.example.jikan.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface WalletTransactionDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(transaction: WalletTransaction): Long

    @Query("SELECT * FROM wallet_transactions ORDER BY createdAtMs DESC")
    fun observeAll(): Flow<List<WalletTransaction>>

    @Query("SELECT * FROM wallet_transactions WHERE idempotencyKey = :idempotencyKey LIMIT 1")
    suspend fun getByIdempotencyKey(idempotencyKey: String): WalletTransaction?

    @Query(
        "SELECT COALESCE(SUM(minutes), 0) FROM wallet_transactions " +
            "WHERE type = :type AND epochDay = :epochDay"
    )
    suspend fun sumMinutesByTypeForDay(type: WalletTransactionType, epochDay: Long): Int

    @Query(
        "SELECT COALESCE(SUM(minutes), 0) FROM wallet_transactions " +
            "WHERE type = :type AND epochDay = :epochDay AND packageName = :packageName"
    )
    suspend fun sumMinutesByTypeForPackageDay(
        type: WalletTransactionType,
        epochDay: Long,
        packageName: String,
    ): Int
}
