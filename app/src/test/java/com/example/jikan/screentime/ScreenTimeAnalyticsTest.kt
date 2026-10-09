package com.example.jikan.screentime

import com.example.jikan.data.EarnRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 5: analytics calculations are deterministic Kotlin — AI receives the
 * resulting [ScreenTimeSummary] as structured facts and must not redo the
 * arithmetic. Every calculation below is unit-tested without an emulator.
 */
class ScreenTimeAnalyticsTest {

    // --- Percentage rounding ---

    @Test
    fun `percentages round to one decimal place`() {
        assertEquals(33.3f, ScreenTimeCalculator.percentage(1, 3))
        assertEquals(66.7f, ScreenTimeCalculator.percentage(2, 3))
        assertEquals(16.7f, ScreenTimeCalculator.percentage(1, 6))
        assertEquals(50.0f, ScreenTimeCalculator.percentage(30, 60))
        assertEquals(100.0f, ScreenTimeCalculator.percentage(60, 60))
    }

    @Test
    fun `percentages are zero when there is nothing to divide`() {
        assertEquals(0f, ScreenTimeCalculator.percentage(0, 60))
        assertEquals(0f, ScreenTimeCalculator.percentage(30, 0))
        assertEquals(0f, ScreenTimeCalculator.percentage(0, 0))
    }

    @Test
    fun `percentages never exceed one hundred`() {
        assertEquals(100.0f, ScreenTimeCalculator.percentage(90, 60))
    }

    // --- Zero usage ---

    @Test
    fun `zero usage produces an empty but valid summary`() {
        val summary = ScreenTimeCalculator.summarize(
            facts(usage = emptyList(), recentDailyTotals = emptyList())
        )

        assertEquals(0, summary.totalScreenTimeMinutes)
        assertEquals(0, summary.restrictedAppMinutes)
        assertEquals(0, summary.earningAppMinutes)
        assertEquals(0, summary.earnedMinutes)
        assertEquals(0, summary.spentMinutes)
        assertEquals(0, summary.walletBalanceMinutes)
        assertTrue(summary.topApps.isEmpty())
        assertTrue(summary.approachingApps.isEmpty())
        assertTrue(summary.atLimitApps.isEmpty())
        assertTrue(summary.exceededApps.isEmpty())
        assertTrue(summary.weeklyTrend.isEmpty())
        assertEquals(UsageChangeDirection.SAME, summary.recentUsageChange.direction)
        assertEquals(UsageChangeDirection.SAME, summary.averageUsageChange.direction)
    }

    // --- Yesterday comparison ---

    @Test
    fun `today versus yesterday reports increases`() {
        val summary = ScreenTimeCalculator.summarize(
            facts(
                usage = listOf(RawAppUsage("app.a", minutes(90))),
                previousDayUsage = listOf(RawAppUsage("app.a", minutes(60))),
            )
        )

        assertEquals(30, summary.recentUsageChange.deltaMinutes)
        assertEquals(50.0f, summary.recentUsageChange.percentageChange)
        assertEquals(UsageChangeDirection.UP, summary.recentUsageChange.direction)
    }

    @Test
    fun `today versus yesterday reports decreases`() {
        val summary = ScreenTimeCalculator.summarize(
            facts(
                usage = listOf(RawAppUsage("app.a", minutes(30))),
                previousDayUsage = listOf(RawAppUsage("app.a", minutes(60))),
            )
        )

        assertEquals(-30, summary.recentUsageChange.deltaMinutes)
        assertEquals(-50.0f, summary.recentUsageChange.percentageChange)
        assertEquals(UsageChangeDirection.DOWN, summary.recentUsageChange.direction)
    }

    @Test
    fun `today versus yesterday reports no change`() {
        val summary = ScreenTimeCalculator.summarize(
            facts(
                usage = listOf(RawAppUsage("app.a", minutes(60))),
                previousDayUsage = listOf(RawAppUsage("app.a", minutes(60))),
            )
        )

        assertEquals(0, summary.recentUsageChange.deltaMinutes)
        assertEquals(0.0f, summary.recentUsageChange.percentageChange)
        assertEquals(UsageChangeDirection.SAME, summary.recentUsageChange.direction)
    }

    @Test
    fun `yesterday with no usage leaves percentage undefined`() {
        val summary = ScreenTimeCalculator.summarize(
            facts(usage = listOf(RawAppUsage("app.a", minutes(15))))
        )

        assertEquals(15, summary.recentUsageChange.deltaMinutes)
        assertNull(summary.recentUsageChange.percentageChange)
        assertEquals(UsageChangeDirection.UP, summary.recentUsageChange.direction)
    }

    // --- Weekly averages ---

    @Test
    fun `weekly average uses recent daily totals`() {
        val summary = ScreenTimeCalculator.summarize(
            facts(
                usage = listOf(RawAppUsage("app.a", minutes(10))),
                recentDailyTotals = listOf(10, 20, 30),
            )
        )

        assertEquals(20, summary.averageDailyScreenTimeMinutes)
    }

