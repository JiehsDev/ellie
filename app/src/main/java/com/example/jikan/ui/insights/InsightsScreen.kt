package com.example.jikan.ui.insights

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.jikan.screentime.ScreenTimeSummary
import com.example.jikan.ui.coach.JikanCoachMessage
import com.example.jikan.ui.home.IconChip
import com.example.jikan.ui.home.StudyDay
import com.example.jikan.ui.home.WeekStrip
import com.example.jikan.ui.theme.JikanTheme
import java.time.LocalDate

/**
 * Phase 11: a dedicated screen explaining screen-time patterns.
 *
 * Every number here comes from [ScreenTimeSummary], calculated
 * deterministically by ScreenTimeCalculator. Nothing on this screen is
 * computed by AI; the coach observation is the same deterministic provider
 * message Home uses.
 */
@Composable
fun InsightsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: InsightsViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    InsightsScreenContent(
        state = state,
        onBack = onBack,
        onOpenUsageSettings = {
            context.startActivity(
                Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        },
        modifier = modifier,
    )
}

@Composable
private fun InsightsScreenContent(
    state: InsightsUiState,
    onBack: () -> Unit,
    onOpenUsageSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp),
        ) {
            IconChip(onClick = onBack) {
                Text("‹", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onBackground)
            }
        }

        if (state.isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return
        }

        val summary = state.summary
        if (!state.hasUsageAccess || summary == null) {
            Column(modifier = Modifier.padding(horizontal = 24.dp)) {
                Text(text = "Insights", style = MaterialTheme.typography.headlineSmall)
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Ellie needs usage access to explain your screen-time patterns. " +
                        "Grant it in system settings, then come back.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(16.dp))
                Text(
                    text = "Open usage settings",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary, CircleShape)
                        .clickable(onClick = onOpenUsageSettings)
                        .padding(horizontal = 20.dp, vertical = 10.dp),
                )
            }
            return
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
        ) {
            Text(text = "Insights", style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(4.dp))
            Text(
                text = "Your screen-time patterns, calculated on-device.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (state.coachMessage.isNotBlank()) {
                Spacer(Modifier.height(18.dp))
                InsightsSectionHeader(title = "COACH INSIGHT")
                Spacer(Modifier.height(10.dp))
                JikanCoachMessage(message = state.coachMessage)
            }

            Spacer(Modifier.height(24.dp))
            TodaySection(summary = summary)

            Spacer(Modifier.height(24.dp))
            TopAppsSection(summary = summary)

            Spacer(Modifier.height(24.dp))
            LimitsSection(summary = summary)

            Spacer(Modifier.height(24.dp))
            EarningBreakdownSection(contributions = state.earningContributions)

            if (summary.weeklyTrend.isNotEmpty()) {
                Spacer(Modifier.height(24.dp))
                WeekStrip(
                    week = summary.weeklyTrend.map { day ->
                        StudyDay(
                            initial = LocalDate.ofEpochDay(day.epochDay).dayOfWeek.name.take(1),
                            minutes = day.minutes,
                            isToday = day.epochDay == summary.epochDay,
                        )
                    },
                    title = "WEEK",
                )
            }

            Spacer(Modifier.height(24.dp))
            ComparisonSection(summary = summary)
        }
    }
}

@Composable
private fun InsightsSectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun InsightRow(
    label: String,
    value: String,
    valueColor: Color = MaterialTheme.colorScheme.onBackground,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = valueColor,
        )
    }
}

@Composable
private fun TodaySection(summary: ScreenTimeSummary) {
    Column {
        InsightsSectionHeader(title = "TODAY")
        Spacer(Modifier.height(12.dp))
        InsightRow(label = "Total screen time", value = formatMinutes(summary.totalScreenTimeMinutes))
        Spacer(Modifier.height(8.dp))
        InsightRow(label = "Restricted-app time", value = formatMinutes(summary.restrictedAppMinutes))
        Spacer(Modifier.height(8.dp))
        InsightRow(label = "Earning-app time", value = formatMinutes(summary.earningAppMinutes))
        Spacer(Modifier.height(8.dp))
        InsightRow(
            label = "Minutes earned",
            value = "+${summary.earnedMinutes}m",
            valueColor = MaterialTheme.colorScheme.secondary,
        )
        Spacer(Modifier.height(8.dp))
        InsightRow(label = "Minutes spent", value = "${summary.spentMinutes}m")
    }
}

@Composable
private fun TopAppsSection(summary: ScreenTimeSummary) {
    val topApps = summary.topApps.take(5)
    Column {
        InsightsSectionHeader(title = "TOP APPS")
        Spacer(Modifier.height(12.dp))
        if (topApps.isEmpty()) {
            Text(
                text = "No app usage recorded today.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            topApps.forEachIndexed { index, app ->
                InsightRow(
                    label = "${index + 1}. ${app.appLabel}",
                    value = formatMinutes(app.minutes),
                )
                if (index < topApps.lastIndex) Spacer(Modifier.height(8.dp))
            }
        }
    }
}

