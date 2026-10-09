package com.example.jikan.ui.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.jikan.data.AppDatabase
import com.example.jikan.data.EarnRule
import com.example.jikan.data.InstalledAppInfo
import com.example.jikan.data.InstalledAppsProvider
import com.example.jikan.screentime.EarnRuleValidator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate

/**
 * Phase 9: the editor state for one earning rule. `ruleId` is null when
 * creating a new rule. Text fields stay as strings until save.
 */
data class EarningEditorState(
    val packageName: String,
    val appLabel: String,
    val ruleId: Long? = null,
    val requiredText: String = "",
    val rewardText: String = "",
    val dailyText: String = "",
    val enabled: Boolean = true,
    val error: String? = null,
)

data class EarningAppsUiState(
    val apps: List<InstalledAppInfo> = emptyList(),
    val rules: Map<String, EarnRule> = emptyMap(),
    val todayProgress: Map<Long, EarningRuleProgress> = emptyMap(),
    val editor: EarningEditorState? = null,
    val isLoading: Boolean = true,
)

class EarningAppsViewModel(application: Application) : AndroidViewModel(application) {
    private val db = AppDatabase.getInstance(application)

    private val _state = MutableStateFlow(EarningAppsUiState())
    val state: StateFlow<EarningAppsUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val apps = InstalledAppsProvider.listLaunchableApps(getApplication())
            _state.update { it.copy(apps = apps, isLoading = false) }
        }
        viewModelScope.launch {
            db.earnRuleDao().observeAll().collect { rules ->
                val today = LocalDate.now().toEpochDay()
                val progress = rules.mapNotNull { rule ->
                    if (rule.id == 0L) {
                        null
                    } else {
                        val row = db.earningAppProgressDao().get(rule.id, today)
                        rule.id to rule.todayProgress(row)
                    }
                }.toMap()
                _state.update {
                    it.copy(
                        rules = rules.associateBy { rule -> rule.packageName },
                        todayProgress = progress,
                    )
                }
            }
        }
    }

    /** Opens the editor: prefilled for an existing rule, blank for a new one. */
    fun openEditor(app: InstalledAppInfo) {
        val rule = _state.value.rules[app.packageName]
        _state.update {
            it.copy(
                editor = EarningEditorState(
                    packageName = app.packageName,
                    appLabel = app.label,
                    ruleId = rule?.id,
                    requiredText = rule?.requiredMinutes?.toString().orEmpty(),
                    rewardText = rule?.rewardMinutes?.toString().orEmpty(),
                    dailyText = rule?.dailyLimitMinutes?.toString().orEmpty(),
                    enabled = rule?.enabled ?: true,
                ),
            )
        }
    }

    fun updateEditor(editor: EarningEditorState) {
        _state.update { it.copy(editor = editor.copy(error = null)) }
    }

    fun dismissEditor() {
        _state.update { it.copy(editor = null) }
    }

    fun saveEditor() {
        val editor = _state.value.editor ?: return
        val required = editor.requiredText.toIntOrNull()
        val reward = editor.rewardText.toIntOrNull()
        val daily = editor.dailyText.toIntOrNull()
        if (required == null || reward == null || daily == null) {
            _state.update { it.copy(editor = editor.copy(error = "Enter whole minutes in every field.")) }
            return
        }
        val rule = EarnRule(
            id = editor.ruleId ?: 0L,
            packageName = editor.packageName,
            requiredMinutes = required,
            rewardMinutes = reward,
            dailyLimitMinutes = daily,
            enabled = editor.enabled,
        )
        // The validator is the single source of truth for rule validity.
        val errors = EarnRuleValidator.validate(rule)
        if (errors.isNotEmpty()) {
            _state.update { it.copy(editor = editor.copy(error = friendlyRuleError(errors))) }
            return
        }
        viewModelScope.launch {
            // Existing rules keep their id, so today's progress rows stay linked.
            db.earnRuleDao().upsert(rule)
            _state.update { it.copy(editor = null) }
        }
    }

    fun deleteEditingRule() {
        val ruleId = _state.value.editor?.ruleId ?: return
        viewModelScope.launch {
            db.earnRuleDao().deleteById(ruleId)
            _state.update { it.copy(editor = null) }
        }
    }

    fun setRuleEnabled(rule: EarnRule, enabled: Boolean) {
        viewModelScope.launch {
            db.earnRuleDao().setEnabled(rule.id, enabled)
        }
    }
}
