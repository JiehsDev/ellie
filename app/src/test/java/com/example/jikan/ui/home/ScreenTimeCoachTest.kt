package com.example.jikan.ui.home

import com.example.jikan.screentime.AppLimitStatus
import com.example.jikan.screentime.AppUsageStatus
import com.example.jikan.screentime.ExceededAppUsage
import com.example.jikan.screentime.ScreenTimeAppUsage
import com.example.jikan.screentime.ScreenTimeSummary
import com.example.jikan.screentime.UsageChange
import com.example.jikan.screentime.UsageChangeDirection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 6: Jikan Coach as a screen-time companion. The provider is pure
 * Kotlin — every message is a deterministic function of [CoachInput], so no
 * local model is required. These tests pin the safety priority, the exact
 * copy for each screen-time event, the tone constraints, and the event
 * derivation used by HomeViewModel.
 */
class ScreenTimeCoachTest {

    // --- Safety priority ---

    @Test
    fun `protection off outranks limit events`() {
        val message = HomeCoachMessageProvider.messageFor(
            input(
                protectionOn = false,
                recentEvent = CoachEvent.AppLimitExceeded("TikTok", 30, 12),
            )
        )

        assertEquals(
            "Protection is off right now. Jikan won't be able to enforce your limits until you turn it back on.",
            message,
        )
    }

    @Test
    fun `banking mode outranks everything`() {
        val message = HomeCoachMessageProvider.messageFor(
            input(
                bankingModeActive = true,
                protectionOn = false,
                recentEvent = CoachEvent.AppLimitExceeded("TikTok", 30, 12),
            )
        )

        assertEquals(
            "Banking mode is on - protection is off. Tap me when you're ready to put the guard back up.",
            message,
        )
    }

    // --- Screen-time event copy ---

    @Test
    fun `limit exceeded message names app and overage`() {
        val message = HomeCoachMessageProvider.messageFor(
            input(recentEvent = CoachEvent.AppLimitExceeded("TikTok", 30, 12))
        )

        assertEquals(
            "TikTok passed its 30-minute limit by 12 minutes. The guard is holding; I'm just keeping score.",
            message,
        )
    }

    @Test
    fun `limit reached message`() {
        val message = HomeCoachMessageProvider.messageFor(
            input(recentEvent = CoachEvent.AppLimitReached("TikTok", 30))
        )

        assertEquals("TikTok just hit its 30-minute limit. I'll keep watch from here.", message)
    }

    @Test
    fun `limit approaching message shows used and remaining`() {
        val message = HomeCoachMessageProvider.messageFor(
            input(recentEvent = CoachEvent.AppLimitApproaching("TikTok", 30, 6))
        )

        assertEquals("TikTok is at 24 of 30 minutes. 6 left before I step in.", message)
    }

    @Test
    fun `earning credit message contrasts with top consumer`() {
        val message = HomeCoachMessageProvider.messageFor(
            input(
                recentEvent = CoachEvent.EarningCreditGranted("Duolingo", 5),
                screenTime = summary(topApps = listOf(appUsage("com.tiktok", "TikTok", 28))),
            )
        )

        assertEquals("Duolingo gave you 5 minutes today, while TikTok used 28 minutes.", message)
    }

    @Test
    fun `earning credit message without top consumer stays simple`() {
        val message = HomeCoachMessageProvider.messageFor(
            input(recentEvent = CoachEvent.EarningCreditGranted("Duolingo", 5))
        )

        assertEquals("Duolingo gave you 5 minutes today.", message)
    }

    @Test
    fun `usage improved message`() {
        val message = HomeCoachMessageProvider.messageFor(
            input(recentEvent = CoachEvent.UsageImproved(18))
        )

        assertEquals("You've used 18 fewer minutes than yesterday. Quiet progress — I noticed.", message)
    }

    @Test
    fun `usage increased message is neutral`() {
        val message = HomeCoachMessageProvider.messageFor(
            input(recentEvent = CoachEvent.UsageIncreased(25))
        )

        assertEquals(
            "Screen time is up 25 minutes from yesterday. No alarm from me — just an observation.",
            message,
        )
    }

    @Test
    fun `high screen time message`() {
        val message = HomeCoachMessageProvider.messageFor(
            input(recentEvent = CoachEvent.HighScreenTime("YouTube", 42))
        )

        assertEquals("YouTube is your biggest time consumer today — 42 minutes.", message)
    }

    @Test
    fun `daily summary composes headline and leader`() {
        val message = HomeCoachMessageProvider.messageFor(
            input(
                recentEvent = CoachEvent.DailySummary,
                screenTime = summary(
                    total = 201, earned = 15, spent = 20,
                    topApps = listOf(appUsage("com.tiktok", "TikTok", 48)),
                ),
            )
        )

        assertEquals(
            "Today so far: 201 minutes of screen time, 15 earned and 20 spent. TikTok is in the lead at 48.",
            message,
        )
    }

