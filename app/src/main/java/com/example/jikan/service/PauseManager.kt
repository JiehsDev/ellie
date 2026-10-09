package com.example.jikan.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.example.jikan.MainActivity
import com.example.jikan.R
import com.example.jikan.data.AppDatabase
import com.example.jikan.data.BankingAllowlistEntry
import com.example.jikan.data.PauseState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

object PauseManager {
    private const val CHANNEL_ID = "jikan_pause_channel"
    private const val NOTIFICATION_ID_LOCKING = 2001
    private const val NOTIFICATION_ID_RESUMED = 2002
    private const val NOTIFICATION_ID_BANKING = 2003
    private const val NOTIFICATION_ID_BANKING_REENABLE = 2004
    private const val NOTIFICATION_ID_BANKING_REMINDER = 2005
    const val WORK_NAME = "pause_expiry_work"
    const val BANKING_WORK_NAME = "banking_allowlist_expiry_work"
    const val BANKING_REMINDER_WORK_NAME = "banking_manual_reenable_reminder_work"
    const val ACTION_TOGGLE = "com.example.jikan.ACTION_TOGGLE_PAUSE"
    const val ACTION_DISABLE_ACCESSIBILITY = "com.example.jikan.ACTION_DISABLE_ACCESSIBILITY"

    suspend fun getExpiry(context: Context): Long = withContext(Dispatchers.IO) {
        val db = AppDatabase.getInstance(context)
        db.pauseStateDao().get()?.expiryTimestampMs ?: 0L
    }

    suspend fun isPaused(context: Context): Boolean {
        val expiry = getExpiry(context)
        return PauseStatusCalculator.status(expiry, System.currentTimeMillis()).isPaused
    }

    suspend fun pause(context: Context, durationMinutes: Int = 10) = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val currentExpiry = getExpiry(context)
        val newExpiry = PauseStatusCalculator.nextExpiry(now, currentExpiry, durationMinutes)

        val db = AppDatabase.getInstance(context)
        db.pauseStateDao().upsert(PauseState(expiryTimestampMs = newExpiry))

