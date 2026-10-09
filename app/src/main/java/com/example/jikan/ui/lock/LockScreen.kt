package com.example.jikan.ui.lock

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.jikan.ui.coach.JikanCoachMessage
import com.example.jikan.ui.home.HomeCoachMessageProvider
import com.example.jikan.ui.theme.JikanTheme
import com.example.jikan.ui.theme.NeoCard
import com.example.jikan.ui.theme.PillButton
import com.example.jikan.ui.theme.Vermillion

/**
 * The entry screen shown on redirect (project-context.md screen flow step 7):
 * blocked-app context + "Start lesson" CTA, deliberately no skip option.
 */
@Composable
fun LockScreen(
    blockedAppLabel: String,
    onStartLesson: () -> Unit,
    modifier: Modifier = Modifier,
    walletBalanceMinutes: Int = 0,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        NeoCard(
            shape = RoundedCornerShape(36.dp),
            modifier = Modifier.size(112.dp),
        ) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(text = "🔒", fontSize = 44.sp, color = Vermillion)
            }
        }
        Spacer(Modifier.height(28.dp))
        Text(
            text = "$blockedAppLabel is locked",
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = "Finish a quick Japanese lesson to unlock it for a few minutes.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text = "Wallet: $walletBalanceMinutes min available",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(18.dp))
        JikanCoachMessage(
            message = HomeCoachMessageProvider.lockMessage(
                appLabel = blockedAppLabel,
                walletBalanceMinutes = walletBalanceMinutes,
            )
        )
        Spacer(Modifier.height(48.dp))
        PillButton(text = "Start lesson", onClick = onStartLesson, modifier = Modifier.fillMaxWidth())
    }
}

@Preview(showBackground = true)
@Composable
private fun LockScreenPreview() {
    JikanTheme {
        LockScreen(blockedAppLabel = "Loopy", onStartLesson = {}, walletBalanceMinutes = 0)
    }
}
