package com.example.jikan.ui.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.jikan.data.AppDatabase
import com.example.jikan.data.InstalledAppInfo
import com.example.jikan.data.InstalledAppsProvider
import com.example.jikan.data.LockedApp
import com.example.jikan.data.LockTier
import com.example.jikan.service.PauseManager
import com.example.jikan.service.StrictModeManager
import com.example.jikan.ui.home.BankingModeStatus
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class LockedAppsUiState(
    val apps: List<InstalledAppInfo> = emptyList(),
    val lockedPackages: Set<String> = emptySet(),
    val lockedAppTiers: Map<String, LockTier> = emptyMap(),
    val bankingModeStatus: BankingModeStatus = BankingModeStatus.Idle,
    val strictModeEnabled: Boolean = false,
    val strictGraceUntilMs: Long = 0L,
    val lastViolationAtMs: Long = 0L,
    val isLoading: Boolean = true,
)

class LockedAppsViewModel(application: Application) : AndroidViewModel(application) {
    private val db = AppDatabase.getInstance(application)

    private val _state = MutableStateFlow(LockedAppsUiState())
    val state: StateFlow<LockedAppsUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val apps = InstalledAppsProvider.listLaunchableApps(getApplication())
            _state.update { it.copy(apps = apps, isLoading = false) }
        }
        viewModelScope.launch {
            db.lockedAppDao().observeLocked().collect { locked ->
                _state.update {
                    it.copy(
                        lockedPackages = locked.map { app -> app.packageName }.toSet(),
                        lockedAppTiers = locked.associate { app -> app.packageName to app.tier },
                    )
                }
            }
        }
        viewModelScope.launch {
            db.settingsDao().observe().collect { settings ->
                _state.update {
                    it.copy(
                        strictModeEnabled = settings?.strictModeEnabled ?: false,
                        strictGraceUntilMs = settings?.strictGraceUntilMs ?: 0L,
                        lastViolationAtMs = settings?.lastViolationAtMs ?: 0L,
                    )
                }
            }
        }
    }

    fun toggle(app: InstalledAppInfo) {
        val isNowLocked = app.packageName !in _state.value.lockedPackages
        viewModelScope.launch {
            if (isNowLocked) {
                db.lockedAppDao().upsertAll(listOf(LockedApp(app.packageName, app.label, isLocked = true, tier = LockTier.EXTREME)))
            } else {
                db.lockedAppDao().setLocked(app.packageName, isLocked = false)
            }
        }
    }

    fun setTier(app: InstalledAppInfo, tier: LockTier) {
        viewModelScope.launch {
            val existing = db.lockedAppDao().get(app.packageName)
            val label = existing?.appLabel ?: app.label
            db.lockedAppDao().upsertAll(listOf(LockedApp(app.packageName, label, isLocked = true, tier = tier)))
        }
    }

    fun disableLockingForBanking() {
        _state.update { it.copy(bankingModeStatus = BankingModeStatus.Selecting) }
    }

    fun startBankingAllowlist(packageName: String, appLabel: String, durationMinutes: Int) {
        startFullDisableBankingMode(durationMinutes)
    }

    fun startFullDisableBankingMode(durationMinutes: Int) {
        _state.update { it.copy(bankingModeStatus = BankingModeStatus.Loading) }
        viewModelScope.launch {
            PauseManager.requestAccessibilityDisable(getApplication(), durationMinutes)
            delay(1_200L)
            _state.update { it.copy(bankingModeStatus = BankingModeStatus.Success) }
        }
    }

    fun dismissBankingModeStatus() {
        _state.update { it.copy(bankingModeStatus = BankingModeStatus.Idle) }
    }

    fun setStrictModeEnabled(enabled: Boolean) {
        viewModelScope.launch {
            StrictModeManager.setStrictModeEnabled(getApplication(), enabled)
        }
    }
}
