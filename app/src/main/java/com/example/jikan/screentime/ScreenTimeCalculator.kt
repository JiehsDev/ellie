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
    /**
     * Configured daily limits. Single source of truth: the repository maps
     * these from the app_restrictions table (Phase 4). The old locked-tier
     * heuristic is retired — do not reintroduce a second limits source.
     */
    val restrictedLimits: List<RestrictedAppLimit> = emptyList(),
    /**
     * Packages locked via locked_apps (tiers). Kept separate from
     * [restrictedLimits]: "restricted" for minutes/status means locked OR
     * limit-configured.
     */
    val lockedPackages: Set<String> = emptySet(),
    val earnRules: List<EarnRule> = emptyList(),
    val earnedMinutes: Int = 0,
    val spentMinutes: Int = 0,
    val walletBalanceMinutes: Int = 0,
    val labels: Map<String, String> = emptyMap(),
    val topAppLimit: Int = 5,
)

object ScreenTimeCalculator {
    fun summarize(facts: ScreenTimeFacts): ScreenTimeSummary {
        val totalMinutes = facts.usage.sumOf { AppUsageAggregator.minutesFromMs(it.foregroundMs) }
        val previousDayMinutes = facts.previousDayUsage.sumOf { AppUsageAggregator.minutesFromMs(it.foregroundMs) }
        // "Restricted" = locked (tiers) or carrying a configured daily limit.
        val restrictedPackages =
            facts.restrictedLimits.map { it.packageName }.toSet() + facts.lockedPackages
        val earningPackages = facts.earnRules.filter { it.enabled }.map { it.packageName }.toSet()
        val usageByPackage = facts.usage.associateBy { it.packageName }
        val averageMinutes = average(facts.recentDailyTotals)

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

        // Phase 5: deterministic limit classification from the single limits
        // source (app_restrictions). Bands: APPROACHING = usage in [80%, 100%)
        // of the limit (integer math, no float drift); AT_LIMIT = exactly at
        // the limit; EXCEEDED = over (existing behavior, preserved).
        val approaching = mutableListOf<AppLimitStatus>()
        val atLimit = mutableListOf<AppLimitStatus>()
        val exceeded = facts.restrictedLimits
            .filter { it.limitMinutes >= 0 }
            .mapNotNull { limit ->
                val minutes = usageByPackage[limit.packageName]
                    ?.let { AppUsageAggregator.minutesFromMs(it.foregroundMs) } ?: 0
                val appLabel = facts.labels[limit.packageName] ?: limit.packageName
                when {
                    minutes > limit.limitMinutes -> ExceededAppUsage(
                        packageName = limit.packageName,
                        appLabel = appLabel,
                        minutes = minutes,
                        limitMinutes = limit.limitMinutes,
                        overLimitMinutes = minutes - limit.limitMinutes,
                    )
                    minutes == limit.limitMinutes -> {
                        atLimit += AppLimitStatus(
                            packageName = limit.packageName,
                            appLabel = appLabel,
                            minutes = minutes,
                            limitMinutes = limit.limitMinutes,
                            remainingMinutes = 0,
                        )
                        null
                    }
                    minutes * 5 >= limit.limitMinutes * 4 -> {
                        approaching += AppLimitStatus(
                            packageName = limit.packageName,
                            appLabel = appLabel,
                            minutes = minutes,
                            limitMinutes = limit.limitMinutes,
                            remainingMinutes = limit.limitMinutes - minutes,
                        )
                        null
                    }
                    else -> null
                }
            }
            .sortedByDescending { it.overLimitMinutes }

        val earningApps = facts.usage
            .filter { it.packageName in earningPackages }
            .sortedByDescending { it.foregroundMs }
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

        // Weekly trend, oldest first. recentDailyTotals[0] is today.
        val weeklyTrend = facts.recentDailyTotals
            .mapIndexed { daysAgo, minutes -> DailyUsage(facts.epochDay - daysAgo, minutes) }
            .sortedBy { it.epochDay }

        return ScreenTimeSummary(
            epochDay = facts.epochDay,
            totalScreenTimeMinutes = totalMinutes,
            previousDayScreenTimeMinutes = previousDayMinutes,
            averageDailyScreenTimeMinutes = averageMinutes,
            restrictedAppMinutes = restrictedMinutes,
            earningAppMinutes = earningMinutes,
            earnedMinutes = facts.earnedMinutes,
            spentMinutes = facts.spentMinutes,
            topApps = topApps,
            exceededApps = exceeded,
            recentUsageChange = change(totalMinutes, previousDayMinutes),
            walletBalanceMinutes = facts.walletBalanceMinutes,
            approachingApps = approaching.sortedBy { it.remainingMinutes },
            atLimitApps = atLimit.sortedBy { it.packageName },
            averageUsageChange = change(totalMinutes, averageMinutes),
            weeklyTrend = weeklyTrend,
            earningApps = earningApps,
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
