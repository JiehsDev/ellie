package com.example.jikan.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface SettingsDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(settings: UserSettings)

    @Query("SELECT * FROM user_settings WHERE id = ${UserSettings.SINGLETON_ID}")
    suspend fun get(): UserSettings?

    @Query("SELECT * FROM user_settings WHERE id = ${UserSettings.SINGLETON_ID}")
    fun observe(): Flow<UserSettings?>
}
