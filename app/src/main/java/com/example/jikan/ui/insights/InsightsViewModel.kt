package com.example.jikan.ui.insights

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.jikan.data.AppDatabase
import com.example.jikan.onboarding.PermissionStatus
import com.example.jikan.screentime.AndroidUsageStatsDataSource
import com.example.jikan.screentime.AppLabelResolver
import com.example.jikan.screentime.ScreenTimeRepository
import com.example.jikan.screentime.ScreenTimeSummary
import com.example.jikan.screentime.ScreenTimeSummaryResult
import com.example.jikan.ui.home.CoachInput
import com.example.jikan.ui.home.HomeCoachMessageProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/**
 * Phase 11: one earning app's contribution today, derived from the engine's
 * own progress rows — deterministic, no AI involved.
 */
data class EarningContribution(
    val packageName: String,
    val appLabel: String,
    val earnedMinutes: Int,
)

data class InsightsUiState(
    val summary: ScreenTimeSummary? = null,
    val earningContributions: List<EarningContribution> = emptyList(),
    val coachMessage: String = "",
    val hasUsageAccess: Boolean = false,
    val isLoading: Boolean = true,
)

class InsightsViewModel(application: Application) : AndroidViewModel(application) {
    private val db = AppDatabase.getInstance(application)
    private val zone: ZoneId = ZoneId.systemDefault()

    private val repository by lazy {
        ScreenTimeRepository(
            db = db,
            usageStatsDataSource = AndroidUsageStatsDataSource(getApplication()),
            appLabelResolver = AppLabelResolver(getApplication()),
            zoneId = zone,
        )
    }

    private val _state = MutableStateFlow(InsightsUiState())
    val state: StateFlow<InsightsUiState> = _state.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            val protectionOn = PermissionStatus.isAccessibilityServiceEnabled(getApplication())
            when (val result = repository.summaryFor()) {
                is ScreenTimeSummaryResult.Available -> {
                    val summary = result.summary
                    _state.update {
                        it.copy(
                            summary = summary,
                            earningContributions = earningContributions(),
                            hasUsageAccess = true,
                            isLoading = false,
                            coachMessage = coachObservation(summary, protectionOn),
                        )
                    }
                }
                ScreenTimeSummaryResult.MissingUsageAccess -> {
                    _state.update { it.copy(hasUsageAccess = false, isLoading = false) }
                }
            }
        }
    }

    /**
     * Which earning-rule apps generated minutes today. Joins the engine's
     * progress rows with their rules — the same data the wallet used.
     */
    private suspend fun earningContributions(): List<EarningContribution> {
        val today = LocalDate.now(zone).toEpochDay()
        val rules = db.earnRuleDao().observeAll().first()
        if (rules.isEmpty()) return emptyList()
        val labels = AppLabelResolver(getApplication()).labelsFor(rules.map { it.packageName })
        return rules.mapNotNull { rule ->
            val progress = db.earningAppProgressDao().get(rule.id, today) ?: return@mapNotNull null
            val earned = progress.rewardedBlocks * rule.rewardMinutes
            if (earned <= 0) null else EarningContribution(
                packageName = rule.packageName,
                appLabel = labels[rule.packageName] ?: rule.packageName,
                earnedMinutes = earned,
            )
        }.sortedByDescending { it.earnedMinutes }
    }

    /**
     * The coach observation: the same deterministic provider Home uses, fed
     * the same structured facts. The model is never asked for analytics.
     */
    private fun coachObservation(summary: ScreenTimeSummary, protectionOn: Boolean): String {
        return HomeCoachMessageProvider.messageFor(
            CoachInput(
                userName = "",
                walletBalanceMinutes = summary.walletBalanceMinutes,
                streakDays = 0,
                dueCount = 0,
                studiedTodayMinutes = 0,
                studiedYesterdayMinutes = 0,
                protectionOn = protectionOn,
                bankingModeActive = false,
                hourOfDay = LocalTime.now(zone).hour,
                recentEvent = HomeCoachMessageProvider.screenTimeEventFor(summary),
                screenTime = summary,
            ),
        )
    }
}
