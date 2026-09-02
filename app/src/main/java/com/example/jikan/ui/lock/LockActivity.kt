package com.example.jikan.ui.lock

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.addCallback
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.example.jikan.data.AppDatabase
import com.example.jikan.ui.study.StudySessionScreen
import com.example.jikan.ui.theme.JikanTheme

/**
 * The app's own entry screen shown on redirect from AppLockAccessibilityService
 * (project-context.md "App-locking mechanism"). Runs in its own task
 * (taskAffinity="", singleTask — see manifest) so it's never sharing a back
 * stack with MainActivity or the blocked app.
 *
 * Back button explicitly navigates to the home launcher rather than relying
 * on moveTaskToBack(), whose "reveal the next task" behavior isn't
 * deterministic about landing on the home screen vs. back on the blocked app.
 */
class LockActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val blockedPackage = intent.getStringExtra(EXTRA_BLOCKED_PACKAGE)
        val takeABreak = intent.getBooleanExtra(EXTRA_TAKE_A_BREAK, false)

        onBackPressedDispatcher.addCallback(this) {
            goHome()
        }

        setContent {
            JikanTheme {
                LockActivityContent(
                    blockedPackage = blockedPackage,
                    takeABreak = takeABreak,
                    onDismiss = { goHome() },
                    onContinueToBlockedApp = { launchBlockedApp(blockedPackage) },
                )
            }
        }
    }

    private fun goHome() {
        val homeIntent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        startActivity(homeIntent)
        finish()
    }

    private fun launchBlockedApp(blockedPackage: String?) {
        val launchIntent = blockedPackage?.let { packageManager.getLaunchIntentForPackage(it) }
        if (launchIntent != null) {
            startActivity(launchIntent)
        }
        finish()
    }

    companion object {
        const val EXTRA_BLOCKED_PACKAGE = "blocked_package"
        const val EXTRA_TAKE_A_BREAK = "take_a_break"
    }
}

@Composable
private fun LockActivityContent(
    blockedPackage: String?,
    takeABreak: Boolean,
    onDismiss: () -> Unit,
    onContinueToBlockedApp: () -> Unit,
) {
    val context = LocalContext.current
    val blockedAppLabel by produceState(initialValue = blockedPackage ?: "this app", key1 = blockedPackage) {
        value = blockedPackage?.let { pkg ->
            AppDatabase.getInstance(context).lockedAppDao().get(pkg)?.appLabel ?: pkg
        } ?: "this app"
    }
    val walletBalance by produceState(initialValue = 0) {
        value = AppDatabase.getInstance(context).walletDao().get()?.creditBalanceMinutes ?: 0
    }
    var lessonStarted by remember { mutableStateOf(false) }

    Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
        when {
            takeABreak -> TakeABreakScreen(
                onDismiss = onDismiss,
                modifier = Modifier.padding(innerPadding),
            )

            !lessonStarted -> LockScreen(
                blockedAppLabel = blockedAppLabel,
                onStartLesson = { lessonStarted = true },
                modifier = Modifier.padding(innerPadding),
                walletBalanceMinutes = walletBalance,
            )

            else -> StudySessionScreen(
                modifier = Modifier.padding(innerPadding),
                unlockedAppLabel = blockedAppLabel,
                onContinue = onContinueToBlockedApp,
            )
        }
    }
}
