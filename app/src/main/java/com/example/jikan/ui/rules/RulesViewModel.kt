package com.example.jikan.ui.rules

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.jikan.data.AppDatabase
import com.example.jikan.data.Wallet
import com.example.jikan.study.CreditCalculator
import com.example.jikan.study.CreditPolicy
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class RulesUiState(
    val earningAppCount: Int = 0,
    val hasEarningApps: Boolean = false,
    val dailyCapMinutes: Int = Wallet.MAX_BALANCE_MINUTES,
    // Estimator inputs.
    val estimatorMinutes: Int = 10,
    val estimatorAccuracy: Int = 90,
    // Credit policy.
    val creditProfile: CreditPolicy.CreditProfile = CreditPolicy.CreditProfile.BALANCED,
    val validatedMinutesToday: Int = 0,
    val allowanceUsedToday: Int = 0,
    val allowanceRemaining: Int = 0,
    val minutesToNextCredit: Int = 0,
    val currentTierLabel: String = "",
)

class RulesViewModel(application: Application) : AndroidViewModel(application) {
    private val db = AppDatabase.getInstance(application)
    private val zone = java.time.ZoneId.systemDefault()

    private val _state = MutableStateFlow(RulesUiState())
    val state: StateFlow<RulesUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            db.earnRuleDao().observeAll().collect { rules ->
                _state.update {
                    it.copy(
                        earningAppCount = rules.count { r -> r.enabled },
                        hasEarningApps = rules.any { r -> r.enabled },
                    )
                }
            }
        }
        refreshPolicy()
    }

    fun refreshPolicy() {
        viewModelScope.launch {
            val settings = db.settingsDao().get()
            val profile = try {
                CreditPolicy.CreditProfile.valueOf(settings?.creditProfileName ?: "BALANCED")
            } catch (_: Exception) {
                CreditPolicy.CreditProfile.BALANCED
            }
            val config = CreditPolicy.configFor(profile)
            val today = java.time.LocalDate.now(zone).toEpochDay()
            val dayState = db.creditDayStateDao().get(today)
            val validated = dayState?.validatedStudyMinutes ?: 0
            val allowanceUsed = dayState?.allowanceUsedMinutes ?: 0
            val tierIdx = CreditPolicy.currentTierIndex(config, validated)
            val tierLabel = if (tierIdx < 0) {
                "Daily cap reached"
            } else {
                val tier = config.earningTiers[tierIdx]
                val end = if (tier.endMinutes == Int.MAX_VALUE) "∞" else "${tier.endMinutes}m"
                "Tier ${tierIdx + 1}: ${tier.studyMinutesPerCredit}:1 (${tier.startMinutes}m–$end)"
            }
            _state.update {
                it.copy(
                    creditProfile = profile,
                    validatedMinutesToday = validated,
                    allowanceUsedToday = allowanceUsed,
                    allowanceRemaining = (config.dailyAllowanceMinutes - allowanceUsed).coerceAtLeast(0),
                    minutesToNextCredit = CreditPolicy.minutesToNextCredit(config, validated),
                    currentTierLabel = tierLabel,
                )
            }
        }
    }

    fun setProfile(profile: CreditPolicy.CreditProfile) {
        viewModelScope.launch {
            val settings = db.settingsDao().get()
            if (settings != null) {
                db.settingsDao().upsert(settings.copy(creditProfileName = profile.name))
            }
            refreshPolicy()
        }
    }

    fun setEstimatorMinutes(minutes: Int) {
        _state.update { it.copy(estimatorMinutes = minutes.coerceIn(5, 60)) }
    }

    fun setEstimatorAccuracy(accuracy: Int) {
        _state.update { it.copy(estimatorAccuracy = accuracy.coerceIn(50, 100)) }
    }

    /** Real credit estimate from the actual formula. ~30s per card. */
    fun estimatorCredits(state: RulesUiState): Int {
        val questions = state.estimatorMinutes * 2
        val correct = (questions * state.estimatorAccuracy / 100.0).toInt()
        return CreditCalculator.creditsEarned(correct, questions)
    }
}
