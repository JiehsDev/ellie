package com.example.jikan.study

import com.example.jikan.data.Card

sealed interface StudyPhase {
    data object Loading : StudyPhase

    data object Empty : StudyPhase

    data class Lesson(val cards: List<Card>, val index: Int) : StudyPhase

    data class Quiz(
        val questions: List<QuizQuestion>,
        val index: Int,
        val correctCount: Int,
        val selectedAnswer: String? = null,
        val isAnswerCorrect: Boolean? = null,
    ) : StudyPhase

    data class Results(
        val correctCount: Int,
        val totalCount: Int,
        val creditsEarned: Int,
        val walletBalance: Int,
        val isPerfect: Boolean,
        val aiSummary: String? = null,
    ) : StudyPhase
}
