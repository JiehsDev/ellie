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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.jikan.data.AppRestriction
import com.example.jikan.data.InstalledAppInfo
import com.example.jikan.ui.theme.IconChip
import com.example.jikan.ui.theme.JikanTheme
import com.example.jikan.ui.theme.RadiusSm

@Composable
fun RestrictedAppsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: RestrictedAppsViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsState()
    RestrictedAppsScreenContent(
        state = state,
        onBack = onBack,
        onSelectApp = viewModel::openEditor,
        onToggleEnabled = viewModel::setRestrictionEnabled,
        onEditorChange = viewModel::updateEditor,
        onSaveEditor = viewModel::saveEditor,
        onDeleteEditor = viewModel::deleteEditingRestriction,
        onDismissEditor = viewModel::dismissEditor,
        modifier = modifier,
    )
}

@Composable
private fun RestrictedAppsScreenContent(
    state: RestrictedAppsUiState,
    onBack: () -> Unit,
    onSelectApp: (InstalledAppInfo) -> Unit,
    onToggleEnabled: (AppRestriction, Boolean) -> Unit,
    onEditorChange: (RestrictionEditorState) -> Unit,
    onSaveEditor: () -> Unit,
    onDeleteEditor: () -> Unit,
    onDismissEditor: () -> Unit,
    modifier: Modifier = Modifier,
) {
    state.editor?.let { editor ->
        RestrictionEditorDialog(
            editor = editor,
            onChange = onEditorChange,
            onSave = onSaveEditor,
            onDelete = if (editor.isNew) null else onDeleteEditor,
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
            Text(text = "App limits", style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(4.dp))
            Text(
                text = "Set a daily limit for any app. When the limit is reached, Jikan's " +
                    "protection steps in automatically — the same deterministic rules " +
                    "every time, never an AI decision.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (!state.hasUsageAccess && !state.isLoading) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Usage access isn't granted, so today's usage can't be shown yet.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        if (state.isLoading) {
            Spacer(Modifier.height(32.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                CircularProgressIndicator()
            }
        } else {
            LazyColumn(contentPadding = PaddingValues(horizontal = 24.dp, vertical = 16.dp)) {
                items(state.apps, key = { it.packageName }) { app ->
                    val restriction = state.restrictions[app.packageName]
                    val usedMinutes = state.usageToday[app.packageName] ?: 0
                    RestrictedAppRow(
                        app = app,
                        restriction = restriction,
                        usedMinutes = usedMinutes,
                        showUsage = state.hasUsageAccess,
                        onSelect = { onSelectApp(app) },
                        onToggle = { enabled -> restriction?.let { onToggleEnabled(it, enabled) } },
                    )
                }
            }
        }
    }
}

@Composable
private fun RestrictedAppRow(
    app: InstalledAppInfo,
    restriction: AppRestriction?,
    usedMinutes: Int,
    showUsage: Boolean,
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
                if (restriction != null) {
                    val statusColor = if (restriction.todayProgress(usedMinutes).isReached) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                    Text(
                        text = if (showUsage) {
                            restriction.todayProgress(usedMinutes).describe()
                        } else {
                            "Daily limit: ${restriction.dailyLimitMinutes}m"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = statusColor,
                    )
                } else {
                    Text(
                        text = "Tap to set a daily limit",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (restriction != null) {
                Switch(
                    checked = restriction.enabled,
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
private fun RestrictionEditorDialog(
    editor: RestrictionEditorState,
    onChange: (RestrictionEditorState) -> Unit,
    onSave: () -> Unit,
    onDelete: (() -> Unit)?,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(editor.appLabel) },
        text = {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Daily limit:",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.width(120.dp),
                    )
                    TextField(
                        value = editor.limitText,
                        onValueChange = {
                            onChange(editor.copy(limitText = it.filter { c -> c.isDigit() }.take(4)))
                        },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        textStyle = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.width(76.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "minutes",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                }
                Spacer(Modifier.height(12.dp))
                Text(
                    text = "When the limit is reached, Jikan steps in automatically. " +
                        "0 blocks the app as soon as it opens.",
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

@androidx.compose.ui.tooling.preview.Preview(showBackground = true)
@Composable
private fun RestrictedAppsScreenPreview() {
    JikanTheme {
        RestrictedAppsScreenContent(
            state = RestrictedAppsUiState(
                apps = listOf(
                    InstalledAppInfo("com.tiktok", "TikTok", null),
                    InstalledAppInfo("com.youtube", "YouTube", null),
                ),
                restrictions = mapOf(
                    "com.tiktok" to AppRestriction("com.tiktok", "TikTok", 30),
                    "com.youtube" to AppRestriction("com.youtube", "YouTube", 45),
                ),
                usageToday = mapOf("com.tiktok" to 23, "com.youtube" to 45),
                hasUsageAccess = true,
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
