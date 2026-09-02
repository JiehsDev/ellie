package com.example.jikan.service

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import com.example.jikan.data.AppDatabase
import com.example.jikan.data.AppUsage
import com.example.jikan.data.AppUsageDao
import com.example.jikan.data.Wallet
import com.example.jikan.data.WalletDao
import com.example.jikan.lock.RedirectGate
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
    /**
     * A throw inside a tick would otherwise reach the main thread's uncaught handler
     * and take the whole locking service down silently — the app keeps looking fine
     * while nothing is being locked. Log and keep ticking instead.
     */
    private val errorHandler = CoroutineExceptionHandler { _, throwable ->
        Log.e(TAG, "Wallet tick failed", throwable)
    }
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate + errorHandler)
    private val gate = RedirectGate()
    private val handler = Handler(Looper.getMainLooper())

    @Volatile private var lockedPackages: Set<String> = emptySet()
    @Volatile private var walletBalance: Int = 0
    @Volatile private var currentForegroundPackage: String? = null

    private lateinit var walletDao: WalletDao
    private lateinit var appUsageDao: AppUsageDao

    private val pollRunnable = object : Runnable {
        override fun run() {
            checkForegroundPackage(rootInActiveWindow?.packageName?.toString())
            handler.postDelayed(this, POLL_INTERVAL_MS)
        }
    }

    private val spendRunnable = object : Runnable {
        override fun run() {
            val foreground = currentForegroundPackage
            if (foreground != null && foreground in lockedPackages && walletBalance > 0) {
                serviceScope.launch {
                    walletDao.spend(SPEND_MINUTES_PER_TICK)
                    recordUsage(foreground, SPEND_MINUTES_PER_TICK)
                    WidgetUpdater.refresh(applicationContext)
                }
            }
            handler.postDelayed(this, SPEND_TICK_INTERVAL_MS)
        }
    }

    /**
     * Slow passive trickle so staying out of locked apps is worth something on its
     * own, without making it a substitute for studying — the rate is intentionally
     * much slower than what a single lesson earns (CreditCalculator).
     */
    private val regenRunnable = object : Runnable {
        override fun run() {
            val foreground = currentForegroundPackage
            if (foreground != null && foreground !in lockedPackages && walletBalance < Wallet.MAX_BALANCE_MINUTES) {
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

        db.lockedAppDao().observeLocked()
            .onEach { apps -> lockedPackages = apps.map { it.packageName }.toSet() }
            .launchIn(serviceScope)

        walletDao.observe()
            .onEach { wallet -> walletBalance = wallet?.creditBalanceMinutes ?: 0 }
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
        if (packageName.isNullOrBlank()) return
        currentForegroundPackage = packageName
        if (packageName == this.packageName) return
        if (packageName !in lockedPackages) return
        if (walletBalance > 0) return

        when (val decision = gate.evaluate(packageName)) {
            RedirectGate.Decision.Suppressed -> Unit
            is RedirectGate.Decision.Redirect -> redirect(packageName, decision.takeABreak)
        }
    }

    /** Attributes spent minutes to the app they were spent in, for Home's "spent today". */
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
        handler.removeCallbacks(pollRunnable)
        handler.removeCallbacks(spendRunnable)
        handler.removeCallbacks(regenRunnable)
        serviceScope.cancel()
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
