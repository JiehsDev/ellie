package com.example.jikan.ui.home

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.jikan.R
import com.example.jikan.data.EarnRule
import com.example.jikan.data.ThemeMode
import com.example.jikan.service.PauseManager
import com.example.jikan.ui.theme.RadiusMd
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Stitch reference implementation: the home screen is the study-to-unlock
 * mission control. Dark forest-charcoal theme, section order: greeting,
 * JikanCoach, screen credit, study CTA, daily rhythm, earning rules,
 * locked apps.
 *
 * All numbers come from real app state (wallet, study, usage stats,
 * earning rules, locked apps). The reference's figures are illustrative.
 */
fun HomeScreen(
    onStudyNow: () -> Unit,
    onOpenLockedApps: () -> Unit,
    onOpenEarningApps: () -> Unit,
    onOpenRestrictedApps: () -> Unit,
    onOpenInsights: () -> Unit,
    onOpenCoach: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: HomeViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    var showThemeDialog by remember { mutableStateOf(false) }

    // The user leaves the app to flip the accessibility toggle, so re-check on return.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refreshProtectionStatus() }

    HomeScreenContent(
        state = state,
        onStudyNow = onStudyNow,
        onOpenLockedApps = onOpenLockedApps,
        onOpenEarningApps = onOpenEarningApps,
        onOpenRestrictedApps = onOpenRestrictedApps,
        onOpenInsights = onOpenInsights,
        onOpenCoach = onOpenCoach,
        onOpenThemeSettings = { showThemeDialog = true },
        onStartBankingAllowlist = viewModel::startBankingAllowlist,
        onStartFullDisableBankingMode = viewModel::startFullDisableBankingMode,
        onDismissBankingModeStatus = viewModel::dismissBankingModeStatus,
        onOpenAccessibilitySettings = { PauseManager.openAccessibilitySettings(context) },
        onFixProtection = {
            context.startActivity(
                Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        },
        onFixBattery = {
            context.startActivity(
                Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        },
        modifier = modifier,
    )

    if (showThemeDialog) {
        AlertDialog(
            onDismissRequest = { showThemeDialog = false },
            title = { Text("Theme") },
            text = {
                ThemeModeSelector(
                    selected = state.themeMode,
                    onSelect = {
                        viewModel.setThemeMode(it)
                        showThemeDialog = false
                    },
                )
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showThemeDialog = false }) { Text("Close") }
            },
        )
    }
}

@Composable
private fun HomeScreenContent(
    state: HomeUiState,
    onStudyNow: () -> Unit,
    onOpenLockedApps: () -> Unit,
    onOpenEarningApps: () -> Unit,
    onOpenRestrictedApps: () -> Unit,
    onOpenInsights: () -> Unit,
    onOpenCoach: () -> Unit,
    onOpenThemeSettings: () -> Unit,
    onStartBankingAllowlist: (String, String, Int) -> Unit,
    onStartFullDisableBankingMode: (Int) -> Unit,
    onDismissBankingModeStatus: () -> Unit,
    onOpenAccessibilitySettings: () -> Unit,
    onFixProtection: () -> Unit,
    onFixBattery: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (state.isLoading) {
        Box(modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.background))
        return
    }

    BankingModeDialog(
        status = state.bankingModeStatus,
        lockedApps = state.lockedApps,
        onDismiss = onDismissBankingModeStatus,
        onAllowApp = onStartBankingAllowlist,
        onFullDisable = onStartFullDisableBankingMode,
        onOpenAccessibilitySettings = onOpenAccessibilitySettings,
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .padding(top = 12.dp, bottom = 28.dp),
    ) {
        TopBar(
            lifetimeEarnedMinutes = state.lifetimeEarnedMinutes,
            onOpenThemeSettings = onOpenThemeSettings,
            onOpenInsights = onOpenInsights,
        )

        Spacer(Modifier.height(18.dp))
        GreetingHeader(state = state)

        if (state.coachMessage.isNotBlank()) {
            Spacer(Modifier.height(16.dp))
            CoachCard(message = state.coachMessage, mood = state.coachMood, onClick = onOpenCoach)
        }

        if (!state.protectionOn) {
            Spacer(Modifier.height(16.dp))
            ProtectionBanner(onFix = onFixProtection)
        }

        if (state.protectionOn && !state.batteryOptimizationsIgnored) {
            Spacer(Modifier.height(16.dp))
            BatteryWarningBanner(onFix = onFixBattery)
        }

        if (state.strictModeEnabled || state.bankingModeActive || state.bankingDisabledUntilMs > System.currentTimeMillis()) {
            Spacer(Modifier.height(16.dp))
            StrictStatusBanner(state = state)
        }

        Spacer(Modifier.height(16.dp))
        CreditCard(state = state)

        Spacer(Modifier.height(16.dp))
        StudyCta(state = state, onStudyNow = onStudyNow)

        Spacer(Modifier.height(22.dp))
        DailyRhythm(state = state)

        Spacer(Modifier.height(22.dp))
        EarningRulesSection(state = state, onManage = onOpenEarningApps)

        Spacer(Modifier.height(22.dp))
        LockedAppsSection(
            state = state,
            onEditLimits = onOpenLockedApps,
            onOpenRestrictedApps = onOpenRestrictedApps,
        )

        Spacer(Modifier.height(14.dp))
        InfoNote(
            text = "Apps stay locked until you earn credits or complete approved reviews."
        )
    }
}

