package com.example.jikan.coach

import com.example.jikan.data.AiInsightDao
import com.example.jikan.data.AiInsightEntity

class RealAiCoach(
    private val inferenceEngine: InferenceEngine,
    private val insightDao: AiInsightDao,
) : AiCoach {

    override suspend fun summarizeSession(session: StudySessionData): AiStudySummary {
        val prompt = AiPromptBuilder.buildSessionSummaryPrompt(session)
        val rawText = try {
            if (!inferenceEngine.isReady()) {
                inferenceEngine.initialize()
            }
            inferenceEngine.generate(prompt)
        } catch (e: Exception) {
            "Consistent practice today. You reviewed ${session.cardsReviewed} cards with ${session.accuracyPercent}% accuracy and earned ${session.creditsEarned} minutes."
        }

        val cleanedText = rawText.trim()
        return AiStudySummary(text = cleanedText)
    }

    override suspend fun explainMistake(cardKana: String, userAnswer: String): String {
        val prompt = "Explain briefly why user answered '$userAnswer' for Japanese character '$cardKana'."
        return try {
            inferenceEngine.generate(prompt, maxTokens = 64)
        } catch (e: Exception) {
            "For '$cardKana', the correct reading differs from '$userAnswer'. Review pronunciation and stroke order."
        }
    }

    suspend fun saveInsight(sessionId: Long, summary: AiStudySummary, type: String = "SUMMARY") {
        val entity = AiInsightEntity(
            sessionId = sessionId,
            type = type,
            content = summary.text,
            createdAt = System.currentTimeMillis(),
            modelVersion = ModelManager.MODEL_VERSION,
        )
        insightDao.insert(entity)
    }
}
