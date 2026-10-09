package com.example.jikan.ui.deepdive

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.jikan.data.AppDatabase
import com.example.jikan.data.WalletTransactionType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

enum class DeepDiveRange { FOUR_WEEK, MONTHLY, ALL_TIME }

data class WeekStats(
    val label: String,
    val dateRange: String,
    val daysActive: Int,
    val daysTotal: Int,
    val isCurrent: Boolean,
    val avgStudyMinutes: Int,
    val allowanceMinutes: Int,
    val retentionPercent: Int,
)

data class DeepDiveUiState(
    val range: DeepDiveRange = DeepDiveRange.FOUR_WEEK,
    val activePeriodText: String = "",
    val weeks: List<WeekStats> = emptyList(),
    val onPacePercent: Int = 0,
    val overallRetention: Int = 0,
    val pacingInsight: String = "",
    val reflection: String = "",
    val isLoading: Boolean = true,
)

class DeepDiveViewModel(application: Application) : AndroidViewModel(application) {
    private val db = AppDatabase.getInstance(application)
    private val zone = ZoneId.systemDefault()

    private val _state = MutableStateFlow(DeepDiveUiState())
    val state: StateFlow<DeepDiveUiState> = _state.asStateFlow()

    init {
        refresh()
    }

    fun setRange(range: DeepDiveRange) {
        _state.update { it.copy(range = range) }
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            val range = _state.value.range
            val today = LocalDate.now(zone)
            val sessions = db.sessionDao().getAllOnce()
            val transactions = db.walletTransactionDao().observeAll().first()

            val weeks = when (range) {
                DeepDiveRange.FOUR_WEEK -> {
                    // Four 7-day blocks ending today.
                    (0 until 4).map { w ->
                        val end = today.minusDays(w * 7L)
                        val start = end.minusDays(6)
                        buildWeek(
                            label = if (w == 0) "Week 4 (Current)" else "Week ${4 - w}",
                            start = start, end = end, isCurrent = w == 0,
                            sessions = sessions, transactions = transactions,
                        )
                    }.reversed()
                }
                DeepDiveRange.MONTHLY -> {
                    // Last 4 calendar months.
                    (0 until 4).map { m ->
                        val month = today.minusMonths(m.toLong())
                        val start = month.withDayOfMonth(1)
                        val end = if (m == 0) today else month.withDayOfMonth(month.lengthOfMonth())
                        buildWeek(
                            label = month.format(DateTimeFormatter.ofPattern("MMM yyyy", Locale.US)),
                            start = start, end = end, isCurrent = m == 0,
                            sessions = sessions, transactions = transactions,
                        )
                    }.reversed()
                }
                DeepDiveRange.ALL_TIME -> {
                    val firstDay = sessions.minOfOrNull {
                        Instant.ofEpochMilli(it.startedAt).atZone(zone).toLocalDate()
                    } ?: today
                    listOf(
                        buildWeek(
                            label = "All Time",
                            start = firstDay, end = today, isCurrent = true,
                            sessions = sessions, transactions = transactions,
                        )
                    )
                }
            }

            val totalDays = weeks.sumOf { it.daysTotal }.coerceAtLeast(1)
            val activeDays = weeks.sumOf { it.daysActive }
            val onPace = (activeDays * 100 / totalDays).coerceIn(0, 100)
            val overallRetention = if (weeks.isNotEmpty()) {
                weeks.map { it.retentionPercent }.average().toInt()
            } else 0

            val periodText = when (range) {
                DeepDiveRange.FOUR_WEEK -> {
                    val start = today.minusDays(27)
                    "${start.format(DateTimeFormatter.ofPattern("MMM d", Locale.US))} – " +
                        "${today.format(DateTimeFormatter.ofPattern("MMM d", Locale.US))} (28 Days)"
                }
                DeepDiveRange.MONTHLY -> "Last 4 months"
                DeepDiveRange.ALL_TIME -> "Since you started"
            }

            _state.update {
                it.copy(
                    activePeriodText = periodText,
                    weeks = weeks,
                    onPacePercent = onPace,
                    overallRetention = overallRetention,
                    pacingInsight = buildInsight(weeks),
                    reflection = buildReflection(weeks, onPace),
                    isLoading = false,
                )
            }
        }
    }

    private fun buildWeek(
        label: String,
        start: LocalDate,
        end: LocalDate,
        isCurrent: Boolean,
        sessions: List<com.example.jikan.data.StudySession>,
        transactions: List<com.example.jikan.data.WalletTransaction>,
    ): WeekStats {
        val startEpoch = start.toEpochDay()
        val endEpoch = end.toEpochDay()
        val weekSessions = sessions.filter {
            val d = Instant.ofEpochMilli(it.startedAt).atZone(zone).toLocalDate().toEpochDay()
            d in startEpoch..endEpoch
        }
        val daysActive = weekSessions.map {
            Instant.ofEpochMilli(it.startedAt).atZone(zone).toLocalDate()
        }.toSet().size
        val daysTotal = (endEpoch - startEpoch + 1).toInt().coerceAtLeast(1)
        val avgStudy = if (weekSessions.isNotEmpty()) {
            weekSessions.sumOf { it.durationMinutes } / weekSessions.size
        } else 0
        val allowance = transactions.filter {
            it.type == WalletTransactionType.EARN && it.epochDay in startEpoch..endEpoch
        }.sumOf { it.minutes }
        // Retention proxy: session accuracy.
        val totalQ = weekSessions.sumOf { it.questionsTotal }
        val totalC = weekSessions.sumOf { it.questionsCorrect }
        val retention = if (totalQ > 0) (totalC * 100 / totalQ) else 0

        val dateFmt = DateTimeFormatter.ofPattern("MMM d", Locale.US)
        val dateRange = "${start.format(dateFmt)}–${end.format(dateFmt)}"
        return WeekStats(
            label = label,
            dateRange = dateRange,
            daysActive = daysActive,
            daysTotal = daysTotal,
            isCurrent = isCurrent,
            avgStudyMinutes = avgStudy,
            allowanceMinutes = allowance,
            retentionPercent = retention,
        )
    }

    private fun buildInsight(weeks: List<WeekStats>): String {
        if (weeks.isEmpty() || weeks.all { it.daysActive == 0 }) {
            return "No sessions recorded yet. Your pacing insight will appear after your first week."
        }
        return "Pacing shows consistent engagement. Variations align naturally with your daily rhythm."
    }

    private fun buildReflection(weeks: List<WeekStats>, onPace: Int): String {
        val totalSessions = weeks.sumOf { it.daysActive }
        return if (totalSessions == 0) {
            "Your deep dive is empty. Complete a study session to start tracking."
        } else {
            "$onPace% on pace across ${weeks.size} periods. Your rhythm reflects steady, voluntary practice."
        }
    }
}
