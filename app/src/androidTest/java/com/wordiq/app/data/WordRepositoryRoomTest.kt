package com.wordiq.app.data

import android.content.Context
import android.net.Uri
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.wordiq.app.data.local.WordIqDatabase
import com.wordiq.app.data.local.LearningSkill
import com.wordiq.app.data.local.ReviewLogEntity
import com.wordiq.app.learning.AnswerGrader
import com.wordiq.app.learning.AnswerOutcome
import com.wordiq.app.learning.ExerciseType
import com.wordiq.app.learning.PracticeRequest
import com.wordiq.app.learning.PracticeFocus
import com.wordiq.app.learning.PracticeScopeType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WordRepositoryRoomTest {
    private lateinit var database: WordIqDatabase
    private lateinit var repository: WordRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, WordIqDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = WordRepository(database)
    }

    @After
    fun tearDown() = database.close()

    @Test
    fun sharedConceptHasOneIdentityAndOneLearningHistory() = runBlocking {
        repository.createSet("Chapter 2", null)
        repository.createSet("Work Finnish", null)
        val sets = repository.standaloneSets.first { it.size == 2 }
        val conceptId = repository.saveWord(
            WordDraft(
                finnish = "vaikuttaa",
                persian = "تأثیر گذاشتن",
                english = "to affect",
                setIds = sets.map { it.id }.toSet(),
            ),
        )

        assertEquals(conceptId, repository.observeSet(sets[0].id).first { it?.words?.isNotEmpty() == true }!!.words.single().id)
        assertEquals(conceptId, repository.observeSet(sets[1].id).first { it?.words?.isNotEmpty() == true }!!.words.single().id)
        assertEquals(LearningSkill.entries.size, database.wordIqDao().learningStateCount(conceptId))
        assertEquals(1, database.wordIqDao().membershipCount(conceptId, sets[0].id))
    }

    @Test
    fun collectionDeletionMakesSetsIndependentAndPreservesWords() = runBlocking {
        repository.createCollection("Suomen Mestari 3")
        val collection = repository.collections.first { it.isNotEmpty() }.single().collection
        repository.createSet("Chapter 1", collection.id)
        val set = repository.collections.first { it.single().sets.isNotEmpty() }.single().sets.single()
        val conceptId = repository.saveWord(WordDraft(finnish = "kauppa", english = "shop", setIds = setOf(set.id)))

        repository.deleteCollection(collection)

        assertTrue(repository.standaloneSets.first { it.any { item -> item.id == set.id } }.any { it.id == set.id })
        assertEquals(conceptId, repository.observeSet(set.id).first { it != null }!!.words.single().id)
    }

    @Test
    fun deletingOneSetDoesNotDeleteSharedConcept() = runBlocking {
        repository.createSet("One", null)
        repository.createSet("Two", null)
        val sets = repository.standaloneSets.first { it.size == 2 }
        val conceptId = repository.saveWord(
            WordDraft(finnish = "ystävä", english = "friend", setIds = sets.map { it.id }.toSet()),
        )

        repository.deleteSet(sets.first())

        assertEquals(conceptId, repository.observeSet(sets.last().id).first { it != null }!!.words.single().id)
        assertEquals(LearningSkill.entries.size, database.wordIqDao().learningStateCount(conceptId))
    }

    @Test
    fun dataPersistsAfterDatabaseIsClosedAndReopened() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val databaseName = "wordiq-stage-one-persistence-test.db"
        context.deleteDatabase(databaseName)
        try {
            var fileDatabase = Room.databaseBuilder(context, WordIqDatabase::class.java, databaseName).build()
            var fileRepository = WordRepository(fileDatabase)
            fileRepository.createSet("Persistent set", null)
            val set = fileRepository.standaloneSets.first { it.isNotEmpty() }.single()
            val conceptId = fileRepository.saveWord(
                WordDraft(finnish = "pysyä", english = "to remain", setIds = setOf(set.id)),
            )
            val practice = PracticeRepository(fileDatabase)
            val meaning = practice.buildPlan(
                PracticeRequest(PracticeScopeType.SET, scopeId = set.id),
                now = 1_800_000_000_000L,
            ).items.first { it.exerciseType == ExerciseType.MEANING_RECOGNITION }
            practice.record(meaning, AnswerOutcome.EXACT, 2_000, 0, false, now = 1_800_000_000_000L)
            val scheduledAt = fileDatabase.wordIqDao().getLearningState(conceptId, meaning.skill.name)!!.nextReview
            fileDatabase.close()

            fileDatabase = Room.databaseBuilder(context, WordIqDatabase::class.java, databaseName).build()
            fileRepository = WordRepository(fileDatabase)
            val reopened = fileRepository.observeSet(set.id).first { it != null }!!
            assertEquals(conceptId, reopened.words.single().id)
            assertEquals(scheduledAt, fileDatabase.wordIqDao().getLearningState(conceptId, meaning.skill.name)!!.nextReview)
            fileDatabase.close()
        } finally {
            context.deleteDatabase(databaseName)
        }
    }

    @Test
    fun scheduledReviewPersistsButManualStudyDoesNotChangeSchedule() = runBlocking {
        repository.createSet("Practice set", null)
        val set = repository.standaloneSets.first { it.isNotEmpty() }.single()
        val conceptId = repository.saveWord(
            WordDraft(finnish = "oppia", english = "to learn", setIds = setOf(set.id)),
        )
        val practice = PracticeRepository(database)
        val request = PracticeRequest(PracticeScopeType.SET, scopeId = set.id)
        val plan = practice.buildPlan(request, now = 1_800_000_000_000L)
        val meaning = plan.items.first { it.exerciseType == ExerciseType.MEANING_RECOGNITION }

        practice.record(
            item = meaning,
            outcome = AnswerOutcome.EXACT,
            latencyMs = 3_000,
            hintsUsed = 0,
            revealed = false,
            now = 1_800_000_000_000L,
        )
        val scheduledState = database.wordIqDao().getLearningState(conceptId, meaning.skill.name)!!
        assertTrue(scheduledState.nextReview!! > scheduledState.lastReview!!)
        assertEquals(1, database.wordIqDao().reviewLogCount(conceptId, true))
        assertEquals(3_000L, database.wordIqDao().latestReviewLog(conceptId)!!.responseLatencyMs)

        val manualPlan = practice.buildPlan(
            request.copy(manual = true, focus = PracticeFocus.MEANING_ENGLISH),
            now = 1_800_000_100_000L,
        )
        val manual = manualPlan.items.first { it.skill == meaning.skill }
        practice.record(
            item = manual,
            outcome = AnswerOutcome.EXACT,
            latencyMs = 1_000,
            hintsUsed = 0,
            revealed = false,
            now = 1_800_000_100_000L,
        )

        assertEquals(scheduledState, database.wordIqDao().getLearningState(conceptId, meaning.skill.name))
        assertEquals(1, database.wordIqDao().reviewLogCount(conceptId, false))
    }

    @Test
    fun globalSetAndCollectionScopesSelectTheCorrectConcepts() = runBlocking {
        repository.createCollection("Course")
        val collection = repository.collections.first { it.isNotEmpty() }.single().collection
        repository.createSet("Chapter A", collection.id)
        repository.createSet("Chapter B", collection.id)
        repository.createSet("Independent", null)
        val courseSets = repository.collections.first { it.single().sets.size == 2 }.single().sets
        val independent = repository.standaloneSets.first { it.isNotEmpty() }.single()
        val first = repository.saveWord(WordDraft(finnish = "ensimmäinen", english = "first", setIds = setOf(courseSets[0].id)))
        val second = repository.saveWord(WordDraft(finnish = "toinen", english = "second", setIds = setOf(courseSets[1].id)))
        val outside = repository.saveWord(WordDraft(finnish = "ulkona", english = "outside", setIds = setOf(independent.id)))
        val practice = PracticeRepository(database)

        val globalIds = practice.buildPlan(PracticeRequest(PracticeScopeType.GLOBAL, manual = true)).items.map { it.concept.concept.id }.toSet()
        val setIds = practice.buildPlan(PracticeRequest(PracticeScopeType.SET, courseSets[0].id, manual = true)).items.map { it.concept.concept.id }.toSet()
        val collectionIds = practice.buildPlan(PracticeRequest(PracticeScopeType.COLLECTION, collection.id, manual = true)).items.map { it.concept.concept.id }.toSet()

        assertEquals(setOf(first, second, outside), globalIds)
        assertEquals(setOf(first), setIds)
        assertEquals(setOf(first, second), collectionIds)
    }

    @Test
    fun listeningReplayCountPersistsAndLowersConfidence() = runBlocking {
        repository.createSet("Listening", null)
        val set = repository.standaloneSets.first { it.isNotEmpty() }.single()
        val conceptId = repository.saveWord(
            WordDraft(finnish = "kuunnella", english = "to listen", setIds = setOf(set.id)),
        )
        val practice = PracticeRepository(database)
        val listening = practice.buildPlan(
            PracticeRequest(PracticeScopeType.SET, set.id),
            now = 1_800_000_000_000L,
        ).items.first { it.exerciseType == ExerciseType.LISTEN_CHOICE }

        val result = practice.record(
            item = listening,
            outcome = AnswerOutcome.EXACT,
            latencyMs = 2_000,
            hintsUsed = 0,
            revealed = false,
            audioReplayCount = 2,
            now = 1_800_000_000_000L,
        )

        assertEquals("HARD", result.rating.name)
        assertEquals(2, database.wordIqDao().latestReviewLog(conceptId)!!.audioReplayCount)
        assertEquals(2, database.wordIqDao().getLearningState(conceptId, listening.skill.name)!!.recentHintUsage)
    }

    @Test
    fun structuredFormsAndSpokenFinnishPersist() = runBlocking {
        repository.createSet("Forms", null)
        val set = repository.standaloneSets.first { it.isNotEmpty() }.single()
        val conceptId = repository.saveWord(
            WordDraft(
                finnish = "minä olen",
                persian = "من هستم",
                english = "I am",
                spokenFinnish = "mä oon",
                forms = listOf(
                    WordFormDraft(
                        form = "minä olin",
                        label = "past",
                        explanation = "I was",
                        decomposition = "minä + olin",
                        persianMeaning = "من بودم",
                        persianNote = "olin = بودم",
                    ),
                ),
                setIds = setOf(set.id),
            ),
        )

        val saved = repository.observeConcept(conceptId).first { it != null }!!
        assertEquals("mä oon", saved.concept.spokenFinnish)
        assertEquals("minä + olin", saved.forms.single().decomposition)
        assertEquals("من بودم", saved.forms.single().persianMeaning)
        assertEquals("olin = بودم", saved.forms.single().persianNote)
    }

    @Test
    fun rememberedLemmaFailsMorphologyButStrengthensLexicalProduction() = runBlocking {
        repository.createSet("Morphology", null)
        val set = repository.standaloneSets.first { it.isNotEmpty() }.single()
        val conceptId = repository.saveWord(
            WordDraft(
                finnish = "kauppa",
                english = "store",
                forms = listOf(WordFormDraft("kaupasta", "elative", "from the store", "kauppa + sta")),
                setIds = setOf(set.id),
            ),
        )
        val practice = PracticeRepository(database)
        val manualProduction = practice.buildPlan(
            PracticeRequest(PracticeScopeType.SET, set.id, manual = true, focus = PracticeFocus.FORMS),
        ).items.first { it.exerciseType == ExerciseType.FORM_PRODUCTION }
        val scheduledProduction = manualProduction.copy(scheduled = true)
        val grade = AnswerGrader().grade("kauppa", scheduledProduction.expectedAnswers, scheduledProduction.expectedLemma)

        val result = practice.record(
            item = scheduledProduction,
            outcome = grade.outcome,
            latencyMs = 4_000,
            hintsUsed = 0,
            revealed = false,
            lemmaRemembered = grade.lemmaRemembered,
            now = 1_800_000_000_000L,
        )

        assertEquals("AGAIN", result.rating.name)
        assertEquals(1, database.wordIqDao().getLearningState(conceptId, LearningSkill.MORPHOLOGY.name)!!.failedRecalls)
        assertEquals(1, database.wordIqDao().getLearningState(conceptId, LearningSkill.FINNISH_PRODUCTION.name)!!.successfulRecalls)
        assertTrue(database.wordIqDao().latestReviewLog(conceptId)!!.lemmaRemembered)
    }

    @Test
    fun librarySearchFindsFinnishTranslationsAndInflectedForms() = runBlocking {
        repository.createSet("Search", null)
        val set = repository.standaloneSets.first { it.isNotEmpty() }.single()
        val id = repository.saveWord(
            WordDraft(
                finnish = "kauppa",
                persian = "فروشگاه",
                english = "shop",
                forms = listOf(WordFormDraft("kaupasta", "elative")),
                setIds = setOf(set.id),
            ),
        )

        assertEquals(id, repository.searchConcepts("kauppa").first { it.isNotEmpty() }.single().concept.id)
        assertEquals(id, repository.searchConcepts("فروش").first { it.isNotEmpty() }.single().concept.id)
        assertEquals(id, repository.searchConcepts("shop").first { it.isNotEmpty() }.single().concept.id)
        assertEquals(id, repository.searchConcepts("kaupasta").first { it.isNotEmpty() }.single().concept.id)
    }

    @Test
    fun progressAndSetReadinessComeFromLearningHistory() = runBlocking {
        repository.createSet("Progress", null)
        val set = repository.standaloneSets.first { it.isNotEmpty() }.single()
        val conceptId = repository.saveWord(WordDraft(finnish = "oppia", english = "learn", setIds = setOf(set.id)))
        val meaning = database.wordIqDao().getLearningState(conceptId, LearningSkill.MEANING_RECOGNITION.name)!!
        database.wordIqDao().upsertLearningState(
            meaning.copy(lastReview = 100L, nextReview = 200L, stability = 20.0, successfulRecalls = 2),
        )

        val progress = repository.observeProgress(now = 300L).first { it.recognizedConcepts == 1 }
        val setProgress = repository.observeSetProgress(set.id, now = 300L).first { it != null }!!

        assertEquals(1, progress.learningConcepts)
        assertEquals(1, progress.recognizedConcepts)
        assertEquals(1, setProgress.wordCount)
        assertEquals(1, setProgress.readyCount)
    }

    @Test
    fun resettingProgressPreservesVocabularyAndSets() = runBlocking {
        repository.createSet("Keep me", null)
        val set = repository.standaloneSets.first { it.isNotEmpty() }.single()
        val conceptId = repository.saveWord(WordDraft(finnish = "sana", english = "word", setIds = setOf(set.id)))
        database.wordIqDao().upsertLearningState(
            database.wordIqDao().getLearningState(conceptId, LearningSkill.LISTENING.name)!!.copy(
                lastReview = 10L,
                nextReview = 20L,
                successfulRecalls = 3,
            ),
        )

        repository.resetLearningProgress()

        assertEquals(conceptId, repository.observeSet(set.id).first { it != null }!!.words.single().id)
        val state = database.wordIqDao().getLearningState(conceptId, LearningSkill.LISTENING.name)!!
        assertEquals(null, state.lastReview)
        assertEquals(0, state.successfulRecalls)
    }

    @Test
    fun setDeleteUndoRestoresMembershipWithoutResettingWord() = runBlocking {
        repository.createSet("Undo set", null)
        val set = repository.standaloneSets.first { it.isNotEmpty() }.single()
        val conceptId = repository.saveWord(
            WordDraft(finnish = "kauppa", english = "shop", setIds = setOf(set.id)),
        )

        val snapshot = repository.deleteSetForUndo(set)
        assertTrue(repository.standaloneSets.first().isEmpty())

        repository.restoreSet(snapshot)

        val restored = repository.observeSet(set.id).first { it?.words?.isNotEmpty() == true }!!
        assertEquals(conceptId, restored.words.single().id)
    }

    @Test
    fun wordDeleteUndoRestoresDetailsMembershipAndLearningHistory() = runBlocking {
        repository.createSet("Undo word", null)
        val set = repository.standaloneSets.first { it.isNotEmpty() }.single()
        val conceptId = repository.saveWord(
            WordDraft(
                finnish = "kauppa",
                english = "shop",
                forms = listOf(WordFormDraft("kaupassa", "inessive")),
                exampleFinnish = "Olen kaupassa.",
                setIds = setOf(set.id),
            ),
        )
        val state = database.wordIqDao().getLearningState(conceptId, LearningSkill.MEANING_RECOGNITION.name)!!
        database.wordIqDao().upsertLearningState(state.copy(lastReview = 100L, nextReview = 200L, successfulRecalls = 2))
        database.wordIqDao().insertReviewLog(
            ReviewLogEntity(
                conceptId = conceptId,
                skill = LearningSkill.MEANING_RECOGNITION.name,
                exerciseType = ExerciseType.MEANING_RECOGNITION.name,
                outcome = AnswerOutcome.EXACT.name,
                internalRating = "GOOD",
                responseLatencyMs = 1_200,
                hintsUsed = 0,
                revealed = false,
                scheduled = true,
                reviewedAt = 100L,
            ),
        )

        val details = repository.observeConcept(conceptId).first { it != null }!!
        val snapshot = repository.deleteWordForUndo(details)
        assertTrue(repository.observeSet(set.id).first { it != null }!!.words.isEmpty())

        repository.restoreWord(snapshot)

        val restored = repository.observeConcept(conceptId).first { it != null }!!
        assertEquals("kaupassa", restored.forms.single().form)
        assertEquals("Olen kaupassa.", restored.examples.single().finnishSentence)
        assertEquals(1, database.wordIqDao().membershipCount(conceptId, set.id))
        assertEquals(2, database.wordIqDao().getLearningState(conceptId, LearningSkill.MEANING_RECOGNITION.name)!!.successfulRecalls)
        assertEquals(1, database.wordIqDao().reviewLogCount(conceptId, true))
    }

    @Test
    fun collectionDeleteUndoKeepsSetMemberships() = runBlocking {
        repository.createCollection("Course")
        val collection = repository.collections.first { it.isNotEmpty() }.single().collection
        repository.createSet("Chapter 1", collection.id)
        val set = repository.collections.first { it.single().sets.isNotEmpty() }.single().sets.single()
        val conceptId = repository.saveWord(
            WordDraft(finnish = "kirja", english = "book", setIds = setOf(set.id)),
        )

        val snapshot = repository.deleteCollectionForUndo(collection)
        assertEquals(set.id, repository.standaloneSets.first { it.isNotEmpty() }.single().id)

        repository.restoreCollection(snapshot)

        val restored = repository.observeSet(set.id).first { it?.words?.isNotEmpty() == true }!!
        assertEquals(collection.id, restored.set.collectionId)
        assertEquals(conceptId, restored.words.single().id)
    }

    @Test
    fun csvExportAndImportRoundTripVocabularyMembershipAndMetadata() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val portability = DataPortabilityRepository(context, database)
        repository.createSet("Portable", null)
        val set = repository.standaloneSets.first { it.isNotEmpty() }.single()
        val conceptId = repository.saveWord(
            WordDraft(
                finnish = "työ",
                persian = "کار",
                english = "work",
                exampleFinnish = "Olen työssä.",
                setIds = setOf(set.id),
            ),
        )
        val state = database.wordIqDao().getLearningState(conceptId, LearningSkill.MEANING_RECOGNITION.name)!!
        database.wordIqDao().upsertLearningState(state.copy(stability = 8.5, lastReview = 100L, nextReview = 200L))
        val file = java.io.File(context.cacheDir, "wordiq-csv-roundtrip.csv")

        assertEquals(1, portability.exportCsv(Uri.fromFile(file)))
        repository.deleteWord(repository.observeConcept(conceptId).first { it != null }!!.concept)
        val result = portability.importCsv(Uri.fromFile(file))

        assertEquals(1, result.importedRows)
        val restored = repository.searchConcepts("työ").first { it.isNotEmpty() }.single()
        assertEquals("کار", restored.concept.persianTranslations)
        assertEquals("Olen työssä.", restored.examples.single().finnishSentence)
        assertEquals(8.5, database.wordIqDao().getLearningState(restored.concept.id, LearningSkill.MEANING_RECOGNITION.name)!!.stability, 0.0)
        file.delete()
        Unit
    }

    @Test
    fun structuredBackupRestoresFormsAndReviewHistory() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val portability = DataPortabilityRepository(context, database)
        repository.createCollection("Course")
        val collection = repository.collections.first { it.isNotEmpty() }.single().collection
        repository.createSet("Chapter", collection.id)
        val set = repository.collections.first { it.single().sets.isNotEmpty() }.single().sets.single()
        val conceptId = repository.saveWord(
            WordDraft(
                finnish = "kauppa",
                english = "shop",
                forms = listOf(WordFormDraft("kaupassa", "inessive", decomposition = "kauppa + ssa")),
                setIds = setOf(set.id),
            ),
        )
        database.wordIqDao().insertReviewLog(
            ReviewLogEntity(
                conceptId = conceptId,
                skill = LearningSkill.MORPHOLOGY.name,
                exerciseType = "FORM_RECOGNITION",
                outcome = "EXACT",
                internalRating = "GOOD",
                responseLatencyMs = 1_500,
                hintsUsed = 0,
                revealed = false,
                scheduled = true,
                reviewedAt = 123L,
            ),
        )
        val file = java.io.File(context.cacheDir, "wordiq-full-backup.json")

        assertEquals(1, portability.exportBackup(Uri.fromFile(file)))
        repository.deleteWord(repository.observeConcept(conceptId).first { it != null }!!.concept)
        assertEquals(1, portability.restoreBackup(Uri.fromFile(file)))

        val restored = repository.observeConcept(conceptId).first { it != null }!!
        assertEquals("kauppa + ssa", restored.forms.single().decomposition)
        assertEquals(1, database.wordIqDao().reviewLogCount(conceptId, true))
        assertEquals("Course", repository.collections.first { it.isNotEmpty() }.single().collection.name)
        file.delete()
        Unit
    }
}
