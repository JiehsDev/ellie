package com.example.jikan.screentime

import com.example.jikan.data.EarnRule
import kotlin.math.roundToInt

data class RestrictedAppLimit(
    val packageName: String,
    val limitMinutes: Int,
)

data class ScreenTimeFacts(
    val epochDay: Long,
    val usage: List<RawAppUsage>,
    val previousDayUsage: List<RawAppUsage> = emptyList(),
    val recentDailyTotals: List<Int> = emptyList(),
    val restrictedLimits: List<RestrictedAppLimit> = emptyList(),
    val earnRules: List<EarnRule> = emptyList(),
    val earnedMinutes: Int = 0,
    val spentMinutes: Int = 0,
    val labels: Map<String, String> = emptyMap(),
    val topAppLimit: Int = 5,
)

object ScreenTimeCalculator {
    fun summarize(facts: ScreenTimeFacts): ScreenTimeSummary {
        val totalMinutes = facts.usage.sumOf { AppUsageAggregator.minutesFromMs(it.foregroundMs) }
        val previousDayMinutes = facts.previousDayUsage.sumOf { AppUsageAggregator.minutesFromMs(it.foregroundMs) }
        val restrictedPackages = facts.restrictedLimits.map { it.packageName }.toSet()
        val earningPackages = facts.earnRules.filter { it.enabled }.map { it.packageName }.toSet()
        val usageByPackage = facts.usage.associateBy { it.packageName }

        val topApps = facts.usage
            .sortedByDescending { it.foregroundMs }
            .take(facts.topAppLimit)
            .map { usage ->
                val minutes = AppUsageAggregator.minutesFromMs(usage.foregroundMs)
                ScreenTimeAppUsage(
                    packageName = usage.packageName,
                    appLabel = facts.labels[usage.packageName] ?: usage.packageName,
                    minutes = minutes,
                    percentage = percentage(minutes, totalMinutes),
                    status = statusFor(usage.packageName, restrictedPackages, earningPackages),
                )
            }

        val restrictedMinutes = facts.usage
            .filter { it.packageName in restrictedPackages }
            .sumOf { AppUsageAggregator.minutesFromMs(it.foregroundMs) }
        val earningMinutes = facts.usage
            .filter { it.packageName in earningPackages }
            .sumOf { AppUsageAggregator.minutesFromMs(it.foregroundMs) }

        val exceeded = facts.restrictedLimits
            .filter { it.limitMinutes >= 0 }
            .mapNotNull { limit ->
                val minutes = usageByPackage[limit.packageName]?.let { AppUsageAggregator.minutesFromMs(it.foregroundMs) } ?: 0
                if (minutes > limit.limitMinutes) {
                    ExceededAppUsage(
                        packageName = limit.packageName,
                        appLabel = facts.labels[limit.packageName] ?: limit.packageName,
                        minutes = minutes,
                        limitMinutes = limit.limitMinutes,
                        overLimitMinutes = minutes - limit.limitMinutes,
                    )
                } else {
                    null
                }
            }
            .sortedByDescending { it.overLimitMinutes }

        return ScreenTimeSummary(
            epochDay = facts.epochDay,
            totalScreenTimeMinutes = totalMinutes,
            previousDayScreenTimeMinutes = previousDayMinutes,
            averageDailyScreenTimeMinutes = average(facts.recentDailyTotals),
            restrictedAppMinutes = restrictedMinutes,
            earningAppMinutes = earningMinutes,
            earnedMinutes = facts.earnedMinutes,
            spentMinutes = facts.spentMinutes,
            topApps = topApps,
            exceededApps = exceeded,
            recentUsageChange = change(totalMinutes, previousDayMinutes),
        )
    }

    fun percentage(minutes: Int, totalMinutes: Int): Float {
        if (minutes <= 0 || totalMinutes <= 0) return 0f
        return ((minutes * 1000f / totalMinutes).roundToInt() / 10f).coerceIn(0f, 100f)
    }

    private fun statusFor(
        packageName: String,
        restrictedPackages: Set<String>,
        earningPackages: Set<String>,
    ): AppUsageStatus = when {
        packageName in restrictedPackages && packageName in earningPackages -> AppUsageStatus.RESTRICTED_AND_EARNING
        packageName in restrictedPackages -> AppUsageStatus.RESTRICTED
        packageName in earningPackages -> AppUsageStatus.EARNING
        else -> AppUsageStatus.GENERAL
    }

    private fun average(values: List<Int>): Int {
        if (values.isEmpty()) return 0
        return (values.sum().toFloat() / values.size).roundToInt()
    }

    private fun change(todayMinutes: Int, previousMinutes: Int): UsageChange {
        val delta = todayMinutes - previousMinutes
        val direction = when {
            delta < 0 -> UsageChangeDirection.DOWN
            delta > 0 -> UsageChangeDirection.UP
            else -> UsageChangeDirection.SAME
        }
        val percent = if (previousMinutes <= 0) null else {
            ((delta * 1000f / previousMinutes).roundToInt() / 10f)
        }
        return UsageChange(deltaMinutes = delta, percentageChange = percent, direction = direction)
    }
}
