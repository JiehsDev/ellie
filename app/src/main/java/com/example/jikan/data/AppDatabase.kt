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

@Database(
    entities = [
        Card::class,
        UserCardProgress::class,
        Session::class,
        Wallet::class,
        LockedApp::class,
        UserSettings::class,
        AppUsage::class,
    ],
    version = 3,
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
                    .addMigrations(MIGRATION_2_3)
                    .fallbackToDestructiveMigration(dropAllTables = true)
                    .build()
                    .also { instance = it }
            }
    }
}
