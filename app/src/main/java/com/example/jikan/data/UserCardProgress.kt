package com.example.jikan.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "user_card_progress",
    foreignKeys = [
        ForeignKey(
            entity = Card::class,
            parentColumns = ["id"],
            childColumns = ["cardId"],
            onDelete = ForeignKey.CASCADE,
        )
    ],
    indices = [Index("cardId", unique = true)],
)
data class UserCardProgress(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val cardId: Long,
    val intervalDays: Int = 0,
    val easeFactor: Float = 2.5f,
    val repetitions: Int = 0,
    val dueAt: Long,
    val lastReviewedAt: Long? = null,
)
