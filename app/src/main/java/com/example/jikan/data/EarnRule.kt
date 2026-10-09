package com.example.jikan.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "earn_rules",
    indices = [Index(value = ["packageName"], unique = true)],
)
data class EarnRule(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val packageName: String,
    val requiredMinutes: Int,
    val rewardMinutes: Int,
    val dailyLimitMinutes: Int,
    val enabled: Boolean = true,
    /** User-facing label for the activity, e.g. "Daily Language Practice". */
    val label: String = "",
)
