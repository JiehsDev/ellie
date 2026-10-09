package com.example.jikan.ui.habits

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.jikan.data.AppDatabase
import com.example.jikan.data.EarnRule
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

data class CalendarDay(
    val date: LocalDate,
    val minutes: Int,
    val label: String, // "32m", "Rest", "Pause", or day number
    val isToday: Boolean,
    val isActive: Boolean,
)

data class HabitPractice(
    val id: String, // "flashcards" or "rule:{id}"
    val name: String,
    val subtitle: String,
    val consecutiveDays: Int,
    val todayMinutes: Int,
    val todayCredits: Int,
    val consistencyPercent: Int,
    val isRestDay: Boolean,
    val iconType: IconType,
)

enum class IconType { FLASHCARDS, LANGUAGE, READING }

data class HabitsUiState(
    val streakDays: Int = 0,
    val cycleTotalText: String = "",
    val longestRunDays: Int = 0,
    val periodStartText: String = "",
    val periodEndText: String = "",
    val calendarDays: List<CalendarDay> = emptyList(),
    val todayMinutes: Int = 0,
    val habits: List<HabitPractice> = emptyList(),
    val reflection: String = "",
    val isLoading: Boolean = true,
)

class HabitsViewModel(application: Application) : AndroidViewModel(application) {
    private val db = AppDatabase.getInstance(application)
    private val zone = ZoneId.systemDefault()
    private val cycleDays = 28

    private val _state = MutableStateFlow(HabitsUiState())
    val state: StateFlow<HabitsUiState> = _state.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            val today = LocalDate.now(zone)
            val startDate = today.minusDays(cycleDays - 1L)
            val sessions = db.sessionDao().getAllOnce()
            val wallet = db.walletDao().get()
            val earnRules = db.earnRuleDao().observeAll().first()
            val transactions = db.walletTransactionDao().observeAll().first()

            // Minutes per day from sessions.
            val minutesByDay = sessions.groupBy {
                Instant.ofEpochMilli(it.startedAt).atZone(zone).toLocalDate()
            }.mapValues { (_, list) -> list.sumOf { it.durationMinutes } }

            // Calendar days.
            val calendarDays = (0 until cycleDays).map { i ->
                val date = startDate.plusDays(i.toLong())
                val minutes = minutesByDay[date] ?: 0
                val isToday = date == today
                CalendarDay(
                    date = date,
                    minutes = minutes,
                    label = when {
                        isToday -> "${date.dayOfMonth}"
                        minutes > 0 -> "${minutes}m"
                        // Past days with no activity: Rest (weekend) or Pause (weekday).
                        date.isBefore(today) -> {
                            val dow = date.dayOfWeek.value
                            if (dow >= 6) "Rest" else "Pause"
                        }
                        else -> "${date.dayOfMonth}"
                    },
                    isToday = isToday,
                    isActive = minutes > 0,
                )
            }

            // Streak and longest run from session days.
            val activeDays = minutesByDay.keys.sorted()
            val longestRun = computeLongestRun(activeDays)
            val streakDays = wallet?.currentStreakDays ?: 0
            val activeInCycle = (0 until cycleDays).count { i ->
                val d = startDate.plusDays(i.toLong())
                (minutesByDay[d] ?: 0) > 0
            }

            // Habit practices.
            val habits = buildHabits(
                today = today,
                startDate = startDate,
                sessions = sessions,
                earnRules = earnRules,
                transactions = transactions,
                minutesByDay = minutesByDay,
            )

            val todayMinutes = minutesByDay[today] ?: 0
            val dateFmt = DateTimeFormatter.ofPattern("MMM d", Locale.US)

