package com.example.jikan

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.example.jikan.data.AppDatabase
import com.example.jikan.data.ThemeMode
import com.example.jikan.ui.home.HomeScreen
import com.example.jikan.ui.onboarding.OnboardingScreen
import com.example.jikan.ui.insights.InsightsScreen
import com.example.jikan.ui.settings.EarningAppsScreen
import com.example.jikan.ui.settings.LockedAppsScreen
import com.example.jikan.ui.settings.RestrictedAppsScreen
import com.example.jikan.ui.allowance.AllowanceScreen
import com.example.jikan.ui.apprules.AppRulesScreen
import com.example.jikan.ui.apptrend.AppTrendScreen
import com.example.jikan.ui.coach.CoachScreen
import com.example.jikan.ui.deepdive.DeepDiveScreen
import com.example.jikan.ui.habits.HabitDetailScreen
import com.example.jikan.ui.habits.HabitsScreen
import com.example.jikan.ui.habits.HistoryScreen
import com.example.jikan.ui.earning.setup.EarningSetupScreen
import com.example.jikan.ui.ledger.LedgerScreen
import com.example.jikan.ui.limits.LimitsMainScreen
import com.example.jikan.ui.rhythm.RhythmScreen
import com.example.jikan.ui.rules.RulesScreen
import com.example.jikan.ui.splash.SplashScreen
import com.example.jikan.study.StudySessionViewModel
import com.example.jikan.ui.study.StudyDashboardScreen
import com.example.jikan.ui.study.StudySessionScreen
import com.example.jikan.ui.theme.JikanTheme
import com.example.jikan.service.StrictModeManager
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        requestNotificationPermissionIfNeeded()
        StrictModeManager.scheduleWatchdog(applicationContext)
        val startInStudy = intent.getBooleanExtra(EXTRA_START_STUDY, false)
        val openBankingMode = intent.getBooleanExtra(EXTRA_OPEN_BANKING_MODE, false)
        setContent {
            val context = LocalContext.current
            val settings by AppDatabase.getInstance(context).settingsDao().observe().collectAsState(initial = null)
            JikanTheme(themeMode = settings?.themeMode ?: ThemeMode.SYSTEM) {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    AppRoot(
                        modifier = Modifier.padding(innerPadding),
                        startInStudy = startInStudy,
                        openBankingMode = openBankingMode,
                    )
                }
            }
        }
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val permission = Manifest.permission.POST_NOTIFICATIONS
        if (ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED) return
        ActivityCompat.requestPermissions(this, arrayOf(permission), REQUEST_POST_NOTIFICATIONS)
    }

    companion object {
        const val EXTRA_START_STUDY = "start_study"
        const val EXTRA_OPEN_BANKING_MODE = "open_banking_mode"
        private const val REQUEST_POST_NOTIFICATIONS = 1001
    }
}
private enum class HomeRoute { HOME, STUDY, SESSION, LIMITS_MAIN, ALLOWANCE, RULES, LEDGER, APP_RULES, EARNING_SETUP, DEEP_DIVE, APP_TREND, COACH, HABITS, HABIT_DETAIL, HISTORY, LOCKED_APPS, EARNING_APPS, RESTRICTED_APPS, INSIGHTS }

