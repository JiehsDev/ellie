package com.example.jikan.productivity

import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** One day of evidence. [observed] is false when the lock service was not running. */
data class DaySignal(
    val lockedAppMinutes: Int,
    val creditsEarned: Int,
    val hadCompletion: Boolean,
    val observed: Boolean,
)

enum class ProductivityBand { DRIFTING, SETTLING, STEADY, FOCUSED, LOCKED_IN }

data class ProductivityResult(
    val score: Int,
    val band: ProductivityBand,
    val observedDays: Int,
    val unobservedDays: Int,
)

/**
 * A rolling seven-day productivity level, replacing the old all-or-nothing streak: one
 * missed day costs a seventh rather than everything.
 *
 * The restraint scale is 90 minutes rather than the 60-minute wallet ceiling on
 * purpose. Anchored at 60, a user who earned 60 minutes legitimately and spent them
 * would score zero on the heaviest-weighted term — perfect compliance producing the
 * worst possible restraint, with the score contradicting the economy underneath it.
 */
object ProductivityScore {
    const val WINDOW_DAYS = 7
    const val RESTRAINT_SCALE_MINUTES = 90
    const val EFFORT_TARGET_MINUTES = 15

    /** A day counts as observed once the service has been seen alive this long. */
    const val OBSERVED_THRESHOLD_MINUTES = 15

    private const val W_RESTRAINT = 0.45
    private const val W_EFFORT = 0.35
    private const val W_CONSISTENCY = 0.20

    /** Null when there is no history to judge — a fresh install is not a failure. */
    fun compute(days: List<DaySignal>): ProductivityResult? {
        if (days.isEmpty()) return null

        val observed = days.filter { it.observed }
        val activeDays = days.count { it.hadCompletion }
        if (observed.isEmpty() && activeDays == 0) return null

        val effort = days.sumOf {
            min(1.0, it.creditsEarned / EFFORT_TARGET_MINUTES.toDouble())
        } / days.size
        val consistency = activeDays.toDouble() / days.size

        val weighted = if (observed.isEmpty()) {
            // Nothing was watched, so restraint is unknowable. Renormalise rather than
            // inventing a value for it.
            val remaining = W_EFFORT + W_CONSISTENCY
            (W_EFFORT / remaining) * effort + (W_CONSISTENCY / remaining) * consistency
        } else {
            val restraint = observed.sumOf {
                max(0.0, 1.0 - it.lockedAppMinutes / RESTRAINT_SCALE_MINUTES.toDouble())
            } / observed.size
            W_RESTRAINT * restraint + W_EFFORT * effort + W_CONSISTENCY * consistency
        }

        val score = (weighted * 100).roundToInt().coerceIn(0, 100)
        return ProductivityResult(
            score = score,
            band = bandFor(score),
            observedDays = observed.size,
            unobservedDays = days.size - observed.size,
        )
    }

    fun bandFor(score: Int): ProductivityBand = when {
        score < 20 -> ProductivityBand.DRIFTING
        score < 45 -> ProductivityBand.SETTLING
        score < 65 -> ProductivityBand.STEADY
        score < 85 -> ProductivityBand.FOCUSED
        else -> ProductivityBand.LOCKED_IN
    }

    fun label(band: ProductivityBand): String = when (band) {
        ProductivityBand.DRIFTING -> "Drifting"
        ProductivityBand.SETTLING -> "Settling"
        ProductivityBand.STEADY -> "Steady"
        ProductivityBand.FOCUSED -> "Focused"
        ProductivityBand.LOCKED_IN -> "Locked in"
    }
}
