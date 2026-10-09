package com.example.jikan.screentime

import com.example.jikan.data.AppRestriction
import com.example.jikan.data.LockTier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 4: the restriction policy is pure Kotlin with no Android
 * dependencies, so every branch is unit-testable without an emulator.
 * The policy answers "Is this package currently allowed to be opened?"
 * deterministically — AI may explain the decision, never make it.
 */
class AppRestrictionPolicyTest {

    private val tiktok = AppRestriction(
        packageName = "com.tiktok",
        appLabel = "TikTok",
        dailyLimitMinutes = 30,
    )
    private val instagram = AppRestriction(
        packageName = "com.instagram",
        appLabel = "Instagram",
        dailyLimitMinutes = 30,
    )

    private fun input(
        protectionEnabled: Boolean = true,
        isPaused: Boolean = false,
        bankingExempt: Boolean = false,
        strictModeEnabled: Boolean = false,
        restriction: AppRestriction? = tiktok,
        dailyUsageMinutes: Int = 0,
        walletBalanceMinutes: Int = 0,
        existingLockTier: LockTier? = null,
    ) = RestrictionCheckInput(
        protectionEnabled = protectionEnabled,
        isPaused = isPaused,
        bankingExempt = bankingExempt,
        strictModeEnabled = strictModeEnabled,
        restriction = restriction,
        dailyUsageMinutes = dailyUsageMinutes,
        walletBalanceMinutes = walletBalanceMinutes,
        existingLockTier = existingLockTier,
    )

    // --- Limit boundaries ---

    @Test
    fun `below limit is allowed`() {
        val decision = AppRestrictionPolicy.evaluate(input(dailyUsageMinutes = 20))

        assertEquals(RestrictionDecision.Allowed(AllowReason.UNDER_LIMIT), decision)
    }

    @Test
    fun `exactly at limit with empty wallet is blocked`() {
        val decision = AppRestrictionPolicy.evaluate(input(dailyUsageMinutes = 30))

        assertEquals(RestrictionDecision.Blocked(BlockReason.OVER_DAILY_LIMIT), decision)
    }

    @Test
    fun `exactly at limit with wallet extends access`() {
        val decision = AppRestrictionPolicy.evaluate(
            input(dailyUsageMinutes = 30, walletBalanceMinutes = 10)
        )

        assertEquals(RestrictionDecision.Allowed(AllowReason.WALLET_EXTENDED), decision)
    }

    @Test
    fun `over limit with empty wallet is blocked`() {
        val decision = AppRestrictionPolicy.evaluate(input(dailyUsageMinutes = 31))

        assertEquals(RestrictionDecision.Blocked(BlockReason.OVER_DAILY_LIMIT), decision)
    }

    @Test
    fun `over limit with wallet but strict mode is a hard block`() {
        val decision = AppRestrictionPolicy.evaluate(
            input(dailyUsageMinutes = 45, walletBalanceMinutes = 10, strictModeEnabled = true)
        )

        assertEquals(RestrictionDecision.Blocked(BlockReason.OVER_DAILY_LIMIT_STRICT), decision)
    }

    @Test
    fun `over limit with wallet and no strict mode extends access`() {
        val decision = AppRestrictionPolicy.evaluate(
            input(dailyUsageMinutes = 45, walletBalanceMinutes = 10, strictModeEnabled = false)
        )

        assertEquals(RestrictionDecision.Allowed(AllowReason.WALLET_EXTENDED), decision)
    }

    // --- Protection state ---

    @Test
    fun `protection disabled allows everything`() {
        val decision = AppRestrictionPolicy.evaluate(
            input(
                protectionEnabled = false,
                dailyUsageMinutes = 90,
                existingLockTier = LockTier.EXTREME,
            )
        )

        assertEquals(RestrictionDecision.Allowed(AllowReason.PROTECTION_OFF), decision)
    }

    @Test
    fun `paused protection allows over-limit app`() {
        val decision = AppRestrictionPolicy.evaluate(
            input(isPaused = true, dailyUsageMinutes = 90)
        )

        assertEquals(RestrictionDecision.Allowed(AllowReason.PAUSED), decision)
    }

