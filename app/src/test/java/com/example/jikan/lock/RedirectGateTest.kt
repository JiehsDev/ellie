package com.example.jikan.lock

import org.junit.Assert.assertEquals
import org.junit.Test

class RedirectGateTest {
    @Test
    fun `first sighting of a blocked package redirects normally`() {
        val gate = RedirectGate()
        val decision = gate.evaluate("com.tiktok", now = 0L)
        assertEquals(RedirectGate.Decision.Redirect(takeABreak = false), decision)
    }

    @Test
    fun `reappearing within the debounce window is suppressed`() {
        val gate = RedirectGate()
        gate.evaluate("com.tiktok", now = 0L)
        val decision = gate.evaluate("com.tiktok", now = 900L)
        assertEquals(RedirectGate.Decision.Suppressed, decision)
    }

    @Test
    fun `reappearing after the debounce window redirects again`() {
        val gate = RedirectGate()
        gate.evaluate("com.tiktok", now = 0L)
        val decision = gate.evaluate("com.tiktok", now = 1600L)
        assertEquals(RedirectGate.Decision.Redirect(takeABreak = false), decision)
    }

    @Test
    fun `five reopens inside ten seconds trips the take-a-break signal`() {
        val gate = RedirectGate()
        val timestamps = listOf(0L, 1600L, 3200L, 4800L, 6400L)
        val decisions = timestamps.map { gate.evaluate("com.tiktok", now = it) }

        decisions.dropLast(1).forEach {
            assertEquals(RedirectGate.Decision.Redirect(takeABreak = false), it)
        }
        assertEquals(RedirectGate.Decision.Redirect(takeABreak = true), decisions.last())
    }

    @Test
    fun `take-a-break resets once reopens age out of the rolling window`() {
        val gate = RedirectGate()
        listOf(0L, 1600L, 3200L, 4800L, 6400L).forEach { gate.evaluate("com.tiktok", now = it) }
        val decision = gate.evaluate("com.tiktok", now = 20_000L)
        assertEquals(RedirectGate.Decision.Redirect(takeABreak = false), decision)
    }

    @Test
    fun `different packages are tracked independently`() {
        val gate = RedirectGate()
        gate.evaluate("com.tiktok", now = 0L)
        val decision = gate.evaluate("com.mobile.legends", now = 100L)
        assertEquals(RedirectGate.Decision.Redirect(takeABreak = false), decision)
    }
}
