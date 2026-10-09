package com.example.jikan.data

import androidx.room.withTransaction
import com.example.jikan.screentime.WalletAccounting
import com.example.jikan.screentime.WalletAccountingResult
import java.time.Instant
import java.time.ZoneId

class WalletLedgerRepository(
    private val db: AppDatabase,
    private val zoneId: ZoneId = ZoneId.systemDefault(),
) {
    suspend fun earn(
        minutes: Int,
        packageName: String?,
        idempotencyKey: String,
        nowMs: Long = System.currentTimeMillis(),
        note: String? = null,
        source: WalletTransactionSource = WalletTransactionSource.UNKNOWN,
        ruleId: Long? = null,
        qualifyingMinutes: Int? = null,
    ): WalletAccountingResult = applyTransaction(
        type = WalletTransactionType.EARN,
        minutes = minutes,
        packageName = packageName,
        idempotencyKey = idempotencyKey,
        nowMs = nowMs,
        note = note,
        source = source,
        ruleId = ruleId,
        qualifyingMinutes = qualifyingMinutes,
    )

    suspend fun spend(
        minutes: Int,
        packageName: String?,
        idempotencyKey: String,
        nowMs: Long = System.currentTimeMillis(),
        note: String? = null,
    ): WalletAccountingResult = applyTransaction(
        type = WalletTransactionType.SPEND,
        minutes = minutes,
        packageName = packageName,
        idempotencyKey = idempotencyKey,
        nowMs = nowMs,
        note = note,
        source = WalletTransactionSource.UNKNOWN,
        ruleId = null,
        qualifyingMinutes = null,
    )

    suspend fun manualAdjustment(
        minutes: Int,
        idempotencyKey: String,
        nowMs: Long = System.currentTimeMillis(),
        note: String? = null,
    ): WalletAccountingResult = applyTransaction(
        type = WalletTransactionType.MANUAL_ADJUSTMENT,
        minutes = minutes,
        packageName = null,
        idempotencyKey = idempotencyKey,
        nowMs = nowMs,
        note = note,
        source = WalletTransactionSource.UNKNOWN,
        ruleId = null,
        qualifyingMinutes = null,
    )

    private suspend fun applyTransaction(
        type: WalletTransactionType,
        minutes: Int,
        packageName: String?,
        idempotencyKey: String,
        nowMs: Long,
        note: String?,
        source: WalletTransactionSource,
        ruleId: Long?,
        qualifyingMinutes: Int?,
    ): WalletAccountingResult = db.withTransaction {
        if (db.walletTransactionDao().getByIdempotencyKey(idempotencyKey) != null) {
            return@withTransaction WalletAccountingResult.Duplicate
        }

        val wallet = db.walletDao().get() ?: Wallet()
        val draft = WalletTransaction(
            type = type,
            minutes = minutes,
            balanceAfter = wallet.creditBalanceMinutes,
            createdAtMs = nowMs,
            epochDay = Instant.ofEpochMilli(nowMs).atZone(zoneId).toLocalDate().toEpochDay(),
            packageName = packageName,
            idempotencyKey = idempotencyKey,
            note = note,
            source = source,
            ruleId = ruleId,
            qualifyingMinutes = qualifyingMinutes,
        )
        when (val result = WalletAccounting.apply(wallet.creditBalanceMinutes, draft)) {
            is WalletAccountingResult.Applied -> {
                val transaction = draft.copy(balanceAfter = result.balanceAfter)
                val inserted = db.walletTransactionDao().insert(transaction)
                if (inserted == -1L) {
                    WalletAccountingResult.Duplicate
                } else {
                    db.walletDao().upsert(wallet.copy(creditBalanceMinutes = result.balanceAfter))
                    result
                }
            }

            WalletAccountingResult.Duplicate,
            WalletAccountingResult.InsufficientBalance,
            is WalletAccountingResult.Invalid -> result
        }
    }
}
