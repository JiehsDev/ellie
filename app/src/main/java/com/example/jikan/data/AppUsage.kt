package com.example.jikan.data

import androidx.room.Entity

/**
 * One row per (day, locked app) recording how many minutes were spent in it.
 * Written by the accessibility service's spend tick, so a row only exists for
 * apps that actually cost the user credits.
 */
@Entity(tableName = "app_usage", primaryKeys = ["epochDay", "packageName"])
data class AppUsage(
    val epochDay: Long,
    val packageName: String,
    val minutes: Int,
)
