package com.wordiq.app.learning

import com.wordiq.app.data.local.ConceptWithDetails
import com.wordiq.app.data.local.ExampleSentenceEntity
import com.wordiq.app.data.local.LearningSkill
import com.wordiq.app.data.local.LearningStateEntity
import com.wordiq.app.data.local.VocabularyConceptEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PracticeQueueTest {
    private val builder = PracticeQueueBuilder()
    private val now = 1_800_000_000_000L

    @Test
    fun newWordsReceiveCoreListeningAndContextExercises() {
        val concept = concept(1)
        val plan = builder.build(listOf(concept), listOf(concept), request(), 12_000, now)
        assertEquals(
            listOf(
                ExerciseType.INTRODUCTION,
                ExerciseType.MEANING_RECOGNITION,
                ExerciseType.FINNISH_PRODUCTION,
                ExerciseType.LISTEN_CHOICE,
                ExerciseType.SPELLING_RECALL,
                ExerciseType.CLOZE_CHOICE,
            ),
            plan.items.map { it.exerciseType },
        )
        assertEquals(1, plan.newConceptCount)
    }

    @Test
    fun highReviewLoadPreventsNewWordIntake() {
        val due = (1L..14L).map { concept(it, due = true, productionDue = true) }
        val new = listOf(concept(100), concept(101))
        val plan = builder.build(due + new, due + new, request(), 12_000, now)
        assertEquals(0, plan.newConceptCount)
        assertTrue(plan.items.none { it.exerciseType == ExerciseType.INTRODUCTION })
        assertTrue(plan.items.size <= 25)
    }

    @Test
    fun manualStudyNeverMarksItemsScheduled() {
        val concepts = (1L..4L).map { concept(it, due = true) }
        val plan = builder.build(concepts, concepts, request().copy(manual = true), 12_000, now)
        assertTrue(plan.items.isNotEmpty())
        assertFalse(plan.items.any { it.scheduled })
    }

    @Test
    fun reviewFocusDoesNotIntroduceNewWords() {
        val concept = concept(1)
        val plan = builder.build(listOf(concept), listOf(concept), request().copy(focus = PracticeFocus.REVIEW), 12_000, now)
        assertTrue(plan.items.isEmpty())
    }

    @Test
    fun listeningWeaknessSelectsSupportiveAudioExerciseAndAvoidsImmediateRepeat() {
        val weakListening = concept(1, learned = true, listeningDue = true, listeningFailures = 3)
        val review = request().copy(focus = PracticeFocus.REVIEW)
        val first = builder.build(listOf(weakListening), listOf(weakListening), review, 12_000, now)
        assertTrue(first.items.any { it.exerciseType == ExerciseType.LISTEN_CHOICE })

        val varied = builder.build(
            listOf(weakListening),
            listOf(weakListening),
            review,
            12_000,
            now,
            mapOf(1L to ExerciseType.LISTEN_CHOICE),
        )
        assertTrue(varied.items.any { it.exerciseType == ExerciseType.LISTEN_MEANING })
    }

    @Test
    fun strongListeningMovesFromChoiceToTypedAudio() {
        val strongListening = concept(1, learned = true, listeningDue = true, listeningSuccesses = 3)
        val plan = builder.build(
            listOf(strongListening),
            listOf(strongListening),
            request().copy(focus = PracticeFocus.REVIEW),
            12_000,
            now,
        )
        assertTrue(plan.items.any { it.exerciseType == ExerciseType.LISTEN_TYPE })
    }

    @Test
    fun storedExampleCreatesBeginnerClozeWithSurfaceAnswer() {
        val target = concept(1)
        val plan = builder.build(listOf(target), listOf(target, concept(2)), request(), 12_000, now)
        val cloze = plan.items.first { it.exerciseType == ExerciseType.CLOZE_CHOICE }
        assertEquals("I use ______.", cloze.prompt)
        assertEquals(listOf("word1"), cloze.expectedAnswers)
        assertTrue("word1" in cloze.options)
    }

    @Test
    fun repeatedFailuresInsertRescueBeforeDuePractice() {
        val trouble = concept(1, due = true, meaningFailures = 3)
        val plan = builder.build(listOf(trouble), listOf(trouble, concept(2)), request(), 12_000, now)
        assertEquals(ExerciseType.RESCUE, plan.items.first().exerciseType)
        assertFalse(plan.items.first().scheduled)
        assertEquals("wor · d1", plan.items.first().rescueContent?.spellingChunks)
    }

    @Test
    fun selfCheckMeaningUsesFinnishPromptAndSchedulesMeaningSkill() {
        val concepts = listOf(concept(1), concept(2))
        val plan = builder.build(
            concepts,
            concepts,
            request().copy(focus = PracticeFocus.SELF_CHECK_MEANING, explanationLanguage = "English"),
            12_000,
            now,
        )

        assertTrue(plan.items.isNotEmpty())
        assertTrue(plan.items.all { it.selfCheck })
        assertTrue(plan.items.all { it.scheduled })
        assertTrue(plan.items.all { it.exerciseType == ExerciseType.MEANING_RECOGNITION })
        assertTrue(plan.items.all { it.promptLanguage == PromptLanguage.FINNISH })
        assertTrue(plan.items.all { it.answerLanguage == PromptLanguage.ENGLISH })
    }

    @Test
    fun reverseSelfCheckUsesRequestedPersianDirection() {
        val target = concept(1)
        val plan = builder.build(
            listOf(target),
            listOf(target),
            request().copy(focus = PracticeFocus.SELF_CHECK_PERSIAN),
            12_000,
            now,
        )

        val item = plan.items.single()
        assertTrue(item.selfCheck)
        assertEquals(PromptLanguage.PERSIAN, item.promptLanguage)
        assertEquals(target.concept.persianTranslations, item.prompt)
        assertEquals(listOf(target.concept.finnishLemma), item.expectedAnswers)
    }

    @Test
    fun dedicatedMultipleChoiceContainsOnlyChoiceQuestionsWithDistractors() {
        val concepts = listOf(concept(1), concept(2), concept(3))
        val plan = builder.build(
            concepts,
            concepts,
            request().copy(focus = PracticeFocus.MULTIPLE_CHOICE_ENGLISH),
            12_000,
            now,
        )

        assertTrue(plan.items.isNotEmpty())
        assertTrue(plan.items.all { it.exerciseType == ExerciseType.REVERSE_RECOGNITION })
        assertTrue(plan.items.all { it.options.distinct().size >= 2 })
        assertTrue(plan.items.all { it.scheduled })
    }

    @Test
    fun dedicatedExercisesUseShuffledConceptOrder() {
        val concepts = listOf(concept(1), concept(2), concept(3), concept(4))
        val reversingBuilder = PracticeQueueBuilder(shuffleConcepts = { it.reversed() })

        val plan = reversingBuilder.build(
            concepts,
            concepts,
            request().copy(focus = PracticeFocus.SELF_CHECK_MEANING, explanationLanguage = "English"),
            12_000,
            now,
        )

        assertEquals(listOf(4L, 3L, 2L, 1L), plan.items.map { it.concept.concept.id })
    }

    @Test
    fun multipleChoiceOptionsUseSessionShuffle() {
        val concepts = listOf(concept(1), concept(2), concept(3), concept(4))
        val sortingBuilder = PracticeQueueBuilder(
            shuffleConcepts = { it },
            shuffleOptions = { it.sortedDescending() },
        )

        val item = sortingBuilder.build(
            concepts,
            concepts,
            request().copy(focus = PracticeFocus.MULTIPLE_CHOICE_ENGLISH),
            12_000,
            now,
        ).items.first()

        assertEquals(listOf("word4", "word3", "word2", "word1"), item.options)
        assertTrue(item.expectedAnswers.single() in item.options)
    }

    @Test
    fun multipleChoiceGracefullySkipsScopeWithoutADistractor() {
        val only = concept(1)
        val plan = builder.build(
            listOf(only),
            listOf(only),
            request().copy(focus = PracticeFocus.MULTIPLE_CHOICE_MEANING),
            12_000,
            now,
        )

        assertTrue(plan.items.isEmpty())
    }

    @Test
    fun spellingTestContainsOnlyFinnishSpellingQuestions() {
        val concepts = listOf(concept(1), concept(2))
        val plan = builder.build(
            concepts,
            concepts,
            request().copy(focus = PracticeFocus.SPELLING_ENGLISH),
            12_000,
            now,
        )

        assertTrue(plan.items.isNotEmpty())
        assertTrue(plan.items.all { it.exerciseType == ExerciseType.SPELLING_RECALL })
        assertTrue(plan.items.all { it.promptLanguage == PromptLanguage.ENGLISH })
        assertTrue(plan.items.all { it.expectedAnswers == listOf(it.concept.concept.finnishLemma) })
    }

    @Test
    fun longerDailyGoalProducesALongerDueSession() {
        val due = (1L..60L).map { concept(it, due = true) }
        val fiveMinutes = builder.build(due, due, request().copy(dailyMinutes = 5), 12_000, now)
        val fifteenMinutes = builder.build(due, due, request().copy(dailyMinutes = 15), 12_000, now)

        assertTrue(fifteenMinutes.items.size > fiveMinutes.items.size)
        assertTrue(fifteenMinutes.estimatedMinutes > fiveMinutes.estimatedMinutes)
    }

    private fun request() = PracticeRequest(PracticeScopeType.GLOBAL, dailyMinutes = 5)

    private fun concept(
        id: Long,
        due: Boolean = false,
        productionDue: Boolean = false,
        learned: Boolean = false,
        listeningDue: Boolean = false,
        listeningFailures: Int = 0,
        listeningSuccesses: Int = 0,
        meaningFailures: Int = 0,
    ): ConceptWithDetails {
        val created = now - id * 1_000
        val states = buildList {
            add(
                LearningStateEntity(
                    conceptId = id,
                    skill = LearningSkill.MEANING_RECOGNITION.name,
                    lastReview = if (due || learned) now - 2 * DAY_MS else null,
                    nextReview = if (due) now - DAY_MS else if (learned) now + DAY_MS else null,
                    stability = if (due || learned) 2.0 else 0.0,
                    failedRecalls = meaningFailures,
                ),
            )
            add(
                LearningStateEntity(
                    conceptId = id,
                    skill = LearningSkill.FINNISH_PRODUCTION.name,
                    lastReview = if (productionDue) now - 2 * DAY_MS else null,
                    nextReview = if (productionDue) now - DAY_MS else null,
                    stability = if (productionDue) 2.0 else 0.0,
                ),
            )
            add(
                LearningStateEntity(
                    conceptId = id,
                    skill = LearningSkill.LISTENING.name,
                    lastReview = if (listeningDue) now - 2 * DAY_MS else null,
                    nextReview = if (listeningDue) now - DAY_MS else null,
                    stability = if (listeningDue) 1.0 else 0.0,
                    failedRecalls = listeningFailures,
                    successfulRecalls = listeningSuccesses,
                ),
            )
        }
        return ConceptWithDetails(
            concept = VocabularyConceptEntity(
                id = id,
                finnishLemma = "word$id",
                persianTranslations = "واژه$id",
                englishTranslations = "meaning$id",
                createdAt = created,
                updatedAt = created,
            ),
            forms = emptyList(),
            examples = listOf(
                ExampleSentenceEntity(
                    id = id,
                    conceptId = id,
                    finnishSentence = "I use word$id.",
                    englishTranslation = "Example $id",
                    targetWordOrForm = "word$id",
                ),
            ),
            sets = emptyList(),
            learningStates = states,
        )
    }

    companion object { private const val DAY_MS = 86_400_000L }
}
