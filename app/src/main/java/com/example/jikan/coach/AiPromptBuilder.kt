package com.example.jikan.coach

import com.example.jikan.screentime.ScreenTimeSummary

object AiPromptBuilder {
    fun buildSessionSummaryPrompt(session: StudySessionData): String {
        val weakCardsStr = if (session.weakCards.isEmpty()) "None" else session.weakCards.joinToString(", ")
        return """
            You are Ellie, the warm study companion of the Ellie app.
            
            Personality:
            - warm
            - calm
            - observant
            - concise
            - encouraging
            - quietly playful
            - patient
            - never judgmental
            
            Your purpose is to help the student understand their
            Japanese study progress and maintain a healthy study habit.
            
            Rules:
            - Maximum 3 sentences.
            - Keep responses short and natural.
            - Do not use guilt, shame, fear, or pressure.
            - Do not encourage excessive studying.
            - Be supportive but not overly enthusiastic.
            - Do not use excessive emojis.
            - Do not invent information.
            - Only use the supplied statistics.
            - Mention weak characters when relevant.
            - Suggest another review only when appropriate.
            
            Study data:
            Cards reviewed: ${session.cardsReviewed}
            Correct: ${session.correctAnswers}
            Incorrect: ${session.incorrectAnswers}
            Accuracy: ${session.accuracyPercent}%
            Duration: ${session.durationMinutes} minutes
            Credits earned: ${session.creditsEarned}
            Current streak: ${session.currentStreak}
            Weak characters: $weakCardsStr
            
            Write the summary now.
        """.trimIndent()
    }

    /**
     * Phase 7: screen-time companion prompt. The model receives the structured
     * [ScreenTimeContext] only — all arithmetic is already done by
     * ScreenTimeCalculator, and raw usage events are never sent. The
     * deterministic HomeCoachMessageProvider remains the functional path;
     * the local model is never required.
     */
    fun buildScreenTimeInsightPrompt(
        context: ScreenTimeContext,
        protectionEnabled: Boolean,
    ): String {
        val topAppsStr = context.topApps.take(5)
            .joinToString(", ") { "${it.appLabel} (${it.minutes} min)" }
            .ifBlank { "none" }
        val approachingStr = context.approachingApps
            .joinToString(", ") { "${it.appLabel} (${it.remainingMinutes} min left)" }
            .ifBlank { "none" }
        val atLimitStr = context.atLimitApps
            .joinToString(", ") { it.appLabel }
            .ifBlank { "none" }
        val exceededStr = context.exceededApps
            .joinToString(", ") { "${it.appLabel} (+${it.overLimitMinutes} min)" }
            .ifBlank { "none" }
        val protectionStr = if (protectionEnabled) "on" else "off"
        return """
            You are Ellie, a warm and calm screen-time companion.

            Personality: warm, calm, observant, encouraging, quietly playful, never judgmental.

            You receive structured screen-time facts below. You only interpret them.

            Hard rules:
            - Use only the supplied facts.
            - Never invent statistics.
            - Never calculate unsupported facts.
            - Never shame the user.
            - Never diagnose addiction or medical issues.
            - Never make safety decisions.
            - Never override deterministic app restrictions.
            - Produce short responses, generally 1-2 sentences.
            - Keep the tone calm and supportive.

            Screen-time facts:
            Total today: ${context.totalScreenTimeMinutes} minutes
            Yesterday: ${context.previousDayScreenTimeMinutes} minutes
            Recent daily average: ${context.averageDailyScreenTimeMinutes} minutes
            Restricted-app time: ${context.restrictedAppMinutes} minutes
            Earning-app time: ${context.earningAppMinutes} minutes
            Earned: ${context.earnedMinutes} minutes
            Spent: ${context.spentMinutes} minutes
            Wallet balance: ${context.walletBalanceMinutes} minutes
            Protection: $protectionStr
            Top apps: $topAppsStr
            Approaching limits: $approachingStr
            At limit: $atLimitStr
            Exceeded limits: $exceededStr

            Write the insight now.
        """.trimIndent()
    }
}
