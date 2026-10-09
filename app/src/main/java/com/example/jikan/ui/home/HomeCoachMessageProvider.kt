package com.example.jikan.ui.home

import com.example.jikan.data.Wallet

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
}

object HomeCoachMessageProvider {
    fun messageFor(input: CoachInput): String {
        val maxBalanceMinutes = Wallet.MAX_BALANCE_MINUTES
        return when {
            input.bankingModeActive ->
                "Banking mode is on - protection is off. Tap me when you're ready to put the guard back up."

            !input.protectionOn ->
                "I can't guard your apps right now. Turn Jikan back on in Accessibility and I'll take watch again."

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
            input.recentEvent is CoachEvent.SessionCompleted && input.recentEvent.isPerfect -> CoachMood.Proud
            input.recentEvent is CoachEvent.LockedAppBlocked -> CoachMood.Focused
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
