package com.example.jikan.ui.study

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.jikan.data.AppDatabase
import com.example.jikan.data.CardTier
import com.example.jikan.data.DailyGoalPreset
import com.example.jikan.study.CreditCalculator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId

/** Per-tier ("deck") stats for the study dashboard. All from real data. */
data class DeckStats(
    val tier: CardTier,
    val displayName: String,
    val totalCards: Int,
    val dueCount: Int,
    val masteredCount: Int,
    /** Average ease factor as a retention proxy (2.5 = 100%). */
    val retentionPercent: Int,
    /** Earliest upcoming due time, for "next review" display. 0 if none. */
    val nextDueAt: Long = 0L,
)

data class StudyDashboardUiState(
    val dueCount: Int = 0,
    val decksWithDue: Int = 0,
    val cardsReviewedToday: Int = 0,
    val cardGoal: Int = 0,
    val earnedTodayMinutes: Int = 0,
    val dailyGoalMinutes: Int = 45,
    val estMinutes: Int = 0,
    val estRewardMinutes: Int = 0,
    val coachMessage: String = "",
    val decks: List<DeckStats> = emptyList(),
    val isLoading: Boolean = true,
)

class StudyDashboardViewModel(application: Application) : AndroidViewModel(application) {
    private val db = AppDatabase.getInstance(application)

    private val _state = MutableStateFlow(StudyDashboardUiState())
    val state: StateFlow<StudyDashboardUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            combine(
                db.cardDao().observeAll(),
                db.progressDao().observeAll(),
                db.sessionDao().observeAll(),
                db.settingsDao().observe(),
            ) { cards, progress, sessions, settings ->
                val now = System.currentTimeMillis()
                val zone = ZoneId.systemDefault()
                val startOfDay = Instant.ofEpochMilli(now).atZone(zone)
                    .toLocalDate().atStartOfDay(zone).toInstant().toEpochMilli()

                val progressByCard = progress.associateBy { it.cardId }
                val cardsByTier = cards.groupBy { it.tier }

                val decks = cardsByTier.map { (tier, tierCards) ->
                    val tierProgress = tierCards.mapNotNull { progressByCard[it.id] }
                    val due = tierProgress.count { it.dueAt <= now } +
                        // Cards never reviewed are due.
                        (tierCards.size - tierProgress.size)
                    val mastered = tierProgress.count { it.repetitions >= 3 }
                    val retention = if (tierProgress.isNotEmpty()) {
                        ((tierProgress.sumOf { it.easeFactor.toDouble() } / tierProgress.size) / 2.5 * 100)
                            .toInt().coerceIn(0, 100)
                    } else 0
                    val nextDue = tierProgress.map { it.dueAt }.filter { it > now }.minOrNull() ?: 0L
                    DeckStats(
                        tier = tier,
                        displayName = tierDisplayName(tier),
                        totalCards = tierCards.size,
                        dueCount = due,
                        masteredCount = mastered,
                        retentionPercent = retention,
                        nextDueAt = nextDue,
                    )
                }.sortedByDescending { it.dueCount }

                val dueCount = decks.sumOf { it.dueCount }
                val todaysSessions = sessions.filter { it.startedAt >= startOfDay }
                val reviewedToday = todaysSessions.sumOf { it.questionsTotal }
                val earnedToday = todaysSessions.sumOf { it.creditsEarned }
                val goalMinutes = when (settings?.dailyGoalPreset) {
                    DailyGoalPreset.SERIOUS -> 45
                    else -> 30
                }

                // Estimates from real rates: ~30s per card, credit formula.
                val estMinutes = (dueCount * 30 + 30) / 60
                val estReward = CreditCalculator.creditsEarned(
                    correctCount = (dueCount * 0.8).toInt(),
                    totalCount = dueCount,
                )

                Triple(decks, dueCount, Triple(reviewedToday, earnedToday, goalMinutes)) to
                    Pair(estMinutes, estReward)
            }.collect { (data, estimates) ->
                val (decks, dueCount, today) = data
                val (reviewedToday, earnedToday, goalMinutes) = today
                val (estMinutes, estReward) = estimates
                _state.update {
                    it.copy(
                        dueCount = dueCount,
                        decksWithDue = decks.count { d -> d.dueCount > 0 },
                        cardsReviewedToday = reviewedToday,
                        cardGoal = goalMinutes * 2, // ~30s per card
                        earnedTodayMinutes = earnedToday,
                        dailyGoalMinutes = goalMinutes,
                        estMinutes = estMinutes,
                        estRewardMinutes = estReward,
                        coachMessage = buildCoachMessage(dueCount, decks.count { d -> d.dueCount > 0 }, estMinutes, estReward),
                        decks = decks,
                        isLoading = false,
                    )
                }
            }
        }
    }

    fun refresh() {
        // State is flow-driven; exposed for pull-to-refresh if needed.
    }

    private fun buildCoachMessage(due: Int, deckCount: Int, estMin: Int, reward: Int): String {
        return if (due == 0) {
            "All caught up. Your next reviews unlock naturally tomorrow."
        } else {
            "$due reviews due across $deckCount ${if (deckCount == 1) "deck" else "decks"}. " +
                "A disciplined $estMin minutes unlocks +${reward}m of leisure app time."
        }
    }

    companion object {
        fun tierDisplayName(tier: CardTier): String = when (tier) {
            CardTier.HIRAGANA_VOWELS -> "Hiragana · Vowels"
            CardTier.HIRAGANA_K -> "Hiragana · K"
            CardTier.HIRAGANA_S -> "Hiragana · S"
            CardTier.HIRAGANA_T -> "Hiragana · T"
            CardTier.HIRAGANA_N -> "Hiragana · N"
            CardTier.HIRAGANA_H -> "Hiragana · H"
            CardTier.HIRAGANA_M -> "Hiragana · M"
            CardTier.HIRAGANA_Y -> "Hiragana · Y"
            CardTier.HIRAGANA_R -> "Hiragana · R"
            CardTier.HIRAGANA_W_N -> "Hiragana · W/N"
            CardTier.KATAKANA -> "Katakana"
            CardTier.VOCAB_BASIC -> "Basic Vocab"
            CardTier.PHRASES_BASIC -> "Basic Phrases"
        }
    }
}
