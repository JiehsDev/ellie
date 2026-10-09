package com.example.jikan.ui.home

import com.example.jikan.coach.ScreenTimeContext
import com.example.jikan.data.Wallet
import com.example.jikan.screentime.ScreenTimeSummary

/**
 * Phase 6: Jikan Coach is a screen-time companion.
 *
 * The provider stays pure Kotlin and deterministic: every message is a pure
 * function of [CoachInput]. AI may interpret the structured facts, but it
 * never decides blocks, protection state, wallet accounting, or usage math —
 * those are computed elsewhere (see the screentime package) and arrive here
 * as facts.
 *
 * Safety priority: protection/security messages always outrank fun
 * observations, streaks, wallet notes, and generic insights.
 *
 * Tone: calm, observant, supportive, slightly playful — never judgmental.
 * Normal messages are at most two sentences and never invent statistics.
 */
data class CoachInput(
    val userName: String,
    val walletBalanceMinutes: Int,
    val streakDays: Int,
    val dueCount: Int,
    val studiedTodayMinutes: Int,
    val studiedYesterdayMinutes: Int,
    val protectionOn: Boolean,
    val bankingModeActive: Boolean,
    val hourOfDay: Int,
    val recentEvent: CoachEvent = CoachEvent.HomeOpened,
    /** Phase 6: structured screen-time facts (Phase 5 summary). Null when unavailable. */
    val screenTime: ScreenTimeSummary? = null,
)

enum class CoachMood {
    Calm,
    Proud,
    Concerned,
    Playful,
    Focused,
    WelcomeBack,
}

sealed interface CoachEvent {
    data object HomeOpened : CoachEvent
    data object StudyStarted : CoachEvent
    data class LockedAppBlocked(val appLabel: String) : CoachEvent
    data class SessionCompleted(
        val correctCount: Int,
        val totalCount: Int,
        val creditsEarned: Int,
        val isPerfect: Boolean,
    ) : CoachEvent

    // Phase 6: screen-time companion events. Each carries its own facts so
    // messages never invent statistics.
    data class AppLimitApproaching(
        val appLabel: String,
        val limitMinutes: Int,
        val remainingMinutes: Int,
    ) : CoachEvent

    data class AppLimitReached(val appLabel: String, val limitMinutes: Int) : CoachEvent
    data class AppLimitExceeded(
        val appLabel: String,
        val limitMinutes: Int,
        val overLimitMinutes: Int,
    ) : CoachEvent

    data object ProtectionDisabled : CoachEvent
    data object ProtectionRestored : CoachEvent
    data class HighScreenTime(val appLabel: String, val minutes: Int) : CoachEvent
    data class UsageImproved(val fewerMinutes: Int) : CoachEvent
    data class UsageIncreased(val extraMinutes: Int) : CoachEvent
    data class EarningCreditGranted(val appLabel: String, val creditsEarned: Int) : CoachEvent
    data object DailySummary : CoachEvent
}

/**
 * Phase 7: builds the deterministic provider input for a screen-time
 * context. Used by the AI coach implementations as the guaranteed fallback
 * when the local model is unavailable — one mapping, no duplication.
 */
fun ScreenTimeContext.toCoachInput(protectionEnabled: Boolean): CoachInput = CoachInput(
    userName = "",
    walletBalanceMinutes = walletBalanceMinutes,
    streakDays = 0,
    dueCount = 0,
    studiedTodayMinutes = 0,
    studiedYesterdayMinutes = 0,
    protectionOn = protectionEnabled,
    bankingModeActive = false,
    hourOfDay = 12,
    recentEvent = HomeCoachMessageProvider.screenTimeEventFor(this),
    screenTime = this,
)

object HomeCoachMessageProvider {
    // Noise gates for observational messages (minutes).
    private const val IMPROVED_THRESHOLD_MINUTES = 15
    private const val INCREASED_THRESHOLD_MINUTES = 30
    private const val TOP_APP_MINIMUM_MINUTES = 5

    fun messageFor(input: CoachInput): String {
        // SAFETY FIRST: protection/security outranks everything else.
        if (input.bankingModeActive) {
            return "Banking mode is on - protection is off. Tap me when you're ready to put the guard back up."
        }
        if (!input.protectionOn) {
            return "Protection is off right now. Jikan won't be able to enforce your limits until you turn it back on."
        }
        return screenTimeEventMessage(input)
            ?: observationMessage(input)
            ?: legacyMessage(input)
    }

