package com.example.jikan.onboarding

import android.app.Application
import android.os.Build
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.jikan.data.AppDatabase
import com.example.jikan.data.DailyGoalPreset
import com.example.jikan.data.InstalledAppInfo
import com.example.jikan.data.InstalledAppsProvider
import com.example.jikan.data.LockedApp
import com.example.jikan.data.UserSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class OnboardingViewModel(application: Application) : AndroidViewModel(application) {
    private val db = AppDatabase.getInstance(application)

    private val _state = MutableStateFlow(OnboardingUiState(manufacturer = Build.MANUFACTURER))
    val state: StateFlow<OnboardingUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val apps = InstalledAppsProvider.listLaunchableApps(getApplication())
            _state.update { it.copy(installedApps = apps, isLoadingApps = false) }
        }
        refreshPermissionStatus()
    }

    fun refreshPermissionStatus() {
        val context = getApplication<Application>()
        _state.update {
            it.copy(
                accessibilityGranted = PermissionStatus.isAccessibilityServiceEnabled(context),
                batteryExemptionGranted = PermissionStatus.isIgnoringBatteryOptimizations(context),
            )
        }
    }

    fun toggleApp(app: InstalledAppInfo) {
        val isNowSelected = app.packageName !in _state.value.selectedPackages
        _state.update {
            val updated = if (isNowSelected) it.selectedPackages + app.packageName else it.selectedPackages - app.packageName
            it.copy(selectedPackages = updated)
        }
        viewModelScope.launch {
            if (isNowSelected) {
                db.lockedAppDao().upsertAll(listOf(LockedApp(app.packageName, app.label, isLocked = true)))
            } else {
                db.lockedAppDao().setLocked(app.packageName, isLocked = false)
            }
        }
    }

    fun onNameChanged(name: String) {
        _state.update { it.copy(userName = name) }
        persistSettings()
    }

    fun onSessionLengthChanged(minutes: Int) {
        _state.update { it.copy(sessionLengthMinutes = minutes) }
        persistSettings()
    }

    fun onDailyGoalSelected(preset: DailyGoalPreset) {
        _state.update { it.copy(dailyGoalPreset = preset) }
        persistSettings()
    }

    private fun persistSettings() {
        val current = _state.value
        viewModelScope.launch {
            db.settingsDao().upsert(
                UserSettings(
                    userName = current.userName,
                    sessionLengthMinutes = current.sessionLengthMinutes,
                    dailyGoalPreset = current.dailyGoalPreset,
                    onboardingCompleted = false,
                )
            )
        }
    }

    fun goNext() {
        val steps = OnboardingStep.entries
        val nextIndex = (_state.value.stepIndex + 1).coerceAtMost(steps.lastIndex)
        _state.update { it.copy(step = steps[nextIndex]) }
    }

    fun goBack() {
        val steps = OnboardingStep.entries
        val prevIndex = (_state.value.stepIndex - 1).coerceAtLeast(0)
        _state.update { it.copy(step = steps[prevIndex]) }
    }

    fun completeOnboarding(onDone: () -> Unit) {
        val current = _state.value
        viewModelScope.launch {
            db.settingsDao().upsert(
                UserSettings(
                    userName = current.userName,
                    sessionLengthMinutes = current.sessionLengthMinutes,
                    dailyGoalPreset = current.dailyGoalPreset,
                    onboardingCompleted = true,
                )
            )
            onDone()
        }
    }
}
