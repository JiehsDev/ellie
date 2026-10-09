package com.example.jikan.service

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.example.jikan.data.AppDatabase
import com.example.jikan.data.UserSettings
import com.example.jikan.data.Wallet
import com.example.jikan.onboarding.PermissionStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

object StrictModeManager {
    private const val WATCHDOG_WORK_NAME = "protection_watchdog_work"
    private const val GRACE_MS = 30 * 60_000L
    private const val FORFEIT_FRACTION = 0.5
    private const val MIN_FORFEIT_MINUTES = 5

    suspend fun reconcile(context: Context, nowMs: Long = System.currentTimeMillis()) = withContext(Dispatchers.IO) {
        val db = AppDatabase.getInstance(context)
        val settings = db.settingsDao().get() ?: UserSettings()
        if (!settings.strictModeEnabled) return@withContext

        val enabled = PermissionStatus.isAccessibilityServiceEnabled(context)
        when {
            enabled -> clearGrace(db, settings)
            settings.bankingModeActive -> Unit
            nowMs < settings.bankingDisabledUntilMs -> Unit
            settings.strictGraceUntilMs == 0L -> startGrace(db, settings, nowMs)
            nowMs < settings.strictGraceUntilMs -> Unit
            settings.lastViolationAtMs < settings.strictGraceUntilMs -> applyPenalty(db, settings, nowMs)
        }
    }

    fun scheduleWatchdog(context: Context) {
        val request = PeriodicWorkRequestBuilder<ProtectionWatchdogWorker>(15, TimeUnit.MINUTES).build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            WATCHDOG_WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            request,
        )
    }

    suspend fun setStrictModeEnabled(context: Context, enabled: Boolean) = withContext(Dispatchers.IO) {
        val db = AppDatabase.getInstance(context)
        val settings = db.settingsDao().get() ?: UserSettings()
        db.settingsDao().upsert(
            settings.copy(
                strictModeEnabled = enabled,
                strictGraceUntilMs = if (enabled) settings.strictGraceUntilMs else 0L,
            )
        )
        if (enabled) scheduleWatchdog(context)
    }

    suspend fun markApprovedAccessibilityDisable(context: Context, durationMinutes: Int) = withContext(Dispatchers.IO) {
        val db = AppDatabase.getInstance(context)
        val settings = db.settingsDao().get() ?: UserSettings()
        db.settingsDao().upsert(
            settings.copy(
                bankingDisabledUntilMs = System.currentTimeMillis() + durationMinutes * 60_000L,
                bankingModeActive = true,
                strictGraceUntilMs = 0L,
            )
        )
    }

    private suspend fun startGrace(db: AppDatabase, settings: UserSettings, nowMs: Long) {
        db.settingsDao().upsert(settings.copy(strictGraceUntilMs = nowMs + GRACE_MS))
    }

    private suspend fun clearGrace(db: AppDatabase, settings: UserSettings) {
        if (settings.strictGraceUntilMs != 0L || settings.bankingDisabledUntilMs != 0L) {
            db.settingsDao().upsert(settings.copy(strictGraceUntilMs = 0L, bankingDisabledUntilMs = 0L))
        }
    }

    private suspend fun applyPenalty(db: AppDatabase, settings: UserSettings, nowMs: Long) {
        val wallet = db.walletDao().get() ?: Wallet()
        if (wallet.creditBalanceMinutes > 0) {
            val fractional = (wallet.creditBalanceMinutes * FORFEIT_FRACTION).toInt()
            val forfeit = fractional.coerceAtLeast(MIN_FORFEIT_MINUTES).coerceAtMost(wallet.creditBalanceMinutes)
            db.walletDao().spend(forfeit)
        }
        db.settingsDao().upsert(settings.copy(lastViolationAtMs = nowMs))
    }
}
