package com.example.jikan.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface PauseStateDao {
    @Query("SELECT * FROM pause_state WHERE id = ${PauseState.SINGLETON_ID}")
    suspend fun get(): PauseState?

    @Query("SELECT * FROM pause_state WHERE id = ${PauseState.SINGLETON_ID}")
    fun observe(): Flow<PauseState?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(state: PauseState)
}
