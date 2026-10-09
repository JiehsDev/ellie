package com.example.jikan.ui.allowance

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.jikan.data.AppDatabase
import com.example.jikan.data.Wallet
import com.example.jikan.data.WalletTransaction
import com.example.jikan.data.WalletTransactionType
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
import java.time.ZoneId

data class OutflowApp(
    val packageName: String,
    val appLabel: String,
    val minutesUsed: Int,
    val isActive: Boolean,
)

data class AllowanceUiState(
    val balanceMinutes: Int = 0,
    val earnedTodayMinutes: Int = 0,
    val spentTodayMinutes: Int = 0,
    val dueCount: Int = 0,
    val estRewardMinutes: Int = 0,
    val quickRewardMinutes: Int = 0,
    val outflowApps: List<OutflowApp> = emptyList(),
    val transactionsToday: List<WalletTransaction> = emptyList(),
    val transactionCount7d: Int = 0,
    val coachMessage: String = "",
    val isLoading: Boolean = true,
)

class AllowanceViewModel(application: Application) : AndroidViewModel(application) {
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

    private val _state = MutableStateFlow(AllowanceUiState())
    val state: StateFlow<AllowanceUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            combine(
                db.walletDao().observe(),
                db.walletTransactionDao().observeAll(),
                db.progressDao().observeDueCount(System.currentTimeMillis()),
            ) { wallet, transactions, dueCount ->
                Triple(wallet, transactions, dueCount)
            }.collect { (wallet, transactions, dueCount) ->
                val summary = when (val result = screenTimeRepository.summaryFor()) {
                    is ScreenTimeSummaryResult.Available -> result.summary
                    ScreenTimeSummaryResult.MissingUsageAccess -> null
                }
                val today = java.time.Instant.now().atZone(zone).toLocalDate().toEpochDay()
                val todayTx = transactions.filter { it.epochDay == today }
                    .sortedByDescending { it.createdAtMs }
                val weekTx = transactions.filter { it.epochDay >= today - 6 }

                val earnedToday = todayTx
                    .filter { it.type == WalletTransactionType.EARN }
                    .sumOf { it.minutes }
                val spentToday = (summary?.totalScreenTimeMinutes ?: 0) -
                    (summary?.earningAppMinutes ?: 0)

                val outflow = (summary?.topApps ?: emptyList())
                    .filter { app -> summary?.earningApps?.none { it.packageName == app.packageName } ?: true }
                    .take(3)
                    .map { app ->
                        OutflowApp(
                            packageName = app.packageName,
                            appLabel = app.appLabel,
                            minutesUsed = app.minutes,
                            isActive = app.minutes > 0,
                        )
                    }

                val estReward = CreditCalculator.creditsEarned(
                    correctCount = (dueCount * 0.8).toInt(),
                    totalCount = dueCount,
                )

                _state.update {
                    it.copy(
                        balanceMinutes = wallet?.creditBalanceMinutes ?: 0,
                        earnedTodayMinutes = earnedToday,
                        spentTodayMinutes = spentToday.coerceAtLeast(0),
                        dueCount = dueCount,
                        estRewardMinutes = estReward,
                        quickRewardMinutes = CreditCalculator.creditsEarned(4, 5),
                        outflowApps = outflow,
                        transactionsToday = todayTx,
                        transactionCount7d = weekTx.size,
                        coachMessage = buildCoachMessage(
                            wallet?.creditBalanceMinutes ?: 0,
                            dueCount,
                            estReward,
                        ),
                        isLoading = false,
                    )
                }
            }
        }
    }

    private fun buildCoachMessage(balance: Int, due: Int, reward: Int): String {
        return if (due == 0) {
            "Your balance covers tonight's leisure apps. All caught up on reviews."
        } else {
            "Your balance covers tonight's leisure apps. " +
                "Clear $due more flashcards to earn +${reward}m."
        }
    }
}

/** Max balance is the daily cap. */
val DAILY_CAP_MINUTES = Wallet.MAX_BALANCE_MINUTES
