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
)