    @Test
    fun `protection restored message`() {
        val message = HomeCoachMessageProvider.messageFor(
            input(recentEvent = CoachEvent.ProtectionRestored)
        )

        assertEquals("Protection is back on. I'll keep watch — you handle the day.", message)
    }

    // --- Passive observations from facts ---

    @Test
    fun `top app observation fires when no event did`() {
        val message = HomeCoachMessageProvider.messageFor(
            input(screenTime = summary(topApps = listOf(appUsage("com.yt", "YouTube", 42))))
        )

        assertEquals("YouTube is your biggest time consumer today — 42 minutes.", message)
    }

    @Test
    fun `tiny top app does not trigger an observation`() {
        val message = HomeCoachMessageProvider.messageFor(
            input(
                studiedTodayMinutes = 12,
                screenTime = summary(total = 45, topApps = listOf(appUsage("com.yt", "YouTube", 3))),
            )
        )

        assertEquals("Steady day so far: 45 minutes total. Nothing urgent on my watch.", message)
    }

    // --- Event derivation ---

    @Test
    fun `event derivation prioritizes exceeded over approaching`() {
        val event = HomeCoachMessageProvider.screenTimeEventFor(
            summary(
                exceededApps = listOf(exceeded("com.tiktok", "TikTok", 30, 12)),
                approachingApps = listOf(limitStatus("com.yt", "YouTube", 30, 6)),
            )
        )

        assertEquals(CoachEvent.AppLimitExceeded("TikTok", 30, 12), event)
    }

    @Test
    fun `event derivation picks approaching limit`() {
        val event = HomeCoachMessageProvider.screenTimeEventFor(
            summary(approachingApps = listOf(limitStatus("com.yt", "YouTube", 30, 6)))
        )

        assertEquals(CoachEvent.AppLimitApproaching("YouTube", 30, 6), event)
    }

    @Test
    fun `event derivation picks reached limit`() {
        val event = HomeCoachMessageProvider.screenTimeEventFor(
            summary(atLimitApps = listOf(limitStatus("com.yt", "YouTube", 30, 0)))
        )

        assertEquals(CoachEvent.AppLimitReached("YouTube", 30), event)
    }

    @Test
    fun `event derivation notices meaningful improvement`() {
        val event = HomeCoachMessageProvider.screenTimeEventFor(summary(usageDelta = -20))

        assertEquals(CoachEvent.UsageImproved(20), event)
    }

    @Test
    fun `event derivation notices meaningful increase`() {
        val event = HomeCoachMessageProvider.screenTimeEventFor(summary(usageDelta = 40))

        assertEquals(CoachEvent.UsageIncreased(40), event)
    }

    @Test
    fun `event derivation stays quiet on small changes`() {
        val event = HomeCoachMessageProvider.screenTimeEventFor(summary(usageDelta = 5))

        assertEquals(CoachEvent.HomeOpened, event)
    }

    // --- Mood ---

    @Test
    fun `mood reflects screen-time events`() {
        assertEquals(
            CoachMood.Focused,
            HomeCoachMessageProvider.moodFor(input(recentEvent = CoachEvent.AppLimitExceeded("T", 30, 1))),
        )
        assertEquals(
            CoachMood.Proud,
            HomeCoachMessageProvider.moodFor(input(recentEvent = CoachEvent.UsageImproved(20))),
        )
        assertEquals(
            CoachMood.WelcomeBack,
            HomeCoachMessageProvider.moodFor(input(recentEvent = CoachEvent.ProtectionRestored)),
        )
        assertEquals(
            CoachMood.Calm,
            HomeCoachMessageProvider.moodFor(input(recentEvent = CoachEvent.AppLimitApproaching("T", 30, 6))),
        )
        assertEquals(
            CoachMood.Playful,
            HomeCoachMessageProvider.moodFor(input(recentEvent = CoachEvent.EarningCreditGranted("D", 5))),
        )
        assertEquals(
            CoachMood.Concerned,
            HomeCoachMessageProvider.moodFor(input(recentEvent = CoachEvent.ProtectionDisabled)),
        )
    }

    // --- Tone constraints ---

    @Test
    fun `no message uses banned judgmental language`() {
        val banned = listOf("wasting", "addicted", "addict", "you failed", "need to stop")
        val messages = allScreenTimeMessages()

        for (message in messages) {
            for (word in banned) {
                assertTrue(
                    "banned phrase '$word' in: $message",
                    !message.lowercase().contains(word),
                )
            }
        }
    }

