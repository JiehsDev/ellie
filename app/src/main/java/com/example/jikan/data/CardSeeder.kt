package com.example.jikan.data

import android.content.Context
import org.json.JSONArray

object CardSeeder {
    private const val SEED_ASSET_NAME = "hiragana.json"

    suspend fun seedIfEmpty(context: Context, cardDao: CardDao) {
        if (cardDao.count() > 0) return
        cardDao.insertAll(loadFromAssets(context))
    }

    private val TIER_BY_JSON_KEY = mapOf(
        "hiragana_vowels" to CardTier.HIRAGANA_VOWELS,
        "hiragana_k" to CardTier.HIRAGANA_K,
        "hiragana_s" to CardTier.HIRAGANA_S,
        "hiragana_t" to CardTier.HIRAGANA_T,
        "hiragana_n" to CardTier.HIRAGANA_N,
        "hiragana_h" to CardTier.HIRAGANA_H,
        "hiragana_m" to CardTier.HIRAGANA_M,
        "hiragana_yayuyo" to CardTier.HIRAGANA_Y,
        "hiragana_r" to CardTier.HIRAGANA_R,
        "hiragana_wn" to CardTier.HIRAGANA_W_N,
    )

    private fun loadFromAssets(context: Context): List<Card> {
        val json = context.assets.open(SEED_ASSET_NAME).bufferedReader().use { it.readText() }
        val array = JSONArray(json)
        val sortOrderByTier = mutableMapOf<CardTier, Int>()
        return (0 until array.length()).map { index ->
            val obj = array.getJSONObject(index)
            val tierKey = obj.getString("tier")
            val tier = TIER_BY_JSON_KEY[tierKey]
                ?: error("Unknown card tier \"$tierKey\" in $SEED_ASSET_NAME")
            val sortOrder = sortOrderByTier.getOrDefault(tier, 0)
            sortOrderByTier[tier] = sortOrder + 1
            Card(
                character = obj.getString("character"),
                romaji = obj.getString("romaji"),
                audioResName = obj.getString("audio"),
                mnemonic = if (obj.has("mnemonic") && !obj.isNull("mnemonic")) obj.getString("mnemonic") else null,
                tier = tier,
                sortOrder = sortOrder,
            )
        }
    }
}
