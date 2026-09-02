package com.example.jikan.ui.quiz

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.jikan.data.Card
import com.example.jikan.data.CardTier
import com.example.jikan.study.QuestionType
import com.example.jikan.study.QuizQuestion
import com.example.jikan.ui.theme.JikanProgressBar
import com.example.jikan.ui.theme.JikanTheme
import com.example.jikan.ui.theme.NeoCard
import com.example.jikan.ui.theme.PillButton
import com.example.jikan.ui.theme.RadiusMd
import com.example.jikan.ui.theme.Sage
import com.example.jikan.ui.theme.neoRaised
import com.example.jikan.ui.theme.SageSoft
import com.example.jikan.ui.theme.Vermillion
import com.example.jikan.ui.theme.VermillionSoft

@Composable
fun QuizScreen(
    question: QuizQuestion,
    progress: Float,
    selectedAnswer: String?,
    isAnswerCorrect: Boolean?,
    onAnswerSelected: (String) -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        JikanProgressBar(progress = progress)
        Spacer(Modifier.height(30.dp))
        Text(
            text = "What does this say?",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(18.dp))
        NeoCard(
            shape = RoundedCornerShape(36.dp),
            modifier = Modifier.size(132.dp),
        ) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(text = question.prompt, fontSize = 72.sp)
            }
        }
        Spacer(Modifier.height(28.dp))
        Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            question.options.forEach { option ->
                AnswerOption(
                    text = option,
                    state = when {
                        selectedAnswer == null -> AnswerState.Neutral
                        option == question.correctAnswer -> AnswerState.Correct
                        option == selectedAnswer -> AnswerState.Incorrect
                        else -> AnswerState.Neutral
                    },
                    enabled = selectedAnswer == null,
                    onClick = { onAnswerSelected(option) },
                )
            }
        }
        if (selectedAnswer != null) {
            Spacer(Modifier.height(18.dp))
            Text(
                text = if (isAnswerCorrect == true) "Correct!" else "Not quite — the answer was ${question.correctAnswer}.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
        Spacer(Modifier.weight(1f))
        if (selectedAnswer != null) {
            PillButton(text = "Next", onClick = onNext, modifier = Modifier.fillMaxWidth())
        }
    }
}

private enum class AnswerState { Neutral, Correct, Incorrect }

@Composable
private fun AnswerOption(
    text: String,
    state: AnswerState,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(RadiusMd)
    val (bg, fg) = when (state) {
        AnswerState.Correct -> SageSoft to Sage
        AnswerState.Incorrect -> VermillionSoft to Vermillion
        AnswerState.Neutral -> MaterialTheme.colorScheme.background to MaterialTheme.colorScheme.onBackground
    }
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = shape,
        color = bg,
        contentColor = fg,
        modifier = Modifier
            .fillMaxWidth()
            .height(58.dp)
            .let { if (state == AnswerState.Neutral) it.neoRaised(shape, elevation = 6.dp) else it },
    ) {
        Box(Modifier.fillMaxSize().padding(horizontal = 20.dp), contentAlignment = Alignment.CenterStart) {
            Text(text = text, style = MaterialTheme.typography.titleMedium)
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun QuizScreenPreview() {
    JikanTheme {
        QuizScreen(
            question = QuizQuestion(
                card = Card(
                    id = 1,
                    character = "さ",
                    romaji = "sa",
                    audioResName = "kana_sa",
                    tier = CardTier.HIRAGANA_VOWELS,
                    sortOrder = 0,
                ),
                type = QuestionType.CHARACTER_TO_ROMAJI,
                prompt = "さ",
                options = listOf("sa", "chi", "ki", "ru"),
                correctAnswer = "sa",
            ),
            progress = 0.62f,
            selectedAnswer = "chi",
            isAnswerCorrect = false,
            onAnswerSelected = {},
            onNext = {},
        )
    }
}
