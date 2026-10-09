package com.example.jikan.screentime

import kotlin.math.ceil

data class UsageInterval(
    val packageName: String,
    val startMs: Long,
    val endMs: Long,
)

data class RawAppUsage(
    val packageName: String,
    val foregroundMs: Long,
)

object AppUsageAggregator {
    private const val MIN_SESSION_MS = 5_000L

    fun aggregateIntervals(
        intervals: List<UsageInterval>,
        windowStartMs: Long,
        windowEndMs: Long,
        minSessionMs: Long = MIN_SESSION_MS,
    ): List<RawAppUsage> {
        if (windowEndMs <= windowStartMs) return emptyList()

        return intervals
            .asSequence()
            .filter { it.packageName.isNotBlank() && it.endMs > it.startMs }
            .mapNotNull { interval ->
                val clippedStart = maxOf(interval.startMs, windowStartMs)
                val clippedEnd = minOf(interval.endMs, windowEndMs)
                val duration = clippedEnd - clippedStart
                if (duration < minSessionMs) null else interval.packageName to duration
            }
            .groupBy({ it.first }, { it.second })
            .map { (packageName, durations) -> RawAppUsage(packageName, durations.sum()) }
            .sortedByDescending { it.foregroundMs }
    }

    fun minutesFromMs(ms: Long): Int {
        if (ms <= 0L) return 0
        return ceil(ms / 60_000.0).toInt()
    }
}
