package com.example.jikan.study

import kotlin.math.min

/**
 * Central source of truth for Ellie's time-credit economy.
 *
 * All earning and spending calculations go through this policy. It is pure
 * Kotlin with no Android dependencies, making it fully unit-testable.
 *
 * ## Earning model
 * Credits are earned from **validated study minutes** (duration of completed
 * study sessions), not per-answer. For the Balanced profile, earning is
 * tiered:
 * - First 60 validated minutes/day: 2 study minutes = 1 credit
 * - Next 60 validated minutes/day (60-120): 3 study minutes = 1 credit
 * - Beyond 120: no standard credits
 *
 * Earnings are calculated incrementally: only the NEW minutes in the current
 * tier generate credits. Fractional progress is preserved via integer
 * division across the cumulative daily total — repeated short sessions
 * cannot generate extra credits through rounding.
 *
 * ## Spending model
 * - Daily recreational allowance is shared across all restricted apps.
 * - A single unlock session cannot exceed the profile's max duration.
 * - Available unlock time = min(earned credits, allowance remaining, profile max).
 */
object CreditPolicy {

    enum class CreditProfile {
        GENTLE,
        BALANCED,
        FOCUSED,
    }

    /**
     * An earning tier: [startMinutes, endMinutes) of validated study time
     * earns 1 credit per [studyMinutesPerCredit] minutes.
     */
    data class EarningTier(
        val startMinutes: Int,
        val endMinutes: Int, // Int.MAX_VALUE for uncapped
        val studyMinutesPerCredit: Int,
    )

    data class ProfileConfig(
        val profile: CreditProfile,
        val displayName: String,
        val description: String,
        val earningTiers: List<EarningTier>,
        val dailyAllowanceMinutes: Int,
        val maxUnlockMinutes: Int,
    )

    val GENTLE = ProfileConfig(
        profile = CreditProfile.GENTLE,
        displayName = "Gentle",
        description = "1 study minute = 1 credit. Generous allowance for lighter days.",
        earningTiers = listOf(
            EarningTier(0, Int.MAX_VALUE, 1),
        ),
        dailyAllowanceMinutes = 60,
        maxUnlockMinutes = 20,
    )

    val BALANCED = ProfileConfig(
        profile = CreditProfile.BALANCED,
        displayName = "Balanced",
        description = "2:1 for the first hour, then 3:1 for the second hour of study.",
        earningTiers = listOf(
            EarningTier(0, 60, 2),
            EarningTier(60, 120, 3),
        ),
        dailyAllowanceMinutes = 30,
        maxUnlockMinutes = 15,
    )

    val FOCUSED = ProfileConfig(
        profile = CreditProfile.FOCUSED,
        displayName = "Focused",
        description = "3 study minutes = 1 credit. Minimal allowance for deep-focus days.",
        earningTiers = listOf(
            EarningTier(0, Int.MAX_VALUE, 3),
        ),
        dailyAllowanceMinutes = 20,
        maxUnlockMinutes = 10,
    )

    fun configFor(profile: CreditProfile): ProfileConfig = when (profile) {
        CreditProfile.GENTLE -> GENTLE
        CreditProfile.BALANCED -> BALANCED
        CreditProfile.FOCUSED -> FOCUSED
    }

    /**
     * Total credits earned for [validatedMinutes] cumulative study minutes
     * under the given profile. Used for incremental calculation.
     */
    fun totalCreditsForMinutes(config: ProfileConfig, validatedMinutes: Int): Int {
        if (validatedMinutes <= 0) return 0
        var credits = 0
        for (tier in config.earningTiers) {
            if (validatedMinutes <= tier.startMinutes) break
            val minutesInTier = min(validatedMinutes, tier.endMinutes) - tier.startMinutes
            credits += minutesInTier / tier.studyMinutesPerCredit
        }
        return credits
    }

    /**
     * Incremental credits for NEW validated minutes, given the already-counted
     * daily total. This is the method to call when a session completes.
     *
     * Example (Balanced): 40 prior minutes + 20 new = 60 total → 30 credits
     * total, 20 already awarded → 10 new credits.
     */
    fun creditsForNewMinutes(
        config: ProfileConfig,
        priorValidatedMinutes: Int,
        newMinutes: Int,
    ): Int {
        if (newMinutes <= 0) return 0
        val before = totalCreditsForMinutes(config, priorValidatedMinutes)
        val after = totalCreditsForMinutes(config, priorValidatedMinutes + newMinutes)
        return (after - before).coerceAtLeast(0)
    }

    /**
     * Minutes of validated study still needed to earn the next credit.
     * Returns 0 if no more credits can be earned today (cap reached).
     */
    fun minutesToNextCredit(config: ProfileConfig, validatedMinutes: Int): Int {
        if (validatedMinutes < 0) return 0
        for (tier in config.earningTiers) {
            if (validatedMinutes < tier.endMinutes) {
                val tierStart = maxOf(validatedMinutes, tier.startMinutes)
                val progressInTier = tierStart - tier.startMinutes
                val remainder = progressInTier % tier.studyMinutesPerCredit
                return if (remainder == 0 && progressInTier > 0 && validatedMinutes >= tier.startMinutes) {
                    // At a tier boundary already counted; next credit needs a full ratio.
                    // Actually if remainder==0 we just earned one; next needs full ratio.
                    tier.studyMinutesPerCredit
                } else {
                    tier.studyMinutesPerCredit - remainder
                }
            }
        }
        return 0 // Beyond all tiers: no more credits today.
    }

    /**
     * Current earning tier index for display, or -1 if capped.
     */
    fun currentTierIndex(config: ProfileConfig, validatedMinutes: Int): Int {
        config.earningTiers.forEachIndexed { i, tier ->
            if (validatedMinutes < tier.endMinutes) return i
        }
        return -1
    }

    /**
     * Maximum unlock duration in minutes: the minimum of earned credits,
     * remaining daily allowance, and the profile's per-session cap.
     */
    fun maxUnlockDuration(
        config: ProfileConfig,
        earnedCreditBalance: Int,
        allowanceRemaining: Int,
    ): Int {
        return minOf(
            earnedCreditBalance.coerceAtLeast(0),
            allowanceRemaining.coerceAtLeast(0),
            config.maxUnlockMinutes,
        )
    }

    /**
     * Explains why an unlock was limited, for non-judgmental UI messaging.
     */
    fun unlockLimitReason(
        config: ProfileConfig,
        earnedCreditBalance: Int,
        allowanceRemaining: Int,
    ): String {
        return when {
            earnedCreditBalance <= 0 && allowanceRemaining <= 0 ->
                "No credits earned yet and no allowance remaining. Study to earn time."
            earnedCreditBalance <= 0 ->
                "No credits earned yet. Study to earn time."
            allowanceRemaining <= 0 ->
                "Daily recreational allowance is used up. It resets tomorrow."
            allowanceRemaining < earnedCreditBalance ->
                "Limited to $allowanceRemaining minutes by your remaining daily allowance."
            earnedCreditBalance < config.maxUnlockMinutes ->
                "Limited to $earnedCreditBalance minutes by your earned credit balance."
            else ->
                "Limited to ${config.maxUnlockMinutes} minutes per unlock session."
        }
    }
}
