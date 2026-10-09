package com.example.jikan.screentime

import com.example.jikan.data.EarnRule

data class EarningResult(
    val completedBlocks: Int,
    val rewardMinutes: Int,
)

object EarningCalculator {
    fun calculate(rule: EarnRule, foregroundMinutes: Int, alreadyEarnedToday: Int): EarningResult {
        if (!rule.enabled || EarnRuleValidator.validate(rule).isNotEmpty()) {
            return EarningResult(completedBlocks = 0, rewardMinutes = 0)
        }
        if (foregroundMinutes < rule.requiredMinutes) {
            return EarningResult(completedBlocks = 0, rewardMinutes = 0)
        }

        val completedBlocks = foregroundMinutes / rule.requiredMinutes
        val uncappedReward = completedBlocks * rule.rewardMinutes
        val remainingDailyReward = (rule.dailyLimitMinutes - alreadyEarnedToday).coerceAtLeast(0)
        val reward = uncappedReward.coerceAtMost(remainingDailyReward)
        return EarningResult(
            completedBlocks = completedBlocks,
            rewardMinutes = reward,
        )
    }
}