@Composable
private fun AppRoot(
    modifier: Modifier = Modifier,
    startInStudy: Boolean = false,
    openBankingMode: Boolean = false,
) {
    val context = LocalContext.current
    var onboardingCompleted by remember { mutableStateOf<Boolean?>(null) }
    var minSplashElapsed by remember { mutableStateOf(false) }
    var route by remember {
        mutableStateOf(
            when {
                openBankingMode -> HomeRoute.LOCKED_APPS
                startInStudy -> HomeRoute.STUDY
                else -> HomeRoute.HOME
            }
        )
    }
    var studySessionKey by remember { mutableStateOf(0) }
    var sessionSize by remember { mutableStateOf(StudySessionViewModel.SESSION_SIZE) }
    var appRulesPackage by remember { mutableStateOf("") }
    var habitDetailId by remember { mutableStateOf("flashcards") }
    var earningSetupRuleId by remember { mutableStateOf<Long?>(null) }
    var earningSetupBackToHabit by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        onboardingCompleted = AppDatabase.getInstance(context).settingsDao().get()?.onboardingCompleted ?: false
    }
    LaunchedEffect(Unit) {
        delay(1200)
        minSplashElapsed = true
    }

    when {
        onboardingCompleted == null || !minSplashElapsed -> SplashScreen(modifier = modifier)

        onboardingCompleted == false -> OnboardingScreen(modifier = modifier, onFinished = { onboardingCompleted = true })

        else -> {
            val showBottomNav = route == HomeRoute.HOME ||
                route == HomeRoute.STUDY ||
                route == HomeRoute.LIMITS_MAIN ||
                route == HomeRoute.INSIGHTS
            Scaffold(
                modifier = modifier,
                bottomBar = {
                    if (showBottomNav) {
                        BottomNavBar(
                            current = route,
                            onSelect = { route = it },
                        )
                    }
                },
            ) { navPadding ->
                val contentModifier = Modifier.padding(navPadding)
                when (route) {
                    HomeRoute.HOME -> HomeScreen(
                        modifier = contentModifier,
                        onStudyNow = {
                            studySessionKey++
                            route = HomeRoute.STUDY
                        },
                        onOpenLockedApps = { route = HomeRoute.LOCKED_APPS },
                        onOpenEarningApps = { route = HomeRoute.EARNING_APPS },
                        onOpenRestrictedApps = { route = HomeRoute.RESTRICTED_APPS },
                        onOpenInsights = { route = HomeRoute.INSIGHTS },
                        onOpenCoach = { route = HomeRoute.COACH },
                    )

                    HomeRoute.STUDY -> StudyDashboardScreen(
                        modifier = contentModifier,
                        onStartSession = {
                            sessionSize = StudySessionViewModel.SESSION_SIZE
                            studySessionKey++
                            route = HomeRoute.SESSION
                        },
                        onQuickBurst = {
                            sessionSize = 5
                            studySessionKey++
                            route = HomeRoute.SESSION
                        },
                        onOpenHabits = { route = HomeRoute.HABITS },
                    )

                    HomeRoute.SESSION -> StudySessionScreen(
                        modifier = contentModifier,
                        sessionKey = studySessionKey,
                        sessionSize = sessionSize,
                        onContinue = { route = HomeRoute.STUDY },
                    )

                    HomeRoute.LIMITS_MAIN -> LimitsMainScreen(
                        modifier = contentModifier,
                        onOpenBalance = { route = HomeRoute.ALLOWANCE },
                        onAddRestrictedApp = { route = HomeRoute.RESTRICTED_APPS },
                        onEditRestrictedApp = { pkg ->
                            appRulesPackage = pkg
                            route = HomeRoute.APP_RULES
                        },
                        onOpenEarningApps = { route = HomeRoute.EARNING_APPS },
                        onEditEarningRule = { ruleId ->
                            earningSetupRuleId = ruleId
                            route = HomeRoute.EARNING_SETUP
                        },
                    )

                    HomeRoute.APP_RULES -> AppRulesScreen(
                        packageName = appRulesPackage,
                        modifier = contentModifier,
                        onBack = { route = HomeRoute.LIMITS_MAIN },
                        onOpenEarningApps = { route = HomeRoute.EARNING_APPS },
                    )

                    HomeRoute.ALLOWANCE -> AllowanceScreen(
                        modifier = contentModifier,
                        onBack = { route = HomeRoute.LIMITS_MAIN },
                        onStartSession = {
                            sessionSize = StudySessionViewModel.SESSION_SIZE
                            studySessionKey++
                            route = HomeRoute.SESSION
                        },
                        onQuickBurst = {
                            sessionSize = 5
                            studySessionKey++
                            route = HomeRoute.SESSION
                        },
                        onOpenRules = { route = HomeRoute.RESTRICTED_APPS },
                        onOpenConversionRules = { route = HomeRoute.RULES },
                        onOpenLedger = { route = HomeRoute.LEDGER },
                    )

                    HomeRoute.LEDGER -> LedgerScreen(
                        modifier = contentModifier,
                        onBack = { route = HomeRoute.ALLOWANCE },
                        onQuickBurst = {
                            sessionSize = 5
                            studySessionKey++
                            route = HomeRoute.SESSION
                        },
                    )

                    HomeRoute.RULES -> RulesScreen(
                        modifier = contentModifier,
                        onBack = { route = HomeRoute.ALLOWANCE },
                        onOpenEarningApps = { route = HomeRoute.EARNING_APPS },
                    )

                    HomeRoute.LOCKED_APPS -> LockedAppsScreen(
                        modifier = contentModifier,
                        onBack = { route = HomeRoute.HOME },
                        openBankingModeOnStart = openBankingMode,
                    )

                    HomeRoute.EARNING_APPS -> EarningAppsScreen(
                        modifier = contentModifier,
                        onBack = { route = HomeRoute.HOME },
                    )

                    HomeRoute.EARNING_SETUP -> EarningSetupScreen(
                        ruleId = earningSetupRuleId,
                        packageName = null,
                        appLabel = null,
                        modifier = contentModifier,
                        onBack = {
                            route = if (earningSetupBackToHabit) {
                                earningSetupBackToHabit = false
                                HomeRoute.HABIT_DETAIL
                            } else {
                                HomeRoute.LIMITS_MAIN
                            }
                        },
                    )

                    HomeRoute.RESTRICTED_APPS -> RestrictedAppsScreen(
                        modifier = contentModifier,
                        onBack = { route = HomeRoute.HOME },
                    )

                    HomeRoute.INSIGHTS -> RhythmScreen(
                        modifier = contentModifier,
                        onExport = { /* CSV export lives on the Ledger screen */ },
                        onDeepDive = { route = HomeRoute.DEEP_DIVE },
                        onAppTrend = { pkg ->
                            appRulesPackage = pkg
                            route = HomeRoute.APP_TREND
                        },
                    )

                    HomeRoute.APP_TREND -> AppTrendScreen(
                        packageName = appRulesPackage,
                        modifier = contentModifier,
                        onBack = { route = HomeRoute.INSIGHTS },
                        onAdjustLimits = { pkg ->
                            appRulesPackage = pkg
                            route = HomeRoute.APP_RULES
                        },
                    )

                    HomeRoute.COACH -> CoachScreen(
                        modifier = contentModifier,
                        onBack = { route = HomeRoute.HOME },
                        onQuickBurst = {
                            sessionSize = 5
                            studySessionKey++
                            route = HomeRoute.SESSION
                        },
                        onStartSession = {
                            sessionSize = StudySessionViewModel.SESSION_SIZE
                            studySessionKey++
                            route = HomeRoute.SESSION
                        },
                        onInspectAppRules = { pkg ->
                            appRulesPackage = pkg
                            route = HomeRoute.APP_RULES
                        },
                    )

                    HomeRoute.HABITS -> HabitsScreen(
                        modifier = contentModifier,
                        onBack = { route = HomeRoute.STUDY },
                        onOpenHabitDetail = { habitId ->
                            habitDetailId = habitId
                            route = HomeRoute.HABIT_DETAIL
                        },
                        onOpenHistory = { route = HomeRoute.HISTORY },
                    )

                    HomeRoute.HABIT_DETAIL -> HabitDetailScreen(
                        habitId = habitDetailId,
                        modifier = contentModifier,
                        onBack = { route = HomeRoute.HABITS },
                        onStartReview = {
                            sessionSize = 5
                            studySessionKey++
                            route = HomeRoute.SESSION
                        },
                        onAdjustRules = {
                            val ruleId = habitDetailId.removePrefix("rule:").toLongOrNull()
                            if (ruleId != null) {
                                earningSetupRuleId = ruleId
                                earningSetupBackToHabit = true
                                route = HomeRoute.EARNING_SETUP
                            } else {
                                route = HomeRoute.STUDY
                            }
                        },
                    )

                    HomeRoute.HISTORY -> HistoryScreen(
                        modifier = contentModifier,
                        onBack = { route = HomeRoute.HABITS },
                    )

                    HomeRoute.DEEP_DIVE -> DeepDiveScreen(
                        modifier = contentModifier,
                        onBack = { route = HomeRoute.INSIGHTS },
                    )
                }
            }
        }
    }
}