        val delayMs = newExpiry - now
        val workRequest = OneTimeWorkRequestBuilder<PauseExpiryWorker>()
            .setInitialDelay(delayMs, TimeUnit.MILLISECONDS)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            WORK_NAME,
            androidx.work.ExistingWorkPolicy.REPLACE,
            workRequest,
        )

        showToggleNotification(context, isPaused = true, expiryTimestampMs = newExpiry)
    }

    suspend fun resume(context: Context) = withContext(Dispatchers.IO) {
        val db = AppDatabase.getInstance(context)
        db.pauseStateDao().upsert(PauseState(expiryTimestampMs = 0L))

        WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)

        showToggleNotification(context, isPaused = false)
        showResumedNotification(context)
    }

    suspend fun handleExpiry(context: Context) = withContext(Dispatchers.IO) {
        val db = AppDatabase.getInstance(context)
        db.pauseStateDao().upsert(PauseState(expiryTimestampMs = 0L))

        showToggleNotification(context, isPaused = false)
        showResumedNotification(context)
    }

    suspend fun toggle(context: Context, durationMinutes: Int = 10) {
        if (isPaused(context)) {
            resume(context)
        } else {
            pause(context, durationMinutes)
        }
    }

    suspend fun requestBankingAllowlist(
        context: Context,
        packageName: String,
        appLabel: String,
        durationMinutes: Int,
    ) = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val expiresAt = now + durationMinutes * 60_000L
        val db = AppDatabase.getInstance(context)
        db.bankingAllowlistDao().upsert(
            BankingAllowlistEntry(
                packageName = packageName,
                appLabel = appLabel,
                expiresAtMs = expiresAt,
            )
        )

        val workRequest = OneTimeWorkRequestBuilder<BankingAllowlistWorker>()
            .setInitialDelay(expiresAt - now, TimeUnit.MILLISECONDS)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            BANKING_WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            workRequest,
        )
        showBankingNotification(context, appLabel, expiresAt)
    }

    suspend fun expireBankingAllowlist(context: Context) = withContext(Dispatchers.IO) {
        val db = AppDatabase.getInstance(context)
        db.bankingAllowlistDao().pruneExpired(System.currentTimeMillis())
        cancelBankingNotification(context)
        showBankingEndedNotification(context)
    }

    suspend fun endBankingAllowlist(context: Context, packageName: String? = null) = withContext(Dispatchers.IO) {
        val db = AppDatabase.getInstance(context)
        if (packageName != null) {
            db.bankingAllowlistDao().delete(packageName)
        } else {
            db.bankingAllowlistDao().pruneExpired(Long.MAX_VALUE)
        }
        WorkManager.getInstance(context).cancelUniqueWork(BANKING_WORK_NAME)
        cancelBankingNotification(context)
        showBankingEndedNotification(context)
    }

    suspend fun requestAccessibilityDisable(context: Context, durationMinutes: Int = 10) {
        StrictModeManager.markApprovedAccessibilityDisable(context, durationMinutes)
        showManualReenableNotification(context)
        scheduleBankingReminder(context)
        PauseTileService.requestTileRefresh(context)
        context.sendBroadcast(Intent(ACTION_DISABLE_ACCESSIBILITY).setPackage(context.packageName))
    }

    suspend fun handleBankingReminder(context: Context) = withContext(Dispatchers.IO) {
        val db = AppDatabase.getInstance(context)
        val settings = db.settingsDao().get() ?: return@withContext
        if (settings.bankingModeActive) {
            showBankingReminderNotification(context)
        }
    }

    suspend fun clearBankingModeIfProtectionEnabled(context: Context, protectionEnabled: Boolean) = withContext(Dispatchers.IO) {
        val db = AppDatabase.getInstance(context)
        val settings = db.settingsDao().get() ?: return@withContext
        if (settings.bankingModeActive && protectionEnabled) {
            db.settingsDao().upsert(
                settings.copy(
                    bankingModeActive = false,
                    bankingDisabledUntilMs = 0L,
                    strictGraceUntilMs = 0L,
                )
            )
            WorkManager.getInstance(context).cancelUniqueWork(BANKING_REMINDER_WORK_NAME)
            cancelManualReenableNotification(context)
            showResumedNotification(context)
            PauseTileService.requestTileRefresh(context)
        } else if (settings.bankingModeActive && !protectionEnabled) {
            showManualReenableNotification(context)
            PauseTileService.requestTileRefresh(context)
        }
    }

    suspend fun prepareForAccessibilityDisable(context: Context) = withContext(Dispatchers.IO) {
        val db = AppDatabase.getInstance(context)
        db.pauseStateDao().upsert(PauseState(expiryTimestampMs = 0L))
        WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        cancelLockingNotification(context)
    }

    fun showToggleNotification(context: Context, isPaused: Boolean, expiryTimestampMs: Long = 0L) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        createChannel(notificationManager)

        val toggleIntent = bankingModeIntent(context)
        val togglePendingIntent = PendingIntent.getActivity(
            context,
            0,
            toggleIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val title: String
        val text: String
        val actionLabel: String
        val status = PauseStatusCalculator.status(expiryTimestampMs, System.currentTimeMillis())
        if (isPaused && status.isPaused) {
            val remainingMinutes = status.remainingMinutes
            title = "Locking paused"
            text = "Resumes in ${remainingMinutes}m"
            actionLabel = "Banking mode"
        } else {
            title = "Locking active"
            text = "Use banking mode to turn protection off for apps like GCash."
            actionLabel = "Banking mode"
        }

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(text)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .addAction(0, actionLabel, togglePendingIntent)
            .build()

        safeNotify(notificationManager, NOTIFICATION_ID_LOCKING, notification)
    }

    private fun showResumedNotification(context: Context) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        createChannel(notificationManager)

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Locking resumed")
            .setContentText("App locking is active again.")
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        safeNotify(notificationManager, NOTIFICATION_ID_RESUMED, notification)
    }

    private fun showBankingNotification(context: Context, appLabel: String, expiresAtMs: Long) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        createChannel(notificationManager)
        val remainingMinutes = ((expiresAtMs - System.currentTimeMillis()) / 60_000L).coerceAtLeast(1)
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Banking mode active")
            .setContentText("$appLabel allowed · ${remainingMinutes}m left")
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()
        safeNotify(notificationManager, NOTIFICATION_ID_BANKING, notification)
    }

    private fun showBankingEndedNotification(context: Context) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        createChannel(notificationManager)
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Banking mode ended")
            .setContentText("Locking resumed.")
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        safeNotify(notificationManager, NOTIFICATION_ID_RESUMED, notification)
    }

    private fun showManualReenableNotification(context: Context) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        createChannel(notificationManager)
        val pendingIntent = PendingIntent.getActivity(
            context,
            1,
            openAccessibilitySettingsIntent(),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Banking mode is on")
            .setContentText("Protection is off. Tap, find Jikan in Accessibility, and turn it on.")
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        safeNotify(notificationManager, NOTIFICATION_ID_BANKING_REENABLE, notification)
    }

    private fun showBankingReminderNotification(context: Context) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        createChannel(notificationManager)
        val pendingIntent = PendingIntent.getActivity(
            context,
            3,
            openAccessibilitySettingsIntent(),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Protection is still off")
            .setContentText("Find Jikan in Accessibility and turn it on when banking is done.")
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        safeNotify(notificationManager, NOTIFICATION_ID_BANKING_REMINDER, notification)
    }

    fun openAccessibilitySettings(context: Context) {
        context.startActivity(openAccessibilitySettingsIntent())
    }

    fun openAccessibilitySettingsIntent(): Intent {
        return Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    fun bankingModeIntent(context: Context): Intent {
        return Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
            putExtra(MainActivity.EXTRA_OPEN_BANKING_MODE, true)
        }
    }

    private fun scheduleBankingReminder(context: Context) {
        val workRequest = OneTimeWorkRequestBuilder<BankingFullDisableExpiryWorker>()
            .setInitialDelay(60, TimeUnit.MINUTES)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            BANKING_REMINDER_WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            workRequest,
        )
    }

    private fun cancelLockingNotification(context: Context) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.cancel(NOTIFICATION_ID_LOCKING)
    }

    private fun cancelBankingNotification(context: Context) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.cancel(NOTIFICATION_ID_BANKING)
    }

    private fun cancelManualReenableNotification(context: Context) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.cancel(NOTIFICATION_ID_BANKING_REENABLE)
        notificationManager.cancel(NOTIFICATION_ID_BANKING_REMINDER)
    }

    fun createReminderChannel(notificationManager: NotificationManager) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "jikan_reminder_channel",
                "Jikan Reminders",
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = "Shows gentle reminders for lightly restricted apps"
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun createChannel(notificationManager: NotificationManager) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Jikan Locking",
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = "Shows app locking status and pause controls"
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun safeNotify(
        notificationManager: NotificationManager,
        notificationId: Int,
        notification: android.app.Notification,
    ) {
        try {
            notificationManager.notify(notificationId, notification)
        } catch (exception: SecurityException) {
            Log.w(TAG, "Notification permission denied; pause state still updated", exception)
        }
    }

    private const val TAG = "JikanPause"
}
