package com.example.jikan.ui.home

import android.content.Intent
import android.provider.Settings
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.Canvas
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.jikan.ui.theme.JikanTheme
import com.example.jikan.ui.theme.PillButton
import com.example.jikan.ui.theme.RadiusMd
import com.example.jikan.ui.theme.RadiusSm
import com.example.jikan.ui.theme.neoRaised
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun HomeScreen(
    onStudyNow: () -> Unit,
    onOpenLockedApps: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: HomeViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current

    // The user leaves the app to flip the accessibility toggle, so re-check on return.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refreshProtectionStatus() }

    HomeScreenContent(
        state = state,
        onStudyNow = onStudyNow,
        onOpenLockedApps = onOpenLockedApps,
        onFixProtection = {
            context.startActivity(
                Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        },
        modifier = modifier,
    )
}

@Composable
private fun HomeScreenContent(
    state: HomeUiState,
    onStudyNow: () -> Unit,
    onOpenLockedApps: () -> Unit,
    onFixProtection: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Rendering the default state before the first read would claim zero minutes, no
    // streak and no locked apps — indistinguishable from having lost everything.
    if (state.isLoading) {
        Box(modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.background))
        return
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp)
            .padding(top = 8.dp, bottom = 32.dp),
    ) {
        Greeting(userName = state.userName)

        if (!state.protectionOn) {
            Spacer(Modifier.height(20.dp))
            ProtectionBanner(onFix = onFixProtection)
        }

        Spacer(Modifier.height(28.dp))
        TimeDial(
            minutes = state.walletBalanceMinutes,
            maxMinutes = state.maxBalanceMinutes,
            modifier = Modifier.align(Alignment.CenterHorizontally),
        )

        Spacer(Modifier.height(20.dp))
        PillButton(
            text = if (state.dueCount > 0) "Study now · ${state.dueCount} due" else "Study now",
            onClick = onStudyNow,
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(28.dp))
        StatLedger(state = state)

        Spacer(Modifier.height(28.dp))
        LockedAppsSection(
            apps = state.lockedApps,
            lockedCount = state.lockedAppCount,
            onEdit = onOpenLockedApps,
        )

        Spacer(Modifier.height(28.dp))
        WeekStrip(week = state.week)
    }
}

@Composable
private fun Greeting(userName: String) {
    val today = LocalDate.now()
    val hour = LocalTime.now().hour
    val partOfDay = when {
        hour < 12 -> "Good morning"
        hour < 18 -> "Good afternoon"
        else -> "Good evening"
    }
    Column {
        Text(
            text = today.format(DateTimeFormatter.ofPattern("EEEE, d MMMM", Locale.getDefault())).uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = if (userName.isBlank()) partOfDay else "$partOfDay, $userName",
            style = MaterialTheme.typography.headlineMedium,
        )
    }
}

@Composable
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
            text = "Locked apps open freely until you turn Jikan back on in Accessibility.",
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

/**
 * The screen's one bold element: minutes left drawn as a depleting ring, echoing the
 * hourglass in the app's mark. The arc makes the 60-minute ceiling legible in a way a
 * bare number can't, and turns vermillion when the balance is nearly gone.
 */
@Composable
private fun TimeDial(
    minutes: Int,
    maxMinutes: Int,
    modifier: Modifier = Modifier,
) {
    val fraction = if (maxMinutes > 0) (minutes / maxMinutes.toFloat()).coerceIn(0f, 1f) else 0f
    val sweep by animateFloatAsState(
        targetValue = fraction,
        animationSpec = tween(durationMillis = 900),
        label = "dialSweep",
    )
    val isLow = minutes <= 10
    val arcColor = if (isLow) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary
    val trackColor = MaterialTheme.colorScheme.surfaceVariant

    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = modifier) {
        Box(contentAlignment = Alignment.Center) {
            Canvas(
                modifier = Modifier
                    .size(212.dp)
                    .neoRaised(CircleShape, elevation = 10.dp)
                    .background(MaterialTheme.colorScheme.background, CircleShape),
            ) {
                val stroke = 18.dp.toPx()
                val inset = stroke / 2 + 18.dp.toPx()
                val arcSize = Size(size.width - inset * 2, size.height - inset * 2)
                val topLeft = Offset(inset, inset)
                drawArc(
                    color = trackColor,
                    startAngle = -90f,
                    sweepAngle = 360f,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = stroke, cap = StrokeCap.Round),
                )
                if (sweep > 0f) {
                    drawArc(
                        color = arcColor,
                        startAngle = -90f,
                        sweepAngle = 360f * sweep,
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcSize,
                        style = Stroke(width = stroke, cap = StrokeCap.Round),
                    )
                }
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "$minutes",
                    style = MaterialTheme.typography.displayLarge,
                    color = if (isLow) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onBackground,
                )
                Text(
                    text = "MINUTES LEFT",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        Text(
            text = when {
                minutes <= 0 -> "Study to earn your first minutes."
                minutes >= maxMinutes -> "Wallet full — $maxMinutes min is the cap."
                else -> "of $maxMinutes min max"
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun StatLedger(state: HomeUiState) {
    Column {
        Hairline()
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 18.dp),
        ) {
            LedgerStat(
                value = "${state.streakDays}",
                unit = if (state.streakDays == 1) "day" else "days",
                label = "STREAK",
                // Sage, not vermillion: vermillion means "running out" on the dial, and a
                // growing streak is the opposite of a warning.
                valueColor = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.weight(1f),
            )
            LedgerStat(
                value = "${state.minutesSpentToday}",
                unit = "min",
                label = "SPENT TODAY",
                valueColor = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.weight(1f),
            )
            LedgerStat(
                value = "${state.cardsLearned}",
                unit = "/ ${state.totalCards}",
                label = "LEARNED",
                valueColor = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f),
            )
        }
        Hairline()
    }
}

@Composable
private fun LedgerStat(
    value: String,
    unit: String,
    label: String,
    valueColor: Color,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(text = value, style = MaterialTheme.typography.titleLarge, color = valueColor)
            Spacer(Modifier.width(3.dp))
            Text(
                text = unit,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 3.dp),
            )
        }
        Spacer(Modifier.height(2.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun Hairline() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(MaterialTheme.colorScheme.outline),
    )
}

