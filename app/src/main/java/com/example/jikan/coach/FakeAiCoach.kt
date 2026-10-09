package com.example.jikan.coach

import com.example.jikan.ui.home.HomeCoachMessageProvider
import com.example.jikan.ui.home.toCoachInput

class FakeAiCoach : AiCoach {
    override suspend fun summarizeSession(session: StudySessionData): AiStudySummary {
        val summaryText = if (session.accuracyPercent >= 90) {
            "Exceptional focus today. You reviewed ${session.cardsReviewed} cards with ${session.accuracyPercent}% accuracy and earned ${session.creditsEarned} minutes. Your streak is now ${session.currentStreak} days."
        } else {
            "Steady practice today. You reviewed ${session.cardsReviewed} cards with ${session.accuracyPercent}% accuracy. Keep reviewing weak items tomorrow to build consistency."
        }
        return AiStudySummary(text = summaryText)
    }

    override suspend fun explainMistake(cardKana: String, userAnswer: String): String {
        return "For '$cardKana', the correct reading is distinct from '$userAnswer'. Review stroke patterns and audio pronunciation."
    }

    /**
     * Phase 7: always deterministic — the shared provider message for the
     * context. Doubles as [RealAiCoach]'s fallback, so the coach works with
     * no local model at all.
     */
    override suspend fun insightForScreenTime(
        context: ScreenTimeContext,
        protectionEnabled: Boolean,
    ): String = HomeCoachMessageProvider.messageFor(context.toCoachInput(protectionEnabled))
}
