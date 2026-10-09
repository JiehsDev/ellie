package com.example.jikan.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "ai_insights")
data class AiInsightEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val sessionId: Long,
    val type: String,
    val content: String,
    val createdAt: Long,
    val modelVersion: String,
)
