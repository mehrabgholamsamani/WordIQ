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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PracticeAudioPolicyTest {
    @Test
    fun visibleFinnishPromptPronouncesImmediately() {
        val item = item(
            type = ExerciseType.MEANING_RECOGNITION,
            promptLanguage = PromptLanguage.FINNISH,
            prompt = "kauppa",
            expected = listOf("shop"),
            audio = "kauppa",
        )

        assertEquals("kauppa", automaticPronunciationText(state(item)))
    }

    @Test
    fun productionAnswerIsSilentUntilFeedbackRevealsFinnish() {
        val item = item(
            type = ExerciseType.FINNISH_PRODUCTION,
            promptLanguage = PromptLanguage.ENGLISH,
            prompt = "shop",
            expected = listOf("kauppa"),
        )

        assertNull(automaticPronunciationText(state(item)))
        assertEquals("kauppa", automaticPronunciationText(state(item, feedback = feedback("kauppa"))))
    }

    @Test
    fun reverseSelfCheckWaitsForReveal() {
        val item = item(
            type = ExerciseType.FREE_RECALL,
            promptLanguage = PromptLanguage.PERSIAN,
            prompt = "فروشگاه",
            expected = listOf("kauppa"),
            selfCheck = true,
        )

        assertNull(automaticPronunciationText(state(item)))
        assertEquals("kauppa", automaticPronunciationText(state(item).copy(selfCheckRevealed = true)))
    }

    @Test
    fun spellingPromptUsesFinnishAudio() {
        val item = item(
            type = ExerciseType.SPELLING_RECALL,
            promptLanguage = PromptLanguage.ENGLISH,
            prompt = "shop",
            expected = listOf("kauppa"),
            audio = "kauppa",
        )

        assertEquals("kauppa", automaticPronunciationText(state(item)))
    }

    private fun state(item: PracticeItem, feedback: PracticeFeedback? = null) = PracticeUiState(
        loading = false,
        items = listOf(item),
        feedback = feedback,
    )

    private fun feedback(expected: String) = PracticeFeedback(
        GradeResult(AnswerOutcome.EXACT, expected, expected, 0),
        expected,
    )

    private fun item(
        type: ExerciseType,
        promptLanguage: PromptLanguage,
        prompt: String,
        expected: List<String>,
        audio: String? = null,
        selfCheck: Boolean = false,
    ) = PracticeItem(
        concept = ConceptWithDetails(
            concept = VocabularyConceptEntity(finnishLemma = "kauppa", createdAt = 1L, updatedAt = 1L),
            forms = emptyList(),
            examples = emptyList(),
            sets = emptyList(),
            learningStates = emptyList(),
        ),
        exerciseType = type,
        skill = if (type == ExerciseType.MEANING_RECOGNITION) LearningSkill.MEANING_RECOGNITION else LearningSkill.FINNISH_PRODUCTION,
        promptLanguage = promptLanguage,
        prompt = prompt,
        expectedAnswers = expected,
        scheduled = true,
        audioText = audio,
        selfCheck = selfCheck,
    )
}
