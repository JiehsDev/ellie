package com.example.jikan.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * How many minutes the lock service was seen alive on a given day.
 *
 * This exists so the productivity score can tell "a quiet day" from "Jikan was not
 * running", which otherwise look identical: both record no locked-app usage.
 */
@Entity(tableName = "protected_days")
data class ProtectedDay(
    @PrimaryKey val epochDay: Long,
    val observedMinutes: Int,
)
