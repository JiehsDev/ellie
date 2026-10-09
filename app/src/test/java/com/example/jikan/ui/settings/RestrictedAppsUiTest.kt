package com.example.jikan.ui.settings

import com.example.jikan.data.AppRestriction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 10: enforcement is untouched — these tests cover only the
 * configuration screen's display math and editor validation.
 */
class RestrictedAppsUiTest {

    private fun restriction(limit: Int = 30) =
        AppRestriction(packageName = "com.tiktok", appLabel = "TikTok", dailyLimitMinutes = limit)

    @Test
    fun `under limit shows remaining`() {
        val p = restriction(30).todayProgress(23)

        assertEquals(23, p.usedMinutes)
        assertEquals(30, p.limitMinutes)
        assertEquals(7, p.remainingMinutes)
        assertFalse(p.isReached)
        assertEquals("23m / 30m · 7m remaining", p.describe())
    }

    @Test
    fun `exactly at limit counts as reached`() {
        val p = restriction(45).todayProgress(45)

        assertTrue(p.isReached)
        assertEquals(0, p.remainingMinutes)
        assertEquals("45m / 45m · Limit reached", p.describe())
    }

    @Test
    fun `over limit stays reached with zero remaining`() {
        val p = restriction(30).todayProgress(42)

        assertTrue(p.isReached)
        assertEquals(0, p.remainingMinutes)
        assertEquals("42m / 30m · Limit reached", p.describe())
    }

    @Test
    fun `zero limit means any usage is reached`() {
        val p = restriction(0).todayProgress(1)

        assertTrue(p.isReached)
        assertEquals("1m / 0m · Limit reached", p.describe())
    }

    @Test
    fun `zero limit with zero usage is reached`() {
        val p = restriction(0).todayProgress(0)

        assertTrue(p.isReached)
    }

    @Test
    fun `validateDailyLimit accepts zero and positives`() {
        assertNull(validateDailyLimit("0"))
        assertNull(validateDailyLimit("30"))
        assertNull(validateDailyLimit("180"))
    }

    @Test
    fun `validateDailyLimit rejects bad input`() {
        assertEquals("Enter whole minutes.", validateDailyLimit(""))
        assertEquals("Enter whole minutes.", validateDailyLimit("abc"))
        assertEquals("Enter whole minutes.", validateDailyLimit("12.5"))
        assertEquals("The limit can't be negative.", validateDailyLimit("-5"))
    }
}
