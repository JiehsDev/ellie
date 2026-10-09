package com.example.jikan.data

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class DailyGoalPreset {
    CASUAL,
    SERIOUS,
}

enum class ThemeMode {
    SYSTEM,
    LIGHT,
    DARK,
}

@Entity(tableName = "user_settings")
data class UserSettings(
    @PrimaryKey val id: Int = SINGLETON_ID,
    val userName: String = "",
    val sessionLengthMinutes: Int = 12,
    val dailyGoalPreset: DailyGoalPreset = DailyGoalPreset.CASUAL,
    val onboardingCompleted: Boolean = false,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val strictModeEnabled: Boolean = false,
    val strictPinHash: String? = null,
    val strictGraceUntilMs: Long = 0L,
    val bankingDisabledUntilMs: Long = 0L,
    val bankingModeActive: Boolean = false,
    val lastViolationAtMs: Long = 0L,
) {
    companion object {
        const val SINGLETON_ID = 1
    }
}
