package com.example.jikan.onboarding

import com.example.jikan.data.DailyGoalPreset
import com.example.jikan.data.InstalledAppInfo

enum class OnboardingStep {
    NAME_INPUT,
    WELCOME,
    APP_PICKER,
    STUDY_PREFS,
    ACCESSIBILITY_PERMISSION,
    BATTERY_PERMISSION,
    ALL_SET,
}

data class OnboardingUiState(
    val step: OnboardingStep = OnboardingStep.NAME_INPUT,
    val userName: String = "",
    val isLoadingApps: Boolean = true,
    val installedApps: List<InstalledAppInfo> = emptyList(),
    val selectedPackages: Set<String> = emptySet(),
    val sessionLengthMinutes: Int = 12,
    val dailyGoalPreset: DailyGoalPreset = DailyGoalPreset.CASUAL,
    val accessibilityGranted: Boolean = false,
    val batteryExemptionGranted: Boolean = false,
    val manufacturer: String = "",
) {
    val stepIndex: Int get() = OnboardingStep.entries.indexOf(step)
    val stepCount: Int get() = OnboardingStep.entries.size
}
