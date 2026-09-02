package com.example.jikan.study

import com.example.jikan.data.Card

enum class QuestionType {
    CHARACTER_TO_ROMAJI,
    ROMAJI_TO_CHARACTER,
}

data class QuizQuestion(
    val card: Card,
    val type: QuestionType,
    val prompt: String,
    val options: List<String>,
    val correctAnswer: String,
)
