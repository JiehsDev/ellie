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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.jikan.ui.theme.JikanTheme

@Composable
fun AllSetScreen(
    onStartFirstLesson: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(text = "You're all set", fontSize = 32.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(12.dp))
        Text(
            text = "Your locked apps are ready. Study a quick hiragana lesson now to earn your first minutes.",
            style = MaterialTheme.typography.bodyLarge,
        )
        Spacer(Modifier.height(48.dp))
        Button(onClick = onStartFirstLesson, modifier = Modifier.fillMaxWidth()) {
            Text("Start first lesson")
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun AllSetScreenPreview() {
    JikanTheme {
        AllSetScreen(onStartFirstLesson = {})
    }
}
