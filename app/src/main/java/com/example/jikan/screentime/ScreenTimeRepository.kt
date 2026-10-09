package com.example.jikan.screentime

import com.example.jikan.data.AppDatabase
import com.example.jikan.data.WalletTransactionType
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.time.ZoneId

sealed interface ScreenTimeSummaryResult {
    data class Available(val summary: ScreenTimeSummary) : ScreenTimeSummaryResult
    data object MissingUsageAccess : ScreenTimeSummaryResult
}

class ScreenTimeRepository(
    private val db: AppDatabase,
    private val usageStatsDataSource: UsageStatsDataSource,
    private val appLabelResolver: AppLabelResolver,
    private val zoneId: ZoneId = ZoneId.systemDefault(),
) {
    suspend fun summaryFor(date: LocalDate = LocalDate.now(zoneId)): ScreenTimeSummaryResult {
        if (!usageStatsDataSource.hasUsageAccess()) {
            return ScreenTimeSummaryResult.MissingUsageAccess
        }

        val todayWindow = windowFor(date)
        val previousWindow = windowFor(date.minusDays(1))
        val todayUsage = usageStatsDataSource.queryUsage(todayWindow.first, todayWindow.second)
        val previousUsage = usageStatsDataSource.queryUsage(previousWindow.first, previousWindow.second)
        val recentTotals = (0 until RECENT_DAY_COUNT).map { daysAgo ->
            val day = date.minusDays(daysAgo.toLong())
            val window = windowFor(day)
            usageStatsDataSource.queryUsage(window.first, window.second)
                .sumOf { AppUsageAggregator.minutesFromMs(it.foregroundMs) }
        }

        val lockedApps = db.lockedAppDao().observeLocked().first()
        val earnRules = db.earnRuleDao().observeEnabled().first()
        val packages = (todayUsage.map { it.packageName } +
            lockedApps.map { it.packageName } +
            earnRules.map { it.packageName }).toSet()
        val labels = appLabelResolver.labelsFor(packages)
        val epochDay = date.toEpochDay()

        val earned = db.walletTransactionDao().sumMinutesByTypeForDay(WalletTransactionType.EARN, epochDay)
        val spent = db.walletTransactionDao().sumMinutesByTypeForDay(WalletTransactionType.SPEND, epochDay)

        val summary = ScreenTimeCalculator.summarize(
            ScreenTimeFacts(
                epochDay = epochDay,
                usage = todayUsage,
                previousDayUsage = previousUsage,
                recentDailyTotals = recentTotals,
                restrictedLimits = lockedApps.map {
                    RestrictedAppLimit(
                        packageName = it.packageName,
                        limitMinutes = limitForTier(it.tier),
                    )
                },
                earnRules = earnRules,
                earnedMinutes = earned,
                spentMinutes = spent,
                labels = labels,
            )
        )
        return ScreenTimeSummaryResult.Available(summary)
    }

    private fun windowFor(date: LocalDate): Pair<Long, Long> {
        val start = date.atStartOfDay(zoneId).toInstant().toEpochMilli()
        val end = date.plusDays(1).atStartOfDay(zoneId).toInstant().toEpochMilli()
        return start to end
    }

    private fun limitForTier(tier: com.example.jikan.data.LockTier): Int = when (tier) {
        com.example.jikan.data.LockTier.LIGHT -> 60
        com.example.jikan.data.LockTier.AVERAGE -> 30
        com.example.jikan.data.LockTier.EXTREME -> 0
    }

    private companion object {
        private const val RECENT_DAY_COUNT = 7
    }
}
