package com.example.jikan.srs

import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt

data class SrsState(
    val intervalDays: Int,
    val easeFactor: Float,
    val repetitions: Int,
)

data class SrsResult(
    val intervalDays: Int,
    val easeFactor: Float,
    val repetitions: Int,
    val dueAt: Long,
)

/** Anki-style recall grade for the lesson review card. */
enum class RecallGrade { AGAIN, HARD, GOOD, EASY }

object SrsEngine {
    val INITIAL_STATE = SrsState(intervalDays = 0, easeFactor = 2.5f, repetitions = 0)

    private const val MIN_EASE_FACTOR = 1.3f
    private const val EASE_DELTA_CORRECT = 0.1f
    private const val EASE_DELTA_WRONG = 0.2f

    /**
     * Four-grade review. AGAIN resets like a wrong answer; HARD gives a
     * shorter interval; GOOD is the standard correct path; EASY boosts
     * the interval and ease. Sub-day due times are supported via [dueAt].
     */
    fun reviewGrade(state: SrsState, grade: RecallGrade, now: Long = System.currentTimeMillis()): SrsResult {
        return when (grade) {
            RecallGrade.AGAIN -> SrsResult(
                intervalDays = 0,
                easeFactor = (state.easeFactor - EASE_DELTA_WRONG).coerceAtLeast(MIN_EASE_FACTOR),
                repetitions = 0,
                dueAt = now,
            )
            RecallGrade.HARD -> {
                val repetitions = state.repetitions + 1
                val easeFactor = (state.easeFactor - 0.05f).coerceAtLeast(MIN_EASE_FACTOR)
                // Hard is a shorter interval: 12h for new cards, scaled down after.
                val dueAt = if (state.repetitions == 0) {
                    now + TimeUnit.HOURS.toMillis(12)
                } else {
                    val base = nextInterval(repetitions, state.intervalDays, easeFactor)
                    now + TimeUnit.DAYS.toMillis((base * 0.6).roundToInt().coerceAtLeast(1).toLong())
                }
                SrsResult(
                    intervalDays = TimeUnit.MILLISECONDS.toDays(dueAt - now).toInt(),
                    easeFactor = easeFactor,
                    repetitions = repetitions,
                    dueAt = dueAt,
                )
            }
            RecallGrade.GOOD -> {
                val repetitions = state.repetitions + 1
                val easeFactor = (state.easeFactor + EASE_DELTA_CORRECT).coerceAtLeast(MIN_EASE_FACTOR)
                val intervalDays = nextInterval(repetitions, state.intervalDays, easeFactor)
                SrsResult(
                    intervalDays = intervalDays,
                    easeFactor = easeFactor,
                    repetitions = repetitions,
                    dueAt = now + TimeUnit.DAYS.toMillis(intervalDays.toLong()),
                )
            }
            RecallGrade.EASY -> {
                val repetitions = state.repetitions + 1
                val easeFactor = (state.easeFactor + 0.15f).coerceAtLeast(MIN_EASE_FACTOR)
                val intervalDays = (nextInterval(repetitions, state.intervalDays, easeFactor) * 1.3)
                    .roundToInt().coerceAtLeast(1)
                SrsResult(
                    intervalDays = intervalDays,
                    easeFactor = easeFactor,
                    repetitions = repetitions,
                    dueAt = now + TimeUnit.DAYS.toMillis(intervalDays.toLong()),
                )
            }
        }
    }

    fun review(state: SrsState, wasCorrect: Boolean, now: Long = System.currentTimeMillis()): SrsResult {
        return if (wasCorrect) {
            val repetitions = state.repetitions + 1
            val easeFactor = (state.easeFactor + EASE_DELTA_CORRECT).coerceAtLeast(MIN_EASE_FACTOR)
            val intervalDays = nextInterval(repetitions, state.intervalDays, easeFactor)
            SrsResult(
                intervalDays = intervalDays,
                easeFactor = easeFactor,
                repetitions = repetitions,
                dueAt = now + TimeUnit.DAYS.toMillis(intervalDays.toLong()),
            )
        } else {
            SrsResult(
                intervalDays = 0,
                easeFactor = (state.easeFactor - EASE_DELTA_WRONG).coerceAtLeast(MIN_EASE_FACTOR),
                repetitions = 0,
                dueAt = now,
            )
        }
    }

    private fun nextInterval(repetitions: Int, previousIntervalDays: Int, easeFactor: Float): Int =
        when (repetitions) {
            1 -> 1
            2 -> 3
            3 -> 7
            4 -> 14
            else -> (previousIntervalDays * easeFactor).roundToInt()
        }

    /**
     * Short display text for a grade button, e.g. "<1m", "12h", "1d", "3d".
     * Computed from the actual [reviewGrade] result for the card's state.
     */
    fun gradePreviewText(state: SrsState, grade: RecallGrade, now: Long = System.currentTimeMillis()): String {
        val dueInMs = reviewGrade(state, grade, now).dueAt - now
        return when {
            dueInMs < TimeUnit.MINUTES.toMillis(1) -> "<1m"
            dueInMs < TimeUnit.HOURS.toMillis(1) -> "${TimeUnit.MILLISECONDS.toMinutes(dueInMs)}m"
            dueInMs < TimeUnit.DAYS.toMillis(1) -> "${TimeUnit.MILLISECONDS.toHours(dueInMs)}h"
            else -> "${TimeUnit.MILLISECONDS.toDays(dueInMs)}d"
        }
    }
}
