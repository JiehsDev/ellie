package com.example.jikan.challenge

import org.junit.Assert.assertEquals
import org.junit.Test

class ProximityRepCounterTest {
    @Test
    fun `a full down-and-up cycle counts one rep`() {
        val counter = ProximityRepCounter()
        counter.onReading(near = true, timestampMillis = 0)
        assertEquals(1, counter.onReading(near = false, timestampMillis = 1_000))
    }

    @Test
    fun `consecutive reps count when spaced far enough apart`() {
        val counter = ProximityRepCounter()
        counter.onReading(near = true, timestampMillis = 0)
        counter.onReading(near = false, timestampMillis = 1_000)
        counter.onReading(near = true, timestampMillis = 1_500)
        assertEquals(2, counter.onReading(near = false, timestampMillis = 2_500))
    }

    @Test
    fun `a flick past the sensor is rejected`() {
        val counter = ProximityRepCounter()
        counter.onReading(near = true, timestampMillis = 0)
        assertEquals(0, counter.onReading(near = false, timestampMillis = 100))
    }

    @Test
    fun `reps closer together than the minimum are rejected`() {
        val counter = ProximityRepCounter()
        counter.onReading(near = true, timestampMillis = 0)
        counter.onReading(near = false, timestampMillis = 1_000)
        counter.onReading(near = true, timestampMillis = 1_100)
        assertEquals(1, counter.onReading(near = false, timestampMillis = 1_500))
    }

    @Test
    fun `a phone set down on the sensor is not a rep`() {
        val counter = ProximityRepCounter()
        counter.onReading(near = true, timestampMillis = 0)
        assertEquals(0, counter.onReading(near = false, timestampMillis = 6_000))
    }

    @Test
    fun `repeated identical readings do not double count`() {
        val counter = ProximityRepCounter()
        counter.onReading(near = true, timestampMillis = 0)
        counter.onReading(near = true, timestampMillis = 100)
        counter.onReading(near = false, timestampMillis = 1_000)
        assertEquals(1, counter.onReading(near = false, timestampMillis = 1_100))
    }

    @Test
    fun `a far reading before any near reading is ignored`() {
        val counter = ProximityRepCounter()
        assertEquals(0, counter.onReading(near = false, timestampMillis = 500))
    }

    @Test
    fun `out of order timestamps do not count a rep`() {
        val counter = ProximityRepCounter()
        counter.onReading(near = true, timestampMillis = 5_000)
        assertEquals(0, counter.onReading(near = false, timestampMillis = 1_000))
    }

    @Test
    fun `reset clears progress`() {
        val counter = ProximityRepCounter()
        counter.onReading(near = true, timestampMillis = 0)
        counter.onReading(near = false, timestampMillis = 1_000)
        counter.reset()
        assertEquals(0, counter.reps)
        counter.onReading(near = true, timestampMillis = 2_000)
        assertEquals(1, counter.onReading(near = false, timestampMillis = 3_000))
    }
}
