package com.example.jikan.coach

interface AiCoach {
    suspend fun summarizeSession(session: StudySessionData): AiStudySummary
    suspend fun explainMistake(cardKana: String, userAnswer: String): String
}
