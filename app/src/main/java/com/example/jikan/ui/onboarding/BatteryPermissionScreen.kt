package com.example.jikan.ui.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.jikan.onboarding.OemBatteryHints

@Composable
fun BatteryPermissionScreen(
    isGranted: Boolean,
    manufacturer: String,
    onRequestExemption: () -> Unit,
    onOpenAutostartSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text(text = "Allow background activity", fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(12.dp))
        Text(
            text = "Some phones aggressively close apps running in the background. " +
                "Exempting Jikan from battery optimization keeps the lock working reliably.",
            style = MaterialTheme.typography.bodyLarge,
        )
        Spacer(Modifier.height(24.dp))

        if (isGranted) {
            Text(text = "Battery optimization exemption granted", style = MaterialTheme.typography.titleMedium)
        } else {
            Button(onClick = onRequestExemption, modifier = Modifier.fillMaxWidth()) {
                Text("Ignore battery optimizations")
            }
        }

        if (OemBatteryHints.showsAutostartNote(manufacturer)) {
            Spacer(Modifier.height(24.dp))
            Text(
                text = "On $manufacturer phones, also enable \"Autostart\" for Ellie so it can keep running.",
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.height(12.dp))
            OutlinedButton(onClick = onOpenAutostartSettings, modifier = Modifier.fillMaxWidth()) {
                Text("Open autostart settings")
            }
        }
    }
}
