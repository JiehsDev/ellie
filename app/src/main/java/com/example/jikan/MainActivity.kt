package com.example.jikan

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.example.jikan.data.AppDatabase
import com.example.jikan.ui.home.HomeScreen
import com.example.jikan.ui.onboarding.OnboardingScreen
import com.example.jikan.ui.settings.LockedAppsScreen
import com.example.jikan.ui.splash.SplashScreen
import com.example.jikan.ui.study.StudySessionScreen
import com.example.jikan.ui.theme.JikanTheme
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val startInStudy = intent.getBooleanExtra(EXTRA_START_STUDY, false)
        setContent {
            JikanTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    AppRoot(modifier = Modifier.padding(innerPadding), startInStudy = startInStudy)
                }
            }
        }
    }

    companion object {
        const val EXTRA_START_STUDY = "start_study"
    }
}
private enum class HomeRoute { HOME, STUDY, LOCKED_APPS }

@Composable
private fun AppRoot(modifier: Modifier = Modifier, startInStudy: Boolean = false) {
    val context = LocalContext.current
    var onboardingCompleted by remember { mutableStateOf<Boolean?>(null) }
    var minSplashElapsed by remember { mutableStateOf(false) }
    var route by remember { mutableStateOf(if (startInStudy) HomeRoute.STUDY else HomeRoute.HOME) }
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
            )

            HomeRoute.STUDY -> StudySessionScreen(
                modifier = modifier,
                sessionKey = studySessionKey,
                onContinue = { route = HomeRoute.HOME },
            )

            HomeRoute.LOCKED_APPS -> LockedAppsScreen(
                modifier = modifier,
                onBack = { route = HomeRoute.HOME },
            )
        }
    }
}