            _state.update {
                it.copy(
                    streakDays = streakDays,
                    cycleTotalText = "$activeInCycle of ${cycleDays}d",
                    longestRunDays = longestRun,
                    periodStartText = startDate.format(dateFmt),
                    periodEndText = "${today.format(dateFmt)} (Today)",
                    calendarDays = calendarDays,
                    todayMinutes = todayMinutes,
                    habits = habits,
                    reflection = buildReflection(habits, streakDays),
                    isLoading = false,
                )
            }
        }
    }

    private fun computeLongestRun(activeDays: List<LocalDate>): Int {
        if (activeDays.isEmpty()) return 0
        var longest = 1
        var current = 1
        for (i in 1 until activeDays.size) {
            if (activeDays[i] == activeDays[i - 1].plusDays(1)) {
                current++
                longest = maxOf(longest, current)
            } else {
                current = 1
            }
        }
        return longest
    }

    private fun consecutiveDays(today: LocalDate, activeDays: Set<LocalDate>): Int {
        var count = 0
        var d = today
        // Allow today to be inactive (rest day) without breaking the count.
        if (d !in activeDays) d = d.minusDays(1)
        while (d in activeDays) {
            count++
            d = d.minusDays(1)
        }
        return count
    }

    private fun buildHabits(
        today: LocalDate,
        startDate: LocalDate,
        sessions: List<Session>,
        earnRules: List<EarnRule>,
        transactions: List<WalletTransaction>,
        minutesByDay: Map<LocalDate, Int>,
    ): List<HabitPractice> {
        val result = mutableListOf<HabitPractice>()

        // Flashcards habit from sessions.
        val sessionDays = sessions.map {
            Instant.ofEpochMilli(it.startedAt).atZone(zone).toLocalDate()
        }.toSet()
        val flashStreak = consecutiveDays(today, sessionDays)
        val todaySessions = sessions.filter {
            Instant.ofEpochMilli(it.startedAt).atZone(zone).toLocalDate() == today
        }
        val todayMin = todaySessions.sumOf { it.durationMinutes }
        val todayCredits = todaySessions.sumOf { it.creditsEarned }
        val flashActiveDays = (0 until cycleDays).count { i ->
            startDate.plusDays(i.toLong()) in sessionDays
        }
        val flashConsistency = if (cycleDays > 0) flashActiveDays * 100 / cycleDays else 0
        val totalCards = sessions.sumOf { it.questionsTotal }
        result.add(
            HabitPractice(
                id = "flashcards",
                name = "Flashcard Reviews",
                subtitle = "$flashStreak days consecutive • ${todaySessions.sumOf { it.questionsTotal }} cards",
                consecutiveDays = flashStreak,
                todayMinutes = todayMin,
                todayCredits = todayCredits,
                consistencyPercent = flashConsistency,
                isRestDay = todayMin == 0,
                iconType = IconType.FLASHCARDS,
            )
        )

        // Earning apps as habits.
        earnRules.filter { it.enabled }.forEach { rule ->
            val ruleTx = transactions.filter {
                it.type == WalletTransactionType.EARN &&
                    it.source == WalletTransactionSource.EARNING_APP &&
                    it.packageName == rule.packageName &&
                    it.epochDay >= startDate.toEpochDay()
            }
            val ruleDays = ruleTx.map { LocalDate.ofEpochDay(it.epochDay) }.toSet()
            val ruleStreak = consecutiveDays(today, ruleDays)
            val todayRuleMin = 0 // From progress; simplified.
            val todayRuleCredits = ruleTx.filter {
                it.epochDay == today.toEpochDay()
            }.sumOf { it.minutes }
            val ruleActiveDays = ruleDays.size
            val ruleConsistency = if (cycleDays > 0) ruleActiveDays * 100 / cycleDays else 0
            val appName = rule.label.ifBlank {
                rule.packageName.split(".").lastOrNull()?.replaceFirstChar { c -> c.uppercase() }
                    ?: rule.packageName
            }
            result.add(
                HabitPractice(
                    id = "rule:${rule.id}",
                    name = appName,
                    subtitle = "$ruleStreak days consecutive • ${rule.requiredMinutes}m practice",
                    consecutiveDays = ruleStreak,
                    todayMinutes = todayRuleMin,
                    todayCredits = todayRuleCredits,
                    consistencyPercent = ruleConsistency,
                    isRestDay = todayRuleCredits == 0,
                    iconType = IconType.LANGUAGE,
                )
            )
        }

        return result
    }

    private fun buildReflection(habits: List<HabitPractice>, streakDays: Int): String {
        val active = habits.count { !it.isRestDay }
        return if (habits.isEmpty()) {
            "No habits tracked yet. Complete a review to start your rhythm."
        } else {
            "Your $streakDays-day rhythm shows steady dedication. " +
                "$active of ${habits.size} practices active today."
        }
    }

    /**
     * Logs a manual practice session. For flashcards, creates a Session
     * record with estimated questions (2/min) at 80% accuracy, crediting
     * via the real formula. For earning apps, records practice time
     * against the rule.
     */
    fun logManualSession(habitId: String, minutes: Int, onDone: () -> Unit) {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            if (habitId == "flashcards") {
                val questions = minutes * 2
                val correct = (questions * 0.8).toInt()
                val credits = CreditCalculator.creditsEarned(correct, questions)
                db.sessionDao().insert(
                    Session(
                        startedAt = now - minutes * 60000L,
                        completedAt = now,
                        questionsTotal = questions,
                        questionsCorrect = correct,
                        creditsEarned = credits,
                        isPerfect = false,
                        triggeredByPackage = null,
                    )
                )
                // Record the wallet credit.
                db.walletTransactionDao().insert(
                    WalletTransaction(
                        type = WalletTransactionType.EARN,
                        minutes = credits,
                        balanceAfter = 0, // Updated by wallet logic.
                        createdAtMs = now,
                        epochDay = LocalDate.now(zone).toEpochDay(),
                        note = "Manual practice session",
                        source = WalletTransactionSource.UNKNOWN,
                    )
                )
            } else if (habitId.startsWith("rule:")) {
                val ruleId = habitId.removePrefix("rule:").toLongOrNull() ?: return@launch
                val rule = db.earnRuleDao().observeAll().first().find { it.id == ruleId }
                    ?: return@launch
                // Credit proportional to the rule ratio, capped by daily limit.
                val credits = (minutes * rule.rewardMinutes / rule.requiredMinutes.coerceAtLeast(1))
                    .coerceAtMost(rule.dailyLimitMinutes)
                db.walletTransactionDao().insert(
                    WalletTransaction(
                        type = WalletTransactionType.EARN,
                        minutes = credits,
                        balanceAfter = 0,
                        createdAtMs = now,
                        epochDay = LocalDate.now(zone).toEpochDay(),
                        packageName = rule.packageName,
                        note = "Manual ${rule.label.ifBlank { "practice" }}",
                        source = WalletTransactionSource.EARNING_APP,
                        ruleId = ruleId,
                        qualifyingMinutes = minutes,
                    )
                )
            }
            refresh()
            onDone()
        }
    }

    fun exportCsv(): String {
        val s = _state.value
        val header = "date,minutes"
        val rows = s.calendarDays.map { d -> "${d.date},${d.minutes}" }
        return (listOf(header) + rows).joinToString("\n")
    }
}
