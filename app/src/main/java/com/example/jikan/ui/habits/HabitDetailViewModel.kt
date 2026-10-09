package com.example.jikan.ui.habits

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.jikan.data.AppDatabase
import com.example.jikan.data.Session
import com.example.jikan.data.WalletTransaction
import com.example.jikan.data.WalletTransactionSource
import com.example.jikan.data.WalletTransactionType
import com.example.jikan.study.CreditCalculator
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

data class PracticeLog(
    val dateLabel: String,
    val detailLabel: String,
    val creditLabel: String?,
    val isRestDay: Boolean,
    val isToday: Boolean,
)

data class HeatBar(val date: LocalDate, val minutes: Int)

data class HabitDetailUiState(
    val habitName: String = "",
    val habitKicker: String = "",
    val habitGlyph: String = "",
    val activeDaysText: String = "",
    val todayCompleted: Boolean = false,
    val todayDateLabel: String = "",
    val sessionVolumeText: String = "",
    val sessionTimeText: String = "",
    val creditEarnedText: String = "",
    val retentionRateText: String = "",
    val pacingRewardText: String = "",
    val rhythmPercent: Int = 0,
    val heatBars: List<HeatBar> = emptyList(),
    val heatSummary: String = "",
    val graceActive: Boolean = false,
    val graceText: String = "",
    val recentLogs: List<PracticeLog> = emptyList(),
    val isLoading: Boolean = true,
)

class HabitDetailViewModel(application: Application) : AndroidViewModel(application) {
    private val db = AppDatabase.getInstance(application)
    private val zone = ZoneId.systemDefault()

    private val _state = MutableStateFlow(HabitDetailUiState())
    val state: StateFlow<HabitDetailUiState> = _state.asStateFlow()

    fun load(habitId: String) {
        viewModelScope.launch {
            val today = LocalDate.now(zone)
            val sessions = db.sessionDao().getAllOnce()
            val wallet = db.walletDao().get()
            val streakDays = wallet?.currentStreakDays ?: 0

            if (habitId == "flashcards") {
                loadFlashcards(today, sessions, streakDays)
            } else if (habitId.startsWith("rule:")) {
                loadEarningApp(habitId, today, streakDays)
            }
        }
    }

    private suspend fun loadFlashcards(
        today: LocalDate,
        sessions: List<Session>,
        streakDays: Int,
    ) {
        val todaySessions = sessions.filter {
            Instant.ofEpochMilli(it.startedAt).atZone(zone).toLocalDate() == today
        }
        val todayCards = todaySessions.sumOf { it.questionsTotal }
        val todayMinutes = todaySessions.sumOf { it.durationMinutes }
        val todayCredits = todaySessions.sumOf { it.creditsEarned }
        val lastSession = todaySessions.maxByOrNull { it.startedAt }

        // Retention: avg accuracy of last 10 sessions.
        val recent = sessions.sortedByDescending { it.startedAt }.take(10)
        val retention = if (recent.isNotEmpty()) {
            val totalQ = recent.sumOf { it.questionsTotal }
            val totalC = recent.sumOf { it.questionsCorrect }
            if (totalQ > 0) totalC * 100 / totalQ else 0
        } else 0

        // 30-day heatline.
        val heatBars = (0 until 30).map { i ->
            val date = today.minusDays(29 - i.toLong())
            val mins = sessions.filter {
                Instant.ofEpochMilli(it.startedAt).atZone(zone).toLocalDate() == date
            }.sumOf { it.durationMinutes }
            HeatBar(date, mins)
        }
        val activeCount = heatBars.count { it.minutes > 0 }
        val rhythmPct = activeCount * 100 / 30

        // Grace window: studied yesterday (or day before with streak>=3) but not today.
        val yesterday = today.minusDays(1)
        val studiedYesterday = sessions.any {
            Instant.ofEpochMilli(it.startedAt).atZone(zone).toLocalDate() == yesterday
        }
        val graceActive = !todaySessions.isNotEmpty() && studiedYesterday

        // Recent logs: last 4 days.
        val recentLogs = (0 until 4).map { i ->
            val date = today.minusDays(i.toLong())
            val daySessions = sessions.filter {
                Instant.ofEpochMilli(it.startedAt).atZone(zone).toLocalDate() == date
            }
            val dateFmt = DateTimeFormatter.ofPattern("MMM d", Locale.US)
            if (daySessions.isEmpty()) {
                PracticeLog(
                    dateLabel = when (i) {
                        0 -> "Today, ${date.format(dateFmt)}"
                        1 -> "Yesterday, ${date.format(dateFmt)}"
                        else -> date.format(dateFmt)
                    } + " • Rest Day",
                    detailLabel = "0 reviews completed",
                    creditLabel = null,
                    isRestDay = true,
                    isToday = i == 0,
                )
            } else {
                val cards = daySessions.sumOf { it.questionsTotal }
                val mins = daySessions.sumOf { it.durationMinutes }
                val secs = daySessions.sumOf { it.durationSeconds % 60 }
                val credits = daySessions.sumOf { it.creditsEarned }
                val acc = run {
                    val tq = daySessions.sumOf { it.questionsTotal }
                    val tc = daySessions.sumOf { it.questionsCorrect }
                    if (tq > 0) tc * 100 / tq else 0
                }
                val timeFmt = DateTimeFormatter.ofPattern("HH:mm", Locale.US)
                val timeStr = Instant.ofEpochMilli(daySessions.maxBy { it.startedAt }.startedAt)
                    .atZone(zone).format(timeFmt)
                PracticeLog(
                    dateLabel = when (i) {
                        0 -> "Today, ${date.format(dateFmt)}"
                        1 -> "Yesterday, ${date.format(dateFmt)}"
                        else -> date.format(dateFmt)
                    } + " • $timeStr",
                    detailLabel = "$cards cards • ${mins}m ${secs}s • $acc% recall",
                    creditLabel = "+${credits}m",
                    isRestDay = false,
                    isToday = i == 0,
                )
            }
        }

        val todayFmt = DateTimeFormatter.ofPattern("EEE, MMM d", Locale.US)
        _state.update {
            it.copy(
                habitName = "Japanese N3 Kanji",
                habitKicker = "SPACED REPETITION • HIGH PRIORITY",
                habitGlyph = "漢",
                activeDaysText = "$streakDays Days Active",
                todayCompleted = todaySessions.isNotEmpty(),
                todayDateLabel = today.format(todayFmt),
                sessionVolumeText = "$todayCards cards",
                sessionTimeText = if (lastSession != null) {
                    val t = Instant.ofEpochMilli(lastSession.startedAt).atZone(zone)
                        .format(DateTimeFormatter.ofPattern("HH:mm", Locale.US))
                    "${todayMinutes}m at $t"
                } else "No session yet",
                creditEarnedText = "+${todayCredits}m unlock",
                retentionRateText = "$retention% retention rate",
                pacingRewardText = "1.10x pacing reward",
                rhythmPercent = rhythmPct,
                heatBars = heatBars,
                heatSummary = "$activeCount active sessions • ${30 - activeCount} mindful rest days",
                graceActive = graceActive,
                graceText = "Completing your due cards tomorrow preserves uninterrupted rhythm without penalty.",
                recentLogs = recentLogs,
                isLoading = false,
            )
        }
    }

