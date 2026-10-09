package com.example.jikan.ui.limits

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.jikan.data.AppDatabase
import com.example.jikan.data.AppRestriction
import com.example.jikan.data.EarnRule
import com.example.jikan.onboarding.PermissionStatus
import com.example.jikan.screentime.AndroidUsageStatsDataSource
import com.example.jikan.screentime.AppLabelResolver
import com.example.jikan.screentime.ScreenTimeRepository
import com.example.jikan.screentime.ScreenTimeSummaryResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.ZoneId

enum class LimitsTab { ALL, LOCKED, EARNING, EXEMPT }

data class RestrictedAppRow(
    val restriction: AppRestriction,
    val minutesUsedToday: Int,
    val isLockedApp: Boolean,
)

data class LimitsMainUiState(
    val selectedTab: LimitsTab = LimitsTab.ALL,
    val protectionActive: Boolean = false,
    val lockedAppCount: Int = 0,
    val restrictedApps: List<RestrictedAppRow> = emptyList(),
    val earningApps: List<EarnRule> = emptyList(),
    val exemptApps: List<String> = emptyList(),
    val walletBalanceMinutes: Int = 0,
    val coachMessage: String = "",
    val isLoading: Boolean = true,
)

class LimitsMainViewModel(application: Application) : AndroidViewModel(application) {
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

    private val _state = MutableStateFlow(LimitsMainUiState())
    val state: StateFlow<LimitsMainUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            combine(
                db.appRestrictionDao().observeAll(),
                db.earnRuleDao().observeAll(),
                db.lockedAppDao().observeLocked(),
                db.bankingAllowlistDao().observeActive(System.currentTimeMillis()),
                db.walletDao().observe(),
            ) { restrictions, earnRules, lockedApps, allowlist, wallet ->
                val summary = when (val result = screenTimeRepository.summaryFor()) {
                    is ScreenTimeSummaryResult.Available -> result.summary
                    ScreenTimeSummaryResult.MissingUsageAccess -> null
                }
                val usageByPackage = (summary?.topApps ?: emptyList())
                    .associate { it.packageName to it.minutes }
                val lockedPackages = lockedApps.map { it.packageName }.toSet()

                val rows = restrictions.map { r ->
                    RestrictedAppRow(
                        restriction = r,
                        minutesUsedToday = usageByPackage[r.packageName] ?: 0,
                        isLockedApp = r.packageName in lockedPackages,
                    )
                }.sortedByDescending { it.minutesUsedToday }

                val earningActive = earnRules.filter { it.enabled }
                val earnRate = earningActive.firstOrNull()?.let {
                    "${it.requiredMinutes}m practice → +${it.rewardMinutes}m credit"
                } ?: "No earning apps configured"

                Quint(
                    rows,
                    earningActive,
                    allowlist.map { it.packageName },
                    wallet?.creditBalanceMinutes ?: 0,
                    lockedApps.size,
                    earnRate,
                )
            }.collect { (rows, earning, exempt, balance, lockedCount, earnRate) ->
                val protectionActive = PermissionStatus.isAccessibilityServiceEnabled(getApplication())
                _state.update {
                    it.copy(
                        protectionActive = protectionActive,
                        lockedAppCount = lockedCount,
                        restrictedApps = rows,
                        earningApps = earning,
                        exemptApps = exempt,
                        walletBalanceMinutes = balance,
                        coachMessage = "${lockedCount} apps locked under daily study balance. " +
                            "Earning currently: $earnRate per session.",
                        isLoading = false,
                    )
                }
            }
        }
    }

    fun selectTab(tab: LimitsTab) {
        _state.update { it.copy(selectedTab = tab) }
    }

    fun setRestrictionEnabled(packageName: String, enabled: Boolean) {
        viewModelScope.launch {
            val current = db.appRestrictionDao().get(packageName) ?: return@launch
            db.appRestrictionDao().upsert(current.copy(enabled = enabled))
        }
    }

    private data class Quint<A, B, C, D, E, F>(
        val first: A,
        val second: B,
        val third: C,
        val fourth: D,
        val fifth: E,
        val sixth: F,
    )
}
