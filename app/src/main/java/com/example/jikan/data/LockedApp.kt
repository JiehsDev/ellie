package com.example.jikan.data

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class LockTier {
    LIGHT,
    AVERAGE,
    EXTREME,
}

@Entity(tableName = "locked_apps")
data class LockedApp(
    @PrimaryKey val packageName: String,
    val appLabel: String,
    val isLocked: Boolean = true,
    val tier: LockTier = LockTier.EXTREME,
)
