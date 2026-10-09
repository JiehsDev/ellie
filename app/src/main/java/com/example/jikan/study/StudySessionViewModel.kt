package com.example.jikan.study

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.jikan.coach.LlamaCppEngine
import com.example.jikan.coach.RealAiCoach
import com.example.jikan.coach.StudySessionData
import com.example.jikan.data.AppDatabase
import com.example.jikan.data.Card
import com.example.jikan.data.CardSeeder
import com.example.jikan.data.DailyGoalPreset
import com.example.jikan.data.StudyRepository
import com.example.jikan.srs.RecallGrade
import com.example.jikan.srs.SrsEngine
import com.example.jikan.srs.SrsState
import com.example.jikan.widget.WidgetUpdater
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId

/**
 * Anki-style review flow: each card is shown with its answer and the user
 * grades their recall (Again/Hard/Good/Easy). Grading records the SRS
 * result and advances. When the queue is done, the session completes.
 */
class StudySessionViewModel(application: Application) : AndroidViewModel(application) {
    private val db = AppDatabase.getInstance(application)
    private val repository = StudyRepository(db.cardDao(), db.progressDao(), db.sessionDao(), db.walletDao(), db.settingsDao())

    private val _phase = MutableStateFlow<StudyPhase>(StudyPhase.Loading)
    val phase: StateFlow<StudyPhase> = _phase.asStateFlow()

    private var sessionCards: List<Card> = emptyList()
    private var sessionStartedAt: Long = 0L
    private var sessionLengthMinutes: Int = 12
    // Session-complete stats: per-card recall latency, SRS interval growth,
    // and grade counts (correct = anything but AGAIN).
    private var cardShownAtMs: Long = 0L
    private val recallDurationsMs = mutableListOf<Long>()
    private val intervalGrowthDays = mutableListOf<Double>()
    private val grades = mutableListOf<RecallGrade>()

    init {
        startSession()
    }

    fun startSession(sessionSize: Int = SESSION_SIZE) {
        viewModelScope.launch {
            _phase.value = StudyPhase.Loading
            CardSeeder.seedIfEmpty(getApplication(), db.cardDao())
            val cards = repository.buildSessionQueue(sessionSize)
            if (cards.isEmpty()) {
                _phase.value = StudyPhase.Empty
                return@launch
            }
            sessionCards = cards
            sessionStartedAt = System.currentTimeMillis()
            sessionLengthMinutes = db.settingsDao().get()?.sessionLengthMinutes ?: 12
            recallDurationsMs.clear()
            intervalGrowthDays.clear()
            grades.clear()
            showCard(0)
        }
    }

    private suspend fun showCard(index: Int) {
        val card = sessionCards[index]
        val progress = db.progressDao().getForCard(card.id)
        val state = progress?.let { SrsState(it.intervalDays, it.easeFactor, it.repetitions) }
            ?: SrsEngine.INITIAL_STATE
        val now = System.currentTimeMillis()
        val previews = RecallGrade.entries.associateWith { grade ->
            SrsEngine.gradePreviewText(state, grade, now)
        }
        val earnedToday = earnedTodayMinutes(now)
        val elapsedMin = ((now - sessionStartedAt) / 60_000L).toInt()
        cardShownAtMs = now
        _phase.value = StudyPhase.Lesson(
            cards = sessionCards,
            index = index,
            cardState = state,
            gradePreviews = previews,
            earnedTodayMinutes = earnedToday,
            unlockInMinutes = (sessionLengthMinutes - elapsedMin).coerceAtLeast(0),
            sessionLengthMinutes = sessionLengthMinutes,
        )
    }

    fun onGradeSelected(grade: RecallGrade) {
        val current = _phase.value as? StudyPhase.Lesson ?: return
        val card = current.cards[current.index]

        // Recall latency: time from card shown to grade tapped.
        if (cardShownAtMs > 0L) {
            recallDurationsMs += System.currentTimeMillis() - cardShownAtMs
            cardShownAtMs = 0L
        }
        grades += grade

        viewModelScope.launch {
            val growth = repository.recordGrade(card.id, grade)
            intervalGrowthDays += growth
            val nextIndex = current.index + 1
            if (nextIndex < current.cards.size) {
                showCard(nextIndex)
            } else {
                finishSession()
            }
        }
    }

