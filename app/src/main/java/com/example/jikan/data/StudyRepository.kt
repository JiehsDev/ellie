package com.example.jikan.data

import com.example.jikan.srs.SrsEngine
import com.example.jikan.srs.SrsState
import com.example.jikan.study.CreditCalculator
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

data class SessionCompletionResult(
    val creditsEarned: Int,
    val walletBalance: Int,
    val streakDays: Int,
    val isPerfect: Boolean,
)

class StudyRepository(
    private val cardDao: CardDao,
    private val progressDao: ProgressDao,
    private val sessionDao: SessionDao,
    private val walletDao: WalletDao,
) {
    suspend fun buildSessionQueue(sessionSize: Int): List<Card> {
        val now = System.currentTimeMillis()
        val dueCards = progressDao.getDueCards(now, sessionSize).mapNotNull { cardDao.getById(it.cardId) }
        val remaining = sessionSize - dueCards.size
        val newCards = if (remaining > 0) cardDao.getNewCards(remaining) else emptyList()
        return dueCards + newCards
    }

    suspend fun getAllCardsOnce(): List<Card> = cardDao.getAllOnce()

    suspend fun recordAnswer(cardId: Long, wasCorrect: Boolean, now: Long = System.currentTimeMillis()) {
        val existing = progressDao.getForCard(cardId)
        val state = existing?.let { SrsState(it.intervalDays, it.easeFactor, it.repetitions) }
            ?: SrsEngine.INITIAL_STATE
        val result = SrsEngine.review(state, wasCorrect, now)
        progressDao.upsert(
            UserCardProgress(
                id = existing?.id ?: 0,
                cardId = cardId,
                intervalDays = result.intervalDays,
                easeFactor = result.easeFactor,
                repetitions = result.repetitions,
                dueAt = result.dueAt,
                lastReviewedAt = now,
            )
        )
    }

    suspend fun completeSession(
        correctCount: Int,
        totalCount: Int,
        sessionStartedAt: Long,
        now: Long = System.currentTimeMillis(),
    ): SessionCompletionResult {
        val zone = ZoneId.systemDefault()
        val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate().toEpochDay()

        val wallet = walletDao.get() ?: Wallet()
        val newStreak = when (wallet.lastStudyEpochDay) {
            today -> wallet.currentStreakDays.coerceAtLeast(1)
            today - 1 -> wallet.currentStreakDays + 1
            else -> 1
        }

        val startOfToday = LocalDate.ofEpochDay(today).atStartOfDay(zone).toInstant().toEpochMilli()
        val priorSessionsToday = sessionDao.getSince(startOfToday).size

        val creditsEarned = CreditCalculator.creditsEarned(
            correctCount = correctCount,
            totalCount = totalCount,
            currentStreakDays = newStreak,
            priorSessionsToday = priorSessionsToday,
        )

        val updatedWallet = wallet.copy(
            creditBalanceMinutes = (wallet.creditBalanceMinutes + creditsEarned).coerceAtMost(Wallet.MAX_BALANCE_MINUTES),
            currentStreakDays = newStreak,
            lastStudyEpochDay = today,
            lifetimeCreditsEarned = wallet.lifetimeCreditsEarned + creditsEarned,
        )
        walletDao.upsert(updatedWallet)

        sessionDao.insert(
            Session(
                startedAt = sessionStartedAt,
                completedAt = now,
                questionsTotal = totalCount,
                questionsCorrect = correctCount,
                creditsEarned = creditsEarned,
                isPerfect = correctCount == totalCount,
            )
        )

        return SessionCompletionResult(
            creditsEarned = creditsEarned,
            walletBalance = updatedWallet.creditBalanceMinutes,
            streakDays = newStreak,
            isPerfect = correctCount == totalCount,
        )
    }
}
