package com.example.jikan.ui.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.jikan.data.AppDatabase
import com.example.jikan.data.AppRestriction
import com.example.jikan.data.InstalledAppInfo
import com.example.jikan.data.InstalledAppsProvider
import com.example.jikan.screentime.AndroidUsageStatsDataSource
import com.example.jikan.screentime.AppUsageAggregator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId

/**
 * Phase 10: the editor state for one restriction. `isNew` is true when no
 * restriction exists yet for the app.
 */
data class RestrictionEditorState(
    val packageName: String,
    val appLabel: String,
    val isNew: Boolean,
    val limitText: String = "",
    val enabled: Boolean = true,
    val error: String? = null,
)

data class RestrictedAppsUiState(
    val apps: List<InstalledAppInfo> = emptyList(),
    val restrictions: Map<String, AppRestriction> = emptyMap(),
    val usageToday: Map<String, Int> = emptyMap(),
    val hasUsageAccess: Boolean = false,
    val editor: RestrictionEditorState? = null,
    val isLoading: Boolean = true,
)

class RestrictedAppsViewModel(application: Application) : AndroidViewModel(application) {
    private val db = AppDatabase.getInstance(application)
    private val usageStatsDataSource = AndroidUsageStatsDataSource(getApplication())

    private val _state = MutableStateFlow(RestrictedAppsUiState())
    val state: StateFlow<RestrictedAppsUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val apps = InstalledAppsProvider.listLaunchableApps(getApplication())
            _state.update { it.copy(apps = apps, isLoading = false) }
        }
        viewModelScope.launch {
            db.appRestrictionDao().observeAll().collect { restrictions ->
                _state.update {
                    it.copy(restrictions = restrictions.associateBy { r -> r.packageName })
                }
                refreshUsage(restrictions.map { it.packageName })
            }
        }
    }

    /** Today's foreground minutes per restricted package, from the same source enforcement uses. */
    private suspend fun refreshUsage(packageNames: List<String>) {
        if (!usageStatsDataSource.hasUsageAccess()) {
            _state.update { it.copy(hasUsageAccess = false, usageToday = emptyMap()) }
            return
        }
        val zone = ZoneId.systemDefault()
        val now = System.currentTimeMillis()
        val startOfDayMs = LocalDate.now(zone).atStartOfDay(zone).toInstant().toEpochMilli()
        val wanted = packageNames.toSet()
        val usage = usageStatsDataSource.queryUsage(startOfDayMs, now)
            .filter { it.packageName in wanted }
            .associate { it.packageName to AppUsageAggregator.minutesFromMs(it.foregroundMs) }
        _state.update { it.copy(hasUsageAccess = true, usageToday = usage) }
    }

    /** Opens the editor: prefilled for an existing restriction, blank for a new one. */
    fun openEditor(app: InstalledAppInfo) {
        val restriction = _state.value.restrictions[app.packageName]
        _state.update {
            it.copy(
                editor = RestrictionEditorState(
                    packageName = app.packageName,
                    appLabel = app.label,
                    isNew = restriction == null,
                    limitText = restriction?.dailyLimitMinutes?.toString().orEmpty(),
                    enabled = restriction?.enabled ?: true,
                ),
            )
        }
    }

    fun updateEditor(editor: RestrictionEditorState) {
        _state.update { it.copy(editor = editor.copy(error = null)) }
    }

    fun dismissEditor() {
        _state.update { it.copy(editor = null) }
    }

    fun saveEditor() {
        val editor = _state.value.editor ?: return
        val error = validateDailyLimit(editor.limitText)
        if (error != null) {
            _state.update { it.copy(editor = editor.copy(error = error)) }
            return
        }
        val restriction = AppRestriction(
            packageName = editor.packageName,
            appLabel = editor.appLabel,
            dailyLimitMinutes = editor.limitText.toInt(),
            enabled = editor.enabled,
        )
        viewModelScope.launch {
            // REPLACE on the packageName primary key keeps one row per app.
            // Enforcement reads this table; it is never decided by AI.
            db.appRestrictionDao().upsert(restriction)
            _state.update { it.copy(editor = null) }
        }
    }

    fun deleteEditingRestriction() {
        val packageName = _state.value.editor?.packageName ?: return
        viewModelScope.launch {
            db.appRestrictionDao().delete(packageName)
            _state.update { it.copy(editor = null) }
        }
    }

    fun setRestrictionEnabled(restriction: AppRestriction, enabled: Boolean) {
        viewModelScope.launch {
            db.appRestrictionDao().upsert(restriction.copy(enabled = enabled))
        }
    }
}
