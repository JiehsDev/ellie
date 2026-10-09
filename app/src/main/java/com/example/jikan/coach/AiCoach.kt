package com.example.jikan.coach

interface AiCoach {
    suspend fun summarizeSession(session: StudySessionData): AiStudySummary
    suspend fun explainMistake(cardKana: String, userAnswer: String): String

    /**
     * Phase 7: a short screen-time insight from structured facts.
     *
     * The local model is optional. Implementations must return a
     * deterministic fallback message when the model is unavailable, slow,
     * or misbehaving — Ellie keeps working either way. The model only
     * interprets the supplied [ScreenTimeContext]; it never decides blocks,
     * protection state, wallet accounting, or usage math.
     */
    suspend fun insightForScreenTime(
        context: ScreenTimeContext,
        protectionEnabled: Boolean,
    ): String
}
