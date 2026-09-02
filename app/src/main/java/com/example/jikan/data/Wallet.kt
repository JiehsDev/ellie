package com.example.jikan.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "wallet")
data class Wallet(
    @PrimaryKey val id: Int = SINGLETON_ID,
    val creditBalanceMinutes: Int = 0,
    val currentStreakDays: Int = 0,
    val lastStudyEpochDay: Long? = null,
    val lifetimeCreditsEarned: Int = 0,
) {
    companion object {
        const val SINGLETON_ID = 1

        /** Spendable balance never exceeds this, whether earned by studying or passively regenerated. */
        const val MAX_BALANCE_MINUTES = 60
    }
}
