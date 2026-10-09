package com.example.jikan.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Phase 4: a user-configured daily screen-time limit for one app.
 *
 * Restrictions are independent from [LockedApp] tiers and from [EarnRule]s:
 * a restricted app is usable until its daily limit is reached, then Jikan
 * intervenes through the existing protection / accessibility system.
 *
 * Example: TikTok with [dailyLimitMinutes] = 30 may be opened freely until
 * 30 minutes of foreground time have accumulated today.
 */
@Entity(tableName = "app_restrictions")
data class AppRestriction(
    @PrimaryKey val packageName: String,
    val appLabel: String,
    /**
     * Daily allowed foreground minutes. Must be >= 0.
     * 0 means the app is blocked as soon as it is opened.
     */
    val dailyLimitMinutes: Int,
    val enabled: Boolean = true,
)
