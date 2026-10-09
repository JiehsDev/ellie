package com.example.jikan.ui.rules

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoonStar
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.VideogameAsset
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.jikan.R

/**
 * Stitch reference: Credit Conversion Rules. Explains the real earning
 * and spending mechanics. Mechanisms that don't exist yet are badged
 * "Planned" rather than presented as working.
 */
@Composable
fun RulesScreen(
    onBack: () -> Unit,
    onOpenEarningApps: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: RulesViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .padding(top = 12.dp, bottom = 28.dp),
    ) {
        // Header.
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Filled.ArrowBack,
                contentDescription = "Back",
                tint = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onBack)
                    .padding(8.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = "Credit Conversion Rules",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.weight(1f),
            )
            Icon(
                imageVector = Icons.Filled.Tune,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp),
            )
            Spacer(Modifier.width(12.dp))
            Image(
                painter = painterResource(R.drawable.mascot_jikan_coach),
                contentDescription = "JikanCoach",
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
            )
        }

        Spacer(Modifier.height(14.dp))

        // System calibration.
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(MaterialTheme.colorScheme.surface)
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Image(
                painter = painterResource(R.drawable.mascot_jikan_coach),
                contentDescription = null,
                modifier = Modifier.size(40.dp),
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "SYSTEM CALIBRATION",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                letterSpacing = 1.sp,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = "The deliberate mechanics behind your study-to-unlock equilibrium.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
                lineHeight = 21.sp,
            )
        }

        Spacer(Modifier.height(12.dp))

        // Pacing algorithm.
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(MaterialTheme.colorScheme.surface)
                .padding(20.dp),
        ) {
            Text(
                text = "PACING ALGORITHM",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                letterSpacing = 1.sp,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Correct Recalls × 2 Minutes + Bonuses = Screen Credit",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                textDecoration = TextDecoration.Underline,
                lineHeight = 26.sp,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Engineered with cognitive rhythm principles: rewards sustainable habit flow while safeguarding against dopamine addiction.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 18.sp,
            )
        }

        Spacer(Modifier.height(12.dp))

        // Interactive estimator.
        EstimatorCard(state = state, viewModel = viewModel)

        Spacer(Modifier.height(20.dp))

        // Earning mechanisms.
        SectionHeader(
            title = "Earning Mechanisms",
            subtitle = "Verified pathways to generate distraction allowances.",
            badge = "${listOf(true, state.hasEarningApps).count { it }} Active",
        )
        Spacer(Modifier.height(12.dp))
        EarningCard(
            icon = Icons.Filled.Refresh,
            title = "Spaced Repetition Reviews",
            badge = "Core Engine",
            badgeActive = true,
            subtitle = "Built-in flashcard decks",
            rows = listOf(
                "Base Rate" to "2m credit per correct answer",
                "Perfect Bonus" to "+5m for a flawless session",
                "Streak Bonus" to "1.1x from day 2 onward",
            ),
            footnote = "A 10-minute review at 80% recall yields ~${viewModel.estimatorCredits(state.copy(estimatorMinutes = 10, estimatorAccuracy = 80))}m unlock balance.",
        )
        Spacer(Modifier.height(10.dp))
        EarningCard(
            icon = Icons.Filled.VideogameAsset,
            title = "Verified Learning Apps",
            badge = "Auto-Sync",
            badgeActive = state.hasEarningApps,
            subtitle = if (state.hasEarningApps) "${state.earningAppCount} earning apps configured"
            else "No earning apps yet — tap to set up",
            rows = listOf(
                "Earn Ratio" to "Per-app, e.g. 1:1 practice to credit",
                "Daily Cap" to "Per-app daily maximum",
            ),
            footnote = "Encourages diverse learning formats without binge behavior.",
            onClick = onOpenEarningApps,
        )
        Spacer(Modifier.height(10.dp))
        EarningCard(
            icon = Icons.Filled.HourglassEmpty,
            title = "Deep Focus Blocks",
            badge = "Planned",
            badgeActive = false,
            subtitle = "Hardware-monitored Pomodoro",
            rows = emptyList(),
            footnote = "Timed focus sessions with phone-face-down detection are on the roadmap, not available yet.",
        )

        Spacer(Modifier.height(20.dp))

        // Consumption mechanics.
        SectionHeader(
            title = "Consumption Mechanics",
            subtitle = "How screen credits are safely and transparently spent.",
            badge = null,
        )
        Spacer(Modifier.height(12.dp))
        MechanismRow(
            icon = Icons.Filled.Timer,
            title = "Real-Time 1:1 Drain",
            body = "Allowance is consumed minute-by-minute while a restricted app is in the foreground. Locked apps need available balance to open.",
        )
        Spacer(Modifier.height(10.dp))
        MechanismRow(
            icon = Icons.Filled.Notifications,
            title = "2-Minute Gentle Grace Chime",
            body = "Planned: a soft chime 120 seconds before credits run dry. Not available yet.",
        )
        Spacer(Modifier.height(10.dp))
        MechanismRow(
            icon = Icons.Filled.Lock,
            title = "Automatic Restful Re-Lock",
            body = "At 0 minutes remaining, locked apps stay locked until you earn more. Bypassing requires a fresh study sprint.",
        )

        Spacer(Modifier.height(20.dp))

        // Wellbeing guardrails.
        SectionHeader(
            title = "Wellbeing Guardrails",
            subtitle = "Protections against obsessive credit accumulation.",
            badge = null,
        )
        Spacer(Modifier.height(12.dp))
        GuardrailRow(
            icon = Icons.Filled.Shield,
            title = "Daily Ceiling Cap",
            body = "Prevents compulsive over-studying",
            value = "${state.dailyCapMinutes}m",
        )
        Spacer(Modifier.height(10.dp))
        GuardrailRow(
            icon = Icons.Filled.MoonStar,
            title = "Sleep Lock Window",
            body = "Planned: distraction apps auto-lock overnight",
            value = "Off",
        )
        Spacer(Modifier.height(10.dp))
        GuardrailRow(
            icon = Icons.Filled.Refresh,
            title = "Balance Persistence",
            body = "Unused balance carries over, capped at ${state.dailyCapMinutes}m",
            value = "No reset",
        )

        Spacer(Modifier.height(16.dp))

        // Philosophy.
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(MaterialTheme.colorScheme.surface)
                .padding(20.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Filled.Info,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "The Philosophy of Ma (間)",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(10.dp))
            Text(
                text = "\"Unlocks are not trophies or bribes; they are conscious pauses carved out through focused dedication.\"",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
                lineHeight = 22.sp,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "By tying frivolous digital engagement directly to cognitive rigor, Jikan turns unconscious reflexive checking into an intentional choice.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 18.sp,
            )
        }

        Spacer(Modifier.height(16.dp))

        // Adjust button.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            Icon(
                imageVector = Icons.Filled.Tune,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = "Daily ceiling is fixed at ${state.dailyCapMinutes}m",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Earning ratios are configured per app under Study > Earning Apps.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun SectionHeader(title: String, subtitle: String, badge: String?) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (badge != null) {
            Text(
                text = badge,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.End,
                modifier = Modifier
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(horizontal = 10.dp, vertical = 6.dp),
            )
        }
    }
}

