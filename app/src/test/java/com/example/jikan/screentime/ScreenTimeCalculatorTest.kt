package com.example.jikan.screentime

import com.example.jikan.data.EarnRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ScreenTimeCalculatorTest {

    @Test
    fun `daily aggregation combines duplicate app intervals and clips boundaries`() {
        val usage = AppUsageAggregator.aggregateIntervals(
            intervals = listOf(
                UsageInterval("app.a", startMs = -60_000L, endMs = 60_000L),
                UsageInterval("app.a", startMs = 60_000L, endMs = 180_000L),
                UsageInterval("app.b", startMs = 10_000L, endMs = 12_000L),
            ),
            windowStartMs = 0L,
            windowEndMs = 120_000L,
        )

        assertEquals(listOf(RawAppUsage("app.a", 120_000L)), usage)
    }

    @Test
    fun `top apps are sorted and percentages are calculated`() {
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

        assertEquals(60, summary.totalScreenTimeMinutes)
        assertEquals("Alpha", summary.topApps[0].appLabel)
        assertEquals("Gamma", summary.topApps[1].appLabel)
        assertEquals(50.0f, summary.topApps[0].percentage)
        assertEquals(33.3f, summary.topApps[1].percentage)
    }

    @Test
    fun `day over day comparison reports increase`() {
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
    fun `day over day comparison handles missing previous data`() {
        val summary = ScreenTimeCalculator.summarize(
            facts(usage = listOf(RawAppUsage("app.a", minutes(15))))
        )

        assertEquals(15, summary.recentUsageChange.deltaMinutes)
        assertNull(summary.recentUsageChange.percentageChange)
        assertEquals(UsageChangeDirection.UP, summary.recentUsageChange.direction)
    }

    @Test
    fun `exceeded limit detection reports over limit minutes`() {
        val summary = ScreenTimeCalculator.summarize(
            facts(
                usage = listOf(
                    RawAppUsage("restricted.a", minutes(45)),
                    RawAppUsage("restricted.b", minutes(10)),
                ),
                restrictedLimits = listOf(
                    RestrictedAppLimit("restricted.a", limitMinutes = 30),
                    RestrictedAppLimit("restricted.b", limitMinutes = 20),
                ),
            )
        )

        assertEquals(1, summary.exceededApps.size)
        assertEquals("restricted.a", summary.exceededApps[0].packageName)
        assertEquals(15, summary.exceededApps[0].overLimitMinutes)
    }

    @Test
    fun `restricted and earning app classification is calculated`() {
        val summary = ScreenTimeCalculator.summarize(
            facts(
                usage = listOf(
                    RawAppUsage("restricted", minutes(12)),
                    RawAppUsage("earning", minutes(8)),
                    RawAppUsage("both", minutes(6)),
                    RawAppUsage("general", minutes(4)),
                ),
                restrictedLimits = listOf(
                    RestrictedAppLimit("restricted", limitMinutes = 20),
                    RestrictedAppLimit("both", limitMinutes = 20),
                ),
                earnRules = listOf(
                    earnRule("earning"),
                    earnRule("both"),
                ),
            )
        )

        assertEquals(18, summary.restrictedAppMinutes)
        assertEquals(14, summary.earningAppMinutes)
        assertEquals(AppUsageStatus.RESTRICTED, summary.topApps.first { it.packageName == "restricted" }.status)
        assertEquals(AppUsageStatus.EARNING, summary.topApps.first { it.packageName == "earning" }.status)
        assertEquals(AppUsageStatus.RESTRICTED_AND_EARNING, summary.topApps.first { it.packageName == "both" }.status)
        assertEquals(AppUsageStatus.GENERAL, summary.topApps.first { it.packageName == "general" }.status)
    }

    @Test
    fun `missing usage data returns zero summary`() {
        val summary = ScreenTimeCalculator.summarize(facts(usage = emptyList(), recentDailyTotals = emptyList()))

        assertEquals(0, summary.totalScreenTimeMinutes)
        assertEquals(0, summary.averageDailyScreenTimeMinutes)
        assertEquals(emptyList<ScreenTimeAppUsage>(), summary.topApps)
        assertEquals(UsageChangeDirection.SAME, summary.recentUsageChange.direction)
    }

    @Test
    fun `average daily screen time uses recent totals`() {
        val summary = ScreenTimeCalculator.summarize(
            facts(
                usage = listOf(RawAppUsage("app.a", minutes(10))),
                recentDailyTotals = listOf(10, 20, 30),
            )
        )

        assertEquals(20, summary.averageDailyScreenTimeMinutes)
    }

    private fun facts(
        usage: List<RawAppUsage>,
        previousDayUsage: List<RawAppUsage> = emptyList(),
        recentDailyTotals: List<Int> = emptyList(),
        restrictedLimits: List<RestrictedAppLimit> = emptyList(),
        earnRules: List<EarnRule> = emptyList(),
        labels: Map<String, String> = emptyMap(),
        topAppLimit: Int = 5,
    ) = ScreenTimeFacts(
        epochDay = 10L,
        usage = usage,
        previousDayUsage = previousDayUsage,
        recentDailyTotals = recentDailyTotals,
        restrictedLimits = restrictedLimits,
        earnRules = earnRules,
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