    private suspend fun loadEarningApp(habitId: String, today: LocalDate, streakDays: Int) {
        val ruleId = habitId.removePrefix("rule:").toLongOrNull()
        val rule = db.earnRuleDao().observeAll().first().find { it.id == ruleId }
        val transactions = db.walletTransactionDao().observeAll().first().filter {
            it.type == WalletTransactionType.EARN &&
                it.source == WalletTransactionSource.EARNING_APP &&
                it.ruleId == ruleId
        }
        val todayTx = transactions.filter { it.epochDay == today.toEpochDay() }
        val todayMinutes = todayTx.sumOf { it.qualifyingMinutes }
        val todayCredits = todayTx.sumOf { it.minutes }

        val heatBars = (0 until 30).map { i ->
            val date = today.minusDays(29 - i.toLong())
            val mins = transactions.filter { it.epochDay == date.toEpochDay() }
                .sumOf { it.qualifyingMinutes }
            HeatBar(date, mins)
        }
        val activeCount = heatBars.count { it.minutes > 0 }
        val rhythmPct = activeCount * 100 / 30

        val appName = rule?.label?.ifBlank { null }
            ?: rule?.packageName?.split(".")?.lastOrNull()?.replaceFirstChar { c -> c.uppercase() }
            ?: "Practice"

        _state.update {
            it.copy(
                habitName = appName,
                habitKicker = "EARNING ACTIVITY • ${rule?.requiredMinutes ?: 0}M PRACTICE",
                habitGlyph = appName.firstOrNull()?.uppercase() ?: "•",
                activeDaysText = "$streakDays Days Active",
                todayCompleted = todayTx.isNotEmpty(),
                todayDateLabel = today.format(DateTimeFormatter.ofPattern("EEE, MMM d", Locale.US)),
                sessionVolumeText = "${todayMinutes}m practiced",
                sessionTimeText = "Today",
                creditEarnedText = "+${todayCredits}m unlock",
                retentionRateText = "${rule?.multiplier ?: 1.0}x reward rate",
                pacingRewardText = "1:1 earn ratio",
                rhythmPercent = rhythmPct,
                heatBars = heatBars,
                heatSummary = "$activeCount active sessions • ${30 - activeCount} mindful rest days",
                graceActive = false,
                graceText = "",
                recentLogs = emptyList(),
                isLoading = false,
            )
        }
    }
}