    /**
     * Priority for choosing a screen-time event from a summary. Used by
     * callers that derive [CoachInput.recentEvent] from facts.
     */
    fun screenTimeEventFor(summary: ScreenTimeSummary): CoachEvent {
        val exceeded = summary.exceededApps.firstOrNull()
        if (exceeded != null) {
            return CoachEvent.AppLimitExceeded(
                appLabel = exceeded.appLabel,
                limitMinutes = exceeded.limitMinutes,
                overLimitMinutes = exceeded.overLimitMinutes,
            )
        }
        val atLimit = summary.atLimitApps.firstOrNull()
        if (atLimit != null) {
            return CoachEvent.AppLimitReached(
                appLabel = atLimit.appLabel,
                limitMinutes = atLimit.limitMinutes,
            )
        }
        val approaching = summary.approachingApps.firstOrNull()
        if (approaching != null) {
            return CoachEvent.AppLimitApproaching(
                appLabel = approaching.appLabel,
                limitMinutes = approaching.limitMinutes,
                remainingMinutes = approaching.remainingMinutes,
            )
        }
        val delta = summary.recentUsageChange.deltaMinutes
        if (delta <= -IMPROVED_THRESHOLD_MINUTES) return CoachEvent.UsageImproved(-delta)
        if (delta >= INCREASED_THRESHOLD_MINUTES) return CoachEvent.UsageIncreased(delta)
        return CoachEvent.HomeOpened
    }

    private fun screenTimeEventMessage(input: CoachInput): String? {
        return when (val event = input.recentEvent) {
            is CoachEvent.ProtectionRestored ->
                "Protection is back on. I'll keep watch \u2014 you handle the day."

            is CoachEvent.AppLimitExceeded ->
                "${event.appLabel} passed its ${event.limitMinutes}-minute limit " +
                    "by ${event.overLimitMinutes} minutes. The guard is holding; I'm just keeping score."

            is CoachEvent.AppLimitReached ->
                "${event.appLabel} just hit its ${event.limitMinutes}-minute limit. I'll keep watch from here."

            is CoachEvent.AppLimitApproaching -> {
                val used = event.limitMinutes - event.remainingMinutes
                "${event.appLabel} is at $used of ${event.limitMinutes} minutes. " +
                    "${event.remainingMinutes} left before I step in."
            }

            is CoachEvent.EarningCreditGranted -> earningMessage(input, event)

            is CoachEvent.UsageImproved ->
                "You've used ${event.fewerMinutes} fewer minutes than yesterday. Quiet progress \u2014 I noticed."

            is CoachEvent.UsageIncreased ->
                "Screen time is up ${event.extraMinutes} minutes from yesterday. No alarm from me \u2014 just an observation."

            is CoachEvent.HighScreenTime ->
                "${event.appLabel} is your biggest time consumer today \u2014 ${event.minutes} minutes."

            is CoachEvent.DailySummary -> dailySummaryMessage(input)

            is CoachEvent.ProtectionDisabled ->
                // protectionOn=false already handled above; this is the explicit event form.
                "Protection is off right now. Jikan won't be able to enforce your limits until you turn it back on."

            else -> null
        }
    }

    private fun earningMessage(input: CoachInput, event: CoachEvent.EarningCreditGranted): String {
        val top = input.screenTime?.topApps?.firstOrNull()
        val contrast = if (top != null && top.minutes > 0 &&
            !top.appLabel.equals(event.appLabel, ignoreCase = true)
        ) {
            ", while ${top.appLabel} used ${top.minutes} minutes"
        } else {
            ""
        }
        return "${event.appLabel} gave you ${event.creditsEarned} minutes today$contrast."
    }

    private fun dailySummaryMessage(input: CoachInput): String? {
        val summary = input.screenTime ?: return null
        val headline = "Today so far: ${summary.totalScreenTimeMinutes} minutes of screen time, " +
            "${summary.earnedMinutes} earned and ${summary.spentMinutes} spent."
        val top = summary.topApps.firstOrNull()
        return if (top != null && top.minutes > 0) {
            "$headline ${top.appLabel} is in the lead at ${top.minutes}."
        } else {
            headline
        }
    }

    /**
     * Passive observations from facts when no transient event fired. Only the
     * top-app note lives here; limit/usage headlines arrive as events.
     */
    private fun observationMessage(input: CoachInput): String? {
        val summary = input.screenTime ?: return null
        if (input.recentEvent != CoachEvent.HomeOpened) return null
        val top = summary.topApps.firstOrNull() ?: return null
        if (top.minutes < TOP_APP_MINIMUM_MINUTES) return null
        return "${top.appLabel} is your biggest time consumer today \u2014 ${top.minutes} minutes."
    }

    /**
     * Pre-Phase-6 study/wallet/morning rules, preserved. They still apply when
     * there is no screen-time headline to report.
     */
    private fun legacyMessage(input: CoachInput): String {
        val maxBalanceMinutes = Wallet.MAX_BALANCE_MINUTES
        return when {
            input.recentEvent is CoachEvent.SessionCompleted ->
                sessionMessage(input.recentEvent, input)

            input.recentEvent is CoachEvent.LockedAppBlocked ->
                lockedAppMessage(input.recentEvent, input)

            input.recentEvent is CoachEvent.StudyStarted ->
                "Small mission, clear target: 10 cards. I'll keep count while you handle the kana."

            input.streakDays > 0 && input.studiedYesterdayMinutes > 0 && input.studiedTodayMinutes == 0 ->
                "Your ${input.streakDays}-day streak is waiting for today's check-in. One calm session keeps it breathing."

            input.walletBalanceMinutes == 0 && input.dueCount > 0 ->
                "Your time wallet is empty and ${input.dueCount} cards are waiting. Tiny refill mission: one quiz, no drama."

            input.walletBalanceMinutes in 1..9 ->
                "Only ${input.walletBalanceMinutes} minutes left. A quick study refill would make future-you suspiciously grateful."

            input.walletBalanceMinutes >= maxBalanceMinutes ->
                "Wallet's full at $maxBalanceMinutes min. Spend a little or pause earning for now."

            input.hourOfDay < 12 && input.studiedTodayMinutes == 0 ->
                "Good morning${nameSuffix(input.userName)}. I kept 10 cards warm for your first minutes of the day."

            input.streakDays > 0 ->
                "One session keeps your ${input.streakDays}-day streak alive. Nothing dramatic, just a clean little rep."

            input.screenTime != null ->
                "Steady day so far: ${input.screenTime.totalScreenTimeMinutes} minutes total. Nothing urgent on my watch."

            else ->
                "Ready when you are. Ten cards, a few minutes earned, and we both pretend that was effortless."
        }
    }

