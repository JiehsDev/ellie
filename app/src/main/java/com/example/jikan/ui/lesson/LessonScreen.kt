package com.example.jikan.ui.lesson

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.jikan.audio.KanaAudioPlayer
import com.example.jikan.data.Card
import com.example.jikan.data.CardTier
import com.example.jikan.ui.theme.JikanProgressBar
import com.example.jikan.ui.theme.JikanTheme
import com.example.jikan.ui.theme.NeoCard
import com.example.jikan.ui.theme.PillButton
import com.example.jikan.ui.theme.RadiusLg

@Composable
fun LessonScreen(
    card: Card,
    progress: Float,
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var revealed by remember(card.id) { mutableStateOf(false) }

    val context = LocalContext.current
    val audioPlayer = remember { KanaAudioPlayer(context) }
    DisposableEffect(Unit) { onDispose { audioPlayer.release() } }
    LaunchedEffect(card.id) { audioPlayer.play(card.audioResName) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        JikanProgressBar(progress = progress)
        Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            NeoCard(
                shape = RoundedCornerShape(RadiusLg),
                contentPadding = PaddingValues(24.dp),
                modifier = Modifier
                    .size(width = 264.dp, height = 340.dp)
                    .clickable { audioPlayer.play(card.audioResName) },
            ) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(text = card.character, fontSize = 128.sp)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "🔊 tap to hear it",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (revealed) {
                        Spacer(Modifier.height(22.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(0.3f)
                                .height(1.dp)
                                .background(color = MaterialTheme.colorScheme.outline),
                        )
                        Spacer(Modifier.height(22.dp))
                        Text(
                            text = card.romaji,
                            style = MaterialTheme.typography.headlineSmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        card.mnemonic?.let {
                            Spacer(Modifier.height(10.dp))
                            Text(
                                text = it,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                            )
                        }
                    }
                }
            }
        }
        PillButton(
            text = if (revealed) "Next" else "Reveal",
            onClick = { if (revealed) onNext() else revealed = true },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun LessonScreenPreview() {
    JikanTheme {
        LessonScreen(
            card = Card(
                id = 1,
                character = "あ",
                romaji = "a",
                audioResName = "kana_a",
                mnemonic = "Looks like an antenna.",
                tier = CardTier.HIRAGANA_VOWELS,
                sortOrder = 0,
            ),
            progress = 0.3f,
            onNext = {},
        )
    }
}
