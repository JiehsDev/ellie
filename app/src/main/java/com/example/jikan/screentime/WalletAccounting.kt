package com.example.jikan.screentime

import com.example.jikan.data.Wallet
import com.example.jikan.data.WalletTransaction
import com.example.jikan.data.WalletTransactionType

sealed interface WalletAccountingResult {
    data class Applied(val balanceAfter: Int) : WalletAccountingResult
    data object Duplicate : WalletAccountingResult
    data object InsufficientBalance : WalletAccountingResult
    data class Invalid(val reason: String) : WalletAccountingResult
}

object WalletAccounting {
    fun apply(
        currentBalance: Int,
        transaction: WalletTransaction,
        existingIdempotencyKeys: Set<String> = emptySet(),
        maxBalance: Int = Wallet.MAX_BALANCE_MINUTES,
    ): WalletAccountingResult {
        val key = transaction.idempotencyKey
        if (key != null && key in existingIdempotencyKeys) return WalletAccountingResult.Duplicate
        if (transaction.minutes <= 0) return WalletAccountingResult.Invalid("minutes must be positive")

        val balanceAfter = when (transaction.type) {
            WalletTransactionType.EARN,
            WalletTransactionType.MANUAL_ADJUSTMENT -> (currentBalance + transaction.minutes).coerceAtMost(maxBalance)

            WalletTransactionType.SPEND,
            WalletTransactionType.EXPIRATION -> {
                if (currentBalance < transaction.minutes) return WalletAccountingResult.InsufficientBalance
                currentBalance - transaction.minutes
            }
        }
        return WalletAccountingResult.Applied(balanceAfter)
    }

    fun balanceFromTransactions(startingBalance: Int, transactions: List<WalletTransaction>, maxBalance: Int): Int {
        return transactions.fold(startingBalance) { balance, transaction ->
            when (val result = apply(balance, transaction, maxBalance = maxBalance)) {
                is WalletAccountingResult.Applied -> result.balanceAfter
                WalletAccountingResult.Duplicate,
                WalletAccountingResult.InsufficientBalance,
                is WalletAccountingResult.Invalid -> balance
            }
        }
    }
}
