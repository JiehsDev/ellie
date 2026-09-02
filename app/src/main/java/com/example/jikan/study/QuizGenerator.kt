package com.example.jikan.study

import com.example.jikan.data.Card
import kotlin.random.Random

object QuizGenerator {
    private const val OPTION_COUNT = 4

    fun buildQuestion(card: Card, pool: List<Card>, random: Random = Random.Default): QuizQuestion {
        val type = if (random.nextBoolean()) QuestionType.CHARACTER_TO_ROMAJI else QuestionType.ROMAJI_TO_CHARACTER
        val correctAnswer = answerFor(card, type)
        val prompt = if (type == QuestionType.CHARACTER_TO_ROMAJI) card.character else card.romaji

        val distractors = pool
            .filter { it.id != card.id }
            .distinctBy { answerFor(it, type) }
            .shuffled(random)
            .map { answerFor(it, type) }
            .filter { it != correctAnswer }
            .take(OPTION_COUNT - 1)

        val options = (distractors + correctAnswer).shuffled(random)
        return QuizQuestion(card, type, prompt, options, correctAnswer)
    }

    fun buildQuiz(cards: List<Card>, pool: List<Card>, random: Random = Random.Default): List<QuizQuestion> =
        cards.map { buildQuestion(it, pool, random) }

    private fun answerFor(card: Card, type: QuestionType): String =
        if (type == QuestionType.CHARACTER_TO_ROMAJI) card.romaji else card.character
}
