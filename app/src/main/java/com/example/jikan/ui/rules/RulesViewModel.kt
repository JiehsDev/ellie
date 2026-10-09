package com.example.jikan.ui.rules

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.jikan.data.AppDatabase
import com.example.jikan.data.Wallet
import com.example.jikan.study.CreditCalculator
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
)

class RulesViewModel(application: Application) : AndroidViewModel(application) {
    private val db = AppDatabase.getInstance(application)

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
