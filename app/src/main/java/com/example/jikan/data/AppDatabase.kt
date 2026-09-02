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
                "PRIMARY KEY(`id`))"
        )
        db.execSQL(
            "INSERT INTO `wallet_new` (`id`, `creditBalanceMinutes`, `lifetimeCreditsEarned`) " +
                "SELECT `id`, `creditBalanceMinutes`, `lifetimeCreditsEarned` FROM `wallet`"
        )
        db.execSQL("DROP TABLE `wallet`")
        db.execSQL("ALTER TABLE `wallet_new` RENAME TO `wallet`")
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
    ],
    version = 4,
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
                    .addMigrations(MIGRATION_2_3, MIGRATION_3_4)
                    .fallbackToDestructiveMigration(dropAllTables = true)
                    .build()
                    .also { instance = it }
            }
    }
}
