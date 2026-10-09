package com.example.jikan.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Adds the app_usage table. Written as a real migration rather than leaning on the
 * destructive fallback so an upgrade doesn't wipe the user's streak and wallet.
 */
private val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `app_usage` (" +
                "`epochDay` INTEGER NOT NULL, " +
                "`packageName` TEXT NOT NULL, " +
                "`minutes` INTEGER NOT NULL, " +
                "PRIMARY KEY(`epochDay`, `packageName`))"
        )
    }
}

/**
 * Adds the challenge log and the protected-day log, and drops the streak columns from
 * wallet. SQLite cannot drop columns at this level, so wallet is recreated and copied.
 */
private val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `challenge_completions` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`type` TEXT NOT NULL, " +
                "`epochDay` INTEGER NOT NULL, " +
                "`completedAt` INTEGER NOT NULL, " +
                "`metric` INTEGER NOT NULL, " +
                "`creditsEarned` INTEGER NOT NULL, " +
                "`creditsGranted` INTEGER NOT NULL)"
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_challenge_completions_epochDay` " +
                "ON `challenge_completions` (`epochDay`)"
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `protected_days` (" +
                "`epochDay` INTEGER NOT NULL, " +
                "`observedMinutes` INTEGER NOT NULL, " +
                "PRIMARY KEY(`epochDay`))"
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `wallet_new` (" +
                "`id` INTEGER NOT NULL, " +
                "`creditBalanceMinutes` INTEGER NOT NULL, " +
                "`lifetimeCreditsEarned` INTEGER NOT NULL, " +
                "`currentStreakDays` INTEGER NOT NULL DEFAULT 0, " +
                "`lastStudyEpochDay` INTEGER NOT NULL DEFAULT 0, " +
                "PRIMARY KEY(`id`))"
        )
        db.execSQL(
            "INSERT INTO `wallet_new` (`id`, `creditBalanceMinutes`, `lifetimeCreditsEarned`, `currentStreakDays`, `lastStudyEpochDay`) " +
                "SELECT `id`, `creditBalanceMinutes`, `lifetimeCreditsEarned`, 0, 0 FROM `wallet`"
        )
        db.execSQL("DROP TABLE `wallet`")
        db.execSQL("ALTER TABLE `wallet_new` RENAME TO `wallet`")
    }
}

private val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `pause_state` (" +
                "`id` INTEGER NOT NULL, " +
                "`expiryTimestampMs` INTEGER NOT NULL, " +
                "PRIMARY KEY(`id`))"
        )
    }
}

private val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `locked_apps` ADD COLUMN `tier` TEXT NOT NULL DEFAULT 'EXTREME'")
    }
}

private val MIGRATION_6_7 = object : Migration(6, 7) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `ai_insights` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`sessionId` INTEGER NOT NULL, " +
                "`type` TEXT NOT NULL, " +
                "`content` TEXT NOT NULL, " +
                "`createdAt` INTEGER NOT NULL, " +
                "`modelVersion` TEXT NOT NULL)"
        )
    }
}

private val MIGRATION_7_8 = object : Migration(7, 8) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `user_settings` ADD COLUMN `themeMode` TEXT NOT NULL DEFAULT 'SYSTEM'")
    }
}

private val MIGRATION_8_9 = object : Migration(8, 9) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `user_settings` ADD COLUMN `strictModeEnabled` INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE `user_settings` ADD COLUMN `strictPinHash` TEXT")
        db.execSQL("ALTER TABLE `user_settings` ADD COLUMN `strictGraceUntilMs` INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE `user_settings` ADD COLUMN `bankingDisabledUntilMs` INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE `user_settings` ADD COLUMN `lastViolationAtMs` INTEGER NOT NULL DEFAULT 0")
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `banking_allowlist` (" +
                "`packageName` TEXT NOT NULL, " +
                "`appLabel` TEXT NOT NULL, " +
                "`expiresAtMs` INTEGER NOT NULL, " +
                "PRIMARY KEY(`packageName`))"
        )
    }
}

private val MIGRATION_9_10 = object : Migration(9, 10) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `user_settings` ADD COLUMN `bankingModeActive` INTEGER NOT NULL DEFAULT 0")
    }
}

private val MIGRATION_10_11 = object : Migration(10, 11) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `earn_rules` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`packageName` TEXT NOT NULL, " +
                "`requiredMinutes` INTEGER NOT NULL, " +
                "`rewardMinutes` INTEGER NOT NULL, " +
                "`dailyLimitMinutes` INTEGER NOT NULL, " +
                "`enabled` INTEGER NOT NULL)"
        )
        db.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_earn_rules_packageName` " +
                "ON `earn_rules` (`packageName`)"
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `wallet_transactions` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`type` TEXT NOT NULL, " +
                "`minutes` INTEGER NOT NULL, " +
                "`balanceAfter` INTEGER NOT NULL, " +
                "`createdAtMs` INTEGER NOT NULL, " +
                "`epochDay` INTEGER NOT NULL, " +
                "`packageName` TEXT, " +
                "`idempotencyKey` TEXT, " +
                "`note` TEXT)"
        )
        db.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_wallet_transactions_idempotencyKey` " +
                "ON `wallet_transactions` (`idempotencyKey`)"
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_wallet_transactions_epochDay` " +
                "ON `wallet_transactions` (`epochDay`)"
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_wallet_transactions_packageName` " +
                "ON `wallet_transactions` (`packageName`)"
        )
    }
}

private val MIGRATION_11_12 = object : Migration(11, 12) {    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `wallet_transactions` ADD COLUMN `source` TEXT NOT NULL DEFAULT 'UNKNOWN'")
        db.execSQL("ALTER TABLE `wallet_transactions` ADD COLUMN `ruleId` INTEGER")
        db.execSQL("ALTER TABLE `wallet_transactions` ADD COLUMN `qualifyingMinutes` INTEGER")
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `earning_app_progress` (" +
                "`ruleId` INTEGER NOT NULL, " +
                "`packageName` TEXT NOT NULL, " +
                "`epochDay` INTEGER NOT NULL, " +
                "`accumulatedMs` INTEGER NOT NULL, " +
                "`rewardedBlocks` INTEGER NOT NULL, " +
                "`updatedAtMs` INTEGER NOT NULL, " +
                "PRIMARY KEY(`ruleId`, `epochDay`))"
        )
    }
}

/**
 * Phase 4: generic app restrictions. Adds the app_restrictions table holding
 * one user-configured daily limit per package, independent of locked_apps
 * tiers and earn_rules.
 */
private val MIGRATION_12_13 = object : Migration(12, 13) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `app_restrictions` (" +
                "`packageName` TEXT NOT NULL, " +
                "`appLabel` TEXT NOT NULL, " +
                "`dailyLimitMinutes` INTEGER NOT NULL, " +
                "`enabled` INTEGER NOT NULL, " +
                "PRIMARY KEY(`packageName`))"
        )
    }
}

private val MIGRATION_13_14 = object : Migration(13, 14) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `earn_rules` ADD COLUMN `label` TEXT NOT NULL DEFAULT ''")
    }
}

@Database(
    entities = [
        Card::class,
        UserCardProgress::class,
        Session::class,
        Wallet::class,
        LockedApp::class,
        UserSettings::class,
        AppUsage::class,
        ChallengeCompletion::class,
        ProtectedDay::class,
        PauseState::class,
        AiInsightEntity::class,
        BankingAllowlistEntry::class,
        EarnRule::class,
        WalletTransaction::class,
        EarningAppProgress::class,
        AppRestriction::class,
    ],
    version = 14,
    exportSchema = false,
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun cardDao(): CardDao
    abstract fun progressDao(): ProgressDao
    abstract fun sessionDao(): SessionDao
    abstract fun walletDao(): WalletDao
    abstract fun lockedAppDao(): LockedAppDao
    abstract fun settingsDao(): SettingsDao
    abstract fun appUsageDao(): AppUsageDao
    abstract fun challengeCompletionDao(): ChallengeCompletionDao
    abstract fun protectedDayDao(): ProtectedDayDao
    abstract fun pauseStateDao(): PauseStateDao
    abstract fun aiInsightDao(): AiInsightDao
    abstract fun bankingAllowlistDao(): BankingAllowlistDao
    abstract fun earnRuleDao(): EarnRuleDao
    abstract fun walletTransactionDao(): WalletTransactionDao
    abstract fun earningAppProgressDao(): EarningAppProgressDao
    abstract fun appRestrictionDao(): AppRestrictionDao

    companion object {
        @Volatile
        private var instance: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "jikan.db",
                )
                    .addMigrations(
                        MIGRATION_2_3,
                        MIGRATION_3_4,
                        MIGRATION_4_5,
                        MIGRATION_5_6,
                        MIGRATION_6_7,
                        MIGRATION_7_8,
                        MIGRATION_8_9,
                        MIGRATION_9_10,
                        MIGRATION_10_11,
                        MIGRATION_11_12,
                        MIGRATION_12_13,
                        MIGRATION_13_14,
                    )
                    .fallbackToDestructiveMigration(dropAllTables = true)
                    .build()
                    .also { instance = it }
            }
    }
}