    fun lockMessage(appLabel: String, walletBalanceMinutes: Int): String {
        return messageFor(
            CoachInput(
                userName = "",
                walletBalanceMinutes = walletBalanceMinutes,
                streakDays = 0,
                dueCount = 0,
                studiedTodayMinutes = 0,
                studiedYesterdayMinutes = 0,
                protectionOn = true,
                bankingModeActive = false,
                hourOfDay = 12,
                recentEvent = CoachEvent.LockedAppBlocked(appLabel),
            )
        )
    }

    fun resultsMessage(
        correctCount: Int,
        totalCount: Int,
        creditsEarned: Int,
        walletBalanceMinutes: Int,
        isPerfect: Boolean,
    ): String {
        return messageFor(
            CoachInput(
                userName = "",
                walletBalanceMinutes = walletBalanceMinutes,
                streakDays = 0,
                dueCount = 0,
                studiedTodayMinutes = 1,
                studiedYesterdayMinutes = 0,
                protectionOn = true,
                bankingModeActive = false,
                hourOfDay = 12,
                recentEvent = CoachEvent.SessionCompleted(
                    correctCount = correctCount,
                    totalCount = totalCount,
                    creditsEarned = creditsEarned,
                    isPerfect = isPerfect,
                ),
            )
        )
    }

    fun moodFor(input: CoachInput): CoachMood {
        return when {
            input.bankingModeActive || !input.protectionOn -> CoachMood.Concerned
            input.recentEvent is CoachEvent.ProtectionDisabled -> CoachMood.Concerned
            input.recentEvent is CoachEvent.SessionCompleted && input.recentEvent.isPerfect -> CoachMood.Proud
            input.recentEvent is CoachEvent.UsageImproved -> CoachMood.Proud
            input.recentEvent is CoachEvent.EarningCreditGranted -> CoachMood.Playful
            input.recentEvent is CoachEvent.LockedAppBlocked -> CoachMood.Focused
            input.recentEvent is CoachEvent.AppLimitExceeded -> CoachMood.Focused
            input.recentEvent is CoachEvent.AppLimitReached -> CoachMood.Focused
            input.recentEvent is CoachEvent.ProtectionRestored -> CoachMood.WelcomeBack
            input.recentEvent is CoachEvent.AppLimitApproaching -> CoachMood.Calm
            input.hourOfDay < 12 && input.studiedTodayMinutes == 0 -> CoachMood.WelcomeBack
            input.walletBalanceMinutes in 1..9 -> CoachMood.Playful
            else -> CoachMood.Calm
        }
    }

    private fun sessionMessage(event: CoachEvent.SessionCompleted, input: CoachInput): String {
        val accuracy = if (event.totalCount > 0) (event.correctCount * 100) / event.totalCount else 0
        return when {
            event.isPerfect ->
                "That was clean work: ${event.correctCount}/${event.totalCount}. I am quietly proud, which is still proud."

            accuracy >= 80 ->
                "Nice rhythm: ${event.correctCount}/${event.totalCount}, plus ${event.creditsEarned} minutes earned. A few rough edges, but the pattern is getting steadier."

            event.creditsEarned > 0 ->
                "That session still paid you ${event.creditsEarned} minutes. The missed cards are not enemies; they are just loud hints."

            input.walletBalanceMinutes == 0 ->
                "That one was bumpy, but useful. I marked the rough spots so the next round has a clearer target."

            else ->
                "Session logged. Not flashy, not wasted - the kind of rep memory likes."
        }
    }

    private fun lockedAppMessage(event: CoachEvent.LockedAppBlocked, input: CoachInput): String {
        return if (input.walletBalanceMinutes > 0) {
            "${event.appLabel} is asking for attention. You have ${input.walletBalanceMinutes} minutes saved, so spend them with intention."
        } else {
            "I see the ${event.appLabel} urge. Very human. Earn a few minutes first, then go be mysterious online."
        }
    }

    private fun nameSuffix(userName: String): String {
        val trimmed = userName.trim()
        return if (trimmed.isBlank()) "" else ", $trimmed"
    }
}
