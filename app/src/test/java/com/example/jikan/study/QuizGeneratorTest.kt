package com.example.jikan.study

import com.example.jikan.data.Card
import com.example.jikan.data.CardTier
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class QuizGeneratorTest {

    private fun card(id: Long, character: String, romaji: String) = Card(
        id = id,
        character = character,
        romaji = romaji,
        audioResName = "kana_$romaji",
        tier = CardTier.HIRAGANA_VOWELS,
        sortOrder = id.toInt(),
    )

    private val pool = listOf(
        card(1, "あ", "a"),
        card(2, "い", "i"),
        card(3, "う", "u"),
        card(4, "え", "e"),
        card(5, "お", "o"),
    )

    @Test
    fun `question options always include the correct answer`() {
        val question = QuizGenerator.buildQuestion(pool[0], pool, Random(42))
        assertTrue(question.options.contains(question.correctAnswer))
    }

    @Test
    fun `question options have no duplicates`() {
        val question = QuizGenerator.buildQuestion(pool[0], pool, Random(7))
        assertEquals(question.options.size, question.options.distinct().size)
    }

    @Test
    fun `options are capped at 4 when the pool is large enough`() {
        val question = QuizGenerator.buildQuestion(pool[0], pool, Random(1))
        assertEquals(4, question.options.size)
    }

    @Test
    fun `prompt and correct answer match the question type`() {
        val question = QuizGenerator.buildQuestion(pool[0], pool, Random(2))
        if (question.type == QuestionType.CHARACTER_TO_ROMAJI) {
            assertEquals(pool[0].character, question.prompt)
            assertEquals(pool[0].romaji, question.correctAnswer)
        } else {
            assertEquals(pool[0].romaji, question.prompt)
            assertEquals(pool[0].character, question.correctAnswer)
        }
    }

    @Test
    fun `buildQuiz produces one question per card`() {
        val quiz = QuizGenerator.buildQuiz(pool, pool, Random(3))
        assertEquals(pool.size, quiz.size)
    }
}
