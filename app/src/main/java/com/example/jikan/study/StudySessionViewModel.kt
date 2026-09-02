package com.example.jikan.study

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.jikan.data.AppDatabase
import com.example.jikan.data.Card
import com.example.jikan.data.CardSeeder
import com.example.jikan.data.StudyRepository
import com.example.jikan.widget.WidgetUpdater
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class StudySessionViewModel(application: Application) : AndroidViewModel(application) {
    private val db = AppDatabase.getInstance(application)
    private val repository = StudyRepository(db.cardDao(), db.progressDao(), db.sessionDao(), db.walletDao())

    private val _phase = MutableStateFlow<StudyPhase>(StudyPhase.Loading)
    val phase: StateFlow<StudyPhase> = _phase.asStateFlow()

    private val quizQuestions = mutableListOf<QuizQuestion>()
    private var sessionCards: List<Card> = emptyList()
    private var sessionStartedAt: Long = 0L

    init {
        startSession()
    }

    fun startSession(sessionSize: Int = SESSION_SIZE) {
        viewModelScope.launch {
            _phase.value = StudyPhase.Loading
            CardSeeder.seedIfEmpty(getApplication(), db.cardDao())
            val cards = repository.buildSessionQueue(sessionSize)
            if (cards.isEmpty()) {
                _phase.value = StudyPhase.Empty
                return@launch
            }
            sessionCards = cards
            sessionStartedAt = System.currentTimeMillis()
            _phase.value = StudyPhase.Lesson(cards, index = 0)
        }
    }

    fun onLessonNext() {
        val current = _phase.value as? StudyPhase.Lesson ?: return
        if (current.index + 1 < current.cards.size) {
            _phase.value = current.copy(index = current.index + 1)
        } else {
            startQuiz()
        }
    }

    private fun startQuiz() {
        viewModelScope.launch {
            val pool = repository.getAllCardsOnce()
            quizQuestions.clear()
            quizQuestions += QuizGenerator.buildQuiz(sessionCards, pool)
            _phase.value = StudyPhase.Quiz(quizQuestions.toList(), index = 0, correctCount = 0)
        }
    }

    fun onAnswerSelected(answer: String) {
        val current = _phase.value as? StudyPhase.Quiz ?: return
        if (current.selectedAnswer != null) return
        val question = current.questions[current.index]
        val wasCorrect = answer == question.correctAnswer
        val updatedCorrectCount = if (wasCorrect) current.correctCount + 1 else current.correctCount

        _phase.value = current.copy(
            selectedAnswer = answer,
            isAnswerCorrect = wasCorrect,
            correctCount = updatedCorrectCount,
        )

        viewModelScope.launch {
            repository.recordAnswer(question.card.id, wasCorrect)
            if (!wasCorrect) {
                val pool = repository.getAllCardsOnce()
                quizQuestions += QuizGenerator.buildQuestion(question.card, pool)
                val latest = _phase.value as? StudyPhase.Quiz ?: return@launch
                _phase.value = latest.copy(questions = quizQuestions.toList())
            }
        }
    }

    fun onQuizNext() {
        val current = _phase.value as? StudyPhase.Quiz ?: return
        val nextIndex = current.index + 1
        if (nextIndex < current.questions.size) {
            _phase.value = current.copy(index = nextIndex, selectedAnswer = null, isAnswerCorrect = null)
        } else {
            finishSession(current)
        }
    }

    private fun finishSession(quiz: StudyPhase.Quiz) {
        viewModelScope.launch {
            val total = quiz.questions.size
            val result = repository.completeSession(
                correctCount = quiz.correctCount,
                totalCount = total,
                sessionStartedAt = sessionStartedAt,
            )
            WidgetUpdater.refresh(getApplication())
            _phase.value = StudyPhase.Results(
                correctCount = quiz.correctCount,
                totalCount = total,
                creditsEarned = result.creditsEarned,
                walletBalance = result.walletBalance,
                isPerfect = result.isPerfect,
            )
        }
    }

    fun onKeepStudying() = startSession()

    companion object {
        const val SESSION_SIZE = 10
    }
}
