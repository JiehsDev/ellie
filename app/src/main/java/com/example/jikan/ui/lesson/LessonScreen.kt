package com.example.jikan.ui.lesson

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.jikan.R
import com.example.jikan.audio.KanaAudioPlayer
import com.example.jikan.data.Card
import com.example.jikan.data.CardTier
import com.example.jikan.srs.RecallGrade
import com.example.jikan.srs.SrsState
import com.example.jikan.ui.theme.JikanTheme

/**
 * Stitch reference: the Anki-style review card. The card shows its answer
 * and the user grades their recall (Again/Hard/Good/Easy), which records
 * the SRS result directly. All intervals on the grade buttons are computed
 * from the card's real SRS state.
 */
@Composable
fun LessonScreen(
    card: Card,
    cardState: SrsState,
    gradePreviews: Map<RecallGrade, String>,
    progress: Float,
    index: Int,
    totalCount: Int,
    earnedTodayMinutes: Int,
    unlockInMinutes: Int,
    onGrade: (RecallGrade) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val audioPlayer = remember { KanaAudioPlayer(context) }
    DisposableEffect(Unit) { onDispose { audioPlayer.release() } }
    LaunchedEffect(card.id) { audioPlayer.play(card.audioResName) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .padding(top = 12.dp, bottom = 24.dp),
    ) {
        // Top bar.
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Filled.ArrowBack,
                contentDescription = "Back",
                tint = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onBack)
                    .padding(8.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = "Active Session",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.weight(1f),
            )
            Image(
                painter = painterResource(R.drawable.mascot_jikan_coach),
                contentDescription = "Ellie",
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
            )
        }

        Spacer(Modifier.height(14.dp))

        // Progress bar.
        LinearProgressIndicator(
            progress = { progress.coerceIn(0f, 1f) },
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(CircleShape),
            color = MaterialTheme.colorScheme.secondary,
            trackColor = MaterialTheme.colorScheme.surfaceVariant,
        )

        Spacer(Modifier.height(14.dp))

        // Deck + earned row.
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.secondary),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = "${tierDisplayName(card.tier)} · ${index + 1} of $totalCount",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            ) {
                Icon(
                    imageVector = Icons.Filled.AccessTime,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = "+${earnedTodayMinutes}m earned",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        // The card.
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(MaterialTheme.colorScheme.surface)
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Category + level pills.
            Row(modifier = Modifier.fillMaxWidth()) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(horizontal = 12.dp, vertical = 7.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.secondary),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = tierCategory(card.tier),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        letterSpacing = 1.sp,
                    )
                }
                Spacer(Modifier.weight(1f))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(horizontal = 12.dp, vertical = 7.dp),
                ) {
                    Icon(
                        imageVector = Icons.Filled.Psychology,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = "Level ${cardState.repetitions + 1} Recall",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Spacer(Modifier.height(28.dp))

            // Character + reading.
            Text(
                text = card.character,
                style = MaterialTheme.typography.displayLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 72.sp,
                lineHeight = 80.sp,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "[${card.romaji}]",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )

            // Mnemonic box.
            card.mnemonic?.let { mnemonic ->
                Spacer(Modifier.height(20.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(16.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = mnemonic,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center,
                        lineHeight = 22.sp,
                    )
                }
            }

            Spacer(Modifier.height(20.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
            )
            Spacer(Modifier.height(16.dp))

            // Core definition + audio.
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "CORE DEFINITION",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    letterSpacing = 1.sp,
                    modifier = Modifier.weight(1f),
                )
                Icon(
                    imageVector = Icons.Filled.VolumeUp,
                    contentDescription = "Play audio",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .clickable { audioPlayer.play(card.audioResName) }
                        .padding(10.dp),
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = card.romaji,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "Tap the speaker to hear the pronunciation.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        Spacer(Modifier.height(14.dp))

        // Unlock bar.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surface)
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.secondary),
            )
            Spacer(Modifier.width(10.dp))
            Text(
                text = if (unlockInMinutes > 0)
                    "Next distraction unlock available in $unlockInMinutes mins"
                else
                    "Almost there — finish to unlock",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
                lineHeight = 20.sp,
            )
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "SRS Interval:",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = "Active",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }

        Spacer(Modifier.height(14.dp))

        // Grade buttons.
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            GradeButton(
                grade = RecallGrade.AGAIN,
                preview = gradePreviews[RecallGrade.AGAIN] ?: "<1m",
                containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                contentColor = MaterialTheme.colorScheme.tertiary,
                onGrade = onGrade,
                modifier = Modifier.weight(1f),
            )
            GradeButton(
                grade = RecallGrade.HARD,
                preview = gradePreviews[RecallGrade.HARD] ?: "12h",
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                contentColor = MaterialTheme.colorScheme.onSurface,
                onGrade = onGrade,
                modifier = Modifier.weight(1f),
            )
            GradeButton(
                grade = RecallGrade.GOOD,
                preview = gradePreviews[RecallGrade.GOOD] ?: "1d",
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                contentColor = MaterialTheme.colorScheme.onSurface,
                onGrade = onGrade,
                modifier = Modifier.weight(1f),
            )
            GradeButton(
                grade = RecallGrade.EASY,
                preview = gradePreviews[RecallGrade.EASY] ?: "3d",
                containerColor = MaterialTheme.colorScheme.secondary,
                contentColor = MaterialTheme.colorScheme.onSecondary,
                onGrade = onGrade,
                modifier = Modifier.weight(1f),
            )
        }

        Spacer(Modifier.height(12.dp))
        Text(
            text = "Select recall accuracy · Consistent recall preserves digital app allowance",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
            lineHeight = 18.sp,
        )
    }
}

