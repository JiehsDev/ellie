package com.example.jikan.screentime

import com.example.jikan.data.EarnRule

data class EarningProgressState(
    val accumulatedMs: Long = 0L,
    val rewardedBlocks: Int = 0,
)

data class EarningPlan(
    val state: EarningProgressState,
    val completedBlocks: Int,
    val rewardableBlocks: Int,
    val rewardMinutes: Int,
)

object EarningSessionProcessor {
    fun plan(
        rule: EarnRule,
        previous: EarningProgressState,
        intervalMs: Long,
        alreadyEarnedToday: Int,
    ): EarningPlan {
        if (intervalMs <= 0 || !rule.enabled || EarnRuleValidator.validate(rule).isNotEmpty()) {
            return EarningPlan(previous, previous.rewardedBlocks, 0, 0)
        }

        val accumulatedMs = previous.accumulatedMs + intervalMs
        val completedBlocks = (accumulatedMs / (rule.requiredMinutes * 60_000L)).toInt()
        val newCompletedBlocks = (completedBlocks - previous.rewardedBlocks).coerceAtLeast(0)
        val remainingRewardMinutes = (rule.dailyLimitMinutes - alreadyEarnedToday).coerceAtLeast(0)
        val blocksAllowedByCap = remainingRewardMinutes / rule.rewardMinutes
        val rewardableBlocks = newCompletedBlocks.coerceAtMost(blocksAllowedByCap)
        val rewardedBlocks = previous.rewardedBlocks + rewardableBlocks

        return EarningPlan(
            state = EarningProgressState(
                accumulatedMs = accumulatedMs,
                rewardedBlocks = rewardedBlocks,
            ),
            completedBlocks = completedBlocks,
            rewardableBlocks = rewardableBlocks,
            rewardMinutes = rewardableBlocks * rule.rewardMinutes,
        )
    }
}
