package com.example.jikan.study

import kotlin.math.roundToInt

object CreditCalculator {
    private const val MINUTES_PER_CORRECT = 2
    private const val PERFECT_BONUS_MINUTES = 5

    private const val STREAK_BONUS_MIN_DAYS = 2
    private const val STREAK_BONUS_RATE = 0.10

    private const val DAILY_CAP_DECAY_PER_SESSION = 0.15
    private const val DAILY_CAP_MIN_MULTIPLIER = 0.25

    /**
     * @param currentStreakDays consecutive days of study including today, after today's session counts.
     * @param priorSessionsToday how many sessions were already completed today, before this one.
     */
    fun creditsEarned(
        correctCount: Int,
        totalCount: Int,
        currentStreakDays: Int = 0,
        priorSessionsToday: Int = 0,
    ): Int {
        val base = correctCount * MINUTES_PER_CORRECT
        val perfectBonus = if (totalCount > 0 && correctCount == totalCount) PERFECT_BONUS_MINUTES else 0
        val streakMultiplier = if (currentStreakDays >= STREAK_BONUS_MIN_DAYS) 1.0 + STREAK_BONUS_RATE else 1.0
        val dailyCapMultiplier = dailyCapMultiplier(priorSessionsToday)
        return ((base + perfectBonus) * streakMultiplier * dailyCapMultiplier).roundToInt()
    }

    private fun dailyCapMultiplier(priorSessionsToday: Int): Double {
        val decayed = 1.0 - (priorSessionsToday * DAILY_CAP_DECAY_PER_SESSION)
        return decayed.coerceAtLeast(DAILY_CAP_MIN_MULTIPLIER)
    }
}