@Composable
private fun GradeButton(
    grade: RecallGrade,
    preview: String,
    containerColor: androidx.compose.ui.graphics.Color,
    contentColor: androidx.compose.ui.graphics.Color,
    onGrade: (RecallGrade) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(containerColor)
            .clickable(onClick = { onGrade(grade) })
            .padding(vertical = 14.dp),
    ) {
        Text(
            text = when (grade) {
                RecallGrade.AGAIN -> "Again"
                RecallGrade.HARD -> "Hard"
                RecallGrade.GOOD -> "Good"
                RecallGrade.EASY -> "Easy"
            },
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = contentColor,
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = preview,
            style = MaterialTheme.typography.bodySmall,
            color = contentColor.copy(alpha = 0.75f),
        )
    }
}

private fun tierDisplayName(tier: CardTier): String = when (tier) {
    CardTier.HIRAGANA_VOWELS -> "Hiragana · Vowels"
    CardTier.HIRAGANA_K -> "Hiragana · K"
    CardTier.HIRAGANA_S -> "Hiragana · S"
    CardTier.HIRAGANA_T -> "Hiragana · T"
    CardTier.HIRAGANA_N -> "Hiragana · N"
    CardTier.HIRAGANA_H -> "Hiragana · H"
    CardTier.HIRAGANA_M -> "Hiragana · M"
    CardTier.HIRAGANA_Y -> "Hiragana · Y"
    CardTier.HIRAGANA_R -> "Hiragana · R"
    CardTier.HIRAGANA_W_N -> "Hiragana · W/N"
    CardTier.KATAKANA -> "Katakana"
    CardTier.VOCAB_BASIC -> "Basic Vocab"
    CardTier.PHRASES_BASIC -> "Basic Phrases"
}

private fun tierCategory(tier: CardTier): String = when (tier) {
    CardTier.KATAKANA -> "KATAKANA"
    CardTier.VOCAB_BASIC, CardTier.PHRASES_BASIC -> "VOCAB"
    else -> "KANA"
}

@Preview(showBackground = true)
@Composable
private fun LessonScreenPreview() {
    JikanTheme(themeMode = com.example.jikan.data.ThemeMode.DARK) {
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
            cardState = SrsState(intervalDays = 0, easeFactor = 2.5f, repetitions = 2),
            gradePreviews = mapOf(
                RecallGrade.AGAIN to "<1m",
                RecallGrade.HARD to "12h",
                RecallGrade.GOOD to "1d",
                RecallGrade.EASY to "3d",
            ),
            progress = 0.35f,
            index = 6,
            totalCount = 20,
            earnedTodayMinutes = 5,
            unlockInMinutes = 15,
            onGrade = {},
            onBack = {},
        )
    }
}
