package com.example.jikan.data

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class CardTier {
    HIRAGANA_VOWELS,
    HIRAGANA_K,
    HIRAGANA_S,
    HIRAGANA_T,
    HIRAGANA_N,
    HIRAGANA_H,
    HIRAGANA_M,
    HIRAGANA_Y,
    HIRAGANA_R,
    HIRAGANA_W_N,
    KATAKANA,
    VOCAB_BASIC,
    KANJI_RADICALS,
    PHRASES_BASIC,
}

@Entity(tableName = "cards")
data class Card(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val character: String,
    val romaji: String,
    val audioResName: String,
    val mnemonic: String? = null,
    val strokeOrderData: String? = null,
    val tier: CardTier,
    val sortOrder: Int,
)
