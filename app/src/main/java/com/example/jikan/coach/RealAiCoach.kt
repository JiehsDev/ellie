package com.example.jikan.coach

import com.example.jikan.data.AiInsightDao
import com.example.jikan.data.AiInsightEntity
import kotlinx.coroutines.withTimeout

class RealAiCoach(
    private val inferenceEngine: InferenceEngine,
    private val insightDao: AiInsightDao,
    /**
     * Phase 7: the deterministic fallback. Defaults to [FakeAiCoach], which
     * answers from the shared provider with no model at all — so Ellie
     * keeps working when the local model is unavailable.
     */
    private val fallbackCoach: AiCoach = FakeAiCoach(),
    private val inferenceTimeoutMs: Long = INFERENCE_TIMEOUT_MS,
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

    /**
     * Phase 7: screen-time insight from the local model, with robust
     * fallbacks covering every failure mode:
     *
     * - model unavailable (initialize throws or engine never ready)
     * - model timeout ([inferenceTimeoutMs])
     * - inference failure (generate throws)
     * - malformed output (prompt echo / role-play artifacts)
     * - empty output
     *
     * All five resolve to the deterministic provider message via
     * [fallbackCoach]. The model only interprets the structured
     * [ScreenTimeContext]; it never calculates statistics.
     */
    override suspend fun insightForScreenTime(
        context: ScreenTimeContext,
        protectionEnabled: Boolean,
    ): String {
        val prompt = AiPromptBuilder.buildScreenTimeInsightPrompt(context, protectionEnabled)
        val generated = try {
            if (!inferenceEngine.isReady()) {
                inferenceEngine.initialize()
            }
            withTimeout(inferenceTimeoutMs) {
                inferenceEngine.generate(prompt, maxTokens = INSIGHT_MAX_TOKENS)
            }
        } catch (e: Exception) {
            null
        }
        return InsightSanitizer.sanitize(generated)
            ?: fallbackCoach.insightForScreenTime(context, protectionEnabled)
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

    companion object {
        private const val INFERENCE_TIMEOUT_MS = 30_000L
        private const val INSIGHT_MAX_TOKENS = 96
    }
}
