package com.example.jikan.screentime

import com.example.jikan.data.AppRestriction
import com.example.jikan.data.LockTier

/**
 * Phase 4: generic app restrictions.
 *
 * Deterministic policy answering one question:
 * "Is this package currently allowed to be opened?"
 *
 * Precedence is fixed and safety-first; protection state always wins:
 *
 * 1. Protection off  -> allowed (nothing can be enforced).
 * 2. Paused          -> allowed (user explicitly paused protection).
 * 3. Banking exempt  -> allowed (temporary per-app banking exemption).
 * 4. Existing lock rule:
 *      - wallet has minutes -> allowed (existing spend-to-unlock behavior,
 *        preserved exactly as the AccessibilityService implements it today).
 *      - wallet empty       -> blocked (existing tier intervention applies).
 * 5. Configured app restriction (independent of lock tiers and earn rules):
 *      - usage below limit  -> allowed.
 *      - usage at/over limit:
 *          - wallet has minutes and strict mode is OFF -> allowed; wallet
 *            minutes extend access generically (not tied to studying).
 *          - strict mode is ON -> blocked. Strict means limits are hard;
 *            the wallet cannot buy past a configured limit.
 *          - otherwise -> blocked.
 * 6. No restriction and no lock rule -> allowed.
 *
 * A locked app keeps its existing lock-rule outcome even when it also has a
 * restriction configured: lock rules take precedence (step 4 before step 5).
 *
 * AI may EXPLAIN a decision (see [RestrictionDecision.explain]); it must
 * never MAKE the decision. Every branch above is a pure function of the
 * input — no model, no heuristics.
 */
data class RestrictionCheckInput(
    val protectionEnabled: Boolean,
    val isPaused: Boolean,
    val bankingExempt: Boolean,
    val strictModeEnabled: Boolean,
    val restriction: AppRestriction?,
    val dailyUsageMinutes: Int,
    val walletBalanceMinutes: Int,
    val existingLockTier: LockTier?,
)

enum class AllowReason {
    PROTECTION_OFF,
    PAUSED,
    BANKING_EXEMPT,
    LOCKED_WALLET_OK,
    UNDER_LIMIT,
    WALLET_EXTENDED,
    NO_RESTRICTION,
}

enum class BlockReason {
    LOCKED_NO_WALLET,
    OVER_DAILY_LIMIT,
    OVER_DAILY_LIMIT_STRICT,
}

sealed interface RestrictionDecision {
    data class Allowed(val reason: AllowReason) : RestrictionDecision
    data class Blocked(val reason: BlockReason, val lockTier: LockTier? = null) : RestrictionDecision

    /**
     * Human-readable explanation of the decision, for UI copy or an AI coach
     * message. Explains; never decides.
     */
    fun explain(appLabel: String, input: RestrictionCheckInput): String {
        val label = appLabel.ifBlank { input.restriction?.packageName ?: "This app" }
        return when (this) {
            is Allowed -> when (reason) {
                AllowReason.PROTECTION_OFF ->
                    "$label is allowed: protection is off, so nothing is being enforced."
                AllowReason.PAUSED ->
                    "$label is allowed: protection is paused."
                AllowReason.BANKING_EXEMPT ->
                    "$label is allowed: it is temporarily exempt (banking mode)."
                AllowReason.LOCKED_WALLET_OK ->
                    "$label is allowed: the wallet has ${input.walletBalanceMinutes} min to spend on it."
                AllowReason.UNDER_LIMIT -> {
                    val limit = input.restriction?.dailyLimitMinutes ?: 0
                    "$label is allowed: ${input.dailyUsageMinutes} of $limit min used today."
                }
                AllowReason.WALLET_EXTENDED -> {
                    val limit = input.restriction?.dailyLimitMinutes ?: 0
                    "$label is allowed: its $limit-min daily limit is reached " +
                        "(${input.dailyUsageMinutes} min used), but the wallet has " +
                        "${input.walletBalanceMinutes} min to extend access."
                }
                AllowReason.NO_RESTRICTION ->
                    "$label is allowed: no restriction is configured for it."
            }
            is Blocked -> when (reason) {
                BlockReason.LOCKED_NO_WALLET ->
                    "$label is blocked: it is locked and the wallet is empty."
                BlockReason.OVER_DAILY_LIMIT -> {
                    val limit = input.restriction?.dailyLimitMinutes ?: 0
                    "$label is blocked: its daily limit of $limit min is reached " +
                        "(${input.dailyUsageMinutes} min used today)."
                }
                BlockReason.OVER_DAILY_LIMIT_STRICT -> {
                    val limit = input.restriction?.dailyLimitMinutes ?: 0
                    "$label is blocked: its daily limit of $limit min is reached " +
                        "(${input.dailyUsageMinutes} min used today), and strict mode is on " +
                        "so the wallet can't extend it."
                }
            }
        }
    }
}

object AppRestrictionPolicy {
    /**
     * Pure decision function. No Android dependencies, no I/O, no AI.
     * Safe to unit test without an emulator.
     */
    fun evaluate(input: RestrictionCheckInput): RestrictionDecision {
        if (!input.protectionEnabled) return RestrictionDecision.Allowed(AllowReason.PROTECTION_OFF)
        if (input.isPaused) return RestrictionDecision.Allowed(AllowReason.PAUSED)
        if (input.bankingExempt) return RestrictionDecision.Allowed(AllowReason.BANKING_EXEMPT)

        val tier = input.existingLockTier
        if (tier != null) {
            // Existing lock behavior, preserved: a locked app is usable while
            // the wallet can pay, blocked once the wallet is empty.
            return if (input.walletBalanceMinutes > 0) {
                RestrictionDecision.Allowed(AllowReason.LOCKED_WALLET_OK)
            } else {
                RestrictionDecision.Blocked(BlockReason.LOCKED_NO_WALLET, tier)
            }
        }

        val restriction = input.restriction
        if (restriction == null || !restriction.enabled) {
            return RestrictionDecision.Allowed(AllowReason.NO_RESTRICTION)
        }
        if (input.dailyUsageMinutes < restriction.dailyLimitMinutes) {
            return RestrictionDecision.Allowed(AllowReason.UNDER_LIMIT)
        }
        // At or over the daily limit: the limit is reached.
        if (input.walletBalanceMinutes > 0 && !input.strictModeEnabled) {
            return RestrictionDecision.Allowed(AllowReason.WALLET_EXTENDED)
        }
        return if (input.strictModeEnabled) {
            RestrictionDecision.Blocked(BlockReason.OVER_DAILY_LIMIT_STRICT)
        } else {
            RestrictionDecision.Blocked(BlockReason.OVER_DAILY_LIMIT)
        }
    }
}
