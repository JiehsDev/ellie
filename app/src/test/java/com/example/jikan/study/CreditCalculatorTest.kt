package com.example.jikan.study

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CreditCalculatorTest {
    @Test
    fun `awards flat credits per correct answer`() {
        assertEquals(12, CreditCalculator.creditsEarned(correctCount = 6, totalCount = 10))
    }

    @Test
    fun `perfect score adds a bonus`() {
        assertEquals(25, CreditCalculator.creditsEarned(correctCount = 10, totalCount = 10))
        assertEquals(18, CreditCalculator.creditsEarned(correctCount = 9, totalCount = 10))
    }

    @Test
    fun `zero correct earns zero credits`() {
        assertEquals(0, CreditCalculator.creditsEarned(correctCount = 0, totalCount = 10))
    }

    @Test
    fun `a single study day does not yet earn the streak bonus`() {
        val noStreak = CreditCalculator.creditsEarned(correctCount = 6, totalCount = 10, currentStreakDays = 1)
        assertEquals(12, noStreak)
    }

    @Test
    fun `two or more consecutive days earn a 10 percent streak bonus`() {
        val withStreak = CreditCalculator.creditsEarned(correctCount = 6, totalCount = 10, currentStreakDays = 2)
        assertEquals(13, withStreak) // 12 * 1.10 = 13.2 -> rounds to 13
    }

    @Test
    fun `later sessions the same day earn progressively fewer credits`() {
        val first = CreditCalculator.creditsEarned(correctCount = 6, totalCount = 10, priorSessionsToday = 0)
        val second = CreditCalculator.creditsEarned(correctCount = 6, totalCount = 10, priorSessionsToday = 1)
        val third = CreditCalculator.creditsEarned(correctCount = 6, totalCount = 10, priorSessionsToday = 2)
        assertTrue(second < first)
        assertTrue(third < second)
    }

    @Test
    fun `daily cap never drops credits to zero for a correct answer`() {
        val manySessionsToday = CreditCalculator.creditsEarned(correctCount = 6, totalCount = 10, priorSessionsToday = 50)
        assertTrue("grinding should still earn something", manySessionsToday > 0)
    }
}
