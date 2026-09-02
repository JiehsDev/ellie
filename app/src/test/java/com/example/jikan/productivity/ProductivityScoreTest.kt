package com.example.jikan.productivity

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class ProductivityScoreTest {
    private fun week(
        locked: List<Int>,
        credits: List<Int>,
        activeDays: Int,
        observed: Boolean = true,
    ): List<DaySignal> = List(7) { index ->
        DaySignal(
            lockedAppMinutes = locked[index],
            creditsEarned = credits[index],
            hadCompletion = index < activeDays,
            observed = observed,
        )
    }

    @Test
    fun `a good week scores locked in`() {
        val result = ProductivityScore.compute(
            week(
                locked = listOf(10, 5, 0, 15, 8, 12, 6),
                credits = listOf(18, 12, 0, 20, 15, 10, 14),
                activeDays = 6,
            )
        )
        assertNotNull(result)
        assertEquals(85, result!!.score)
        assertEquals(ProductivityBand.LOCKED_IN, result.band)
    }

    @Test
    fun `a heavy week scores settling`() {
        val result = ProductivityScore.compute(
            week(
                locked = listOf(55, 60, 48, 60, 52, 58, 45),
                credits = listOf(8, 0, 0, 5, 0, 0, 6),
                activeDays = 3,
            )
        )
        assertEquals(33, result!!.score)
        assertEquals(ProductivityBand.SETTLING, result.band)
    }

    @Test
    fun `a bad week scores drifting`() {
        val result = ProductivityScore.compute(
            week(
                locked = listOf(120, 95, 140, 110, 90, 130, 100),
                credits = listOf(0, 5, 0, 0, 0, 0, 0),
                activeDays = 1,
            )
        )
        assertEquals(5, result!!.score)
        assertEquals(ProductivityBand.DRIFTING, result.band)
    }

    @Test
    fun `never touching a locked app scores steady even without studying`() {
        val result = ProductivityScore.compute(
            week(locked = List(7) { 0 }, credits = List(7) { 0 }, activeDays = 0)
        )
        assertEquals(45, result!!.score)
        assertEquals(ProductivityBand.STEADY, result.band)
    }

    @Test
    fun `no history at all yields no score`() {
        val days = List(7) {
            DaySignal(lockedAppMinutes = 0, creditsEarned = 0, hadCompletion = false, observed = false)
        }
        assertNull(ProductivityScore.compute(days))
    }

    @Test
    fun `unobserved days are excluded from restraint rather than counted as spotless`() {
        val days = List(7) { index ->
            DaySignal(
                lockedAppMinutes = if (index == 0) 90 else 0,
                creditsEarned = 0,
                hadCompletion = false,
                observed = index == 0,
            )
        }
        // Only the observed day counts, and it burned the whole scale, so restraint is 0.
        assertEquals(0, ProductivityScore.compute(days)!!.score)
    }

    @Test
    fun `with nothing observed the remaining weights are renormalised`() {
        val days = List(7) {
            DaySignal(lockedAppMinutes = 0, creditsEarned = 15, hadCompletion = true, observed = false)
        }
        // effort 1.0 and consistency 1.0, renormalised over 0.55, is a full score.
        assertEquals(100, ProductivityScore.compute(days)!!.score)
    }

    @Test
    fun `band boundaries fall on the documented scores`() {
        assertEquals(ProductivityBand.DRIFTING, ProductivityScore.bandFor(19))
        assertEquals(ProductivityBand.SETTLING, ProductivityScore.bandFor(20))
        assertEquals(ProductivityBand.SETTLING, ProductivityScore.bandFor(44))
        assertEquals(ProductivityBand.STEADY, ProductivityScore.bandFor(45))
        assertEquals(ProductivityBand.STEADY, ProductivityScore.bandFor(64))
        assertEquals(ProductivityBand.FOCUSED, ProductivityScore.bandFor(65))
        assertEquals(ProductivityBand.FOCUSED, ProductivityScore.bandFor(84))
        assertEquals(ProductivityBand.LOCKED_IN, ProductivityScore.bandFor(85))
    }

    @Test
    fun `it reports how many days went unobserved`() {
        val days = List(7) { index ->
            DaySignal(lockedAppMinutes = 0, creditsEarned = 0, hadCompletion = true, observed = index < 4)
        }
        val result = ProductivityScore.compute(days)!!
        assertEquals(4, result.observedDays)
        assertEquals(3, result.unobservedDays)
    }
}
