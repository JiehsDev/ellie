package com.example.jikan.lock

/**
 * Decides whether a detected foreground blocked-app event should actually fire a
 * redirect, given noisy/duplicate accessibility events and the reopen-race case
 * where a user keeps bouncing back into the same blocked app.
 *
 * Pure Kotlin, no Android deps, so the reopen-race behavior can be unit tested
 * without an emulator.
 */
class RedirectGate {
    sealed interface Decision {
        data object Suppressed : Decision
        data class Redirect(val takeABreak: Boolean) : Decision
    }

    private val redirectTimestamps = mutableMapOf<String, MutableList<Long>>()

    fun evaluate(packageName: String, now: Long = System.currentTimeMillis()): Decision {
        val history = redirectTimestamps.getOrPut(packageName) { mutableListOf() }
        history.removeAll { now - it > RAPID_REOPEN_WINDOW_MS }

        val last = history.lastOrNull()
        if (last != null && now - last < REFIRE_SUPPRESS_MS) {
            return Decision.Suppressed
        }

        history += now
        val takeABreak = history.size >= RAPID_REOPEN_THRESHOLD
        return Decision.Redirect(takeABreak)
    }

    companion object {
        // "if the same blocked package reappears within 1-2 sec of last redirect,
        // skip re-check and go straight to lock screen" — also doubles as a
        // duplicate-event suppressor for bursty TYPE_WINDOW_STATE_CHANGED events.
        private const val REFIRE_SUPPRESS_MS = 1500L

        // "user rapid-reopens 5+ times in 10 seconds" -> frustration signal
        private const val RAPID_REOPEN_WINDOW_MS = 10_000L
        private const val RAPID_REOPEN_THRESHOLD = 5
    }
}
