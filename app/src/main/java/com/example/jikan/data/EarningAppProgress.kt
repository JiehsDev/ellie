package com.example.jikan.data

import androidx.room.Entity

@Entity(
    tableName = "earning_app_progress",
    primaryKeys = ["ruleId", "epochDay"],
)
data class EarningAppProgress(
    val ruleId: Long,
    val packageName: String,
    val epochDay: Long,
    val accumulatedMs: Long,
    val rewardedBlocks: Int,
    val updatedAtMs: Long,
)
