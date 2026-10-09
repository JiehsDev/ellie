package com.example.jikan.screentime

import com.example.jikan.data.EarnRule
import com.example.jikan.data.Wallet
import com.example.jikan.data.WalletTransaction
import com.example.jikan.data.WalletTransactionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ScreenTimeDomainTest {

    @Test
    fun `valid earn rule has no validation errors`() {
        val errors = EarnRuleValidator.validate(
            EarnRule(
                packageName = "com.example.learning",
                requiredMinutes = 2,
                rewardMinutes = 5,
                dailyLimitMinutes = 20,
            )
        )

        assertTrue(errors.isEmpty())
    }

    @Test
    fun `invalid earn rule reports configuration errors`() {
        val errors = EarnRuleValidator.validate(
            EarnRule(
                packageName = "",
                requiredMinutes = 0,
                rewardMinutes = 5,
                dailyLimitMinutes = 3,
            )
        )

        assertEquals(
            listOf(
                "packageName is required",
                "requiredMinutes must be positive",
                "dailyLimitMinutes must be at least rewardMinutes",
            ),
            errors,
        )
    }

    @Test
    fun `earning threshold requires configured active minutes`() {
        val rule = sampleRule(requiredMinutes = 2, rewardMinutes = 5)

        assertEquals(0, EarningCalculator.calculate(rule, foregroundMinutes = 1, alreadyEarnedToday = 0).rewardMinutes)
        assertEquals(5, EarningCalculator.calculate(rule, foregroundMinutes = 2, alreadyEarnedToday = 0).rewardMinutes)
    }

    @Test
    fun `earning calculation grants one reward per completed block`() {
        val rule = sampleRule(requiredMinutes = 2, rewardMinutes = 5)

        val result = EarningCalculator.calculate(rule, foregroundMinutes = 7, alreadyEarnedToday = 0)

        assertEquals(3, result.completedBlocks)
        assertEquals(15, result.rewardMinutes)
    }

    @Test
    fun `daily earning cap limits reward minutes`() {
        val rule = sampleRule(requiredMinutes = 2, rewardMinutes = 5, dailyLimitMinutes = 20)

        val result = EarningCalculator.calculate(rule, foregroundMinutes = 10, alreadyEarnedToday = 15)

        assertEquals(5, result.rewardMinutes)
    }

    @Test
    fun `disabled earn rule grants nothing`() {
        val rule = sampleRule(enabled = false)

        val result = EarningCalculator.calculate(rule, foregroundMinutes = 30, alreadyEarnedToday = 0)

        assertEquals(0, result.completedBlocks)
        assertEquals(0, result.rewardMinutes)
    }

    @Test
    fun `wallet earning increases balance up to max`() {
        val tx = transaction(WalletTransactionType.EARN, minutes = 20, key = "earn-1")

        val result = WalletAccounting.apply(currentBalance = 50, transaction = tx)

        assertEquals(WalletAccountingResult.Applied(Wallet.MAX_BALANCE_MINUTES), result)
    }

    @Test
    fun `wallet spending decreases balance`() {
        val tx = transaction(WalletTransactionType.SPEND, minutes = 7, key = "spend-1")

        val result = WalletAccounting.apply(currentBalance = 12, transaction = tx)

        assertEquals(WalletAccountingResult.Applied(5), result)
    }

    @Test
    fun `wallet spending fails when balance is insufficient`() {
        val tx = transaction(WalletTransactionType.SPEND, minutes = 15, key = "spend-2")

        val result = WalletAccounting.apply(currentBalance = 8, transaction = tx)

        assertEquals(WalletAccountingResult.InsufficientBalance, result)
    }

    @Test
    fun `wallet duplicate idempotency key is rejected`() {
        val tx = transaction(WalletTransactionType.EARN, minutes = 5, key = "earn-duplicate")

        val result = WalletAccounting.apply(
            currentBalance = 0,
            transaction = tx,
            existingIdempotencyKeys = setOf("earn-duplicate"),
        )

        assertEquals(WalletAccountingResult.Duplicate, result)
    }

    @Test
    fun `transaction replay accounts for earn and spend`() {
        val transactions = listOf(
            transaction(WalletTransactionType.EARN, minutes = 10, key = "earn-a"),
            transaction(WalletTransactionType.SPEND, minutes = 4, key = "spend-a"),
            transaction(WalletTransactionType.MANUAL_ADJUSTMENT, minutes = 6, key = "manual-a"),
        )

        val balance = WalletAccounting.balanceFromTransactions(
            startingBalance = 0,
            transactions = transactions,
            maxBalance = Wallet.MAX_BALANCE_MINUTES,
        )

        assertEquals(12, balance)
    }

    private fun sampleRule(
        requiredMinutes: Int = 2,
        rewardMinutes: Int = 5,
        dailyLimitMinutes: Int = 20,
        enabled: Boolean = true,
    ) = EarnRule(
        packageName = "com.example.learning",
        requiredMinutes = requiredMinutes,
        rewardMinutes = rewardMinutes,
        dailyLimitMinutes = dailyLimitMinutes,
        enabled = enabled,
    )

    private fun transaction(
        type: WalletTransactionType,
        minutes: Int,
        key: String,
    ) = WalletTransaction(
        type = type,
        minutes = minutes,
        balanceAfter = 0,
        createdAtMs = 1_000L,
        epochDay = 1L,
        idempotencyKey = key,
    )
}
