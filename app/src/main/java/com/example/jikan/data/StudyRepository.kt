package com.example.jikan.data

import com.example.jikan.srs.RecallGrade
import com.example.jikan.srs.SrsEngine
import com.example.jikan.srs.SrsState
import com.example.jikan.study.CreditCalculator
import com.example.jikan.study.CreditPolicy
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
    private val walletTransactionDao: WalletTransactionDao? = null,
    private val creditDayStateDao: CreditDayStateDao? = null,
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

        // Idempotency: a session is uniquely identified by its start time.
        // Processing the same session twice must not award credits twice.
        val idempotencyKey = "session:$sessionStartedAt"
        val existing = walletTransactionDao?.getByIdempotencyKey(idempotencyKey)
        if (existing != null) {
            val wallet = walletDao.get() ?: Wallet()
            return SessionCompletionResult(
                creditsEarned = existing.minutes,
                walletBalance = wallet.creditBalanceMinutes,
                streakDays = wallet.currentStreakDays,
                isPerfect = correctCount == totalCount,
            )
        }

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

        // New credit policy: earning is based on validated study MINUTES
        // (session duration), tiered per day. This replaces the per-answer
        // formula with its per-session decay.
        val profile = try {
            CreditPolicy.CreditProfile.valueOf(settings?.creditProfileName ?: "BALANCED")
        } catch (_: Exception) {
            CreditPolicy.CreditProfile.BALANCED
        }
        val config = CreditPolicy.configFor(profile)
        val validatedMinutes = ((now - sessionStartedAt) / 60_000L).toInt().coerceAtLeast(1)

        // Get or create today's credit state (atomic for concurrent sessions).
        val dayStateDao = creditDayStateDao
        var priorMinutes = 0
        if (dayStateDao != null) {
            var dayState = dayStateDao.get(today)
            if (dayState == null) {
                dayStateDao.insert(
                    CreditDayState(epochDay = today, profileName = profile.name, lastClockMs = now)
                )
                dayState = dayStateDao.get(today)!!
            }
            priorMinutes = dayState.validatedStudyMinutes
        }
        val creditsEarned = CreditPolicy.creditsForNewMinutes(config, priorMinutes, validatedMinutes)
        dayStateDao?.addValidatedMinutes(today, validatedMinutes, now)

        val updatedWallet = wallet.copy(
            creditBalanceMinutes = (wallet.creditBalanceMinutes + creditsEarned).coerceAtMost(Wallet.MAX_BALANCE_MINUTES),
            currentStreakDays = newStreak,
            lastStudyEpochDay = today,
            lifetimeCreditsEarned = wallet.lifetimeCreditsEarned + creditsEarned,
        )
        walletDao.upsert(updatedWallet)

        // Auditable ledger entry with idempotency key.
        walletTransactionDao?.insert(
            WalletTransaction(
                type = WalletTransactionType.EARN,
                minutes = creditsEarned,
                balanceAfter = updatedWallet.creditBalanceMinutes,
                createdAtMs = now,
                epochDay = today,
                idempotencyKey = idempotencyKey,
                note = "Study session: $validatedMinutes validated minutes " +
                    "(${priorMinutes}m → ${priorMinutes + validatedMinutes}m today, ${config.displayName})",
                source = WalletTransactionSource.UNKNOWN,
                qualifyingMinutes = validatedMinutes,
            )
        )

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