@Composable
private fun EstimatorCard(state: RulesUiState, viewModel: RulesViewModel) {
    val credits = viewModel.estimatorCredits(state)
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
                text = "Interactive Credit Estimator",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = "+${credits}m screen credit",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.tertiary,
            )
        }
        Spacer(Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Study:",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.width(56.dp),
            )
            Slider(
                value = state.estimatorMinutes.toFloat(),
                onValueChange = { viewModel.setEstimatorMinutes(it.toInt()) },
                valueRange = 5f..60f,
                modifier = Modifier.weight(1f),
                colors = SliderDefaults.colors(
                    thumbColor = MaterialTheme.colorScheme.secondary,
                    activeTrackColor = MaterialTheme.colorScheme.secondary,
                ),
            )
            Text(
                text = "${state.estimatorMinutes}m",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.width(44.dp),
                textAlign = TextAlign.End,
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Accuracy:",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.width(8.dp))
            listOf(50, 70, 90, 100).forEach { acc ->
                val selected = state.estimatorAccuracy == acc
                Text(
                    text = "$acc%",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = if (selected) MaterialTheme.colorScheme.onSecondary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(
                            if (selected) MaterialTheme.colorScheme.secondary
                            else MaterialTheme.colorScheme.surfaceVariant
                        )
                        .clickable { viewModel.setEstimatorAccuracy(acc) }
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                )
                Spacer(Modifier.width(6.dp))
            }
            Spacer(Modifier.weight(1f))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Filled.FlashOn,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(14.dp),
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    text = "Real-time",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun EarningCard(
    icon: ImageVector,
    title: String,
    badge: String,
    badgeActive: Boolean,
    subtitle: String,
    rows: List<Pair<String, String>>,
    footnote: String,
    onClick: (() -> Unit)? = null,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surface)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(18.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(22.dp),
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = badge,
                style = MaterialTheme.typography.labelSmall,
                color = if (badgeActive) MaterialTheme.colorScheme.onSurface
                else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(horizontal = 10.dp, vertical = 6.dp),
            )
        }
        if (rows.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(14.dp),
            ) {
                rows.forEach { (label, value) ->
                    Row(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            text = value,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface,
                            textAlign = TextAlign.End,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.Top) {
            Icon(
                imageVector = Icons.Filled.Info,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(14.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = footnote,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 18.sp,
            )
        }
    }
}

@Composable
private fun MechanismRow(icon: ImageVector, title: String, body: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(16.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = body,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 18.sp,
            )
        }
    }
}

@Composable
private fun GuardrailRow(icon: ImageVector, title: String, body: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = body,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 18.sp,
            )
        }
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}
