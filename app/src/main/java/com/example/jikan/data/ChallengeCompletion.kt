package com.example.jikan.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One completed challenge.
 *
 * [creditsEarned] is what the rate awarded before caps; [creditsGranted] is what
 * actually reached the wallet. They are stored separately so the productivity score can
 * measure effort — a user whose wallet happened to be full still did the work.
 */
@Entity(tableName = "challenge_completions", indices = [Index("epochDay")])
data class ChallengeCompletion(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: ChallengeType,
    val epochDay: Long,
    val completedAt: Long,
    val metric: Int,
    val creditsEarned: Int,
    val creditsGranted: Int,
)
