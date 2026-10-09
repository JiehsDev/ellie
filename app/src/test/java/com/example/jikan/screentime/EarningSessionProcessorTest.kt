package com.example.jikan.screentime

import com.example.jikan.data.EarnRule
import com.example.jikan.data.WalletTransaction
import com.example.jikan.data.WalletTransactionSource
import com.example.jikan.data.WalletTransactionType
import org.junit.Assert.assertEquals
import org.junit.Test

class EarningSessionProcessorTest {

    @Test
    fun `threshold reached grants one reward`() {
        val plan = EarningSessionProcessor.plan(
            rule = rule(requiredMinutes = 2, rewardMinutes = 5),
            previous = EarningProgressState(),
            intervalMs = minutes(2),
            alreadyEarnedToday = 0,
        )

        assertEquals(1, plan.rewardableBlocks)
        assertEquals(5, plan.rewardMinutes)
        assertEquals(1, plan.state.rewardedBlocks)
    }

    @Test
    fun `threshold not reached accumulates without reward`() {
        val plan = EarningSessionProcessor.plan(
            rule = rule(requiredMinutes = 2, rewardMinutes = 5),
            previous = EarningProgressState(),
            intervalMs = minutes(1),
            alreadyEarnedToday = 0,
        )

        assertEquals(minutes(1), plan.state.accumulatedMs)
        assertEquals(0, plan.rewardableBlocks)
        assertEquals(0, plan.rewardMinutes)
    }

    @Test
    fun `accumulated partial sessions can complete threshold`() {
        val first = EarningSessionProcessor.plan(
            rule = rule(),
            previous = EarningProgressState(),
            intervalMs = minutes(1) + seconds(10),
            alreadyEarnedToday = 0,
        )

        val second = EarningSessionProcessor.plan(
            rule = rule(),
            previous = first.state,
            intervalMs = seconds(50),
            alreadyEarnedToday = 0,
        )

        assertEquals(0, first.rewardableBlocks)
        assertEquals(1, second.rewardableBlocks)
        assertEquals(5, second.rewardMinutes)
    }

    @Test
    fun `daily cap limits rewards`() {
        val plan = EarningSessionProcessor.plan(
            rule = rule(requiredMinutes = 2, rewardMinutes = 5, dailyLimitMinutes = 20),
            previous = EarningProgressState(),
            intervalMs = minutes(20),
            alreadyEarnedToday = 15,
        )

        assertEquals(1, plan.rewardableBlocks)
        assertEquals(5, plan.rewardMinutes)
    }

    @Test
    fun `extra usage after cap still accumulates but does not reward`() {
        val plan = EarningSessionProcessor.plan(
            rule = rule(requiredMinutes = 2, rewardMinutes = 5, dailyLimitMinutes = 20),
            previous = EarningProgressState(accumulatedMs = minutes(8), rewardedBlocks = 4),
            intervalMs = minutes(4),
            alreadyEarnedToday = 20,
        )

        assertEquals(minutes(12), plan.state.accumulatedMs)
        assertEquals(4, plan.state.rewardedBlocks)
        assertEquals(0, plan.rewardMinutes)
    }

    @Test
    fun `already rewarded progress prevents duplicate processing`() {
        val plan = EarningSessionProcessor.plan(
            rule = rule(requiredMinutes = 2, rewardMinutes = 5),
            previous = EarningProgressState(accumulatedMs = minutes(2), rewardedBlocks = 1),
            intervalMs = 0,
            alreadyEarnedToday = 5,
        )

        assertEquals(1, plan.state.rewardedBlocks)
        assertEquals(0, plan.rewardableBlocks)
        assertEquals(0, plan.rewardMinutes)
    }

    @Test
    fun `disabled rules do not reward`() {
        val plan = EarningSessionProcessor.plan(
            rule = rule(enabled = false),
            previous = EarningProgressState(),
            intervalMs = minutes(10),
            alreadyEarnedToday = 0,
        )

        assertEquals(0, plan.rewardableBlocks)
        assertEquals(0, plan.rewardMinutes)
    }

    @Test
    fun `multiple earning apps keep independent progress`() {
        val readingRule = rule(id = 1, packageName = "com.example.reader")
        val exerciseRule = rule(id = 2, packageName = "com.example.exercise", requiredMinutes = 3, rewardMinutes = 4)

        val reading = EarningSessionProcessor.plan(
            rule = readingRule,
            previous = EarningProgressState(),
            intervalMs = minutes(2),
            alreadyEarnedToday = 0,
        )
        val exercise = EarningSessionProcessor.plan(
            rule = exerciseRule,
            previous = EarningProgressState(),
            intervalMs = minutes(2),
            alreadyEarnedToday = 0,
        )

        assertEquals(5, reading.rewardMinutes)
        assertEquals(0, exercise.rewardMinutes)
    }

    @Test
    fun `wallet metadata records earning app source`() {
        val transaction = WalletTransaction(
            type = WalletTransactionType.EARN,
            minutes = 5,
            balanceAfter = 5,
            createdAtMs = 1_000L,
            epochDay = 1L,
            packageName = "com.example.reader",
            idempotencyKey = "earning-app:1:1:1",
            source = WalletTransactionSource.EARNING_APP,
            ruleId = 1L,
            qualifyingMinutes = 2,
        )

        assertEquals(WalletTransactionSource.EARNING_APP, transaction.source)
        assertEquals(1L, transaction.ruleId)
        assertEquals(2, transaction.qualifyingMinutes)
    }

    private fun rule(
        id: Long = 1,
        packageName: String = "com.example.learning",
        requiredMinutes: Int = 2,
        rewardMinutes: Int = 5,
        dailyLimitMinutes: Int = 20,
        enabled: Boolean = true,
    ) = EarnRule(
        id = id,
        packageName = packageName,
        requiredMinutes = requiredMinutes,
        rewardMinutes = rewardMinutes,
        dailyLimitMinutes = dailyLimitMinutes,
        enabled = enabled,
    )

    private fun minutes(value: Int): Long = value * 60_000L

    private fun seconds(value: Int): Long = value * 1_000L
}
