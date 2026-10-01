package com.wordiq.app.learning

import com.wordiq.app.data.local.ConceptWithDetails
import com.wordiq.app.data.local.ExampleSentenceEntity
import com.wordiq.app.data.local.LearningSkill
import com.wordiq.app.data.local.LearningStateEntity
import com.wordiq.app.data.local.VocabularyConceptEntity
import com.wordiq.app.data.local.WordFormEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FinnishMorphologyTest {
    private val service = ManualFinnishMorphologyService()
    private val now = 1_800_000_000_000L

    @Test
    fun manualMappingsAnalyzeAndUnlockFormsGradually() {
        val concept = kauppa()
        val newState = LearningStateEntity(1, LearningSkill.MORPHOLOGY.name)
        assertEquals(listOf("kaupassa"), service.unlockedAnalyses(concept, newState).map { it.surface })
        assertEquals(3, service.unlockedAnalyses(concept, newState.copy(successfulRecalls = 4)).size)
        assertEquals("kauppa + sta", service.analyze("KAUPASTA", concept)?.decomposition)
    }

    @Test
    fun formsFocusIncludesRecognitionMeaningAndProductionWithoutScheduling() {
        val concept = kauppa()
        val plan = PracticeQueueBuilder().build(
            listOf(concept),
            listOf(concept, simple(2, "koulu")),
            PracticeRequest(PracticeScopeType.GLOBAL, manual = true, focus = PracticeFocus.FORMS, explanationLanguage = "فارسی"),
            12_000,
            now,
        )
        assertEquals(
            listOf(
                ExerciseType.FORM_RECOGNITION_LEMMA,
                ExerciseType.FORM_RECOGNITION_MEANING,
                ExerciseType.FORM_PRODUCTION,
            ),
            plan.items.map { it.exerciseType },
        )
        assertFalse(plan.items.any { it.scheduled })
        assertEquals("kauppa", plan.items.last().expectedLemma)
        assertEquals("-ssa / -ssä ≈ در", plan.items.last().persianNote)
    }

    @Test
    fun inflectedClozeCarriesLemmaAwareMorphologyTarget() {
        val concept = kauppa()
        val plan = PracticeQueueBuilder().build(
            listOf(concept),
            listOf(concept),
            PracticeRequest(PracticeScopeType.GLOBAL),
            12_000,
            now,
        )
        val cloze = plan.items.first { it.exerciseType == ExerciseType.CLOZE_CHOICE }
        assertEquals(LearningSkill.MORPHOLOGY, cloze.skill)
        assertEquals("kauppa", cloze.expectedLemma)
        assertEquals("kauppaan", cloze.morphologyAnalysis?.surface)
    }

    @Test
    fun spokenFinnishRecognitionUsesStandardFormAsAnswer() {
        val phrase = simple(1, "minä olen", spoken = "mä oon")
        val plan = PracticeQueueBuilder().build(
            listOf(phrase),
            listOf(phrase, simple(2, "sinä olet")),
            PracticeRequest(PracticeScopeType.GLOBAL, manual = true, focus = PracticeFocus.SPOKEN),
            12_000,
            now,
        )
        val item = plan.items.single()
        assertEquals(ExerciseType.SPOKEN_RECOGNITION, item.exerciseType)
        assertEquals("mä oon", item.prompt)
        assertEquals(listOf("minä olen"), item.expectedAnswers)
    }

    @Test
    fun minimalPairServiceAndListeningQueueSupportQuantityContrast() {
        val tuli = simple(1, "tuli")
        val tuuli = simple(2, "tuuli")
        val pair = LocalFinnishSoundPairService().findPair(tuli, listOf(tuli, tuuli))
        assertNotNull(pair)
        assertEquals(FinnishQuantityType.VOWEL, pair?.quantityType)

        val plan = PracticeQueueBuilder().build(
            listOf(tuli),
            listOf(tuli, tuuli),
            PracticeRequest(PracticeScopeType.GLOBAL, manual = true, focus = PracticeFocus.LISTENING),
            12_000,
            now,
        )
        val exercise = plan.items.single()
        assertEquals(ExerciseType.MINIMAL_PAIR_LISTENING, exercise.exerciseType)
        assertTrue(exercise.options.containsAll(listOf("tuli", "tuuli")))
        assertEquals("tuli", exercise.audioText)
    }

    private fun kauppa(): ConceptWithDetails {
        val base = simple(1, "kauppa")
        return base.copy(
            forms = listOf(
                WordFormEntity(1, 1, "kaupassa", "inessive", "in the store", "kauppa + ssa", "در فروشگاه", "-ssa / -ssä ≈ در", 0),
                WordFormEntity(2, 1, "kaupasta", "elative", "from the store", "kauppa + sta", "از فروشگاه", "-sta / -stä ≈ از", 1),
                WordFormEntity(3, 1, "kauppaan", "illative", "into the store", "kauppa + an", "به فروشگاه", null, 2),
            ),
            examples = listOf(
                ExampleSentenceEntity(1, 1, "Menen kauppaan.", targetWordOrForm = "kauppaan"),
            ),
        )
    }

    private fun simple(id: Long, lemma: String, spoken: String? = null) = ConceptWithDetails(
        concept = VocabularyConceptEntity(
            id = id,
            finnishLemma = lemma,
            persianTranslations = "معنی$id",
            englishTranslations = "meaning$id",
            spokenFinnish = spoken,
            createdAt = now - id,
            updatedAt = now - id,
        ),
        forms = emptyList(),
        examples = emptyList(),
        sets = emptyList(),
        learningStates = emptyList(),
    )
}