// ---------------------------------------------------------------------------
// Top bar
// ---------------------------------------------------------------------------

@Composable
private fun TopBar(
    lifetimeEarnedMinutes: Int,
    onOpenThemeSettings: () -> Unit,
    onOpenInsights: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column {
            Text(
                text = "JIKAN",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                letterSpacing = 3.sp,
            )
            Text(
                text = "Home",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onBackground,
            )
        }
        Spacer(Modifier.weight(1f))
        // Lifetime earned pill.
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            Icon(
                imageVector = Icons.Filled.AccessTime,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp),
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = "+${lifetimeEarnedMinutes}m",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
        Spacer(Modifier.width(10.dp))
        Icon(
            imageVector = Icons.Filled.Settings,
            contentDescription = "Settings",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .clickable(onClick = onOpenThemeSettings)
                .padding(8.dp),
        )
        Spacer(Modifier.width(4.dp))
        Image(
            painter = painterResource(R.drawable.mascot_jikan_coach),
            contentDescription = "Ellie",
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .clickable(onClick = onOpenInsights),
        )
    }
}

// ---------------------------------------------------------------------------
// Greeting
// ---------------------------------------------------------------------------

@Composable
private fun GreetingHeader(state: HomeUiState) {
    val now = Instant.now().atZone(ZoneId.systemDefault())
    val hour = now.hour
    val greeting = when (hour) {
        in 5..11 -> "Good morning"
        in 12..17 -> "Good afternoon"
        else -> "Good evening"
    }
    val date = now.format(DateTimeFormatter.ofPattern("EEEE, MMM d", Locale.US)).uppercase(Locale.US)

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = date,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                letterSpacing = 1.sp,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = greeting,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onBackground,
            )
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.tertiary),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = "${state.walletBalanceMinutes}m balance",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Coach card
// ---------------------------------------------------------------------------

private fun moodPillText(mood: CoachMood): String = when (mood) {
    CoachMood.Proud -> "Rhythm on track"
    CoachMood.Calm -> "Steady"
    CoachMood.Playful -> "In the zone"
    CoachMood.Focused -> "Locked in"
    CoachMood.Concerned -> "Needs attention"
    CoachMood.WelcomeBack -> "Welcome back"
}

