package com.example.jikan.challenge

/**
 * Counts push-ups from proximity readings.
 *
 * Deliberately free of Android types: timestamped booleans in, rep count out, so the
 * plausibility rules are tested on the JVM rather than by doing push-ups on the floor.
 *
 * A rep is FAR -> NEAR -> FAR, counted on the rising edge, and discarded when the near
 * phase is implausibly short (a hand flicked past the sensor), implausibly long (the
 * phone set face-down), or too soon after the previous rep.
 */
class ProximityRepCounter(
    private val minRepMillis: Long = 800L,
    private val minNearMillis: Long = 250L,
    private val maxNearMillis: Long = 5_000L,
) {
    var reps: Int = 0
        private set

    private var isNear = false
    private var nearSinceMillis: Long? = null
    private var lastRepAtMillis: Long? = null

    /** Feeds one reading in and returns the rep count after it. */
    fun onReading(near: Boolean, timestampMillis: Long): Int {
        if (near == isNear) return reps
        isNear = near

        if (near) {
            nearSinceMillis = timestampMillis
            return reps
        }

        val nearSince = nearSinceMillis ?: return reps
        nearSinceMillis = null

        val nearDuration = timestampMillis - nearSince
        if (nearDuration < minNearMillis || nearDuration > maxNearMillis) return reps

        val lastRep = lastRepAtMillis
        if (lastRep != null && timestampMillis - lastRep < minRepMillis) return reps

        reps++
        lastRepAtMillis = timestampMillis
        return reps
    }

    fun reset() {
        reps = 0
        isNear = false
        nearSinceMillis = null
        lastRepAtMillis = null
    }
}
