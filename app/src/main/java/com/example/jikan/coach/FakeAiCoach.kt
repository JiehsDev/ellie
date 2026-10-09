package com.example.jikan.coach

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
}
