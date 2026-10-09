package com.example.jikan.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface AppRestrictionDao {
    @Query("SELECT * FROM app_restrictions WHERE enabled = 1")
    fun observeEnabled(): Flow<List<AppRestriction>>

    @Query("SELECT * FROM app_restrictions")
    fun observeAll(): Flow<List<AppRestriction>>

    @Query("SELECT * FROM app_restrictions WHERE packageName = :packageName")
    suspend fun get(packageName: String): AppRestriction?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(restriction: AppRestriction)

    @Query("DELETE FROM app_restrictions WHERE packageName = :packageName")
    suspend fun delete(packageName: String)
}
