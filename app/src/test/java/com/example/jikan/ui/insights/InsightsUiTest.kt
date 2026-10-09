package com.example.jikan.ui.insights

import com.example.jikan.screentime.UsageChange
import com.example.jikan.screentime.UsageChangeDirection
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Phase 11: all numbers are pre-calculated by ScreenTimeCalculator — these
 * tests cover only the comparison copy.
 */
class InsightsUiTest {

    @Test
    fun `formatMinutes renders compact durations`() {
        assertEquals("45m", formatMinutes(45))
        assertEquals("1h", formatMinutes(60))
        assertEquals("3h 21m", formatMinutes(201))
        assertEquals("0m", formatMinutes(0))
    }

    @Test
    fun `day comparison describes the delta`() {
        val down = UsageChange(-18, -8f, UsageChangeDirection.DOWN)
        assertEquals("↓ 18m vs yesterday", describeDayComparison(down, 224))

        val up = UsageChange(12, 5f, UsageChangeDirection.UP)
        assertEquals("↑ 12m vs yesterday", describeDayComparison(up, 200))

        val same = UsageChange(0, 0f, UsageChangeDirection.SAME)
        assertEquals("Same as yesterday.", describeDayComparison(same, 200))
    }

    @Test
    fun `day comparison handles first day`() {
        val change = UsageChange(201, null, UsageChangeDirection.UP)
        assertEquals("First day of tracking.", describeDayComparison(change, 0))
    }

    @Test
    fun `average comparison describes the gap`() {
        val below = UsageChange(-12, -5.7f, UsageChangeDirection.DOWN)
        assertEquals("12m below your 7-day average", describeAverageComparison(below))

        val above = UsageChange(8, 3.8f, UsageChangeDirection.UP)
        assertEquals("8m above your 7-day average", describeAverageComparison(above))

        val same = UsageChange(0, 0f, UsageChangeDirection.SAME)
        assertEquals("Right at your 7-day average.", describeAverageComparison(same))
    }
}
