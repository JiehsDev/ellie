package com.example.jikan.ui.earning.setup

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.jikan.data.AppDatabase
import com.example.jikan.data.EarnRule
import com.example.jikan.data.InstalledAppInfo
import com.example.jikan.data.InstalledAppsProvider
import com.example.jikan.screentime.AndroidUsageStatsDataSource
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class EarningSetupUiState(
    val ruleId: Long? = null,
    val packageName: String = "",
    val appLabel: String = "",
    val label: String = "",
    val requiredMinutes: Int = 2,
    val rewardMinutes: Int = 5,
    val dailyLimitMinutes: Int = 10,
    val enabled: Boolean = true,
    val installedApps: List<InstalledAppInfo> = emptyList(),
    val showAppPicker: Boolean = false,
    val usagePermissionActive: Boolean = false,
    val isLoading: Boolean = true,
)

class EarningSetupViewModel(application: Application) : AndroidViewModel(application) {
    private val db = AppDatabase.getInstance(application)

    private val _state = MutableStateFlow(EarningSetupUiState())
    val state: StateFlow<EarningSetupUiState> = _state.asStateFlow()

    fun load(ruleId: Long?, packageName: String?, appLabel: String?) {
        viewModelScope.launch {
            val apps = InstalledAppsProvider.listLaunchableApps(getApplication())
            val usageActive = AndroidUsageStatsDataSource(getApplication()).hasUsageAccess()
            if (ruleId != null && ruleId != 0L) {
                val existing = db.earnRuleDao().observeAll().first()
                    .find { it.id == ruleId }
                _state.update {
                    it.copy(
                        ruleId = ruleId,
                        packageName = existing?.packageName ?: "",
                        appLabel = appLabel
                            ?: apps.find { a -> a.packageName == existing?.packageName }?.label
                            ?: existing?.packageName ?: "",
                        label = existing?.label ?: "",
                        requiredMinutes = existing?.requiredMinutes ?: 2,
                        rewardMinutes = existing?.rewardMinutes ?: 5,
                        dailyLimitMinutes = existing?.dailyLimitMinutes ?: 10,
                        enabled = existing?.enabled ?: true,
                        installedApps = apps,
                        usagePermissionActive = usageActive,
                        isLoading = false,
                    )
                }
            } else {
                _state.update {
                    it.copy(
                        ruleId = null,
                        packageName = packageName ?: "",
                        appLabel = appLabel ?: "",
                        installedApps = apps,
                        usagePermissionActive = usageActive,
                        isLoading = false,
                    )
                }
            }
        }
    }

    fun setLabel(label: String) = _state.update { it.copy(label = label) }
    fun setRequired(minutes: Int) =
        _state.update { it.copy(requiredMinutes = minutes.coerceIn(1, 120)) }
    fun setReward(minutes: Int) =
        _state.update { it.copy(rewardMinutes = minutes.coerceIn(1, 120)) }
    fun setDailyLimit(minutes: Int) =
        _state.update { it.copy(dailyLimitMinutes = minutes.coerceIn(1, 240)) }

    fun showAppPicker(show: Boolean) = _state.update { it.copy(showAppPicker = show) }

    fun selectApp(app: InstalledAppInfo) {
        _state.update {
            it.copy(
                packageName = app.packageName,
                appLabel = app.label,
                showAppPicker = false,
            )
        }
    }

    /** Multiplier like 2.5x. */
    fun multiplier(state: EarningSetupUiState): Double =
        state.rewardMinutes.toDouble() / state.requiredMinutes.coerceAtLeast(1).toDouble()

    fun save(onDone: () -> Unit) {
        viewModelScope.launch {
            val s = _state.value
            if (s.packageName.isBlank()) return@launch
            val rule = EarnRule(
                id = s.ruleId ?: 0L,
                packageName = s.packageName,
                requiredMinutes = s.requiredMinutes,
                rewardMinutes = s.rewardMinutes,
                dailyLimitMinutes = s.dailyLimitMinutes,
                enabled = s.enabled,
                label = s.label.ifBlank { s.appLabel },
            )
            db.earnRuleDao().upsert(rule)
            onDone()
        }
    }

    fun delete(onDone: () -> Unit) {
        viewModelScope.launch {
            _state.value.ruleId?.let { db.earnRuleDao().deleteById(it) }
            onDone()
        }
    }
}