/**
 * Stitch reference bottom navigation: Home, Study, Limits, Rhythm.
 * Shown on the four tab routes; sub-screens (earning, restricted apps)
 * keep their own back navigation.
 */
@Composable
private fun BottomNavBar(
    current: HomeRoute,
    onSelect: (HomeRoute) -> Unit,
) {
    val items = listOf(
        Triple(HomeRoute.HOME, "Home", Icons.Filled.Home),
        Triple(HomeRoute.STUDY, "Study", Icons.Filled.MenuBook),
        Triple(HomeRoute.LIMITS_MAIN, "Limits", Icons.Filled.Lock),
        Triple(HomeRoute.INSIGHTS, "Rhythm", Icons.Filled.ShowChart),
    )
    NavigationBar(
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        items.forEach { (route, label, icon) ->
            val selected = route == current
            NavigationBarItem(
                selected = selected,
                onClick = { onSelect(route) },
                icon = {
                    Icon(
                        imageVector = icon,
                        contentDescription = label,
                    )
                },
                label = { Text(label) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = MaterialTheme.colorScheme.secondary,
                    selectedTextColor = MaterialTheme.colorScheme.secondary,
                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    indicatorColor = MaterialTheme.colorScheme.surfaceVariant,
                ),
            )
        }
    }
}
