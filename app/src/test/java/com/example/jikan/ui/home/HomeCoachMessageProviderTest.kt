package com.example.jikan.ui.home

import com.example.jikan.data.Wallet
import org.junit.Assert.assertEquals
import org.junit.Test

class HomeCoachMessageProviderTest {

    @Test
    fun bankingModeMessageWins() {
        val message = HomeCoachMessageProvider.messageFor(
            input(
                bankingModeActive = true,
                protectionOn = false,
                streakDays = 5,
                studiedYesterdayMinutes = 10,
                studiedTodayMinutes = 0,
            )
        )

        assertEquals("Banking mode is on - protection is off. Tap me when you're ready to put the guard back up.", message)
    }

    @Test
    fun protectionOffMessageWinsAfterBanking() {
        val message = HomeCoachMessageProvider.messageFor(
            input(
                protectionOn = false,
                streakDays = 5,
                studiedYesterdayMinutes = 10,
                studiedTodayMinutes = 0,
            )
        )

        // Phase 6 reworded the protection message around screen-time limits.
        assertEquals(
            "Protection is off right now. Jikan won't be able to enforce your limits until you turn it back on.",
            message,
        )
    }

    @Test
    fun streakAtRiskMessage() {
        val message = HomeCoachMessageProvider.messageFor(
            input(streakDays = 4, studiedYesterdayMinutes = 8, studiedTodayMinutes = 0)
        )

        assertEquals("Your 4-day streak is waiting for today's check-in. One calm session keeps it breathing.", message)
    }

    @Test
    fun emptyWalletAndDueCardsMessage() {
        val message = HomeCoachMessageProvider.messageFor(
            input(walletBalanceMinutes = 0, dueCount = 7, studiedTodayMinutes = 3)
        )

        assertEquals("Your time wallet is empty and 7 cards are waiting. Tiny refill mission: one quiz, no drama.", message)
    }

    @Test
    fun lowWalletMessage() {
        val message = HomeCoachMessageProvider.messageFor(
            input(walletBalanceMinutes = 6, studiedTodayMinutes = 3)
        )

        assertEquals("Only 6 minutes left. A quick study refill would make future-you suspiciously grateful.", message)
    }

    @Test
    fun fullWalletMessage() {
        val message = HomeCoachMessageProvider.messageFor(
            input(walletBalanceMinutes = Wallet.MAX_BALANCE_MINUTES, studiedTodayMinutes = 3)
        )

        assertEquals("Wallet's full at ${Wallet.MAX_BALANCE_MINUTES} min. Spend a little or pause earning for now.", message)
    }

    @Test
    fun morningMessageIncludesName() {
        val message = HomeCoachMessageProvider.messageFor(
            input(userName = "Ari", hourOfDay = 9, studiedTodayMinutes = 0)
        )

        assertEquals("Good morning, Ari. I kept 10 cards warm for your first minutes of the day.", message)
    }

    @Test
    fun fallbackUsesStreakWhenPresent() {
        val message = HomeCoachMessageProvider.messageFor(
            input(streakDays = 3, studiedTodayMinutes = 12)
        )

        assertEquals("One session keeps your 3-day streak alive. Nothing dramatic, just a clean little rep.", message)
    }

    @Test
    fun fallbackWithoutStreak() {
        val message = HomeCoachMessageProvider.messageFor(
            input(streakDays = 0, studiedTodayMinutes = 12)
        )

        assertEquals("Ready when you are. Ten cards, a few minutes earned, and we both pretend that was effortless.", message)
    }

    @Test
    fun lockedAppMessageHasPersonality() {
        val message = HomeCoachMessageProvider.lockMessage("Loopy", walletBalanceMinutes = 0)

        assertEquals("I see the Loopy urge. Very human. Earn a few minutes first, then go be mysterious online.", message)
    }

    @Test
    fun perfectSessionMessageIsProud() {
        val message = HomeCoachMessageProvider.resultsMessage(
            correctCount = 10,
            totalCount = 10,
            creditsEarned = 18,
            walletBalanceMinutes = 18,
            isPerfect = true,
        )

        assertEquals("That was clean work: 10/10. I am quietly proud, which is still proud.", message)
    }

    @Test
    fun moodReflectsSituation() {
        val mood = HomeCoachMessageProvider.moodFor(input(walletBalanceMinutes = 4, studiedTodayMinutes = 2))

        assertEquals(CoachMood.Playful, mood)
    }

    private fun input(
        userName: String = "",
        walletBalanceMinutes: Int = 20,
        streakDays: Int = 0,
        dueCount: Int = 0,
        studiedTodayMinutes: Int = 0,
        studiedYesterdayMinutes: Int = 0,
        protectionOn: Boolean = true,
        bankingModeActive: Boolean = false,
        hourOfDay: Int = 14,
        recentEvent: CoachEvent = CoachEvent.HomeOpened,
    ) = CoachInput(
        userName = userName,
        walletBalanceMinutes = walletBalanceMinutes,
        streakDays = streakDays,
        dueCount = dueCount,
        studiedTodayMinutes = studiedTodayMinutes,
        studiedYesterdayMinutes = studiedYesterdayMinutes,
        protectionOn = protectionOn,
        bankingModeActive = bankingModeActive,
        hourOfDay = hourOfDay,
        recentEvent = recentEvent,
    )
}
