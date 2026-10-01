package com.wordiq.app.ui.screens

import com.wordiq.app.PracticeFeedback
import com.wordiq.app.PracticeUiState
import com.wordiq.app.data.local.ConceptWithDetails
import com.wordiq.app.data.local.LearningSkill
import com.wordiq.app.data.local.VocabularyConceptEntity
import com.wordiq.app.learning.AnswerOutcome
import com.wordiq.app.learning.ExerciseType
import com.wordiq.app.learning.GradeResult
import com.wordiq.app.learning.PracticeItem
import com.wordiq.app.learning.PromptLanguage
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PracticeAutoAdvanceTest {
    @Test
    fun onlySavedExactChoiceAndTypedAnswersAutoAdvance() {
        assertTrue(shouldAutoAdvanceCorrectAnswer(answeredItem(ExerciseType.MEANING_RECOGNITION, listOf("right", "wrong"))))
        assertTrue(shouldAutoAdvanceCorrectAnswer(answeredItem(ExerciseType.SPELLING_RECALL)))
        assertFalse(shouldAutoAdvanceCorrectAnswer(answeredItem(ExerciseType.SPELLING_RECALL, outcome = AnswerOutcome.WRONG)))
        assertFalse(shouldAutoAdvanceCorrectAnswer(answeredItem(ExerciseType.SPELLING_RECALL, saving = true)))
    }

    private fun answeredItem(
        type: ExerciseType,
        options: List<String> = emptyList(),
        outcome: AnswerOutcome = AnswerOutcome.EXACT,
        saving: Boolean = false,
    ): PracticeUiState {
        val concept = ConceptWithDetails(
            concept = VocabularyConceptEntity(
                id = 1,
                finnishLemma = "word",
                englishTranslations = "meaning",
                createdAt = 0,
                updatedAt = 0,
            ),
            forms = emptyList(),
            examples = emptyList(),
            sets = emptyList(),
            learningStates = emptyList(),
        )
        val item = PracticeItem(
            concept = concept,
            exerciseType = type,
            skill = LearningSkill.MEANING_RECOGNITION,
            promptLanguage = PromptLanguage.FINNISH,
            prompt = "word",
            expectedAnswers = listOf("meaning"),
            options = options,
            scheduled = true,
        )
        val feedback = PracticeFeedback(GradeResult(outcome, "meaning", "meaning", 0), "meaning")
        return PracticeUiState(loading = false, items = listOf(item), feedback = feedback, saving = saving)
    }
}
