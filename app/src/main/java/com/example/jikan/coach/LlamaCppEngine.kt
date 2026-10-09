package com.example.jikan.coach

import android.content.Context
import android.util.Log
import com.tensai.llamakt.LlamaEngine
import com.tensai.llamakt.SamplingParams
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class LlamaCppEngine(context: Context) : InferenceEngine {
    private val modelManager = ModelManager(context)
    private val engine = LlamaEngine()
    private var isInitialized = false

    override suspend fun initialize(): Unit = withContext(Dispatchers.IO) {
        if (isInitialized) return@withContext

        try {
            if (!modelManager.ensureModelInstalled()) {
                Log.w(TAG, "Model file unavailable at ${modelManager.modelFile.absolutePath}. AI coach will use offline fallback.")
                isInitialized = false
                return@withContext
            }

            engine.load(
                path = modelManager.getModelPath(),
                nGpuLayers = 0,
                nCtx = CONTEXT_TOKENS,
                nThreads = 0,
                kvCacheType = "q8_0",
                flashAttn = "on",
            )
            isInitialized = true
            Log.i(TAG, "LlamaCppEngine initialized successfully with ${ModelManager.MODEL_VERSION}")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize LlamaCppEngine", e)
            isInitialized = false
        }
    }

    override suspend fun generate(prompt: String, maxTokens: Int): String = withContext(Dispatchers.Default) {
        if (!isReady()) {
            return@withContext "Consistent practice today. Keep up the steady review to reinforce your memory!"
        }

        val output = StringBuilder()
        val sampledTokens = engine.completion(
            prompt = prompt,
            params = SamplingParams(
                nPredict = maxTokens.coerceIn(16, MAX_PREDICT_TOKENS),
                temperature = 0.2f,
                topK = 20,
                topP = 0.85f,
                minP = 0.05f,
                repeatPenalty = 1.1f,
                stopSequences = STOP_SEQUENCES,
            ),
            callback = { token -> output.append(token) },
        )

        if (sampledTokens < 0) {
            Log.w(TAG, "Native llama.cpp generation failed with code $sampledTokens")
        }

        val cleaned = cleanResponse(output.toString())
        return@withContext cleaned.ifBlank {
            "Consistent practice today. Keep up the steady review to reinforce your memory!"
        }
    }

    override fun isReady(): Boolean = isInitialized

    override fun release() {
        if (isInitialized) {
            engine.free()
            isInitialized = false
        }
    }

    private fun cleanResponse(raw: String): String {
        return raw
            .replace(Regex("<\\|[^>]+\\|>"), "")
            .replace(Regex("(?is)<think>.*?</think>"), "")
            .trim()
            .lines()
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .joinToString(" ")
            .take(MAX_RESPONSE_CHARS)
            .trim()
    }

    companion object {
        private const val TAG = "LlamaCppEngine"
        private const val CONTEXT_TOKENS = 1024
        private const val MAX_PREDICT_TOKENS = 128
        private const val MAX_RESPONSE_CHARS = 600
        private val STOP_SEQUENCES = listOf(
            "<|endoftext|>",
            "<|im_end|>",
            "\nUser:",
            "\nStudy data:",
        )
    }
}