    @Test
    fun `normal messages are at most two sentences`() {
        for (message in allScreenTimeMessages()) {
            val terminators = message.count { it == '.' || it == '!' || it == '?' }
            assertTrue("too many sentences in: $message", terminators <= 2)
        }
    }

    @Test
    fun `messages never invent statistics`() {
        // Every number in the copy must come from the input facts.
        val message = HomeCoachMessageProvider.messageFor(
            input(recentEvent = CoachEvent.AppLimitApproaching("TikTok", 30, 6))
        )
        val numbers = Regex("\\d+").findAll(message).map { it.value }.toList()
        assertEquals(listOf("24", "30", "6"), numbers)
    }

    // --- Deterministic fallback ---

    @Test
    fun `coach works with no screen-time data and no model`() {
        val first = HomeCoachMessageProvider.messageFor(input(studiedTodayMinutes = 12))
        val second = HomeCoachMessageProvider.messageFor(input(studiedTodayMinutes = 12))

        assertEquals(first, second)
        assertEquals(
            "Ready when you are. Ten cards, a few minutes earned, and we both pretend that was effortless.",
            first,
        )
    }

    // --- helpers ---

    private fun allScreenTimeMessages(): List<String> {
        val withSummary = summary(topApps = listOf(appUsage("com.tiktok", "TikTok", 28)))
        return listOf(
            CoachEvent.AppLimitExceeded("TikTok", 30, 12),
            CoachEvent.AppLimitReached("TikTok", 30),
            CoachEvent.AppLimitApproaching("TikTok", 30, 6),
            CoachEvent.EarningCreditGranted("Duolingo", 5),
            CoachEvent.UsageImproved(18),
            CoachEvent.UsageIncreased(25),
            CoachEvent.HighScreenTime("YouTube", 42),
            CoachEvent.DailySummary,
            CoachEvent.ProtectionRestored,
            CoachEvent.ProtectionDisabled,
        ).map { event ->
            HomeCoachMessageProvider.messageFor(input(recentEvent = event, screenTime = withSummary))
        }
    }

    private fun input(
        protectionOn: Boolean = true,
        bankingModeActive: Boolean = false,
        recentEvent: CoachEvent = CoachEvent.HomeOpened,
        screenTime: ScreenTimeSummary? = null,
        studiedTodayMinutes: Int = 0,
    ) = CoachInput(
        userName = "",
        walletBalanceMinutes = 20,
        streakDays = 0,
        dueCount = 0,
        studiedTodayMinutes = studiedTodayMinutes,
        studiedYesterdayMinutes = 0,
        protectionOn = protectionOn,
        bankingModeActive = bankingModeActive,
        hourOfDay = 14,
        recentEvent = recentEvent,
        screenTime = screenTime,
    )

    private fun summary(
        total: Int = 0,
        earned: Int = 0,
        spent: Int = 0,
        usageDelta: Int = 0,
        topApps: List<ScreenTimeAppUsage> = emptyList(),
        exceededApps: List<ExceededAppUsage> = emptyList(),
        approachingApps: List<AppLimitStatus> = emptyList(),
        atLimitApps: List<AppLimitStatus> = emptyList(),
    ) = ScreenTimeSummary(
        epochDay = 10L,
        totalScreenTimeMinutes = total,
        previousDayScreenTimeMinutes = 0,
        averageDailyScreenTimeMinutes = 0,
        restrictedAppMinutes = 0,
        earningAppMinutes = 0,
        earnedMinutes = earned,
        spentMinutes = spent,
        topApps = topApps,
        exceededApps = exceededApps,
        recentUsageChange = UsageChange(
            deltaMinutes = usageDelta,
            percentageChange = null,
            direction = UsageChangeDirection.SAME,
        ),
        approachingApps = approachingApps,
        atLimitApps = atLimitApps,
    )

    private fun appUsage(packageName: String, appLabel: String, minutes: Int) = ScreenTimeAppUsage(
        packageName = packageName,
        appLabel = appLabel,
        minutes = minutes,
        percentage = 0f,
        status = AppUsageStatus.GENERAL,
    )

    private fun exceeded(packageName: String, appLabel: String, limit: Int, over: Int) =
        ExceededAppUsage(
            packageName = packageName,
            appLabel = appLabel,
            minutes = limit + over,
            limitMinutes = limit,
            overLimitMinutes = over,
        )

    private fun limitStatus(packageName: String, appLabel: String, limit: Int, remaining: Int) =
        AppLimitStatus(
            packageName = packageName,
            appLabel = appLabel,
            minutes = limit - remaining,
            limitMinutes = limit,
            remainingMinutes = remaining,
        )
}
