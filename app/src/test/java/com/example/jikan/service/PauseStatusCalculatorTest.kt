package com.example.jikan.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PauseStatusCalculatorTest {
    @Test
    fun statusIsPausedBeforeExpiry() {
        val status = PauseStatusCalculator.status(expiryTimestampMs = 160_000L, nowMs = 100_000L)

        assertTrue(status.isPaused)
        assertEquals(1L, status.remainingMinutes)
    }

    @Test
    fun statusIsNotPausedAtOrAfterExpiry() {
        assertFalse(PauseStatusCalculator.status(expiryTimestampMs = 100_000L, nowMs = 100_000L).isPaused)
        assertFalse(PauseStatusCalculator.status(expiryTimestampMs = 99_999L, nowMs = 100_000L).isPaused)
    }

    @Test
    fun nextExpiryStartsFromNowWhenCurrentlyActive() {
        val expiry = PauseStatusCalculator.nextExpiry(
            nowMs = 100_000L,
            currentExpiryMs = 0L,
            durationMinutes = 10,
        )

        assertEquals(700_000L, expiry)
    }

    @Test
    fun nextExpiryExtendsExistingPause() {
        val expiry = PauseStatusCalculator.nextExpiry(
            nowMs = 100_000L,
            currentExpiryMs = 400_000L,
            durationMinutes = 10,
        )

        assertEquals(1_000_000L, expiry)
    }
}
