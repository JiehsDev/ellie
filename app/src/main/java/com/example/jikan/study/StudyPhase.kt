package com.example.jikan.study

import com.example.jikan.data.Card
import com.example.jikan.srs.RecallGrade
import com.example.jikan.srs.SrsState

sealed interface StudyPhase {
    data object Loading : StudyPhase

    data object Empty : StudyPhase

    /**
     * Anki-style review: the card is shown with its answer, and the user
     * grades their recall. Grading advances to the next card.
     */
    data class Lesson(
        val cards: List<Card>,
        val index: Int,
        /** Current SRS state of the displayed card (for level + grade previews). */
        val cardState: SrsState,
        /** Grade -> preview text (e.g. "<1m", "12h", "1d", "3d"). */
        val gradePreviews: Map<RecallGrade, String>,
        /** Minutes earned today (all sessions). */
        val earnedTodayMinutes: Int,
        /** Estimated minutes until this session completes (unlock). */
        val unlockInMinutes: Int,
        /** Session length setting, for the unlock estimate. */
        val sessionLengthMinutes: Int,
    ) : StudyPhase

    data class Results(
        val correctCount: Int,
        val totalCount: Int,
        val creditsEarned: Int,
        val walletBalance: Int,
        val isPerfect: Boolean,
        val aiSummary: String? = null,
        // Session-complete dashboard fields (Stitch reference).
        val durationSeconds: Int = 0,
        val earnedTodayMinutes: Int = 0,
        val avgRecallSeconds: Double = 0.0,
        val avgIntervalGrowthDays: Double = 0.0,
        val studyMinutesToday: Int = 0,
        val dailyGoalMinutes: Int = 45,
        val dueCount: Int = 0,
        val lockedAppLabels: List<String> = emptyList(),
    ) : StudyPhase
}
