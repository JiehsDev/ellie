package com.example.jikan.coach

/**
 * Phase 7: validates and trims local-model output for screen-time insights.
 *
 * Pure Kotlin — unit-testable without Android. Returns the cleaned insight,
 * or null when the output is unusable (blank, or only prompt echo), so the
 * caller can fall back to the deterministic provider message.
 */
object InsightSanitizer {
    private const val MAX_SENTENCES = 2
    private const val MAX_CHARS = 280

    fun sanitize(raw: String?): String? {
        val text = raw?.trim().orEmpty()
        if (text.isEmpty()) return null
        val deEchoed = text
            .lines()
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .filterNot { looksLikePromptEcho(it) }
            .joinToString(" ")
            .trim()
        if (deEchoed.isEmpty()) return null
        val sentences = deEchoed
            .split(Regex("(?<=[.!?])\\s+"))
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .take(MAX_SENTENCES)
        if (sentences.isEmpty()) return null
        return sentences.joinToString(" ").trim().take(MAX_CHARS).trim().ifEmpty { null }
    }

    private fun looksLikePromptEcho(line: String): Boolean {
        val lower = line.lowercase()
        if (lower.startsWith("screen-time facts:") ||
            lower.startsWith("write the insight") ||
            lower.startsWith("you are jikan coach")
        ) {
            return true
        }
        // Fact lines from an echoed prompt, e.g. "Total today: 201 minutes".
        // A genuine 1-2 sentence insight never opens with a "Label: 123" line.
        return line.matches(Regex("[A-Za-z][A-Za-z /-]*:\\s*\\d.*"))
    }
}
