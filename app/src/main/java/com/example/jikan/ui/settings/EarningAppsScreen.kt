package com.example.jikan.ui.settings

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.jikan.data.EarnRule
import com.example.jikan.data.InstalledAppInfo
import com.example.jikan.ui.theme.IconChip
import com.example.jikan.ui.theme.JikanTheme
import com.example.jikan.ui.theme.RadiusSm

@Composable
fun EarningAppsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: EarningAppsViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsState()
    EarningAppsScreenContent(
        state = state,
        onBack = onBack,
        onSelectApp = viewModel::openEditor,
        onToggleEnabled = viewModel::setRuleEnabled,
        onEditorChange = viewModel::updateEditor,
        onSaveEditor = viewModel::saveEditor,
        onDeleteEditor = viewModel::deleteEditingRule,
        onDismissEditor = viewModel::dismissEditor,
        modifier = modifier,
    )
}

@Composable
private fun EarningAppsScreenContent(
    state: EarningAppsUiState,
    onBack: () -> Unit,
    onSelectApp: (InstalledAppInfo) -> Unit,
    onToggleEnabled: (EarnRule, Boolean) -> Unit,
    onEditorChange: (EarningEditorState) -> Unit,
    onSaveEditor: () -> Unit,
    onDeleteEditor: () -> Unit,
    onDismissEditor: () -> Unit,
    modifier: Modifier = Modifier,
) {
    state.editor?.let { editor ->
        EarningRuleEditorDialog(
            editor = editor,
            onChange = onEditorChange,
            onSave = onSaveEditor,
            onDelete = if (editor.ruleId != null) onDeleteEditor else null,
            onDismiss = onDismissEditor,
        )
    }

    Column(modifier = modifier.fillMaxSize()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp),
        ) {
            IconChip(onClick = onBack) {
                Text("‹", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onBackground)
            }
        }

        Column(modifier = Modifier.padding(horizontal = 24.dp)) {
            Text(text = "Earning apps", style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(4.dp))
            Text(
                text = "Pick apps that earn you minutes. Jikan measures foreground time — " +
                    "how long the app stays open on your screen. It can't tell whether " +
                    "you finished a lesson, only that the app was active.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        if (state.isLoading) {
            Spacer(Modifier.height(32.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                CircularProgressIndicator()
            }
        } else {
            LazyColumn(contentPadding = PaddingValues(horizontal = 24.dp, vertical = 16.dp)) {
                items(state.apps, key = { it.packageName }) { app ->
                    val rule = state.rules[app.packageName]
                    val progress = rule?.let { state.todayProgress[it.id] }
                    EarningAppRow(
                        app = app,
                        rule = rule,
                        progress = progress,
                        onSelect = { onSelectApp(app) },
                        onToggle = { enabled -> rule?.let { onToggleEnabled(it, enabled) } },
                    )
                }
            }
        }
    }
}

@Composable
private fun EarningAppRow(
    app: InstalledAppInfo,
    rule: EarnRule?,
    progress: EarningRuleProgress?,
    onSelect: () -> Unit,
    onToggle: (Boolean) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onSelect)
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
                Spacer(
                    modifier = Modifier
                        .size(44.dp)
                        .background(MaterialTheme.colorScheme.surfaceVariant, iconShape),
                )
            }
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = app.label,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Spacer(Modifier.height(2.dp))
                if (rule != null) {
                    Text(
                        text = rule.describe(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (progress != null) {
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = "Today: ${progress.earnedTodayMinutes}m earned · " +
                                "${progress.rewardsRemaining} rewards left",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else {
                    Text(
                        text = "Tap to set up earning",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (rule != null) {
                Switch(
                    checked = rule.enabled,
                    onCheckedChange = onToggle,
                    colors = SwitchDefaults.colors(
                        checkedTrackColor = MaterialTheme.colorScheme.primary,
                        checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                        uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant,
                        uncheckedThumbColor = MaterialTheme.colorScheme.background,
                        uncheckedBorderColor = MaterialTheme.colorScheme.outline,
                    ),
                )
            } else {
                Text(
                    text = "Add",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

@Composable
private fun EarningRuleEditorDialog(
    editor: EarningEditorState,
    onChange: (EarningEditorState) -> Unit,
    onSave: () -> Unit,
    onDelete: (() -> Unit)?,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(editor.appLabel) },
        text = {
            Column {
                EarningNumberRow(
                    label = "Earn:",
                    value = editor.rewardText,
                    suffix = "minutes",
                    onValueChange = { onChange(editor.copy(rewardText = it.filter { c -> c.isDigit() }.take(4))) },
                )
                Spacer(Modifier.height(10.dp))
                EarningNumberRow(
                    label = "For:",
                    value = editor.requiredText,
                    suffix = "minutes of active use",
                    onValueChange = { onChange(editor.copy(requiredText = it.filter { c -> c.isDigit() }.take(4))) },
                )
                Spacer(Modifier.height(10.dp))
                EarningNumberRow(
                    label = "Daily maximum:",
                    value = editor.dailyText,
                    suffix = "minutes",
                    onValueChange = { onChange(editor.copy(dailyText = it.filter { c -> c.isDigit() }.take(4))) },
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    text = "Jikan counts foreground time only — it can't tell whether you finished a lesson.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (editor.error != null) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = editor.error,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onSave) {
                Text("Save")
            }
        },
        dismissButton = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (onDelete != null) {
                    TextButton(onClick = onDelete) {
                        Text("Delete", color = MaterialTheme.colorScheme.error)
                    }
                }
                TextButton(onClick = onDismiss) {
                    Text("Cancel")
                }
            }
        },
    )
}

@Composable
private fun EarningNumberRow(
    label: String,
    value: String,
    suffix: String,
    onValueChange: (String) -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.width(120.dp),
        )
        TextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            textStyle = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.width(76.dp),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = suffix,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
    }
}

@androidx.compose.ui.tooling.preview.Preview(showBackground = true)
@Composable
private fun EarningAppsScreenPreview() {
    JikanTheme {
        EarningAppsScreenContent(
            state = EarningAppsUiState(
                apps = listOf(
                    InstalledAppInfo("com.duolingo", "Duolingo", null),
                    InstalledAppInfo("com.example.reader", "Reader", null),
                ),
                rules = mapOf(
                    "com.duolingo" to EarnRule(
                        id = 1L,
                        packageName = "com.duolingo",
                        requiredMinutes = 2,
                        rewardMinutes = 5,
                        dailyLimitMinutes = 20,
                        enabled = true,
                    ),
                ),
                todayProgress = mapOf(
                    1L to EarningRuleProgress(earnedTodayMinutes = 10, rewardsRemaining = 2),
                ),
                isLoading = false,
            ),
            onBack = {},
            onSelectApp = {},
            onToggleEnabled = { _, _ -> },
            onEditorChange = {},
            onSaveEditor = {},
            onDeleteEditor = {},
            onDismissEditor = {},
        )
    }
}
