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
import androidx.compose.material3.Scaffold
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
import com.example.jikan.ui.settings.EarningAppsScreen
import com.example.jikan.ui.settings.LockedAppsScreen
import com.example.jikan.ui.splash.SplashScreen
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
private enum class HomeRoute { HOME, STUDY, LOCKED_APPS, EARNING_APPS }

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

        else -> when (route) {
            HomeRoute.HOME -> HomeScreen(
                modifier = modifier,
                onStudyNow = {
                    studySessionKey++
                    route = HomeRoute.STUDY
                },
                onOpenLockedApps = { route = HomeRoute.LOCKED_APPS },
                onOpenEarningApps = { route = HomeRoute.EARNING_APPS },
            )

            HomeRoute.STUDY -> StudySessionScreen(
                modifier = modifier,
                sessionKey = studySessionKey,
                onContinue = { route = HomeRoute.HOME },
            )

            HomeRoute.LOCKED_APPS -> LockedAppsScreen(
                modifier = modifier,
                onBack = { route = HomeRoute.HOME },
                openBankingModeOnStart = openBankingMode,
            )

            HomeRoute.EARNING_APPS -> EarningAppsScreen(
                modifier = modifier,
                onBack = { route = HomeRoute.HOME },
            )
        }
    }
}
