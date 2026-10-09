package com.example.jikan.data

import com.example.jikan.srs.RecallGrade
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
    private val settingsDao: SettingsDao? = null,
) {
    suspend fun buildSessionQueue(sessionSize: Int): List<Card> {
        val now = System.currentTimeMillis()
        val dueCards = progressDao.getDueCards(now, sessionSize).mapNotNull { cardDao.getById(it.cardId) }
        val remaining = sessionSize - dueCards.size
        val newCards = if (remaining > 0) cardDao.getNewCards(remaining) else emptyList()
        return dueCards + newCards
    }

    suspend fun getAllCardsOnce(): List<Card> = cardDao.getAllOnce()

    /**
     * Records a recall grade and returns the interval growth in days
     * (new interval minus old interval) for session stats.
     */
    suspend fun recordGrade(cardId: Long, grade: RecallGrade, now: Long = System.currentTimeMillis()): Double {
        val existing = progressDao.getForCard(cardId)
        val state = existing?.let { SrsState(it.intervalDays, it.easeFactor, it.repetitions) }
            ?: SrsEngine.INITIAL_STATE
        val oldInterval = state.intervalDays
        val result = SrsEngine.reviewGrade(state, grade, now)
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
        return result.intervalDays - oldInterval
    }

    /**
     * Records an answer and returns the interval growth in days
     * (new interval minus old interval) for session stats.
     */
    suspend fun recordAnswer(cardId: Long, wasCorrect: Boolean, now: Long = System.currentTimeMillis()): Double {
        val existing = progressDao.getForCard(cardId)
        val state = existing?.let { SrsState(it.intervalDays, it.easeFactor, it.repetitions) }
            ?: SrsEngine.INITIAL_STATE
        val oldInterval = state.intervalDays
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
        return result.intervalDays - oldInterval
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
        val settings = settingsDao?.get()
        val strictViolationRecently = settings?.let {
            it.strictModeEnabled && it.lastViolationAtMs > 0L && now - it.lastViolationAtMs <= STRICT_VIOLATION_FREEZE_REVOKE_MS
        } ?: false
        val newStreak = when {
            wallet.lastStudyEpochDay == today -> wallet.currentStreakDays.coerceAtLeast(1)
            wallet.lastStudyEpochDay == today - 1 -> wallet.currentStreakDays + 1
            wallet.lastStudyEpochDay == today - 2 && wallet.currentStreakDays >= 3 && !strictViolationRecently -> {
                // Streak freeze / grace period: missed 1 day, streak protected!
                wallet.currentStreakDays + 1
            }
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

    private companion object {
        private const val STRICT_VIOLATION_FREEZE_REVOKE_MS = 7 * 24 * 60 * 60 * 1000L
    }
}
