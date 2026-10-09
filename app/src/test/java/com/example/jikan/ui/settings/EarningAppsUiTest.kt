package com.example.jikan.ui.settings

import com.example.jikan.data.EarnRule
import com.example.jikan.data.EarningAppProgress
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Phase 9: the earning engine is untouched — these tests cover only the
 * configuration screen's display math and error copy.
 */
class EarningAppsUiTest {

    private fun rule(
        required: Int = 2,
        reward: Int = 5,
        daily: Int = 20,
    ) = EarnRule(
        id = 1L,
        packageName = "com.duolingo",
        requiredMinutes = required,
        rewardMinutes = reward,
        dailyLimitMinutes = daily,
        enabled = true,
    )

    private fun progress(rewardedBlocks: Int) = EarningAppProgress(
        ruleId = 1L,
        packageName = "com.duolingo",
        epochDay = 10L,
        accumulatedMs = rewardedBlocks * 2L * 60_000L,
        rewardedBlocks = rewardedBlocks,
        updatedAtMs = 0L,
    )

    @Test
    fun `no progress shows zero earned and full rewards`() {
        val p = rule().todayProgress(null)

        assertEquals(0, p.earnedTodayMinutes)
        assertEquals(4, p.rewardsRemaining) // 20 / 5
    }

    @Test
    fun `partial progress computes earned and remaining`() {
        val p = rule().todayProgress(progress(rewardedBlocks = 2))

        assertEquals(10, p.earnedTodayMinutes)
        assertEquals(2, p.rewardsRemaining) // (20 - 10) / 5
    }

    @Test
    fun `reaching the cap leaves zero rewards`() {
        val p = rule().todayProgress(progress(rewardedBlocks = 4))

        assertEquals(20, p.earnedTodayMinutes)
        assertEquals(0, p.rewardsRemaining)
    }

    @Test
    fun `over cap never goes negative`() {
        val p = rule().todayProgress(progress(rewardedBlocks = 99))

        assertEquals(0, p.rewardsRemaining)
    }

    @Test
    fun `invalid rule with zero reward does not crash`() {
        val p = rule(reward = 0).todayProgress(progress(rewardedBlocks = 1))

        assertEquals(0, p.earnedTodayMinutes)
        assertEquals(20, p.rewardsRemaining) // guarded divisor
    }

    @Test
    fun `describe formats the rule compactly`() {
        assertEquals("2m active → +5m · max 20m/day", rule().describe())
    }

    @Test
    fun `friendly errors map validator messages`() {
        assertEquals(
            "Active minutes must be at least 1.",
            friendlyRuleError(listOf("requiredMinutes must be positive")),
        )
        assertEquals(
            "Reward minutes must be at least 1.",
            friendlyRuleError(listOf("rewardMinutes must be positive")),
        )
        assertEquals(
            "Daily maximum must be at least 1.",
            friendlyRuleError(listOf("dailyLimitMinutes must be positive")),
        )
        assertEquals(
            "Daily maximum can't be less than the reward.",
            friendlyRuleError(listOf("dailyLimitMinutes must be at least rewardMinutes")),
        )
        assertEquals(
            "Couldn't save that rule.",
            friendlyRuleError(emptyList()),
        )
    }
}