    @Test
    fun `weekly average of no data is zero`() {
        val summary = ScreenTimeCalculator.summarize(
            facts(usage = listOf(RawAppUsage("app.a", minutes(10))))
        )

        assertEquals(0, summary.averageDailyScreenTimeMinutes)
    }

    @Test
    fun `today versus average reports direction and percentage`() {
        val summary = ScreenTimeCalculator.summarize(
            facts(
                usage = listOf(RawAppUsage("app.a", minutes(90))),
                recentDailyTotals = listOf(60, 60, 60),
            )
        )

        assertEquals(60, summary.averageDailyScreenTimeMinutes)
        assertEquals(30, summary.averageUsageChange.deltaMinutes)
        assertEquals(50.0f, summary.averageUsageChange.percentageChange)
        assertEquals(UsageChangeDirection.UP, summary.averageUsageChange.direction)
    }

    @Test
    fun `today below average reports a decrease`() {
        val summary = ScreenTimeCalculator.summarize(
            facts(
                usage = listOf(RawAppUsage("app.a", minutes(30))),
                recentDailyTotals = listOf(60, 60, 60),
            )
        )

        assertEquals(-30, summary.averageUsageChange.deltaMinutes)
        assertEquals(UsageChangeDirection.DOWN, summary.averageUsageChange.direction)
    }

    // --- Weekly trend ---

    @Test
    fun `weekly trend lists seven days oldest first`() {
        val summary = ScreenTimeCalculator.summarize(
            facts(
                usage = listOf(RawAppUsage("app.a", minutes(100))),
                recentDailyTotals = listOf(100, 90, 80, 70, 60, 50, 40),
            )
        )

        assertEquals(7, summary.weeklyTrend.size)
        assertEquals(
            listOf(
                DailyUsage(epochDay = 4, minutes = 40),
                DailyUsage(epochDay = 5, minutes = 50),
                DailyUsage(epochDay = 6, minutes = 60),
                DailyUsage(epochDay = 7, minutes = 70),
                DailyUsage(epochDay = 8, minutes = 80),
                DailyUsage(epochDay = 9, minutes = 90),
                DailyUsage(epochDay = 10, minutes = 100),
            ),
            summary.weeklyTrend,
        )
    }

    // --- Top-app sorting ---

    @Test
    fun `top apps are sorted by usage descending and honor the limit`() {
        val summary = ScreenTimeCalculator.summarize(
            facts(
                usage = listOf(
                    RawAppUsage("app.a", minutes(30)),
                    RawAppUsage("app.b", minutes(10)),
                    RawAppUsage("app.c", minutes(20)),
                ),
                labels = mapOf("app.a" to "Alpha", "app.b" to "Beta", "app.c" to "Gamma"),
                topAppLimit = 2,
            )
        )

        assertEquals(listOf("app.a", "app.c"), summary.topApps.map { it.packageName })
        assertEquals(50.0f, summary.topApps[0].percentage)
        assertEquals(33.3f, summary.topApps[1].percentage)
    }

    // --- Limit classification ---

    @Test
    fun `usage below eighty percent of the limit is not flagged`() {
        val summary = ScreenTimeCalculator.summarize(
            facts(
                usage = listOf(RawAppUsage("app.a", minutes(23))),
                restrictedLimits = listOf(RestrictedAppLimit("app.a", limitMinutes = 30)),
            )
        )

        assertTrue(summary.approachingApps.isEmpty())
        assertTrue(summary.atLimitApps.isEmpty())
        assertTrue(summary.exceededApps.isEmpty())
    }

    @Test
    fun `usage at eighty percent of the limit is approaching`() {
        val summary = ScreenTimeCalculator.summarize(
            facts(
                usage = listOf(RawAppUsage("app.a", minutes(24))),
                restrictedLimits = listOf(RestrictedAppLimit("app.a", limitMinutes = 30)),
                labels = mapOf("app.a" to "Alpha"),
            )
        )

        assertEquals(1, summary.approachingApps.size)
        assertEquals("app.a", summary.approachingApps[0].packageName)
        assertEquals("Alpha", summary.approachingApps[0].appLabel)
        assertEquals(6, summary.approachingApps[0].remainingMinutes)
        assertTrue(summary.atLimitApps.isEmpty())
        assertTrue(summary.exceededApps.isEmpty())
    }

    @Test
    fun `approaching apps are sorted most urgent first`() {
        val summary = ScreenTimeCalculator.summarize(
            facts(
                usage = listOf(
                    RawAppUsage("app.a", minutes(24)),
                    RawAppUsage("app.b", minutes(29)),
                ),
                restrictedLimits = listOf(
                    RestrictedAppLimit("app.a", limitMinutes = 30),
                    RestrictedAppLimit("app.b", limitMinutes = 30),
                ),
            )
        )

        assertEquals(listOf("app.b", "app.a"), summary.approachingApps.map { it.packageName })
    }

