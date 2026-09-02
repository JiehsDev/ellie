package com.example.jikan.ui.results

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.jikan.ui.theme.JikanTheme
import com.example.jikan.ui.theme.NeoCard
import com.example.jikan.ui.theme.OutlinePillButton
import com.example.jikan.ui.theme.PillButton
import com.example.jikan.ui.theme.Sage

@Composable
fun ResultsScreen(
    correctCount: Int,
    totalCount: Int,
    creditsEarned: Int,
    walletBalance: Int,
    isPerfect: Boolean,
    unlockedAppLabel: String?,
    onContinue: () -> Unit,
    onKeepStudying: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(6.dp))
        Text(
            text = "SESSION COMPLETE",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(14.dp))
        Text(
            text = "$correctCount/$totalCount",
            fontSize = 64.sp,
            style = MaterialTheme.typography.displayLarge,
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = if (isPerfect) "Perfect score!" else "Nicely done!",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(26.dp))
        NeoCard(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text(
                        text = "EARNED",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "+$creditsEarned min",
                        style = MaterialTheme.typography.headlineSmall,
                        color = Sage,
                    )
                }
                Box(
                    Modifier
                        .width(1.dp)
                        .height(36.dp)
                        .background(MaterialTheme.colorScheme.outline),
                )
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "WALLET",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(text = "$walletBalance min", style = MaterialTheme.typography.headlineSmall)
                }
            }
        }

        if (!isPerfect) {
            Spacer(Modifier.height(14.dp))
            NeoCard(modifier = Modifier.fillMaxWidth(), elevation = 6.dp) {
                Text(
                    text = "Missed items queued for review next session.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Spacer(Modifier.weight(1f))
        PillButton(
            text = if (unlockedAppLabel != null) "Continue to $unlockedAppLabel" else "Continue",
            onClick = onContinue,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(12.dp))
        OutlinePillButton(text = "Keep studying", onClick = onKeepStudying, modifier = Modifier.fillMaxWidth())
    }
}

@Preview(showBackground = true)
@Composable
private fun ResultsScreenPreview() {
    JikanTheme {
        ResultsScreen(
            correctCount = 9,
            totalCount = 10,
            creditsEarned = 18,
            walletBalance = 60,
            isPerfect = false,
            unlockedAppLabel = "Loopy",
            onContinue = {},
            onKeepStudying = {},
        )
    }
}
