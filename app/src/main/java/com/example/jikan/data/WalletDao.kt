package com.example.jikan.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface WalletDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(wallet: Wallet)

    @Query("SELECT * FROM wallet WHERE id = ${Wallet.SINGLETON_ID}")
    suspend fun get(): Wallet?

    @Query("SELECT * FROM wallet WHERE id = ${Wallet.SINGLETON_ID}")
    fun observe(): Flow<Wallet?>

    @Query(
        "UPDATE wallet SET creditBalanceMinutes = MAX(creditBalanceMinutes - :minutes, 0) " +
            "WHERE id = ${Wallet.SINGLETON_ID}"
    )
    suspend fun spend(minutes: Int)

    @Query(
        "UPDATE wallet SET creditBalanceMinutes = MIN(creditBalanceMinutes + :minutes, ${Wallet.MAX_BALANCE_MINUTES}) " +
            "WHERE id = ${Wallet.SINGLETON_ID}"
    )
    suspend fun regenerate(minutes: Int)
}