    @Test
    fun `usage exactly at the limit is reported as at limit`() {
        val summary = ScreenTimeCalculator.summarize(
            facts(
                usage = listOf(RawAppUsage("app.a", minutes(30))),
                restrictedLimits = listOf(RestrictedAppLimit("app.a", limitMinutes = 30)),
            )
        )

        assertEquals(1, summary.atLimitApps.size)
        assertEquals("app.a", summary.atLimitApps[0].packageName)
        assertEquals(0, summary.atLimitApps[0].remainingMinutes)
        assertTrue(summary.approachingApps.isEmpty())
        assertTrue(summary.exceededApps.isEmpty())
    }

    @Test
    fun `usage over the limit is reported as exceeded`() {
        val summary = ScreenTimeCalculator.summarize(
            facts(
                usage = listOf(RawAppUsage("app.a", minutes(45))),
                restrictedLimits = listOf(RestrictedAppLimit("app.a", limitMinutes = 30)),
            )
        )

        assertEquals(1, summary.exceededApps.size)
        assertEquals("app.a", summary.exceededApps[0].packageName)
        assertEquals(45, summary.exceededApps[0].minutes)
        assertEquals(15, summary.exceededApps[0].overLimitMinutes)
        assertTrue(summary.approachingApps.isEmpty())
        assertTrue(summary.atLimitApps.isEmpty())
    }

    @Test
    fun `apps without usage are not flagged against their limits`() {
        val summary = ScreenTimeCalculator.summarize(
            facts(
                usage = listOf(RawAppUsage("app.a", minutes(45))),
                restrictedLimits = listOf(
                    RestrictedAppLimit("app.a", limitMinutes = 30),
                    RestrictedAppLimit("app.b", limitMinutes = 30),
                ),
            )
        )

        assertEquals(1, summary.exceededApps.size)
        assertTrue(summary.approachingApps.isEmpty())
        assertTrue(summary.atLimitApps.isEmpty())
    }

    // --- Restricted scope: locked or limit-configured ---

    @Test
    fun `locked apps count as restricted even without a configured limit`() {
        val summary = ScreenTimeCalculator.summarize(
            facts(
                usage = listOf(RawAppUsage("locked.app", minutes(12))),
                lockedPackages = setOf("locked.app"),
            )
        )

        assertEquals(12, summary.restrictedAppMinutes)
        assertEquals(AppUsageStatus.RESTRICTED, summary.topApps[0].status)
    }

    // --- Earning and spending totals ---

    @Test
    fun `earned spent and wallet balance are reported`() {
        val summary = ScreenTimeCalculator.summarize(
            facts(
                usage = listOf(RawAppUsage("app.a", minutes(10))),
                earnedMinutes = 15,
                spentMinutes = 20,
                walletBalanceMinutes = 10,
            )
        )

        assertEquals(15, summary.earnedMinutes)
        assertEquals(20, summary.spentMinutes)
        assertEquals(10, summary.walletBalanceMinutes)
    }

    @Test
    fun `earning apps list contains only earning packages sorted by usage`() {
        val summary = ScreenTimeCalculator.summarize(
            facts(
                usage = listOf(
                    RawAppUsage("learn.a", minutes(20)),
                    RawAppUsage("game.b", minutes(50)),
                    RawAppUsage("learn.c", minutes(5)),
                ),
                earnRules = listOf(earnRule("learn.a"), earnRule("learn.c")),
            )
        )

        assertEquals(listOf("learn.a", "learn.c"), summary.earningApps.map { it.packageName })
        assertEquals(25, summary.earningAppMinutes)
        // 20 of 75 total -> 26.7%
        assertEquals(26.7f, summary.earningApps[0].percentage)
    }

    // --- helpers ---

    private fun facts(
        usage: List<RawAppUsage>,
        previousDayUsage: List<RawAppUsage> = emptyList(),
        recentDailyTotals: List<Int> = emptyList(),
        restrictedLimits: List<RestrictedAppLimit> = emptyList(),
        lockedPackages: Set<String> = emptySet(),
        earnRules: List<EarnRule> = emptyList(),
        earnedMinutes: Int = 0,
        spentMinutes: Int = 0,
        walletBalanceMinutes: Int = 0,
        labels: Map<String, String> = emptyMap(),
        topAppLimit: Int = 5,
    ) = ScreenTimeFacts(
        epochDay = 10L,
        usage = usage,
        previousDayUsage = previousDayUsage,
        recentDailyTotals = recentDailyTotals,
        restrictedLimits = restrictedLimits,
        lockedPackages = lockedPackages,
        earnRules = earnRules,
        earnedMinutes = earnedMinutes,
        spentMinutes = spentMinutes,
        walletBalanceMinutes = walletBalanceMinutes,
        labels = labels,
        topAppLimit = topAppLimit,
    )

    private fun earnRule(packageName: String) = EarnRule(
        packageName = packageName,
        requiredMinutes = 2,
        rewardMinutes = 5,
        dailyLimitMinutes = 20,
        enabled = true,
    )

    private fun minutes(value: Int): Long = value * 60_000L
}
