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
    fun fromThemeMode(mode: ThemeMode): String = mode.name

    @TypeConverter
    fun toThemeMode(value: String): ThemeMode = ThemeMode.valueOf(value)

    @TypeConverter
    fun fromChallengeType(type: ChallengeType): String = type.name

    @TypeConverter
    fun toChallengeType(value: String): ChallengeType = ChallengeType.valueOf(value)

    @TypeConverter
    fun fromWalletTransactionType(type: WalletTransactionType): String = type.name

    @TypeConverter
    fun toWalletTransactionType(value: String): WalletTransactionType = WalletTransactionType.valueOf(value)

    @TypeConverter
    fun fromWalletTransactionSource(source: WalletTransactionSource): String = source.name

    @TypeConverter
    fun toWalletTransactionSource(value: String): WalletTransactionSource = WalletTransactionSource.valueOf(value)
}
