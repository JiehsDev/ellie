package com.example.jikan.ui.settings

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.jikan.data.InstalledAppInfo
import com.example.jikan.data.LockTier
import com.example.jikan.service.PauseManager
import com.example.jikan.ui.home.BankingModeStatus
import com.example.jikan.ui.theme.IconChip
import com.example.jikan.ui.theme.JikanTheme
import com.example.jikan.ui.theme.RadiusSm

@Composable
fun LockedAppsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    openBankingModeOnStart: Boolean = false,
    viewModel: LockedAppsViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsState()
    val context = androidx.compose.ui.platform.LocalContext.current
    LaunchedEffect(openBankingModeOnStart) {
        if (openBankingModeOnStart) {
            viewModel.disableLockingForBanking()
        }
    }
    LockedAppsScreenContent(
        state = state,
        onBack = onBack,
        onToggle = viewModel::toggle,
        onSetTier = viewModel::setTier,
        onDisableLockingForBanking = viewModel::disableLockingForBanking,
        onStartBankingAllowlist = viewModel::startBankingAllowlist,
        onStartFullDisableBankingMode = viewModel::startFullDisableBankingMode,
        onDismissBankingModeStatus = viewModel::dismissBankingModeStatus,
        onOpenAccessibilitySettings = { PauseManager.openAccessibilitySettings(context) },
        onSetStrictModeEnabled = viewModel::setStrictModeEnabled,
        modifier = modifier,
    )
}

@Composable
private fun LockedAppsScreenContent(
    state: LockedAppsUiState,
    onBack: () -> Unit,
    onToggle: (InstalledAppInfo) -> Unit,
    onSetTier: (InstalledAppInfo, LockTier) -> Unit,
    onDisableLockingForBanking: () -> Unit,
    onStartBankingAllowlist: (String, String, Int) -> Unit,
    onStartFullDisableBankingMode: (Int) -> Unit,
    onDismissBankingModeStatus: () -> Unit,
    onOpenAccessibilitySettings: () -> Unit,
    onSetStrictModeEnabled: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val lockedApps = state.apps.filter { it.packageName in state.lockedPackages }
    BankingModeDialog(
        status = state.bankingModeStatus,
        lockedApps = lockedApps,
        onDismiss = onDismissBankingModeStatus,
        onAllowApp = onStartBankingAllowlist,
        onFullDisable = onStartFullDisableBankingMode,
        onOpenAccessibilitySettings = onOpenAccessibilitySettings,
    )

    Column(modifier = modifier.fillMaxSize()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp),
        ) {
            IconChip(onClick = onBack) {
                Text("‹", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onBackground)
            }
            Spacer(Modifier.weight(1f))
            IconChip(onClick = onDisableLockingForBanking) {
                Text(
                    text = "$",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                )
            }
        }

        Column(modifier = Modifier.padding(horizontal = 24.dp)) {
            Text(text = "Locked apps", style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(4.dp))
            Text(
                text = "Choose which apps require a lesson to open, and set their restriction tier.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(18.dp))
            StrictModeRow(
                enabled = state.strictModeEnabled,
                graceUntilMs = state.strictGraceUntilMs,
                lastViolationAtMs = state.lastViolationAtMs,
                onToggle = onSetStrictModeEnabled,
            )
        }

        if (state.isLoading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            LazyColumn(contentPadding = PaddingValues(horizontal = 24.dp, vertical = 16.dp)) {
                items(state.apps, key = { it.packageName }) { app ->
                    val isLocked = app.packageName in state.lockedPackages
                    val currentTier = state.lockedAppTiers[app.packageName] ?: LockTier.EXTREME
                    AppToggleRow(
                        app = app,
                        isLocked = isLocked,
                        currentTier = currentTier,
                        onToggle = { onToggle(app) },
                        onSetTier = { tier -> onSetTier(app, tier) },
                    )
                }
            }
        }
    }
}

@Composable
private fun StrictModeRow(
    enabled: Boolean,
    graceUntilMs: Long,
    lastViolationAtMs: Long,
    onToggle: (Boolean) -> Unit,
) {
    val now = System.currentTimeMillis()
    val status = when {
        !enabled -> "Off"
        graceUntilMs > now -> "Grace ${((graceUntilMs - now) / 60_000L).coerceAtLeast(1)}m"
        lastViolationAtMs > 0L -> "Violation recorded"
        else -> "Healthy"
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = "Strict mode", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(2.dp))
            Text(
                text = status,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(
            checked = enabled,
            onCheckedChange = onToggle,
            colors = SwitchDefaults.colors(
                checkedTrackColor = MaterialTheme.colorScheme.primary,
                checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant,
                uncheckedThumbColor = MaterialTheme.colorScheme.background,
                uncheckedBorderColor = MaterialTheme.colorScheme.outline,
            ),
        )
    }
}

@Composable
private fun BankingModeDialog(
    status: BankingModeStatus,
    lockedApps: List<InstalledAppInfo>,
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

@Composable
private fun AppToggleRow(
    app: InstalledAppInfo,
    isLocked: Boolean,
    currentTier: LockTier,
    onToggle: () -> Unit,
    onSetTier: (LockTier) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            val icon = app.icon
            val iconShape = RoundedCornerShape(RadiusSm)
            if (icon != null) {
                Image(
                    bitmap = icon.toBitmap().asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier
                        .size(44.dp)
                        .background(MaterialTheme.colorScheme.surfaceVariant, iconShape),
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .background(MaterialTheme.colorScheme.surfaceVariant, iconShape),
                )
            }
            Spacer(Modifier.width(14.dp))
            Text(
                text = app.label,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
            )
            Switch(
                checked = isLocked,
                onCheckedChange = { onToggle() },
                colors = SwitchDefaults.colors(
                    checkedTrackColor = MaterialTheme.colorScheme.primary,
                    checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                    uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant,
                    uncheckedThumbColor = MaterialTheme.colorScheme.background,
                    uncheckedBorderColor = MaterialTheme.colorScheme.outline,
                ),
            )
        }
        if (isLocked) {
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 58.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                LockTier.entries.forEach { tier ->
                    val selected = tier == currentTier
                    val text = when (tier) {
                        LockTier.LIGHT -> "Light"
                        LockTier.AVERAGE -> "Average"
                        LockTier.EXTREME -> "Extreme"
                    }
                    Text(
                        text = text,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant, CircleShape)
                            .clickable(onClick = { onSetTier(tier) })
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                    )
                }
            }
        }
    }
}

@androidx.compose.ui.tooling.preview.Preview(showBackground = true)
@Composable
private fun LockedAppsScreenPreview() {
    JikanTheme {
        LockedAppsScreenContent(
            state = LockedAppsUiState(
                apps = listOf(
                    InstalledAppInfo("com.example.loopy", "Loopy", null),
                    InstalledAppInfo("com.example.chatterbox", "Chatterbox", null),
                ),
                lockedPackages = setOf("com.example.loopy"),
                lockedAppTiers = mapOf("com.example.loopy" to LockTier.AVERAGE),
                isLoading = false,
            ),
            onBack = {},
            onToggle = {},
            onSetTier = { _, _ -> },
            onDisableLockingForBanking = {},
            onStartBankingAllowlist = { _, _, _ -> },
            onStartFullDisableBankingMode = {},
            onDismissBankingModeStatus = {},
            onOpenAccessibilitySettings = {},
            onSetStrictModeEnabled = {},
        )
    }
}