@Composable
private fun LockedAppsSection(apps: List<LockedAppChip>, lockedCount: Int, onEdit: () -> Unit) {
    Column {
        SectionHeader(
            title = "LOCKED APPS",
            action = if (lockedCount == 0) "Choose apps  →" else "Edit  →",
            onAction = onEdit,
        )
        Spacer(Modifier.height(14.dp))
        if (lockedCount == 0) {
            Text(
                text = "Nothing is locked yet, so nothing costs you time.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.clickable(onClick = onEdit),
            ) {
                apps.take(MAX_VISIBLE_APPS).forEach { app -> AppIcon(app) }
                if (apps.size > MAX_VISIBLE_APPS) {
                    Text(
                        text = "+${apps.size - MAX_VISIBLE_APPS}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun AppIcon(app: LockedAppChip) {
    val shape = RoundedCornerShape(RadiusSm)
    val icon = app.icon
    if (icon != null) {
        Image(
            bitmap = icon.toBitmap().asImageBitmap(),
            contentDescription = app.label,
            modifier = Modifier
                .size(42.dp)
                .clip(shape)
                .background(MaterialTheme.colorScheme.surfaceVariant, shape),
        )
    } else {
        Box(
            modifier = Modifier
                .size(42.dp)
                .background(MaterialTheme.colorScheme.surfaceVariant, shape),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = app.label.take(1).uppercase(),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun WeekStrip(week: List<StudyDay>) {
    val peak = (week.maxOfOrNull { it.minutes } ?: 0).coerceAtLeast(1)
    Column {
        SectionHeader(title = "STUDIED THIS WEEK", action = null, onAction = null)
        Spacer(Modifier.height(16.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom,
        ) {
            week.forEach { day ->
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        modifier = Modifier.height(BAR_TRACK_HEIGHT),
                        contentAlignment = Alignment.BottomCenter,
                    ) {
                        val ratio = day.minutes / peak.toFloat()
                        val barHeight = (BAR_TRACK_HEIGHT.value * ratio).dp.coerceAtLeast(4.dp)
                        Box(
                            modifier = Modifier
                                .width(22.dp)
                                .height(barHeight)
                                .clip(RoundedCornerShape(6.dp))
                                .background(
                                    when {
                                        // Warm family throughout: a faint tan mark for days with
                                        // nothing, warm grey for past study, indigo for today.
                                        day.minutes == 0 -> MaterialTheme.colorScheme.outline
                                        day.isToday -> MaterialTheme.colorScheme.primary
                                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                                    }
                                ),
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = day.initial,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (day.isToday) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String, action: String?, onAction: (() -> Unit)?) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (action != null && onAction != null) {
            Text(
                text = action,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.clickable(onClick = onAction),
            )
        }
    }
}

private const val MAX_VISIBLE_APPS = 5
private val BAR_TRACK_HEIGHT = 64.dp

@Preview(showBackground = true, heightDp = 900)
@Composable
private fun HomeScreenPreview() {
    JikanTheme {
        HomeScreenContent(
            state = HomeUiState(
                userName = "Jieh",
                walletBalanceMinutes = 19,
                streakDays = 7,
                cardsLearned = 42,
                totalCards = 71,
                dueCount = 8,
                minutesSpentToday = 12,
                lockedApps = listOf(
                    LockedAppChip("com.a", "Loopy", null),
                    LockedAppChip("com.b", "Chatterbox", null),
                    LockedAppChip("com.c", "Scrollr", null),
                ),
                lockedAppCount = 3,
                isLoading = false,
                week = listOf(
                    StudyDay("M", 8, false),
                    StudyDay("T", 14, false),
                    StudyDay("W", 5, false),
                    StudyDay("T", 0, false),
                    StudyDay("F", 18, false),
                    StudyDay("S", 11, false),
                    StudyDay("S", 6, true),
                ),
                protectionOn = false,
            ),
            onStudyNow = {},
            onOpenLockedApps = {},
            onFixProtection = {},
        )
    }
}
