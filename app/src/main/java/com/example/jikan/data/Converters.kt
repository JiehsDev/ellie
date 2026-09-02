package com.example.jikan.data

import androidx.room.TypeConverter

class Converters {
    @TypeConverter
    fun fromCardTier(tier: CardTier): String = tier.name

    @TypeConverter
    fun toCardTier(value: String): CardTier = CardTier.valueOf(value)

    @TypeConverter
    fun fromDailyGoalPreset(preset: DailyGoalPreset): String = preset.name

    @TypeConverter
    fun toDailyGoalPreset(value: String): DailyGoalPreset = DailyGoalPreset.valueOf(value)

    @TypeConverter
    fun fromChallengeType(type: ChallengeType): String = type.name

    @TypeConverter
    fun toChallengeType(value: String): ChallengeType = ChallengeType.valueOf(value)
}