    // --- Banking mode ---

    @Test
    fun `banking exemption allows over-limit app`() {
        val decision = AppRestrictionPolicy.evaluate(
            input(bankingExempt = true, dailyUsageMinutes = 90)
        )

        assertEquals(RestrictionDecision.Allowed(AllowReason.BANKING_EXEMPT), decision)
    }

    // --- Existing lock rules keep their behavior ---

    @Test
    fun `locked app with wallet is allowed and lock rules win over restrictions`() {
        val decision = AppRestrictionPolicy.evaluate(
            input(
                existingLockTier = LockTier.EXTREME,
                dailyUsageMinutes = 90, // also over its restriction limit
                walletBalanceMinutes = 5,
            )
        )

        assertEquals(RestrictionDecision.Allowed(AllowReason.LOCKED_WALLET_OK), decision)
    }

    @Test
    fun `locked app with empty wallet is blocked with its tier`() {
        val decision = AppRestrictionPolicy.evaluate(
            input(existingLockTier = LockTier.AVERAGE, dailyUsageMinutes = 10)
        )

        assertEquals(
            RestrictionDecision.Blocked(BlockReason.LOCKED_NO_WALLET, LockTier.AVERAGE),
            decision,
        )
    }

    // --- No restriction configured ---

    @Test
    fun `no restriction and not locked is allowed`() {
        val decision = AppRestrictionPolicy.evaluate(
            input(restriction = null, dailyUsageMinutes = 500)
        )

        assertEquals(RestrictionDecision.Allowed(AllowReason.NO_RESTRICTION), decision)
    }

    @Test
    fun `disabled restriction is ignored`() {
        val decision = AppRestrictionPolicy.evaluate(
            input(restriction = tiktok.copy(enabled = false), dailyUsageMinutes = 90)
        )

        assertEquals(RestrictionDecision.Allowed(AllowReason.NO_RESTRICTION), decision)
    }

    // --- Multiple restricted apps are independent ---

    @Test
    fun `multiple restricted apps are evaluated independently`() {
        val blocked = AppRestrictionPolicy.evaluate(
            input(restriction = tiktok, dailyUsageMinutes = 31)
        )
        val allowed = AppRestrictionPolicy.evaluate(
            input(restriction = instagram, dailyUsageMinutes = 10)
        )

        assertEquals(RestrictionDecision.Blocked(BlockReason.OVER_DAILY_LIMIT), blocked)
        assertEquals(RestrictionDecision.Allowed(AllowReason.UNDER_LIMIT), allowed)
    }

    // --- Precedence is deterministic ---

    @Test
    fun `safety precedence is fixed regardless of other inputs`() {
        // Protection off beats lock rules, limits, strict mode, everything.
        val decision = AppRestrictionPolicy.evaluate(
            input(
                protectionEnabled = false,
                strictModeEnabled = true,
                dailyUsageMinutes = 999,
                existingLockTier = LockTier.EXTREME,
            )
        )

        assertEquals(RestrictionDecision.Allowed(AllowReason.PROTECTION_OFF), decision)
    }

    // --- AI may explain, never decide ---

    @Test
    fun `blocked decision explains itself in human words`() {
        val in2 = input(dailyUsageMinutes = 31)
        val decision = AppRestrictionPolicy.evaluate(in2)

        val explanation = decision.explain("TikTok", in2)

        assertTrue(explanation.contains("TikTok"))
        assertTrue(explanation.contains("30"))
        assertTrue(explanation.contains("31"))
    }

    @Test
    fun `strict block explanation mentions strict mode`() {
        val in2 = input(
            dailyUsageMinutes = 45,
            walletBalanceMinutes = 10,
            strictModeEnabled = true,
        )
        val decision = AppRestrictionPolicy.evaluate(in2)

        val explanation = decision.explain("TikTok", in2)

        assertTrue(explanation.contains("strict mode"))
    }

    @Test
    fun `allowed decision explanation is never blank`() {
        val in2 = input(dailyUsageMinutes = 5)
        val decision = AppRestrictionPolicy.evaluate(in2)

        assertTrue(decision.explain("TikTok", in2).isNotBlank())
    }
}
