package com.example.jikan.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "pause_state")
data class PauseState(
    @PrimaryKey val id: Int = SINGLETON_ID,
    val expiryTimestampMs: Long = 0L,
) {
    companion object {
        const val SINGLETON_ID = 1
    }
}
