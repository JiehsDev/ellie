package com.example.jikan.ui.study

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.jikan.study.StudyPhase
import com.example.jikan.study.StudySessionViewModel
import com.example.jikan.ui.lesson.LessonScreen
import com.example.jikan.ui.quiz.QuizScreen
import com.example.jikan.ui.results.ResultsScreen

@Composable
fun StudySessionScreen(
    modifier: Modifier = Modifier,
    unlockedAppLabel: String? = null,
    sessionKey: Int = 0,
    onContinue: () -> Unit = {},
    viewModel: StudySessionViewModel = viewModel(),
) {
    LaunchedEffect(sessionKey) {
        if (sessionKey != 0) viewModel.startSession()
    }
    val phase by viewModel.phase.collectAsState()

    when (val current = phase) {
        is StudyPhase.Loading -> LoadingIndicator(modifier)

        is StudyPhase.Empty -> Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No cards due right now. Check back later.")
        }

        is StudyPhase.Lesson -> LessonScreen(
            card = current.cards[current.index],
            progress = (current.index + 1f) / current.cards.size,
            onNext = viewModel::onLessonNext,
            modifier = modifier,
        )

        is StudyPhase.Quiz -> QuizScreen(
            question = current.questions[current.index],
            progress = (current.index + 1f) / current.questions.size,
            selectedAnswer = current.selectedAnswer,
            isAnswerCorrect = current.isAnswerCorrect,
            onAnswerSelected = viewModel::onAnswerSelected,
            onNext = viewModel::onQuizNext,
            modifier = modifier,
        )

        is StudyPhase.Results -> ResultsScreen(
            correctCount = current.correctCount,
            totalCount = current.totalCount,
            creditsEarned = current.creditsEarned,
            walletBalance = current.walletBalance,
            isPerfect = current.isPerfect,
            unlockedAppLabel = unlockedAppLabel,
            onContinue = onContinue,
            onKeepStudying = viewModel::onKeepStudying,
            modifier = modifier,
        )
    }
}

@Composable
private fun LoadingIndicator(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}
