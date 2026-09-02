package com.example.jikan.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface LockedAppDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(apps: List<LockedApp>)

    @Query("SELECT * FROM locked_apps")
    fun observeAll(): Flow<List<LockedApp>>

    @Query("SELECT * FROM locked_apps WHERE isLocked = 1")
    fun observeLocked(): Flow<List<LockedApp>>

    @Query("SELECT * FROM locked_apps WHERE packageName = :packageName")
    suspend fun get(packageName: String): LockedApp?

    @Query("UPDATE locked_apps SET isLocked = :isLocked WHERE packageName = :packageName")
    suspend fun setLocked(packageName: String, isLocked: Boolean)
}
