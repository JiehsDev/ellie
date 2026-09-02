package com.example.jikan.ui.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.jikan.data.AppDatabase
import com.example.jikan.data.InstalledAppInfo
import com.example.jikan.data.InstalledAppsProvider
import com.example.jikan.data.LockedApp
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class LockedAppsUiState(
    val apps: List<InstalledAppInfo> = emptyList(),
    val lockedPackages: Set<String> = emptySet(),
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
                _state.update { it.copy(lockedPackages = locked.map { app -> app.packageName }.toSet()) }
            }
        }
    }

    fun toggle(app: InstalledAppInfo) {
        val isNowLocked = app.packageName !in _state.value.lockedPackages
        viewModelScope.launch {
            if (isNowLocked) {
                db.lockedAppDao().upsertAll(listOf(LockedApp(app.packageName, app.label, isLocked = true)))
            } else {
                db.lockedAppDao().setLocked(app.packageName, isLocked = false)
            }
        }
    }
}
