package com.example.jikan.ui.apptrend

import android.app.Application
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.jikan.data.AppDatabase
import com.example.jikan.data.WalletTransactionType
import com.example.jikan.screentime.AndroidUsageStatsDataSource
import com.example.jikan.screentime.AppLabelResolver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.ZoneId

data class HourlyBucket(val hour: Int, val minutes: Int)

data class AppTrendUiState(
    val packageName: String = "",
    val appLabel: String = "",
    val isEnforced: Boolean = false,
    val weeklyMinutes: Int = 0,
    val weeklyLimitMinutes: Int = 0,
    val allowancePercent: Int = 0,
    val dailyAverageText: String = "",
    val sessionsOpened: Int = 0,
    val avgSessionMinutes: Double = 0.0,
    val hourlyBuckets: List<HourlyBucket> = emptyList(),
    val earnedMinutes: Int = 0,
    val studyEarnedMinutes: Int = 0,
    val appEarnedMinutes: Int = 0,
    val backedPercent: Int = 0,
    val isLoading: Boolean = true,
)

class AppTrendViewModel(application: Application) : AndroidViewModel(application) {
    private val db = AppDatabase.getInstance(application)
    private val zone = ZoneId.systemDefault()

    private val _state = MutableStateFlow(AppTrendUiState())
    val state: StateFlow<AppTrendUiState> = _state.asStateFlow()

    fun load(packageName: String) {
        viewModelScope.launch {
            val context = getApplication<Application>()
            val today = LocalDate.now(zone)
            val weekStart = today.minusDays(6)
            val startMs = weekStart.atStartOfDay(zone).toInstant().toEpochMilli()
            val endMs = System.currentTimeMillis()

            // App info + enforcement.
            val locked = db.lockedAppDao().get(packageName)
            val restriction = db.appRestrictionDao().get(packageName)
            val label = locked?.appLabel ?: restriction?.appLabel
                ?: AppLabelResolver(context).labelFor(packageName)
            val isEnforced = (locked?.isLocked == true) || (restriction?.enabled == true)
            val weeklyLimit = (restriction?.dailyLimitMinutes ?: 0) * 7

            // Weekly usage total.
            val usage = withContext(Dispatchers.IO) {
                AndroidUsageStatsDataSource(context).queryUsage(startMs, endMs)
            }
            val weeklyMinutes = usage.find { it.packageName == packageName }
                ?.totalTimeMs?.div(60000)?.toInt() ?: 0

            // Hourly buckets + session count from UsageEvents.
            val (hourly, sessions) = withContext(Dispatchers.IO) {
                queryHourlyUsage(context, packageName, startMs, endMs)
            }

            // Earned in the last 7 days.
            val txList = db.walletTransactionDao().observeAll().first()
            val startEpochDay = weekStart.toEpochDay()
            val earnTx = txList.filter {
                it.type == WalletTransactionType.EARN && it.epochDay >= startEpochDay
            }
            val studyEarned = earnTx.filter { it.source.name == "UNKNOWN" }.sumOf { it.minutes }
            val appEarned = earnTx.filter { it.source.name == "EARNING_APP" }.sumOf { it.minutes }
            val earned = studyEarned + appEarned
            val backed = if (weeklyMinutes > 0) {
                (earned * 100 / weeklyMinutes).coerceIn(0, 100)
            } else 100

            val avgSeconds = if (sessions > 0) {
                val totalSec = hourly.sumOf { it.minutes } * 60
                totalSec / sessions
            } else 0
            val dailyAvgMin = weeklyMinutes / 7
            val dailyAvgSec = (weeklyMinutes * 60 / 7) % 60

            _state.update {
                it.copy(
                    packageName = packageName,
                    appLabel = label,
                    isEnforced = isEnforced,
                    weeklyMinutes = weeklyMinutes,
                    weeklyLimitMinutes = weeklyLimit,
                    allowancePercent = if (weeklyLimit > 0) {
                        (weeklyMinutes * 100 / weeklyLimit).coerceIn(0, 100)
                    } else 0,
                    dailyAverageText = "${dailyAvgMin}m ${dailyAvgSec}s",
                    sessionsOpened = sessions,
                    avgSessionMinutes = avgSeconds / 60.0,
                    hourlyBuckets = hourly,
                    earnedMinutes = earned,
                    studyEarnedMinutes = studyEarned,
                    appEarnedMinutes = appEarned,
                    backedPercent = backed,
                    isLoading = false,
                )
            }
        }
    }

    /**
     * Builds 24 hourly foreground-minute buckets and counts app launches
     * from UsageEvents. Real system data.
     */
    private fun queryHourlyUsage(
        context: Context,
        packageName: String,
        startMs: Long,
        endMs: Long,
    ): Pair<List<HourlyBucket>, Int> {
        val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val buckets = IntArray(24)
        var sessions = 0
        try {
            val events = usm.queryEvents(startMs, endMs)
            val event = UsageEvents.Event()
            var foregroundStart: Long? = null
            while (events.hasNextEvent()) {
                events.getNextEvent(event)
                if (event.packageName != packageName) continue
                when (event.eventType) {
                    UsageEvents.Event.ACTIVITY_RESUMED -> {
                        sessions++
                        if (foregroundStart == null) foregroundStart = event.timeStamp
                    }
                    UsageEvents.Event.ACTIVITY_PAUSED,
                    UsageEvents.Event.ACTIVITY_STOPPED -> {
                        foregroundStart?.let { start ->
                            val durationMs = event.timeStamp - start
                            if (durationMs in 1..3_600_000) {
                                // Bucket by start hour.
                                val hour = java.time.Instant.ofEpochMilli(start)
                                    .atZone(zone).hour
                                buckets[hour] += (durationMs / 60000).toInt()
                            }
                        }
                        foregroundStart = null
                    }
                }
            }
            // Ongoing session.
            foregroundStart?.let { start ->
                val hour = java.time.Instant.ofEpochMilli(start).atZone(zone).hour
                buckets[hour] += ((endMs - start) / 60000).toInt().coerceAtLeast(0)
            }
        } catch (_: Exception) {
            // No usage access; return empty.
        }
        return buckets.mapIndexed { i, m -> HourlyBucket(i, m) } to sessions
    }

    fun exportCsv(): String {
        val s = _state.value
        val header = "hour,minutes"
        val rows = s.hourlyBuckets.map { "${it.hour},${it.minutes}" }
        return (listOf(
            "app,${s.appLabel},${s.packageName}",
            "weekly_minutes,${s.weeklyMinutes}",
            "sessions,${s.sessionsOpened}",
            header
        ) + rows).joinToString("\n")
    }
}
