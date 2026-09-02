package com.example.jikan.challenge

import com.example.jikan.data.ChallengeType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CreditRulesTest {
    @Test
    fun `ten push-ups earn three minutes`() {
        assertEquals(3, CreditRules.earnedFor(ChallengeType.PUSHUPS, metric = 10))
    }

    @Test
    fun `extra push-ups do not earn extra minutes`() {
        assertEquals(3, CreditRules.earnedFor(ChallengeType.PUSHUPS, metric = 25))
    }

    @Test
    fun `an unfinished challenge earns nothing`() {
        assertEquals(0, CreditRules.earnedFor(ChallengeType.PUSHUPS, metric = 9))
        assertEquals(0, CreditRules.earnedFor(ChallengeType.STEPS, metric = 299))
        assertEquals(0, CreditRules.earnedFor(ChallengeType.PAUSE, metric = 59))
    }

    @Test
    fun `three hundred steps earn six minutes`() {
        assertEquals(6, CreditRules.earnedFor(ChallengeType.STEPS, metric = 300))
    }

    @Test
    fun `a sixty second pause earns two minutes`() {
        assertEquals(2, CreditRules.earnedFor(ChallengeType.PAUSE, metric = 60))
    }

    @Test
    fun `physical challenges are capped per day`() {
        assertEquals(20, CreditRules.dailyCapMinutes(ChallengeType.PUSHUPS))
        assertEquals(20, CreditRules.dailyCapMinutes(ChallengeType.STEPS))
        assertEquals(10, CreditRules.dailyCapMinutes(ChallengeType.PAUSE))
    }

    @Test
    fun `study is not capped in the funnel`() {
        assertNull(CreditRules.dailyCapMinutes(ChallengeType.STUDY))
    }
}
