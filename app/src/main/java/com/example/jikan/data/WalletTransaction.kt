package com.example.jikan.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

enum class WalletTransactionType {
    EARN,
    SPEND,
    MANUAL_ADJUSTMENT,
    EXPIRATION,
}

enum class WalletTransactionSource {
    UNKNOWN,
    EARNING_APP,
}

@Entity(
    tableName = "wallet_transactions",
    indices = [
        Index(value = ["idempotencyKey"], unique = true),
        Index(value = ["epochDay"]),
        Index(value = ["packageName"]),
    ],
)
data class WalletTransaction(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: WalletTransactionType,
    val minutes: Int,
    val balanceAfter: Int,
    val createdAtMs: Long,
    val epochDay: Long,
    val packageName: String? = null,
    val idempotencyKey: String? = null,
    val note: String? = null,
    val source: WalletTransactionSource = WalletTransactionSource.UNKNOWN,
    val ruleId: Long? = null,
    val qualifyingMinutes: Int? = null,
)
