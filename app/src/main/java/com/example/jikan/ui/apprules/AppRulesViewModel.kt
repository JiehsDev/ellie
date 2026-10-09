package com.example.jikan.ui.apprules

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.jikan.data.AppDatabase
import com.example.jikan.data.AppRestriction
import com.example.jikan.data.LockedApp
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

data class AppRulesUiState(
    val packageName: String = "",
    val appLabel: String = "",
    val isLocked: Boolean = false,
    val restriction: AppRestriction? = null,
    val dailyCeilingMinutes: Int = 30,
    val minutesUsedToday: Int = 0,
    val walletBalanceMinutes: Int = 0,
    val serviceActive: Boolean = false,
    val earningAppCount: Int = 0,
    val isLoading: Boolean = true,
)

class AppRulesViewModel(application: Application) : AndroidViewModel(application) {
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

    private val packageNameFlow = MutableStateFlow("")

    private val _state = MutableStateFlow(AppRulesUiState())
    val state: StateFlow<AppRulesUiState> = _state.asStateFlow()

    fun load(packageName: String) {
        packageNameFlow.value = packageName
        viewModelScope.launch {
            combine(
                packageNameFlow,
                db.lockedAppDao().observeAll(),
                db.appRestrictionDao().observeAll(),
                db.earnRuleDao().observeAll(),
                db.walletDao().observe(),
            ) { pkg, lockedApps, restrictions, earnRules, wallet ->
                val locked = lockedApps.find { it.packageName == pkg }
                val restriction = restrictions.find { it.packageName == pkg }
                val summary = when (val result = screenTimeRepository.summaryFor()) {
                    is ScreenTimeSummaryResult.Available -> result.summary
                    ScreenTimeSummaryResult.MissingUsageAccess -> null
                }
                val used = summary?.topApps?.find { it.packageName == pkg }?.minutes ?: 0
                val label = locked?.appLabel
                    ?: restriction?.appLabel
                    ?: summary?.topApps?.find { it.packageName == pkg }?.appLabel
                    ?: pkg
                AppRulesUiState(
                    packageName = pkg,
                    appLabel = label,
                    isLocked = locked?.isLocked == true,
                    restriction = restriction,
                    dailyCeilingMinutes = restriction?.dailyLimitMinutes ?: 30,
                    minutesUsedToday = used,
                    walletBalanceMinutes = wallet?.creditBalanceMinutes ?: 0,
                    serviceActive = PermissionStatus.isAccessibilityServiceEnabled(getApplication()),
                    earningAppCount = earnRules.count { it.enabled },
                    isLoading = false,
                )
            }.collect { _state.value = it }
        }
    }

    fun setLocked(locked: Boolean) {
        viewModelScope.launch {
            val pkg = _state.value.packageName
            val label = _state.value.appLabel
            val existing = db.lockedAppDao().get(pkg)
            if (existing != null) {
                db.lockedAppDao().setLocked(pkg, locked)
            } else if (locked) {
                db.lockedAppDao().upsertAll(listOf(LockedApp(pkg, label, true)))
            }
        }
    }

    fun setDailyCeiling(minutes: Int) {
        val clamped = minutes.coerceIn(10, 90)
        _state.update { it.copy(dailyCeilingMinutes = clamped) }
        viewModelScope.launch {
            val pkg = _state.value.packageName
            val label = _state.value.appLabel
            val existing = db.appRestrictionDao().get(pkg)
            val updated = (existing ?: AppRestriction(pkg, label, clamped, true))
                .copy(dailyLimitMinutes = clamped, enabled = true)
            db.appRestrictionDao().upsert(updated)
        }
    }

    fun releaseApp(onDone: () -> Unit) {
        viewModelScope.launch {
            val pkg = _state.value.packageName
            db.lockedAppDao().setLocked(pkg, false)
            db.appRestrictionDao().delete(pkg)
            onDone()
        }
    }
}
