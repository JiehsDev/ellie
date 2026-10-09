package com.example.jikan.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "banking_allowlist")
data class BankingAllowlistEntry(
    @PrimaryKey val packageName: String,
    val appLabel: String,
    val expiresAtMs: Long,
)
