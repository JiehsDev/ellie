package com.example.jikan.study

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Unit tests for the central credit policy. All assertions use concrete
 * numbers from the spec examples.
 */
class CreditPolicyTest {

    private val balanced = CreditPolicy.BALANCED
    private val gentle = CreditPolicy.GENTLE
    private val focused = CreditPolicy.FOCUSED

    // --- Balanced tiered earning ---

    @Test
    fun `zero study time earns zero`() {
        assertEquals(0, CreditPolicy.totalCreditsForMinutes(balanced, 0))
        assertEquals(0, CreditPolicy.creditsForNewMinutes(balanced, 0, 0))
    }

    @Test
    fun `40 minutes earns 20 credits at 2 to 1`() {
        assertEquals(20, CreditPolicy.totalCreditsForMinutes(balanced, 40))
    }

    @Test
    fun `60 minutes earns 30 credits`() {
        assertEquals(30, CreditPolicy.totalCreditsForMinutes(balanced, 60))
    }

    @Test
    fun `90 minutes earns 40 credits - 30 from first tier plus 10 from second`() {
        // First tier: 60/2 = 30. Second tier: 30/3 = 10. Total 40.
        assertEquals(40, CreditPolicy.totalCreditsForMinutes(balanced, 90))
    }

    @Test
    fun `120 minutes earns 50 credits`() {
        // 60/2 + 60/3 = 30 + 20 = 50.
        assertEquals(50, CreditPolicy.totalCreditsForMinutes(balanced, 120))
    }

    @Test
    fun `beyond 120 minutes earns nothing more`() {
        assertEquals(50, CreditPolicy.totalCreditsForMinutes(balanced, 150))
        assertEquals(50, CreditPolicy.totalCreditsForMinutes(balanced, 300))
    }

    @Test
    fun `incremental earning across tier boundary`() {
        // 50 prior + 20 new = 70 total. Before: 25. After: 30 + 10/3 = 33. New: 8.
        assertEquals(8, CreditPolicy.creditsForNewMinutes(balanced, 50, 20))
    }

    @Test
    fun `short sessions accumulate without rounding abuse`() {
        // Ten 3-minute sessions = 30 minutes total should earn 15, same as one 30-min session.
        var prior = 0
        var total = 0
        repeat(10) {
            total += CreditPolicy.creditsForNewMinutes(balanced, prior, 3)
            prior += 3
        }
        assertEquals(15, total)
        assertEquals(15, CreditPolicy.totalCreditsForMinutes(balanced, 30))
    }

    @Test
    fun `single minute sessions do not generate extra credits`() {
        // Sixty 1-minute sessions = 60 minutes → 30 credits, not 60.
        var prior = 0
        var total = 0
        repeat(60) {
            total += CreditPolicy.creditsForNewMinutes(balanced, prior, 1)
            prior += 1
        }
        assertEquals(30, total)
    }

    @Test
    fun `fractional progress preserved across sessions`() {
        // 3 minutes → 1 credit (3/2=1), 1 minute banked as fractional progress.
        // Next 1 minute → 0 new (4/2=2 total, 1 already → 1 new). Wait: 3+1=4 → 2 total - 1 = 1.
        val first = CreditPolicy.creditsForNewMinutes(balanced, 0, 3)
        assertEquals(1, first)
        val second = CreditPolicy.creditsForNewMinutes(balanced, 3, 1)
        assertEquals(1, second)
    }

    @Test
    fun `minutes to next credit`() {
        assertEquals(2, CreditPolicy.minutesToNextCredit(balanced, 0))
        assertEquals(1, CreditPolicy.minutesToNextCredit(balanced, 1))
        assertEquals(2, CreditPolicy.minutesToNextCredit(balanced, 40))
        // At tier boundary 60: next tier needs 3.
        assertEquals(3, CreditPolicy.minutesToNextCredit(balanced, 60))
        assertEquals(2, CreditPolicy.minutesToNextCredit(balanced, 61))
        // Capped at 120.
        assertEquals(0, CreditPolicy.minutesToNextCredit(balanced, 120))
        assertEquals(0, CreditPolicy.minutesToNextCredit(balanced, 200))
    }

    // --- Gentle profile ---

    @Test
    fun `gentle earns 1 to 1 with no cap`() {
        assertEquals(45, CreditPolicy.totalCreditsForMinutes(gentle, 45))
        assertEquals(200, CreditPolicy.totalCreditsForMinutes(gentle, 200))
    }

    @Test
    fun `gentle allowance and max unlock`() {
        assertEquals(60, gentle.dailyAllowanceMinutes)
        assertEquals(20, gentle.maxUnlockMinutes)
    }

    // --- Focused profile ---

    @Test
    fun `focused earns 3 to 1`() {
        assertEquals(20, CreditPolicy.totalCreditsForMinutes(focused, 60))
        assertEquals(40, CreditPolicy.totalCreditsForMinutes(focused, 120))
    }

    @Test
    fun `focused allowance and max unlock`() {
        assertEquals(20, focused.dailyAllowanceMinutes)
        assertEquals(10, focused.maxUnlockMinutes)
    }

    // --- Spending ---

    @Test
    fun `max unlock is limited by credits`() {
        // 5 credits, 30 allowance, 15 max → 5.
        assertEquals(5, CreditPolicy.maxUnlockDuration(balanced, 5, 30))
    }

    @Test
    fun `max unlock is limited by allowance`() {
        // 20 credits, 8 allowance, 15 max → 8.
        assertEquals(8, CreditPolicy.maxUnlockDuration(balanced, 20, 8))
    }

    @Test
    fun `max unlock is limited by profile cap`() {
        // 50 credits, 30 allowance, 15 max → 15.
        assertEquals(15, CreditPolicy.maxUnlockDuration(balanced, 50, 30))
    }

    @Test
    fun `max unlock is zero when nothing available`() {
        assertEquals(0, CreditPolicy.maxUnlockDuration(balanced, 0, 30))
        assertEquals(0, CreditPolicy.maxUnlockDuration(balanced, 20, 0))
    }

    @Test
    fun `unlock limit reason explains the constraint`() {
        val noCredits = CreditPolicy.unlockLimitReason(balanced, 0, 30)
        assert(noCredits.contains("No credits earned yet"))

        val noAllowance = CreditPolicy.unlockLimitReason(balanced, 20, 0)
        assert(noAllowance.contains("allowance"))

        val limitedByAllowance = CreditPolicy.unlockLimitReason(balanced, 20, 8)
        assert(limitedByAllowance.contains("8 minutes"))
        assert(limitedByAllowance.contains("allowance"))
    }

    // --- Profile config ---

    @Test
    fun `balanced is the default profile config`() {
        val config = CreditPolicy.configFor(CreditPolicy.CreditProfile.BALANCED)
        assertEquals(30, config.dailyAllowanceMinutes)
        assertEquals(15, config.maxUnlockMinutes)
        assertEquals(2, config.earningTiers.size)
    }
}
