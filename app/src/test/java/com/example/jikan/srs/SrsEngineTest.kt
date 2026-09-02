package com.example.jikan.srs

import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SrsEngineTest {

    @Test
    fun `correct answers grow interval through the fixed steps then by ease factor`() {
        var state = SrsEngine.INITIAL_STATE
        val now = 0L

        var result = SrsEngine.review(state, wasCorrect = true, now = now)
        assertEquals(1, result.intervalDays)
        state = state.copy(intervalDays = result.intervalDays, easeFactor = result.easeFactor, repetitions = result.repetitions)

        result = SrsEngine.review(state, wasCorrect = true, now = now)
        assertEquals(3, result.intervalDays)
        state = state.copy(intervalDays = result.intervalDays, easeFactor = result.easeFactor, repetitions = result.repetitions)

        result = SrsEngine.review(state, wasCorrect = true, now = now)
        assertEquals(7, result.intervalDays)
        state = state.copy(intervalDays = result.intervalDays, easeFactor = result.easeFactor, repetitions = result.repetitions)

        result = SrsEngine.review(state, wasCorrect = true, now = now)
        assertEquals(14, result.intervalDays)
        state = state.copy(intervalDays = result.intervalDays, easeFactor = result.easeFactor, repetitions = result.repetitions)

        result = SrsEngine.review(state, wasCorrect = true, now = now)
        assertTrue("interval should keep growing past the fixed steps", result.intervalDays > 14)
    }

    @Test
    fun `correct answer schedules dueAt intervalDays in the future`() {
        val now = 1_000_000L
        val result = SrsEngine.review(SrsEngine.INITIAL_STATE, wasCorrect = true, now = now)
        assertEquals(now + TimeUnit.DAYS.toMillis(1), result.dueAt)
    }

    @Test
    fun `wrong answer resets interval and repetitions and is due immediately`() {
        val progressed = SrsState(intervalDays = 14, easeFactor = 2.8f, repetitions = 4)
        val now = 5_000L

        val result = SrsEngine.review(progressed, wasCorrect = false, now = now)

        assertEquals(0, result.intervalDays)
        assertEquals(0, result.repetitions)
        assertEquals(now, result.dueAt)
    }

    @Test
    fun `ease factor never drops below the floor`() {
        var state = SrsState(intervalDays = 1, easeFactor = 1.3f, repetitions = 1)

        repeat(5) {
            val result = SrsEngine.review(state, wasCorrect = false, now = 0L)
            state = state.copy(easeFactor = result.easeFactor)
            assertTrue(result.easeFactor >= 1.3f)
        }
    }
}
