package com.example.jikan.ui.settings

import com.example.jikan.data.EarnRule
import com.example.jikan.data.EarningAppProgress

/**
 * Phase 9: display math for the earning-app configuration screen.
 *
 * Pure Kotlin — the earning engine owns all accounting; these functions only
 * render what [EarningAppProgress] already recorded.
 */
data class EarningRuleProgress(
    val earnedTodayMinutes: Int,
    val rewardsRemaining: Int,
)

/**
 * Today's progress for a rule. `rewardedBlocks` come straight from the
 * engine's progress row; rewards remaining mirrors the engine's daily-cap
 * math: full rewards still available = (dailyLimit - earned) / reward.
 */
fun EarnRule.todayProgress(progress: EarningAppProgress?): EarningRuleProgress {
    val earned = (progress?.rewardedBlocks ?: 0) * rewardMinutes
    val remaining = (dailyLimitMinutes - earned).coerceAtLeast(0) / rewardMinutes.coerceAtLeast(1)
    return EarningRuleProgress(
        earnedTodayMinutes = earned,
        rewardsRemaining = remaining,
    )
}

/** One-line, user-friendly summary of a rule, e.g. "2m active → +5m · max 20m/day". */
fun EarnRule.describe(): String =
    "${requiredMinutes}m active → +${rewardMinutes}m · max ${dailyLimitMinutes}m/day"

/** Maps [com.example.jikan.screentime.EarnRuleValidator] messages to UI copy. */
fun friendlyRuleError(errors: List<String>): String {
    val first = errors.firstOrNull() ?: return "Couldn't save that rule."
    return when {
        first.contains("dailyLimitMinutes must be at least") ->
            "Daily maximum can't be less than the reward."
        first.contains("requiredMinutes") ->
            "Active minutes must be at least 1."
        first.contains("rewardMinutes") ->
            "Reward minutes must be at least 1."
        first.contains("dailyLimitMinutes") ->
            "Daily maximum must be at least 1."
        else -> "Couldn't save that rule."
    }
}
