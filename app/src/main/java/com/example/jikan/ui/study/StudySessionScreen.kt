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
import com.example.jikan.ui.results.ResultsScreen

@Composable
fun StudySessionScreen(
    modifier: Modifier = Modifier,
    unlockedAppLabel: String? = null,
    sessionKey: Int = 0,
    sessionSize: Int = StudySessionViewModel.SESSION_SIZE,
    onContinue: () -> Unit = {},
    viewModel: StudySessionViewModel = viewModel(),
) {
    LaunchedEffect(sessionKey) {
        if (sessionKey != 0) viewModel.startSession(sessionSize)
    }
    val phase by viewModel.phase.collectAsState()

    when (val current = phase) {
        is StudyPhase.Loading -> LoadingIndicator(modifier)

        is StudyPhase.Empty -> Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No cards due right now. Check back later.")
        }

        is StudyPhase.Lesson -> LessonScreen(
            card = current.cards[current.index],
            cardState = current.cardState,
            gradePreviews = current.gradePreviews,
            progress = (current.index + 1f) / current.cards.size,
            index = current.index,
            totalCount = current.cards.size,
            earnedTodayMinutes = current.earnedTodayMinutes,
            unlockInMinutes = current.unlockInMinutes,
            onGrade = viewModel::onGradeSelected,
            onBack = onContinue,
            modifier = modifier,
        )

        is StudyPhase.Results -> ResultsScreen(
            correctCount = current.correctCount,
            totalCount = current.totalCount,
            creditsEarned = current.creditsEarned,
            walletBalance = current.walletBalance,
            isPerfect = current.isPerfect,
            unlockedAppLabel = unlockedAppLabel,
            aiSummary = current.aiSummary,
            durationSeconds = current.durationSeconds,
            earnedTodayMinutes = current.earnedTodayMinutes,
            avgRecallSeconds = current.avgRecallSeconds,
            avgIntervalGrowthDays = current.avgIntervalGrowthDays,
            studyMinutesToday = current.studyMinutesToday,
            dailyGoalMinutes = current.dailyGoalMinutes,
            dueCount = current.dueCount,
            lockedAppLabels = current.lockedAppLabels,
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
