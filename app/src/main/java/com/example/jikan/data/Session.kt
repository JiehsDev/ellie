package com.example.jikan.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "sessions")
data class Session(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val startedAt: Long,
    val completedAt: Long? = null,
    val questionsTotal: Int,
    val questionsCorrect: Int,
    val creditsEarned: Int,
    val isPerfect: Boolean = false,
    val triggeredByPackage: String? = null,
)