@Composable
private fun LimitsSection(summary: ScreenTimeSummary) {
    Column {
        InsightsSectionHeader(title = "LIMITS")
        Spacer(Modifier.height(12.dp))
        LimitGroup(
            title = "Approaching",
            rows = summary.approachingApps.map {
                "${it.appLabel} · ${it.minutes}/${it.limitMinutes}m · ${it.remainingMinutes}m left"
            },
            color = MaterialTheme.colorScheme.secondary,
        )
        LimitGroup(
            title = "At limit",
            rows = summary.atLimitApps.map { "${it.appLabel} · ${it.limitMinutes}m" },
            color = MaterialTheme.colorScheme.tertiary,
        )
        LimitGroup(
            title = "Exceeded",
            rows = summary.exceededApps.map {
                "${it.appLabel} · ${it.minutes}/${it.limitMinutes}m · +${it.overLimitMinutes}m"
            },
            color = MaterialTheme.colorScheme.error,
        )
        if (summary.approachingApps.isEmpty() && summary.atLimitApps.isEmpty() && summary.exceededApps.isEmpty()) {
            Text(
                text = "No limits set yet.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun LimitGroup(title: String, rows: List<String>, color: Color) {
    if (rows.isEmpty()) return
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.size(8.dp).background(color, CircleShape))
        Spacer(Modifier.width(8.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )
    }
    Spacer(Modifier.height(8.dp))
    rows.forEach { row ->
        Text(
            text = row,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(6.dp))
    }
    Spacer(Modifier.height(6.dp))
}

@Composable
private fun EarningBreakdownSection(contributions: List<EarningContribution>) {
    Column {
        InsightsSectionHeader(title = "EARNING")
        Spacer(Modifier.height(12.dp))
        if (contributions.isEmpty()) {
            Text(
                text = "No minutes earned today yet.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            contributions.forEachIndexed { index, contribution ->
                InsightRow(
                    label = contribution.appLabel,
                    value = "+${contribution.earnedMinutes}m earned",
                    valueColor = MaterialTheme.colorScheme.secondary,
                )
                if (index < contributions.lastIndex) Spacer(Modifier.height(8.dp))
            }
        }
    }
}

@Composable
private fun ComparisonSection(summary: ScreenTimeSummary) {
    Column {
        InsightsSectionHeader(title = "COMPARISON")
        Spacer(Modifier.height(12.dp))
        InsightRow(
            label = "Today vs yesterday",
            value = describeDayComparison(summary.recentUsageChange, summary.previousDayScreenTimeMinutes),
        )
        Spacer(Modifier.height(8.dp))
        InsightRow(
            label = "Today vs 7-day average",
            value = describeAverageComparison(summary.averageUsageChange),
        )
    }
}

@androidx.compose.ui.tooling.preview.Preview(showBackground = true)
@Composable
private fun InsightsScreenPreview() {
    JikanTheme {
        InsightsScreenContent(
            state = InsightsUiState(
                summary = com.example.jikan.screentime.ScreenTimeSummary(
                    epochDay = 10L,
                    totalScreenTimeMinutes = 201,
                    previousDayScreenTimeMinutes = 224,
                    averageDailyScreenTimeMinutes = 210,
                    restrictedAppMinutes = 134,
                    earningAppMinutes = 31,
                    earnedMinutes = 15,
                    spentMinutes = 20,
                    walletBalanceMinutes = 10,
                    topApps = listOf(
                        com.example.jikan.screentime.ScreenTimeAppUsage(
                            "com.youtube", "YouTube", 71, 35.3f,
                            com.example.jikan.screentime.AppUsageStatus.GENERAL,
                        ),
                        com.example.jikan.screentime.ScreenTimeAppUsage(
                            "com.tiktok", "TikTok", 42, 20.9f,
                            com.example.jikan.screentime.AppUsageStatus.GENERAL,
                        ),
                    ),
                    exceededApps = emptyList(),
                    recentUsageChange = com.example.jikan.screentime.UsageChange(
                        -23, -10.3f, com.example.jikan.screentime.UsageChangeDirection.DOWN,
                    ),
                    approachingApps = listOf(
                        com.example.jikan.screentime.ApproachingAppUsage("com.tiktok", "TikTok", 24, 30, 6, 80f),
                    ),
                    weeklyTrend = listOf(
                        com.example.jikan.screentime.DailyUsage(4L, 180),
                        com.example.jikan.screentime.DailyUsage(5L, 240),
                        com.example.jikan.screentime.DailyUsage(6L, 150),
                        com.example.jikan.screentime.DailyUsage(7L, 300),
                        com.example.jikan.screentime.DailyUsage(8L, 190),
                        com.example.jikan.screentime.DailyUsage(9L, 224),
                        com.example.jikan.screentime.DailyUsage(10L, 201),
                    ),
                    earningApps = listOf(
                        com.example.jikan.screentime.ScreenTimeAppUsage(
                            "com.duolingo", "Duolingo", 6, 3f,
                            com.example.jikan.screentime.AppUsageStatus.EARNING,
                        ),
                    ),
                ),
                earningContributions = listOf(
                    EarningContribution("com.duolingo", "Duolingo", 10),
                ),
                coachMessage = "You've used 23 fewer minutes than yesterday. Quiet progress — I noticed.",
                hasUsageAccess = true,
                isLoading = false,
            ),
            onBack = {},
            onOpenUsageSettings = {},
        )
    }
}
