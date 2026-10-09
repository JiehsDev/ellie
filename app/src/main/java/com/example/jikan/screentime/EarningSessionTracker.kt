package com.example.jikan.screentime

import com.example.jikan.data.AppDatabase
import com.example.jikan.data.EarnRule
import com.example.jikan.data.EarningAppProgress
import com.example.jikan.data.WalletLedgerRepository
import com.example.jikan.data.WalletTransactionSource
import com.example.jikan.data.WalletTransactionType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneId

data class EarningGrant(
    val packageName: String,
    val ruleId: Long,
    val qualifyingMinutes: Int,
    val rewardMinutes: Int,
    val idempotencyKey: String,
)

data class EarningProcessingResult(
    val accumulatedMs: Long,
    val completedBlocks: Int,
    val grants: List<EarningGrant>,
)

class EarningSessionTracker(
    private val db: AppDatabase,
    private val walletLedgerRepository: WalletLedgerRepository = WalletLedgerRepository(db),
    private val zoneId: ZoneId = ZoneId.systemDefault(),
) {
    private var activePackageName: String? = null
    private var activeStartedAtMs: Long = 0L

    suspend fun onForegroundAppChanged(packageName: String?, nowMs: Long = System.currentTimeMillis()) {
        val previous = activePackageName
        if (previous == packageName) return
        if (previous != null && activeStartedAtMs > 0L) {
            processForegroundInterval(previous, activeStartedAtMs, nowMs)
        }
        activePackageName = packageName
        activeStartedAtMs = nowMs
    }

    suspend fun flush(nowMs: Long = System.currentTimeMillis()) {
        val current = activePackageName ?: return
        if (activeStartedAtMs <= 0L) return
        processForegroundInterval(current, activeStartedAtMs, nowMs)
        activeStartedAtMs = nowMs
    }

    suspend fun processForegroundInterval(
        packageName: String,
        startedAtMs: Long,
        endedAtMs: Long,
    ): EarningProcessingResult = withContext(Dispatchers.IO) {
        if (endedAtMs <= startedAtMs) {
            return@withContext EarningProcessingResult(0L, 0, emptyList())
        }

        val rule = db.earnRuleDao().getForPackage(packageName)
        if (rule == null || !rule.enabled || EarnRuleValidator.validate(rule).isNotEmpty()) {
            return@withContext EarningProcessingResult(0L, 0, emptyList())
        }

        val epochDay = Instant.ofEpochMilli(startedAtMs).atZone(zoneId).toLocalDate().toEpochDay()
        val existing = db.earningAppProgressDao().get(rule.id, epochDay)
        val alreadyEarnedToday = db.walletTransactionDao().sumMinutesByTypeForPackageDay(
            type = WalletTransactionType.EARN,
            epochDay = epochDay,
            packageName = packageName,
        )
        val previous = EarningProgressState(
            accumulatedMs = existing?.accumulatedMs ?: 0L,
            rewardedBlocks = existing?.rewardedBlocks ?: 0,
        )
        val plan = EarningSessionProcessor.plan(
            rule = rule,
            previous = previous,
            intervalMs = endedAtMs - startedAtMs,
            alreadyEarnedToday = alreadyEarnedToday,
        )

        val grants = mutableListOf<EarningGrant>()
        repeat(plan.rewardableBlocks) { offset ->
            val blockIndex = previous.rewardedBlocks + offset + 1
            val key = idempotencyKey(rule, epochDay, blockIndex)
            walletLedgerRepository.earn(
                minutes = rule.rewardMinutes,
                packageName = packageName,
                idempotencyKey = key,
                nowMs = endedAtMs,
                note = "source=EARNING_APP;ruleId=${rule.id};qualifyingMinutes=${rule.requiredMinutes}",
                source = WalletTransactionSource.EARNING_APP,
                ruleId = rule.id,
                qualifyingMinutes = rule.requiredMinutes,
            )
            grants += EarningGrant(
                packageName = packageName,
                ruleId = rule.id,
                qualifyingMinutes = rule.requiredMinutes,
                rewardMinutes = rule.rewardMinutes,
                idempotencyKey = key,
            )
        }

        db.earningAppProgressDao().upsert(
            EarningAppProgress(
                ruleId = rule.id,
                packageName = packageName,
                epochDay = epochDay,
                accumulatedMs = plan.state.accumulatedMs,
                rewardedBlocks = plan.state.rewardedBlocks,
                updatedAtMs = endedAtMs,
            )
        )

        EarningProcessingResult(
            accumulatedMs = plan.state.accumulatedMs,
            completedBlocks = plan.completedBlocks,
            grants = grants,
        )
    }

    private fun idempotencyKey(rule: EarnRule, epochDay: Long, blockIndex: Int): String {
        return "earning-app:${rule.id}:$epochDay:$blockIndex"
    }
}
