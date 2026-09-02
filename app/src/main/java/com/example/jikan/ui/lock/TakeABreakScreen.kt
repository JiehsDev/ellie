package com.example.jikan.ui.lock

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.jikan.ui.theme.JikanTheme
import com.example.jikan.ui.theme.OutlinePillButton
import com.example.jikan.ui.theme.neoRaised

/**
 * Shown instead of the normal lock prompt when RedirectGate detects 5+ reopens
 * of the same blocked app within 10 seconds — treated as a frustration signal
 * (project-context.md "Reopen race mitigation") rather than just re-firing the
 * lock screen again.
 */
@Composable
fun TakeABreakScreen(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        val bg = MaterialTheme.colorScheme.background
        Box(modifier = Modifier.size(150.dp), contentAlignment = Alignment.Center) {
            Box(Modifier.size(150.dp).neoRaised(CircleShape, elevation = 6.dp).background(bg.copy(alpha = 0.5f), CircleShape))
            Box(Modifier.size(110.dp).neoRaised(CircleShape, elevation = 8.dp).background(bg.copy(alpha = 0.75f), CircleShape))
            Box(Modifier.size(70.dp).neoRaised(CircleShape, elevation = 10.dp).background(bg, CircleShape))
        }
        Spacer(Modifier.height(32.dp))
        Text(text = "Take a beat", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(12.dp))
        Text(
            text = "You've bounced back here a few times. Step away for a bit, then come back and study when you're ready.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(48.dp))
        OutlinePillButton(text = "Okay", onClick = onDismiss, modifier = Modifier.fillMaxWidth())
    }
}

@Preview(showBackground = true)
@Composable
private fun TakeABreakScreenPreview() {
    JikanTheme {
        TakeABreakScreen(onDismiss = {})
    }
}