    fun onBack() {
        // Abandoning mid-session: go back without completing.
        _phase.value = StudyPhase.Empty
    }

    private suspend fun earnedTodayMinutes(now: Long): Int {
        val zone = ZoneId.systemDefault()
        val startOfDay = Instant.ofEpochMilli(now).atZone(zone)
            .toLocalDate().atStartOfDay(zone).toInstant().toEpochMilli()
        return db.sessionDao().getSince(startOfDay).sumOf { it.creditsEarned }
    }

    private fun finishSession() {
        viewModelScope.launch {
            val total = grades.size
            val correctCount = grades.count { it != RecallGrade.AGAIN }
            val now = System.currentTimeMillis()
            val result = repository.completeSession(
                correctCount = correctCount,
                totalCount = total,
                sessionStartedAt = sessionStartedAt,
            )
            WidgetUpdater.refresh(getApplication())

            val durationMin = ((now - sessionStartedAt) / 60_000L).toInt().coerceAtLeast(1)
            val accuracy = if (total > 0) (correctCount * 100) / total else 0
            val sessionData = StudySessionData(
                cardsReviewed = total,
                correctAnswers = correctCount,
                incorrectAnswers = total - correctCount,
                accuracyPercent = accuracy,
                durationMinutes = durationMin,
                creditsEarned = result.creditsEarned,
                weakCards = emptyList(),
                currentStreak = result.streakDays,
                previousAccuracyPercent = null,
            )

            val inferenceEngine = LlamaCppEngine(getApplication())
            val coach = RealAiCoach(inferenceEngine, db.aiInsightDao())
            val summary = coach.summarizeSession(sessionData)
            coach.saveInsight(sessionStartedAt, summary)

            // Session-complete dashboard data (all real, no placeholders).
            val zone = ZoneId.systemDefault()
            val startOfDay = Instant.ofEpochMilli(now).atZone(zone)
                .toLocalDate().atStartOfDay(zone).toInstant().toEpochMilli()
            val todaysSessions = db.sessionDao().getSince(startOfDay)
            val earnedToday = todaysSessions.sumOf { it.creditsEarned }
            val studyMinutesToday = todaysSessions.sumOf {
                val end = it.completedAt ?: now
                ((end - it.startedAt) / 60_000L).toInt().coerceAtLeast(0)
            }
            val settings = db.settingsDao().get()
            val goalMinutes = when (settings?.dailyGoalPreset) {
                DailyGoalPreset.SERIOUS -> 45
                else -> 30
            }
            val dueCount = try {
                db.progressDao().observeDueCount(now).first()
            } catch (_: Exception) { 0 }
            val lockedLabels = try {
                db.lockedAppDao().observeLocked().first()
                    .map { it.appLabel }.take(7)
            } catch (_: Exception) { emptyList() }

            _phase.value = StudyPhase.Results(
                correctCount = correctCount,
                totalCount = total,
                creditsEarned = result.creditsEarned,
                walletBalance = result.walletBalance,
                isPerfect = result.isPerfect,
                aiSummary = summary.text,
                durationSeconds = ((now - sessionStartedAt) / 1000).toInt().coerceAtLeast(0),
                earnedTodayMinutes = earnedToday,
                avgRecallSeconds = if (recallDurationsMs.isNotEmpty())
                    recallDurationsMs.average() / 1000.0 else 0.0,
                avgIntervalGrowthDays = if (intervalGrowthDays.isNotEmpty())
                    intervalGrowthDays.average() else 0.0,
                studyMinutesToday = studyMinutesToday,
                dailyGoalMinutes = goalMinutes,
                dueCount = dueCount,
                lockedAppLabels = lockedLabels,
            )
        }
    }

    fun onKeepStudying() = startSession()

    companion object {
        const val SESSION_SIZE = 10
    }
}
