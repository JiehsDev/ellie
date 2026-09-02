package com.example.jikan.ui.settings

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.jikan.data.InstalledAppInfo
import com.example.jikan.ui.theme.IconChip
import com.example.jikan.ui.theme.JikanTheme
import com.example.jikan.ui.theme.RadiusSm

@Composable
fun LockedAppsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: LockedAppsViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsState()
    LockedAppsScreenContent(
        state = state,
        onBack = onBack,
        onToggle = viewModel::toggle,
        modifier = modifier,
    )
}

@Composable
private fun LockedAppsScreenContent(
    state: LockedAppsUiState,
    onBack: () -> Unit,
    onToggle: (InstalledAppInfo) -> Unit,
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

        Column(modifier = Modifier.padding(horizontal = 24.dp)) {
            Text(text = "Locked apps", style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(4.dp))
            Text(
                text = "Choose which apps require a lesson to open.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        if (state.isLoading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            LazyColumn(contentPadding = PaddingValues(horizontal = 24.dp, vertical = 16.dp)) {
                items(state.apps, key = { it.packageName }) { app ->
                    AppToggleRow(
                        app = app,
                        isLocked = app.packageName in state.lockedPackages,
                        onToggle = { onToggle(app) },
                    )
                }
            }
        }
    }
}

@Composable
private fun AppToggleRow(
    app: InstalledAppInfo,
    isLocked: Boolean,
    onToggle: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
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
                isLoading = false,
            ),
            onBack = {},
            onToggle = {},
        )
    }
}
