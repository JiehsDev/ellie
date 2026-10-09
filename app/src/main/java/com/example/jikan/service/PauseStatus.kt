package com.example.jikan.service

data class PauseStatus(
    val isPaused: Boolean,
    val remainingMinutes: Long,
)

object PauseStatusCalculator {
    fun status(expiryTimestampMs: Long, nowMs: Long): PauseStatus {
        val remainingMs = expiryTimestampMs - nowMs
        if (remainingMs <= 0L) return PauseStatus(isPaused = false, remainingMinutes = 0L)
        return PauseStatus(
            isPaused = true,
            remainingMinutes = (remainingMs / 60_000L).coerceAtLeast(1L),
        )
    }

    fun nextExpiry(nowMs: Long, currentExpiryMs: Long, durationMinutes: Int): Long {
        val base = maxOf(nowMs, currentExpiryMs)
        return base + durationMinutes * 60_000L
    }
}
