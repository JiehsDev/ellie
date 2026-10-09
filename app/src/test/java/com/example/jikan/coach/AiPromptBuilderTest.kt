package com.example.jikan.coach

import com.example.jikan.screentime.AppUsageStatus
import com.example.jikan.screentime.ExceededAppUsage
import com.example.jikan.screentime.ScreenTimeAppUsage
import com.example.jikan.screentime.ScreenTimeSummary
import com.example.jikan.screentime.UsageChange
import com.example.jikan.screentime.UsageChangeDirection
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

    @Test
    fun testScreenTimeInsightPromptCarriesFactsAndGuardrails() {
        val summary = ScreenTimeSummary(
            epochDay = 10L,
            totalScreenTimeMinutes = 201,
            previousDayScreenTimeMinutes = 224,
            averageDailyScreenTimeMinutes = 190,
            restrictedAppMinutes = 134,
            earningAppMinutes = 31,
            earnedMinutes = 15,
            spentMinutes = 20,
            walletBalanceMinutes = 10,
            topApps = listOf(
                ScreenTimeAppUsage("com.tiktok", "TikTok", 48, 23.9f, AppUsageStatus.RESTRICTED),
            ),
            exceededApps = listOf(
                ExceededAppUsage("com.tiktok", "TikTok", 42, 30, 12),
            ),
            recentUsageChange = UsageChange(-23, -10.3f, UsageChangeDirection.DOWN),
        )

        val prompt = AiPromptBuilder.buildScreenTimeInsightPrompt(summary, protectionEnabled = true)

        // Structured facts only — no arithmetic for the model to do.
        assertTrue(prompt.contains("You are Jikan Coach, a calm screen-time companion."))
        assertTrue(prompt.contains("Total today: 201 minutes"))
        assertTrue(prompt.contains("Yesterday: 224 minutes"))
        assertTrue(prompt.contains("Wallet balance: 10 minutes"))
        assertTrue(prompt.contains("Protection: on"))
        assertTrue(prompt.contains("TikTok (48 min)"))
        assertTrue(prompt.contains("TikTok (+12 min)"))
        // Safety guardrails baked into the prompt.
        assertTrue(prompt.contains("Never invent statistics."))
        assertTrue(prompt.contains("Never calculate unsupported facts."))
        assertTrue(prompt.contains("Never shame the user."))
        assertTrue(prompt.contains("Never diagnose addiction"))
        assertTrue(prompt.contains("Never make safety decisions."))
        assertTrue(prompt.contains("Never override deterministic app restrictions."))
        assertTrue(prompt.contains("generally 1-2 sentences"))
    }

    @Test
    fun testScreenTimeInsightPromptReportsProtectionOff() {
        val summary = ScreenTimeSummary(
            epochDay = 10L,
            totalScreenTimeMinutes = 0,
            previousDayScreenTimeMinutes = 0,
            averageDailyScreenTimeMinutes = 0,
            restrictedAppMinutes = 0,
            earningAppMinutes = 0,
            earnedMinutes = 0,
            spentMinutes = 0,
            walletBalanceMinutes = 0,
            topApps = emptyList(),
            exceededApps = emptyList(),
            recentUsageChange = UsageChange(0, null, UsageChangeDirection.SAME),
        )

        val prompt = AiPromptBuilder.buildScreenTimeInsightPrompt(summary, protectionEnabled = false)

        assertTrue(prompt.contains("Protection: off"))
        assertTrue(prompt.contains("Top apps: none"))
    }
}
