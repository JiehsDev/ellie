package com.example.jikan.screentime

enum class AppUsageStatus {
    GENERAL,
    RESTRICTED,
    EARNING,
    RESTRICTED_AND_EARNING,
}

data class ScreenTimeAppUsage(
    val packageName: String,
    val appLabel: String,
    val minutes: Int,
    val percentage: Float,
    val status: AppUsageStatus,
)

data class ExceededAppUsage(
    val packageName: String,
    val appLabel: String,
    val minutes: Int,
    val limitMinutes: Int,
    val overLimitMinutes: Int,
)

/**
 * Phase 5: a configured daily limit that an app is approaching or has
 * exactly reached. (Exceeded limits keep the existing [ExceededAppUsage].)
 */
data class AppLimitStatus(
    val packageName: String,
    val appLabel: String,
    val minutes: Int,
    val limitMinutes: Int,
    /** Minutes left before the limit is reached (>= 0). */
    val remainingMinutes: Int,
)

/** One day of the weekly trend, oldest first in [ScreenTimeSummary.weeklyTrend]. */
data class DailyUsage(
    val epochDay: Long,
    val minutes: Int,
)

enum class UsageChangeDirection {
    DOWN,
    SAME,
    UP,
}

data class UsageChange(
    val deltaMinutes: Int,
    val percentageChange: Float?,
    val direction: UsageChangeDirection,
)

data class ScreenTimeSummary(
    val epochDay: Long,
    val totalScreenTimeMinutes: Int,
    val previousDayScreenTimeMinutes: Int,
    val averageDailyScreenTimeMinutes: Int,
    val restrictedAppMinutes: Int,
    val earningAppMinutes: Int,
    val earnedMinutes: Int,
    val spentMinutes: Int,
    val topApps: List<ScreenTimeAppUsage>,
    val exceededApps: List<ExceededAppUsage>,
    val recentUsageChange: UsageChange,
    // Phase 5: analytics additions. All deterministic; AI receives this
    // structure as facts and must not recompute the arithmetic.
    /** Current wallet balance in minutes. */
    val walletBalanceMinutes: Int = 0,
    /** Apps at >= 80% of their daily limit but still under it, most urgent first. */
    val approachingApps: List<AppLimitStatus> = emptyList(),
    /** Apps exactly at their daily limit. */
    val atLimitApps: List<AppLimitStatus> = emptyList(),
    /** Today vs the recent daily average. */
    val averageUsageChange: UsageChange = UsageChange(0, null, UsageChangeDirection.SAME),
    /** Last 7 days including today, oldest first. */
    val weeklyTrend: List<DailyUsage> = emptyList(),
    /** Earning-rule apps used today, most used first. */
    val earningApps: List<ScreenTimeAppUsage> = emptyList(),
)
