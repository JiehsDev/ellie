package com.example.jikan.ui.onboarding

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.jikan.data.DailyGoalPreset

@Composable
fun StudyPreferencesScreen(
    sessionLengthMinutes: Int,
    dailyGoalPreset: DailyGoalPreset,
    onSessionLengthChanged: (Int) -> Unit,
    onDailyGoalSelected: (DailyGoalPreset) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
    ) {
        Text(text = "Study preferences", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(32.dp))

        Text(text = "Session length: $sessionLengthMinutes min", style = MaterialTheme.typography.titleMedium)
        Slider(
            value = sessionLengthMinutes.toFloat(),
            onValueChange = { onSessionLengthChanged(it.toInt()) },
            valueRange = 5f..30f,
            steps = 24,
        )

        Spacer(Modifier.height(32.dp))
        Text(text = "Daily goal", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        Row {
            FilterChip(
                selected = dailyGoalPreset == DailyGoalPreset.CASUAL,
                onClick = { onDailyGoalSelected(DailyGoalPreset.CASUAL) },
                label = { Text("Casual") },
            )
            Spacer(Modifier.width(12.dp))
            FilterChip(
                selected = dailyGoalPreset == DailyGoalPreset.SERIOUS,
                onClick = { onDailyGoalSelected(DailyGoalPreset.SERIOUS) },
                label = { Text("Serious") },
            )
        }
    }
}
