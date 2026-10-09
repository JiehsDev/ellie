package com.example.jikan.service

import android.accessibilityservice.AccessibilityService
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import com.example.jikan.data.AppDatabase
import com.example.jikan.data.AppUsage
import com.example.jikan.data.AppUsageDao
import com.example.jikan.data.LockTier
import com.example.jikan.data.Wallet
import com.example.jikan.data.WalletDao
import com.example.jikan.lock.RedirectGate
import com.example.jikan.screentime.EarningSessionTracker
import com.example.jikan.ui.lock.LockActivity
import com.example.jikan.widget.WidgetUpdater
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneId

/**
 * Detects when a locked app comes to the foreground and, if the wallet can't
 * afford it, redirects to [LockActivity] instead of an overlay (see
 * project-context.md's "App-locking mechanism" section for why).
 *
 * Primary detection is event-driven (TYPE_WINDOW_STATE_CHANGED); a lightweight
 * poll backs that up in case an event is missed (e.g. some OEM accessibility
 * stacks coalesce/drop events under battery optimization).
 */
class AppLockAccessibilityService : AccessibilityService() {
    private val errorHandler = CoroutineExceptionHandler { _, throwable ->
        Log.e(TAG, "Wallet tick failed", throwable)
    }
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate + errorHandler)
    private val gate = RedirectGate()
    private val handler = Handler(Looper.getMainLooper())

    @Volatile private var lockedPackagesMap: Map<String, LockTier> = emptyMap()
    @Volatile private var bankingAllowlistPackages: Set<String> = emptySet()
    @Volatile private var walletBalance: Int = 0
    @Volatile private var isPaused: Boolean = false
    @Volatile private var currentForegroundPackage: String? = null

    private val lastReminderTimeMap = mutableMapOf<String, Long>()
    private val lastForceCloseTimeMap = mutableMapOf<String, Long>()

    private lateinit var walletDao: WalletDao
    private lateinit var appUsageDao: AppUsageDao
    private lateinit var earningSessionTracker: EarningSessionTracker
    private var disableReceiverRegistered = false

    private val disableReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action != PauseManager.ACTION_DISABLE_ACCESSIBILITY) return
            serviceScope.launch {
                PauseManager.prepareForAccessibilityDisable(applicationContext)
                disableSelf()
            }
        }
    }

    private val pollRunnable = object : Runnable {
        override fun run() {
            checkForegroundPackage(rootInActiveWindow?.packageName?.toString())
            handler.postDelayed(this, POLL_INTERVAL_MS)
        }
    }

    private val spendRunnable = object : Runnable {
        override fun run() {
            val foreground = currentForegroundPackage
            if (!isPaused && foreground != null && foreground in lockedPackagesMap.keys && walletBalance > 0) {
                serviceScope.launch {
                    walletDao.spend(SPEND_MINUTES_PER_TICK)
                    recordUsage(foreground, SPEND_MINUTES_PER_TICK)
                    WidgetUpdater.refresh(applicationContext)
                }
            }
            handler.postDelayed(this, SPEND_TICK_INTERVAL_MS)
        }
    }

    private val regenRunnable = object : Runnable {
        override fun run() {
            val foreground = currentForegroundPackage
            if (!isPaused && foreground != null && foreground !in lockedPackagesMap.keys && walletBalance < Wallet.MAX_BALANCE_MINUTES) {
                serviceScope.launch {
                    walletDao.regenerate(REGEN_MINUTES_PER_TICK)
                    WidgetUpdater.refresh(applicationContext)
                }
            }
            handler.postDelayed(this, REGEN_TICK_INTERVAL_MS)
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        val db = AppDatabase.getInstance(applicationContext)
        walletDao = db.walletDao()
        appUsageDao = db.appUsageDao()
        earningSessionTracker = EarningSessionTracker(db)
        registerDisableReceiver()

        db.lockedAppDao().observeLocked()
            .onEach { apps -> lockedPackagesMap = apps.associate { it.packageName to it.tier } }
            .launchIn(serviceScope)

        walletDao.observe()
            .onEach { wallet -> walletBalance = wallet?.creditBalanceMinutes ?: 0 }
            .launchIn(serviceScope)

        db.bankingAllowlistDao().observeActive(System.currentTimeMillis())
            .onEach { entries -> bankingAllowlistPackages = entries.map { it.packageName }.toSet() }
            .launchIn(serviceScope)

        db.pauseStateDao().observe()
            .onEach { state ->
                val expiry = state?.expiryTimestampMs ?: 0L
                isPaused = PauseStatusCalculator.status(expiry, System.currentTimeMillis()).isPaused
                PauseManager.showToggleNotification(applicationContext, isPaused, expiry)
            }
            .launchIn(serviceScope)

        handler.post(pollRunnable)
        handler.postDelayed(spendRunnable, SPEND_TICK_INTERVAL_MS)
        handler.postDelayed(regenRunnable, REGEN_TICK_INTERVAL_MS)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        checkForegroundPackage(event.packageName?.toString())
    }

    private fun checkForegroundPackage(packageName: String?) {
        if (isPaused) return
        if (packageName.isNullOrBlank()) return
        currentForegroundPackage = packageName
        serviceScope.launch(Dispatchers.IO) {
            earningSessionTracker.onForegroundAppChanged(
                packageName = packageName.takeUnless { it == this@AppLockAccessibilityService.packageName },
            )
        }
        if (packageName == this.packageName) return
        if (packageName in bankingAllowlistPackages) return
        val tier = lockedPackagesMap[packageName] ?: return
        if (walletBalance > 0) return
        if (isPaused) return

        serviceScope.launch(Dispatchers.IO) {
            val expiry = AppDatabase.getInstance(applicationContext).pauseStateDao().get()?.expiryTimestampMs ?: 0L
            val paused = PauseStatusCalculator.status(expiry, System.currentTimeMillis()).isPaused
            isPaused = paused
            if (!paused) {
                withContext(Dispatchers.Main.immediate) {
                    handleLockedPackage(packageName, tier)
                }
            }
        }
    }

    private fun handleLockedPackage(packageName: String, tier: LockTier) {
        if (isPaused || walletBalance > 0) return
        val now = System.currentTimeMillis()
        when (tier) {
            LockTier.EXTREME -> {
                when (val decision = gate.evaluate(packageName)) {
                    RedirectGate.Decision.Suppressed -> Unit
                    is RedirectGate.Decision.Redirect -> redirect(packageName, decision.takeABreak)
                }
            }
            LockTier.AVERAGE -> {
                val lastClose = lastForceCloseTimeMap[packageName] ?: 0L
                if (now - lastClose > 3 * 60_000L) {
                    lastForceCloseTimeMap[packageName] = now
                    redirect(packageName, takeABreak = true)
                }
            }
            LockTier.LIGHT -> {
                val lastRemind = lastReminderTimeMap[packageName] ?: 0L
                if (now - lastRemind > 3 * 60_000L) {
                    lastReminderTimeMap[packageName] = now
                    showLightReminderNotification()
                }
            }
        }
    }

    private fun showLightReminderNotification() {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
        PauseManager.createReminderChannel(notificationManager)
        val notification = androidx.core.app.NotificationCompat.Builder(this, "jikan_reminder_channel")
            .setSmallIcon(com.example.jikan.R.drawable.ic_launcher_foreground)
            .setContentTitle("Usage Reminder")
            .setContentText("You've been using restricted apps for a while without credits. Consider taking a break!")
            .setPriority(androidx.core.app.NotificationCompat.PRIORITY_LOW)
            .setAutoCancel(true)
            .build()
        notificationManager.notify(3001, notification)
    }

    private suspend fun recordUsage(packageName: String, minutes: Int) {
        val today = Instant.ofEpochMilli(System.currentTimeMillis())
            .atZone(ZoneId.systemDefault())
            .toLocalDate()
            .toEpochDay()
        appUsageDao.insertIfAbsent(AppUsage(epochDay = today, packageName = packageName, minutes = 0))
        appUsageDao.addMinutes(epochDay = today, packageName = packageName, minutes = minutes)
    }

    private fun redirect(blockedPackage: String, takeABreak: Boolean) {
        val intent = Intent(this, LockActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(LockActivity.EXTRA_BLOCKED_PACKAGE, blockedPackage)
            putExtra(LockActivity.EXTRA_TAKE_A_BREAK, takeABreak)
        }
        startActivity(intent)
    }

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        super.onDestroy()
        if (::earningSessionTracker.isInitialized) {
            runBlocking(Dispatchers.IO) {
                earningSessionTracker.flush()
            }
        }
        unregisterDisableReceiver()
        handler.removeCallbacks(pollRunnable)
        handler.removeCallbacks(spendRunnable)
        handler.removeCallbacks(regenRunnable)
        serviceScope.cancel()
    }

    private fun registerDisableReceiver() {
        if (disableReceiverRegistered) return
        val filter = IntentFilter(PauseManager.ACTION_DISABLE_ACCESSIBILITY)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(disableReceiver, filter, RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            registerReceiver(disableReceiver, filter)
        }
        disableReceiverRegistered = true
    }

    private fun unregisterDisableReceiver() {
        if (!disableReceiverRegistered) return
        unregisterReceiver(disableReceiver)
        disableReceiverRegistered = false
    }

    companion object {
        private const val TAG = "JikanLock"
        private const val POLL_INTERVAL_MS = 400L
        private const val SPEND_TICK_INTERVAL_MS = 60_000L
        private const val SPEND_MINUTES_PER_TICK = 1
        private const val REGEN_TICK_INTERVAL_MS = 10 * 60_000L
        private const val REGEN_MINUTES_PER_TICK = 1
    }
}
