package com.example.jikan.coach

import org.junit.Assert.assertTrue
import org.junit.Test

class AiPromptBuilderTest {

    @Test
    fun testSessionSummaryPromptBuilding() {
        val sessionData = StudySessionData(
            cardsReviewed = 10,
            correctAnswers = 8,
            incorrectAnswers = 2,
            accuracyPercent = 80,
            durationMinutes = 5,
            creditsEarned = 10,
            weakCards = listOf("あ", "い"),
            currentStreak = 4,
            previousAccuracyPercent = 70,
        )

        val prompt = AiPromptBuilder.buildSessionSummaryPrompt(sessionData)

        assertTrue(prompt.contains("Cards reviewed: 10"))
        assertTrue(prompt.contains("Accuracy: 80%"))
        assertTrue(prompt.contains("Duration: 5 minutes"))
        assertTrue(prompt.contains("Current streak: 4"))
        assertTrue(prompt.contains("Weak characters: あ, い"))
    }
}
