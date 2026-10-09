package com.example.jikan.ui.coach

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.jikan.data.AppDatabase
import com.example.jikan.data.Wallet
import com.example.jikan.screentime.AndroidUsageStatsDataSource
import com.example.jikan.screentime.AppLabelResolver
import com.example.jikan.screentime.ScreenTimeRepository
import com.example.jikan.screentime.ScreenTimeSummaryResult
import com.example.jikan.study.CreditCalculator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalTime
import java.time.ZoneId

data class OutflowAlert(
    val packageName: String,
    val appLabel: String,
    val minutesUsed: Int,
    val dailyLimit: Int,
    val percentOfLimit: Int,
)

data class CoachUiState(
    val greeting: String = "",
    val heroMessage: String = "",
    val balanceMinutes: Int = 0,
    val dueCount: Int = 0,
    val estMinutes: Int = 0,
    val lockoutTitle: String = "",
    val lockoutSub: String = "",
    val streakDays: Int = 0,
    val quickRewardMinutes: Int = 0,
    val pacingRewardMinutes: Int = 0,
    val outflowAlert: OutflowAlert? = null,
    val actionItemCount: Int = 0,
    val lastRefreshMs: Long = 0L,
    val isLoading: Boolean = true,
)

class CoachViewModel(application: Application) : AndroidViewModel(application) {
    private val db = AppDatabase.getInstance(application)
    private val zone = ZoneId.systemDefault()
    private val screenTimeRepository by lazy {
        ScreenTimeRepository(
            db = db,
            usageStatsDataSource = AndroidUsageStatsDataSource(getApplication()),
            appLabelResolver = AppLabelResolver(getApplication()),
            zoneId = zone,
        )
    }

    private val _state = MutableStateFlow(CoachUiState())
    val state: StateFlow<CoachUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            combine(
                db.walletDao().observe(),
                db.progressDao().observeDueCount(System.currentTimeMillis()),
                db.lockedAppDao().observeLocked(),
                db.appRestrictionDao().observeAll(),
            ) { wallet, dueCount, lockedApps, restrictions ->
                val summary = when (val result = screenTimeRepository.summaryFor()) {
                    is ScreenTimeSummaryResult.Available -> result.summary
                    ScreenTimeSummaryResult.MissingUsageAccess -> null
                }
                val usageByPackage = (summary?.topApps ?: emptyList())
                    .associate { it.packageName to it }

                // Leisure lockout: first locked app, or top usage app.
                val lockedApp = lockedApps.firstOrNull()
                val topApp = (summary?.topApps ?: emptyList()).firstOrNull()
                val (lockoutTitle, lockoutSub) = when {
                    lockedApp != null -> {
                        val label = lockedApp.appLabel.substringBefore(" ")
                        "$label Locked" to "${topApp?.appLabel ?: "No"}: ${topApp?.minutes ?: 0}m active"
                    }
                    topApp != null -> "${topApp.appLabel}" to "${topApp.minutes}m active"
                    else -> "No lockouts" to "All apps free"
                }

                // Outflow alert: app using >40% of its daily limit.
                val outflow = restrictions.mapNotNull { r ->
                    val used = usageByPackage[r.packageName]?.minutes ?: 0
                    if (r.enabled && r.dailyLimitMinutes > 0 && used > 0) {
                        val pct = used * 100 / r.dailyLimitMinutes
                        if (pct >= 40) OutflowAlert(
                            r.packageName,
                            r.appLabel,
                            used,
                            r.dailyLimitMinutes,
                            pct,
                        ) else null
                    } else null
                }.maxByOrNull { it.percentOfLimit }

                val estMinutes = (dueCount * 30 + 30) / 60
                val pacingReward = CreditCalculator.creditsEarned(
                    correctCount = (dueCount * 0.8).toInt(),
                    totalCount = dueCount,
                )
                val hour = LocalTime.now().hour
                val greeting = when (hour) {
                    in 5..11 -> "Good morning"
                    in 12..17 -> "Good afternoon"
                    else -> "Good evening"
                }
                val balance = wallet?.creditBalanceMinutes ?: 0
                val streak = wallet?.currentStreakDays ?: 0

                val actionCount = listOf(
                    dueCount > 0,
                    outflow != null,
                ).count { it }

                CoachUiState(
                    greeting = greeting,
                    heroMessage = buildHeroMessage(greeting, balance, dueCount),
                    balanceMinutes = balance,
                    dueCount = dueCount,
                    estMinutes = estMinutes,
                    lockoutTitle = lockoutTitle,
                    lockoutSub = lockoutSub,
                    streakDays = streak,
                    quickRewardMinutes = CreditCalculator.creditsEarned(4, 5),
                    pacingRewardMinutes = pacingReward,
                    outflowAlert = outflow,
                    actionItemCount = actionCount,
                    lastRefreshMs = System.currentTimeMillis(),
                    isLoading = false,
                )
            }.collect { _state.value = it }
        }
    }

    private fun buildHeroMessage(greeting: String, balance: Int, due: Int): String {
        return if (due > 0) {
            "$greeting. You have a $balance minute cushion with $due cards due for review."
        } else {
            "$greeting. You have a healthy $balance minute cushion. All caught up on reviews."
        }
    }

    fun refreshText(lastRefreshMs: Long): String {
        if (lastRefreshMs == 0L) return "just now"
        val mins = ((System.currentTimeMillis() - lastRefreshMs) / 60000).toInt()
        return if (mins < 1) "just now" else "$mins min ago"
    }
}