@Composable
private fun CoachCard(message: String, mood: CoachMood, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surface)
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Image(
            painter = painterResource(R.drawable.mascot_jikan_coach),
            contentDescription = "Ellie",
            modifier = Modifier
                .size(56.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
        )
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Ellie",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.weight(1f))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(horizontal = 10.dp, vertical = 5.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.secondary),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = moodPillText(mood),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.height(6.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                lineHeight = 22.sp,
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Screen credit card
// ---------------------------------------------------------------------------

@Composable
private fun CreditCard(state: HomeUiState) {
    val balance = state.walletBalanceMinutes
    val earnedToday = state.screenTimeSummary?.earnedMinutes ?: 0
    val lowBalance = balance < 10
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(20.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "AVAILABLE SCREEN CREDIT",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                letterSpacing = 1.sp,
                modifier = Modifier.weight(1f),
            )
            if (lowBalance) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.tertiaryContainer)
                        .padding(horizontal = 10.dp, vertical = 5.dp),
                ) {
                    Icon(
                        imageVector = Icons.Filled.AccessTime,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.size(14.dp),
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = "Low balance (${balance}m left)",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.tertiary,
                    )
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = "$balance",
                style = MaterialTheme.typography.displayLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 64.sp,
                lineHeight = 64.sp,
            )
            Spacer(Modifier.width(10.dp))
            Text(
                text = "minutes remaining",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 10.dp),
            )
        }
        Spacer(Modifier.height(12.dp))
        LinearProgressIndicator(
            progress = { (balance.toFloat() / state.maxBalanceMinutes.coerceAtLeast(1).toFloat()).coerceIn(0f, 1f) },
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(CircleShape),
            color = MaterialTheme.colorScheme.secondary,
            trackColor = MaterialTheme.colorScheme.surfaceVariant,
        )
        Spacer(Modifier.height(12.dp))
        Row(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = "$earnedToday of ${state.maxBalanceMinutes} minutes earned today",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = "${state.lockedAppCount} apps currently locked",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Study CTA
// ---------------------------------------------------------------------------

@Composable
private fun StudyCta(state: HomeUiState, onStudyNow: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.secondary)
            .clickable(onClick = onStudyNow)
            .padding(horizontal = 20.dp, vertical = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Filled.MenuBook,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSecondary,
            modifier = Modifier.size(26.dp),
        )
        Spacer(Modifier.width(12.dp))
        Text(
            text = "Start study session",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSecondary,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = "${state.dueCount} reviews • ${state.sessionLengthMinutes} min",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSecondary,
        )
    }
}

// ---------------------------------------------------------------------------
// Daily rhythm
// ---------------------------------------------------------------------------

@Composable
private fun DailyRhythm(state: HomeUiState) {
    val summary = state.screenTimeSummary
    val goalMinutes = state.dailyGoalMinutes
    val studiedToday = state.minutesSpentToday
    val distractingMinutes = (summary?.totalScreenTimeMinutes ?: 0) - (summary?.earningAppMinutes ?: 0)
    val overLimits = (summary?.exceededApps?.isNotEmpty() == true) ||
        (summary?.atLimitApps?.isNotEmpty() == true)

    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "DAILY RHYTHM",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                letterSpacing = 1.sp,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = "Goal: ${goalMinutes}m",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(12.dp))
        Row(modifier = Modifier.fillMaxWidth()) {
            // Study time card.
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(20.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(16.dp),
            ) {
                Text(
                    text = "Study time today",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = "${studiedToday}m",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = " / ${goalMinutes}m",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 4.dp),
                    )
                }
                Spacer(Modifier.height(10.dp))
                // 5-dot progress.
                val filled = ((studiedToday.toFloat() / goalMinutes.coerceAtLeast(1).toFloat()) * 5)
                    .toInt().coerceIn(0, 5)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    repeat(5) { i ->
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(
                                    if (i < filled) MaterialTheme.colorScheme.secondary
                                    else MaterialTheme.colorScheme.surfaceVariant
                                ),
                        )
                    }
                }
            }
            Spacer(Modifier.width(12.dp))
            // Distracting apps card.
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(20.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(16.dp),
            ) {
                Text(
                    text = "Distracting apps",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "${distractingMinutes}m",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(10.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(
                                if (overLimits) MaterialTheme.colorScheme.tertiary
                                else MaterialTheme.colorScheme.secondary
                            ),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = if (overLimits) "Over limits" else "Under budget",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Earning rules
// ---------------------------------------------------------------------------

@Composable
private fun EarningRulesSection(state: HomeUiState, onManage: () -> Unit) {
    val rules = state.earningRules
    val labels = state.screenTimeSummary?.earningApps?.associate { it.packageName to it.appLabel }
        .orEmpty()

    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Earning rules",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = if (rules.isEmpty()) "Set up" else "Manage",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.clickable(onClick = onManage),
            )
        }
        if (rules.isEmpty()) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Pick apps that earn you minutes.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            Spacer(Modifier.height(12.dp))
            rules.take(3).forEach { rule ->
                EarningRuleRow(
                    rule = rule,
                    appLabel = labels[rule.packageName] ?: rule.packageName,
                )
                Spacer(Modifier.height(10.dp))
            }
        }
    }
}

