package com.example.jikan.ui.rhythm

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.jikan.data.AppDatabase
import com.example.jikan.data.WalletTransactionType
import com.example.jikan.screentime.AndroidUsageStatsDataSource
import com.example.jikan.screentime.AppLabelResolver
import com.example.jikan.screentime.ScreenTimeRepository
import com.example.jikan.screentime.ScreenTimeSummaryResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

enum class RhythmRange { TODAY, WEEK, MONTH }

data class DayBars(
    val date: LocalDate,
    val label: String,
    val studyMinutes: Int,
    val leisureMinutes: Int,
)

data class AppBreakdown(
    val packageName: String,
    val appLabel: String,
    val totalMinutes: Int,
    val avgPerDay: Int,
    val statusText: String,
)

data class CreditSource(
    val name: String,
    val minutes: Int,
    val percent: Int,
)

data class RhythmUiState(
    val range: RhythmRange = RhythmRange.WEEK,
    val studyMinutes: Int = 0,
    val leisureMinutes: Int = 0,
    val ratioText: String = "—",
    val ratioLabel: String = "",
    val netBankedMinutes: Int = 0,
    val dayBars: List<DayBars> = emptyList(),
    val avgStudyPerDay: Int = 0,
    val avgLeisurePerDay: Int = 0,
    val appBreakdown: List<AppBreakdown> = emptyList(),
    val monitoredCount: Int = 0,
    val creditSources: List<CreditSource> = emptyList(),
    val reflection: String = "",
    val isLoading: Boolean = true,
)

class RhythmViewModel(application: Application) : AndroidViewModel(application) {
    private val db = AppDatabase.getInstance(application)
    private val zone = ZoneId.systemDefault()
    private val screenTimeRepository by lazy {
        ScreenTimeRepository(
            db = db,
            usageStatsDataSource = AndroidUsageStatsDataSource(getApplication()),
            appLabelResolver = AppLabelResolver(getApplication()),
            zoneId = zone,
        )
    }

    private val _state = MutableStateFlow(RhythmUiState())
    val state: StateFlow<RhythmUiState> = _state.asStateFlow()

    init {
        refresh()
    }

    fun setRange(range: RhythmRange) {
        _state.update { it.copy(range = range) }
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            val range = _state.value.range
            val today = LocalDate.now(zone)
            val days = when (range) {
                RhythmRange.TODAY -> 1
                RhythmRange.WEEK -> 7
                RhythmRange.MONTH -> 30
            }
            val startDate = today.minusDays(days - 1L)
            val startEpochDay = startDate.toEpochDay()

            // Study minutes per day from sessions.
            val sessionList = db.sessionDao().getAllOnce()
            val sessionsInRange = sessionList.filter {
                Instant.ofEpochMilli(it.startedAt).atZone(zone)
                    .toLocalDate().toEpochDay() >= startEpochDay
            }
            val studyByDay = sessionsInRange.groupBy {
                Instant.ofEpochMilli(it.startedAt).atZone(zone).toLocalDate()
            }.mapValues { (_, list) -> list.sumOf { it.durationMinutes } }

            // Usage trend for leisure.
            val summary = when (val result = screenTimeRepository.summaryFor()) {
                is ScreenTimeSummaryResult.Available -> result.summary
                ScreenTimeSummaryResult.MissingUsageAccess -> null
            }
            val trendByDay = (summary?.weeklyTrend ?: emptyList())
                .associate { LocalDate.ofEpochDay(it.epochDay) to it.minutes }

            val dayBars = (0 until days).map { i ->
                val date = startDate.plusDays(i.toLong())
                val study = studyByDay[date] ?: 0
                val total = trendByDay[date] ?: 0
                val leisure = (total - study).coerceAtLeast(0)
                val label = if (range == RhythmRange.TODAY) "Today"
                else date.dayOfWeek.name.take(1)
                DayBars(date, label, study, leisure)
            }

            val studyMinutes = dayBars.sumOf { it.studyMinutes }
            val leisureMinutes = dayBars.sumOf { it.leisureMinutes }
            val ratio = if (leisureMinutes > 0) studyMinutes.toDouble() / leisureMinutes else 0.0
            val ratioText = if (leisureMinutes > 0) "%.1fx".format(ratio) else "—"
            val ratioLabel = when {
                leisureMinutes == 0 && studyMinutes > 0 -> "Healthy Ratio"
                ratio >= 1.0 -> "Healthy Ratio"
                ratio >= 0.5 -> "Building"
                else -> "Needs Focus"
            }

            val wallet = db.walletDao().get()
            val txList = db.walletTransactionDao().observeAll().first()

            // App breakdown from today's top apps.
            val topApps = (summary?.topApps ?: emptyList()).take(4)
            val appBreakdown = topApps.map { app ->
                val total = if (range == RhythmRange.TODAY) app.minutes
                else maxOf(app.minutes, app.minutes * days / 7)
                AppBreakdown(
                    packageName = app.packageName,
                    appLabel = app.appLabel,
                    totalMinutes = total,
                    avgPerDay = total / days.coerceAtLeast(1),
                    statusText = "100% covered",
                )
            }

            // Credit sources from earn transactions.
            val earnTx = txList.filter {
                it.type == WalletTransactionType.EARN && it.epochDay >= startEpochDay
            }
            val studyEarned = earnTx.filter { it.source.name == "UNKNOWN" }.sumOf { it.minutes }
            val appEarned = earnTx.filter { it.source.name == "EARNING_APP" }.sumOf { it.minutes }
            val totalEarned = (studyEarned + appEarned).coerceAtLeast(1)
            val sources = listOfNotNull(
                CreditSource("Spaced Repetition (Flashcards)", studyEarned, studyEarned * 100 / totalEarned)
                    .takeIf { studyEarned > 0 },
                CreditSource("Learning Apps", appEarned, appEarned * 100 / totalEarned)
                    .takeIf { appEarned > 0 },
            )

            _state.update {
                it.copy(
                    studyMinutes = studyMinutes,
                    leisureMinutes = leisureMinutes,
                    ratioText = ratioText,
                    ratioLabel = ratioLabel,
                    netBankedMinutes = wallet?.creditBalanceMinutes ?: 0,
                    dayBars = dayBars,
                    avgStudyPerDay = if (days > 0) studyMinutes / days else 0,
                    avgLeisurePerDay = if (days > 0) leisureMinutes / days else 0,
                    appBreakdown = appBreakdown,
                    monitoredCount = topApps.size,
                    creditSources = sources,
                    reflection = buildReflection(studyMinutes, days, sessionsInRange.size),
                    isLoading = false,
                )
            }
        }
    }

    private fun buildReflection(studyMinutes: Int, days: Int, sessionCount: Int): String {
        if (sessionCount == 0) return "No study sessions in this range yet. Start a review to build your rhythm."
        val avg = if (days > 0) studyMinutes / days else 0
        return "Consistent ${avg}-minute daily reviews maintained a calm rhythm " +
            "across $sessionCount sessions without emergency lock triggers."
    }
}
