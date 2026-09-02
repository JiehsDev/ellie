package com.example.jikan.challenge

import com.example.jikan.data.ChallengeType

/**
 * The whole rate table in one readable place.
 *
 * Rates are set by return — minutes earned per minute of real effort — so that no
 * challenge is obviously the only sensible choice: push-ups 6:1, study ~4:1, walking
 * and pausing 2:1. Push-ups keep a modest edge because physical effort per second is
 * genuinely higher than reading flashcards.
 */
object CreditRules {
    const val PUSHUP_TARGET_REPS = 10
    const val PUSHUP_MINUTES = 3
    const val PUSHUP_DAILY_CAP_MINUTES = 20

    const val STEPS_TARGET = 300
    const val STEPS_MINUTES = 6
    const val STEPS_DAILY_CAP_MINUTES = 20

    const val PAUSE_TARGET_SECONDS = 60
    const val PAUSE_MINUTES = 2
    const val PAUSE_DAILY_CAP_MINUTES = 10

    /** Minutes earned for completing [type] with [metric]. Study computes its own. */
    fun earnedFor(type: ChallengeType, metric: Int): Int = when (type) {
        ChallengeType.PUSHUPS -> if (metric >= PUSHUP_TARGET_REPS) PUSHUP_MINUTES else 0
        ChallengeType.STEPS -> if (metric >= STEPS_TARGET) STEPS_MINUTES else 0
        ChallengeType.PAUSE -> if (metric >= PAUSE_TARGET_SECONDS) PAUSE_MINUTES else 0
        ChallengeType.STUDY -> error("Study credits come from CreditCalculator, not CreditRules")
    }

    /** Null means no cap in the funnel; study is limited by CreditCalculator's decay. */
    fun dailyCapMinutes(type: ChallengeType): Int? = when (type) {
        ChallengeType.PUSHUPS -> PUSHUP_DAILY_CAP_MINUTES
        ChallengeType.STEPS -> STEPS_DAILY_CAP_MINUTES
        ChallengeType.PAUSE -> PAUSE_DAILY_CAP_MINUTES
        ChallengeType.STUDY -> null
    }

    fun targetFor(type: ChallengeType): Int = when (type) {
        ChallengeType.PUSHUPS -> PUSHUP_TARGET_REPS
        ChallengeType.STEPS -> STEPS_TARGET
        ChallengeType.PAUSE -> PAUSE_TARGET_SECONDS
        ChallengeType.STUDY -> 0
    }
}
