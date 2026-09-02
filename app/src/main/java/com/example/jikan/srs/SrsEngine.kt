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

object SrsEngine {
    val INITIAL_STATE = SrsState(intervalDays = 0, easeFactor = 2.5f, repetitions = 0)

    private const val MIN_EASE_FACTOR = 1.3f
    private const val EASE_DELTA_CORRECT = 0.1f
    private const val EASE_DELTA_WRONG = 0.2f

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
}
