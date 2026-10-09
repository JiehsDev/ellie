package com.example.jikan.coach

data class StudySessionData(
    val cardsReviewed: Int,
    val correctAnswers: Int,
    val incorrectAnswers: Int,
    val accuracyPercent: Int,
    val durationMinutes: Int,
    val creditsEarned: Int,
    val weakCards: List<String>,
    val currentStreak: Int,
    val previousAccuracyPercent: Int?
)