@Composable
private fun EarningRuleRow(rule: EarnRule, appLabel: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = appLabel,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = rule.packageName.substringAfterLast('.').take(12),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(
                text = "Practice ${rule.requiredMinutes}m → earn ${rule.rewardMinutes}m credits",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (rule.enabled) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(horizontal = 10.dp, vertical = 6.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.secondary),
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = "Active",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Locked apps
// ---------------------------------------------------------------------------

@Composable
private fun LockedAppsSection(
    state: HomeUiState,
    onEditLimits: () -> Unit,
    onOpenRestrictedApps: () -> Unit,
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Locked apps",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = "${state.lockedAppCount}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = "Edit limits",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.clickable(onClick = onOpenRestrictedApps),
            )
        }
        Spacer(Modifier.height(12.dp))
        if (state.lockedApps.isEmpty()) {
            Text(
                text = "No locked apps yet.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.clickable(onClick = onEditLimits),
            )
        } else {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(end = 8.dp),
            ) {
                val visible = state.lockedApps.take(5)
                items(visible, key = { it.packageName }) { app ->
                    LockedAppTile(
                        label = app.label,
                        iconBitmap = runCatching {
                            app.icon?.toBitmap()?.asImageBitmap()
                        }.getOrNull(),
                    )
                }
                if (state.lockedApps.size > 5) {
                    item {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.clickable(onClick = onEditLimits),
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(56.dp)
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = "+${state.lockedApps.size - 5}",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Spacer(Modifier.height(6.dp))
                            Text(
                                text = "more",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LockedAppTile(label: String, iconBitmap: ImageBitmap?) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box {
            if (iconBitmap != null) {
                Image(
                    bitmap = iconBitmap,
                    contentDescription = null,
                    modifier = Modifier
                        .size(56.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = label.take(1).uppercase(),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .size(20.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.tertiaryContainer)
                    .padding(4.dp),
                contentAlignment = Alignment.Center,
            ) {
                androidx.compose.material3.Icon(
                    imageVector = Icons.Filled.Lock,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.size(12.dp),
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = label.take(10),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun InfoNote(text: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(
            imageVector = Icons.Filled.Info,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.tertiary,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(10.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            lineHeight = 20.sp,
        )
    }
}


// ---------------------------------------------------------------------------
// Preserved system banners and dialogs (unchanged behavior)
// ---------------------------------------------------------------------------

private fun StrictStatusBanner(state: HomeUiState) {
    val now = System.currentTimeMillis()
    val bankingActive = state.bankingModeActive || state.bankingDisabledUntilMs > now
    val graceActive = state.strictGraceUntilMs > now && !state.protectionOn
    val recentViolation = state.lastViolationAtMs > 0L
    val title = when {
        bankingActive -> "Banking mode"
        graceActive -> "Strict grace period"
        recentViolation && !state.protectionOn -> "Strict violation recorded"
        state.strictModeEnabled && state.protectionOn -> "Strict mode healthy"
        else -> "Strict mode"
    }
    val body = when {
        bankingActive -> "Protection is off for banking. Find Ellie in Accessibility and turn it on when you're done."
        graceActive -> {
            val minutes = ((state.strictGraceUntilMs - now) / 60_000L).coerceAtLeast(1)
            "Protection is off. Re-enable Jikan within ${minutes}m to avoid a wallet penalty."
        }
        recentViolation && !state.protectionOn -> "Grace expired while protection was off. Re-enable Jikan to return to healthy."
        state.strictModeEnabled && state.protectionOn -> "Protection is on. Watchdog checks roughly every 15 minutes."
        else -> "Strict mode tracks protection health."
    }
    val shape = RoundedCornerShape(RadiusMd)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(MaterialTheme.colorScheme.primaryContainer, shape)
            .padding(18.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
        )
    }
}

private fun ThemeModeSelector(
    selected: ThemeMode,
    onSelect: (ThemeMode) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        ThemeMode.entries.forEach { mode ->
            val active = mode == selected
            Text(
                text = when (mode) {
                    ThemeMode.SYSTEM -> "System"
                    ThemeMode.LIGHT -> "Light"
                    ThemeMode.DARK -> "Dark"
                },
                style = MaterialTheme.typography.labelMedium,
                color = if (active) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .clip(CircleShape)
                    .background(
                        if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                        CircleShape,
                    )
                    .clickable(onClick = { onSelect(mode) })
                    .padding(horizontal = 16.dp, vertical = 10.dp),
            )
        }
    }
}

private fun BankingModeDialog(
    status: BankingModeStatus,
    lockedApps: List<LockedAppChip>,
    onDismiss: () -> Unit,
    onAllowApp: (String, String, Int) -> Unit,
    onFullDisable: (Int) -> Unit,
    onOpenAccessibilitySettings: () -> Unit,
) {
    when (status) {
        BankingModeStatus.Idle -> Unit
        BankingModeStatus.Selecting -> AlertDialog(
            onDismissRequest = onDismiss,
            confirmButton = {
                TextButton(onClick = { onFullDisable(10) }) {
                    Text("Turn off protection")
                }
            },
            dismissButton = {
                TextButton(onClick = onDismiss) {
                    Text("Cancel")
                }
            },
            title = { Text("Banking mode") },
            text = {
                Text("Banking mode turns protection OFF until you turn it back on. Your locked apps will be unlocked while it's off.")
            },
        )
        BankingModeStatus.Loading -> AlertDialog(
            onDismissRequest = {},
            confirmButton = {},
            icon = { CircularProgressIndicator() },
            title = { Text("Turning off protection") },
            text = { Text("Ellie is turning off its Accessibility service for banking mode.") },
        )
        BankingModeStatus.Success -> AlertDialog(
            onDismissRequest = onDismiss,
            confirmButton = {
                TextButton(onClick = onOpenAccessibilitySettings) {
                    Text("Open accessibility settings")
                }
            },
            dismissButton = {
                TextButton(onClick = onDismiss) {
                    Text("I'll do it later")
                }
            },
            title = { Text("Protection is off") },
            text = { Text("When you're done, open Accessibility settings, find Ellie in the list, and turn it on.") },
        )
    }
}

private fun ProtectionBanner(onFix: () -> Unit) {
    val shape = RoundedCornerShape(RadiusMd)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(MaterialTheme.colorScheme.errorContainer, shape)
            .padding(18.dp),
    ) {
        Text(
            text = "Protection is off",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onErrorContainer,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = "Locked apps open freely until you turn Ellie back on in Accessibility.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onErrorContainer,
        )
        Spacer(Modifier.height(14.dp))
        Text(
            text = "Turn on",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onError,
            modifier = Modifier
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.error, CircleShape)
                .clickable(onClick = onFix)
                .padding(horizontal = 20.dp, vertical = 10.dp),
        )
    }
}

private fun BatteryWarningBanner(onFix: () -> Unit) {
    val shape = RoundedCornerShape(RadiusMd)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(MaterialTheme.colorScheme.tertiaryContainer, shape)
            .padding(18.dp),
    ) {
        Text(
            text = "Battery restrictions active",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onTertiaryContainer,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = "Your phone may stop Ellie in the background. Exempt Ellie from battery optimizations for reliable locking.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onTertiaryContainer,
        )
        Spacer(Modifier.height(14.dp))
        Text(
            text = "Fix battery settings",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onTertiary,
            modifier = Modifier
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.tertiary, CircleShape)
                .clickable(onClick = onFix)
                .padding(horizontal = 20.dp, vertical = 10.dp),
        )
    }
}
