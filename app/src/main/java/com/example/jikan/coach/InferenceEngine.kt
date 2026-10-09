package com.example.jikan.coach

interface InferenceEngine {
    suspend fun initialize()
    suspend fun generate(prompt: String, maxTokens: Int = 128): String
    fun isReady(): Boolean
    fun release()
}
