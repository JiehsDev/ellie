package com.example.jikan.ui.onboarding

import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.jikan.onboarding.OemBatteryHints
import com.example.jikan.onboarding.OnboardingStep
import com.example.jikan.onboarding.OnboardingViewModel

@Composable
fun OnboardingScreen(
    onFinished: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: OnboardingViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) viewModel.refreshPermissionStatus()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Column(modifier = modifier) {
        if (state.step != OnboardingStep.NAME_INPUT && state.step != OnboardingStep.WELCOME && state.step != OnboardingStep.ALL_SET) {
            LinearProgressIndicator(
                progress = { (state.stepIndex + 1f) / state.stepCount },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 12.dp),
            )
        }

        Column(modifier = Modifier.weight(1f)) {
            when (state.step) {
                OnboardingStep.NAME_INPUT -> NameInputScreen(
                    name = state.userName,
                    onNameChanged = viewModel::onNameChanged,
                    onContinue = viewModel::goNext,
                )

                OnboardingStep.WELCOME -> WelcomeScreen(onGetStarted = viewModel::goNext)

                OnboardingStep.APP_PICKER -> AppPickerScreen(
                    apps = state.installedApps,
                    selectedPackages = state.selectedPackages,
                    isLoading = state.isLoadingApps,
                    onToggle = viewModel::toggleApp,
                )

                OnboardingStep.STUDY_PREFS -> StudyPreferencesScreen(
                    sessionLengthMinutes = state.sessionLengthMinutes,
                    dailyGoalPreset = state.dailyGoalPreset,
                    onSessionLengthChanged = viewModel::onSessionLengthChanged,
                    onDailyGoalSelected = viewModel::onDailyGoalSelected,
                )

                OnboardingStep.ACCESSIBILITY_PERMISSION -> AccessibilityPermissionScreen(
                    isGranted = state.accessibilityGranted,
                    onOpenSettings = {
                        context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                    },
                )

                OnboardingStep.BATTERY_PERMISSION -> BatteryPermissionScreen(
                    isGranted = state.batteryExemptionGranted,
                    manufacturer = state.manufacturer,
                    onRequestExemption = {
                        val intent = Intent(
                            Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                            Uri.parse("package:${context.packageName}"),
                        )
                        runCatching { context.startActivity(intent) }
                    },
                    onOpenAutostartSettings = {
                        val target = OemBatteryHints.autostartTargetFor(state.manufacturer)
                        val intent = target?.let {
                            Intent().apply { component = ComponentName(it.packageName, it.className) }
                        }
                        try {
                            if (intent != null) context.startActivity(intent)
                        } catch (_: ActivityNotFoundException) {
                            context.startActivity(
                                Intent(
                                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                    Uri.parse("package:${context.packageName}"),
                                )
                            )
                        }
                    },
                )

                OnboardingStep.ALL_SET -> AllSetScreen(
                    onStartFirstLesson = { viewModel.completeOnboarding(onFinished) },
                )
            }
        }

        if (state.step != OnboardingStep.NAME_INPUT && state.step != OnboardingStep.WELCOME && state.step != OnboardingStep.ALL_SET) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                TextButton(onClick = viewModel::goBack) {
                    Text("Back")
                }
                Button(
                    onClick = viewModel::goNext,
                    enabled = state.step != OnboardingStep.ACCESSIBILITY_PERMISSION || state.accessibilityGranted,
                ) {
                    Text("Next")
                }
            }
        }
    }
}
