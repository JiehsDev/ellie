package com.example.jikan.data

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class DailyGoalPreset {
    CASUAL,
    SERIOUS,
}

@Entity(tableName = "user_settings")
data class UserSettings(
    @PrimaryKey val id: Int = SINGLETON_ID,
    val userName: String = "",
    val sessionLengthMinutes: Int = 12,
    val dailyGoalPreset: DailyGoalPreset = DailyGoalPreset.CASUAL,
    val onboardingCompleted: Boolean = false,
) {
    companion object {
        const val SINGLETON_ID = 1
    }
}
