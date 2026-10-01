package com.wordiq.app.data

import androidx.room.withTransaction
import com.wordiq.app.data.local.CollectionEntity
import com.wordiq.app.data.local.CollectionWithSets
import com.wordiq.app.data.local.ConceptWithDetails
import com.wordiq.app.data.local.ExampleSentenceEntity
import com.wordiq.app.data.local.FlashcardSetEntity
import com.wordiq.app.data.local.LearningSkill
import com.wordiq.app.data.local.LearningStateEntity
import com.wordiq.app.data.local.ReviewLogEntity
import com.wordiq.app.data.local.SetMembershipEntity
import com.wordiq.app.data.local.SetWithWords
import com.wordiq.app.data.local.VocabularyConceptEntity
import com.wordiq.app.data.local.WordFormEntity
import com.wordiq.app.data.local.WordIqDatabase
import java.io.Serializable
import java.util.Calendar
import java.util.TimeZone
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow

private const val TIME_REFRESH_INTERVAL_MS = 60_000L

data class ProgressSummary(
    val totalConcepts: Int = 0,
    val learningConcepts: Int = 0,
    val strongConcepts: Int = 0,
    val recognizedConcepts: Int = 0,
    val recalledConcepts: Int = 0,
    val listenedConcepts: Int = 0,
    val weakConcepts: Int = 0,
    val practiceDays: Int = 0,
)

data class DeletedSetSnapshot(
    val set: FlashcardSetEntity,
    val memberships: List<SetMembershipEntity>,
)

data class DeletedCollectionSnapshot(
    val collection: CollectionEntity,
    val sets: List<FlashcardSetEntity>,
)

data class DeletedWordSnapshot(
    val details: ConceptWithDetails,
    val memberships: List<SetMembershipEntity>,
    val reviewLogs: List<ReviewLogEntity>,
)

data class WordFormDraft(
    val form: String,
    val label: String = "",
    val explanation: String = "",
    val decomposition: String = "",
    val persianMeaning: String = "",
    val persianNote: String = "",
) : Serializable

data class WordDraft(
    val id: Long = 0,
    val finnish: String = "",
    val persian: String = "",
    val english: String = "",
    val partOfSpeech: String = "",
    val notes: String = "",
    val spokenFinnish: String = "",
    val exampleFinnish: String = "",
    val examplePersian: String = "",
    val exampleEnglish: String = "",
    val forms: List<WordFormDraft> = emptyList(),
    val setIds: Set<Long> = emptySet(),
) : Serializable

internal fun startOfLocalWeekMillis(now: Long, timeZone: TimeZone = TimeZone.getDefault()): Long {
    val calendar = Calendar.getInstance(timeZone).apply { timeInMillis = now }
    val daysSinceMonday = (calendar.get(Calendar.DAY_OF_WEEK) - Calendar.MONDAY + 7) % 7
    calendar.add(Calendar.DAY_OF_MONTH, -daysSinceMonday)
    calendar.set(Calendar.HOUR_OF_DAY, 0)
    calendar.set(Calendar.MINUTE, 0)
    calendar.set(Calendar.SECOND, 0)
    calendar.set(Calendar.MILLISECOND, 0)
    return calendar.timeInMillis
}

internal fun localPracticeDayCount(reviewedAt: List<Long>, timeZone: TimeZone = TimeZone.getDefault()): Int =
    reviewedAt.map { timestamp ->
        Calendar.getInstance(timeZone).apply { timeInMillis = timestamp }.let { calendar ->
            calendar.get(Calendar.YEAR) to calendar.get(Calendar.DAY_OF_YEAR)
        }
    }.distinct().size

fun validateWordDraft(draft: WordDraft): String? = when {
    draft.finnish.isBlank() -> "Finnish is required"
    draft.persian.isBlank() && draft.english.isBlank() -> "Add a Persian or English translation"
    draft.setIds.isEmpty() -> "Choose at least one set"
    else -> null
}

@OptIn(ExperimentalCoroutinesApi::class)
class WordRepository(private val database: WordIqDatabase) {
    private val dao = database.wordIqDao()

    val collections: Flow<List<CollectionWithSets>> = dao.observeCollections()
    val standaloneSets: Flow<List<FlashcardSetEntity>> = dao.observeStandaloneSets()
    val allSets: Flow<List<FlashcardSetEntity>> = dao.observeAllSets()
    val conceptCount: Flow<Int> = dao.observeConceptCount()
    val allSetProgress: Flow<List<com.wordiq.app.data.local.SetProgressSnapshot>> =
        currentTimeFlow().flatMapLatest(dao::observeAllSetProgress)

    fun observeSet(setId: Long): Flow<SetWithWords?> = dao.observeSet(setId)
    fun observeSetMastery(setId: Long): Flow<Double> = dao.observeSetMastery(setId)
    fun observeSetProgress(setId: Long, now: Long): Flow<com.wordiq.app.data.local.SetProgressSnapshot?> =
        dao.observeSetProgress(setId, now)
    fun observeSetProgress(setId: Long): Flow<com.wordiq.app.data.local.SetProgressSnapshot?> =
        currentTimeFlow().flatMapLatest { now -> dao.observeSetProgress(setId, now) }
    fun observeCollectionSetProgress(collectionId: Long, now: Long): Flow<List<com.wordiq.app.data.local.SetProgressSnapshot>> =
        dao.observeCollectionSetProgress(collectionId, now)
    fun observeCollectionSetProgress(collectionId: Long): Flow<List<com.wordiq.app.data.local.SetProgressSnapshot>> =
        currentTimeFlow().flatMapLatest { now -> dao.observeCollectionSetProgress(collectionId, now) }
    fun observeCollection(collectionId: Long): Flow<CollectionWithSets?> = dao.observeCollection(collectionId)
    fun observeConcept(conceptId: Long): Flow<ConceptWithDetails?> = dao.observeConcept(conceptId)
    fun searchConcepts(query: String): Flow<List<ConceptWithDetails>> = dao.searchConcepts(query.trim())

    fun observeProgress(): Flow<ProgressSummary> =
        currentTimeFlow().flatMapLatest { now -> observeProgress(now) }

    fun observeProgress(now: Long, timeZone: TimeZone = TimeZone.getDefault()): Flow<ProgressSummary> {
        val weekStartedAt = startOfLocalWeekMillis(now, timeZone)
        return combine(
            conceptCount,
            dao.observeLearningStates(),
            dao.observeScheduledReviewLogsSince(weekStartedAt),
        ) { total, states, logs ->
            val byConcept = states.groupBy { it.conceptId }
            val strong = byConcept.count { (_, conceptStates) ->
                val meaning = conceptStates.firstOrNull { it.skill == LearningSkill.MEANING_RECOGNITION.name }
                val production = conceptStates.firstOrNull { it.skill == LearningSkill.FINNISH_PRODUCTION.name }
                meaning != null && production != null &&
                    meaning.successfulRecalls > 0 && production.successfulRecalls > 0 &&
                    (meaning.stability + production.stability) / 2.0 >= 14.0
            }
            val active = byConcept.count { (_, conceptStates) -> conceptStates.any { it.lastReview != null } }
            ProgressSummary(
                totalConcepts = total,
                learningConcepts = (active - strong).coerceAtLeast(0),
                strongConcepts = strong,
                recognizedConcepts = byConcept.count { (_, values) -> values.any { it.skill == LearningSkill.MEANING_RECOGNITION.name && it.successfulRecalls > 0 } },
                recalledConcepts = byConcept.count { (_, values) -> values.any { it.skill == LearningSkill.FINNISH_PRODUCTION.name && it.successfulRecalls > 0 } },
                listenedConcepts = byConcept.count { (_, values) -> values.any { it.skill == LearningSkill.LISTENING.name && it.successfulRecalls > 0 } },
                weakConcepts = byConcept.count { (_, values) -> values.any { it.failedRecalls >= 2 || it.lapses >= 2 } },
                practiceDays = localPracticeDayCount(logs.map { it.reviewedAt }, timeZone).coerceAtMost(7),
            )
        }
    }

    private fun currentTimeFlow(): Flow<Long> = flow {
        while (true) {
            emit(System.currentTimeMillis())
            delay(TIME_REFRESH_INTERVAL_MS)
        }
    }

    suspend fun resetLearningProgress() = database.withTransaction {
        dao.clearReviewLogs()
        dao.resetLearningStates()
    }

    suspend fun createCollection(name: String) {
        val cleanName = name.trim()
        if (cleanName.isEmpty()) return
        val nextOrder = dao.getCollections().size
        dao.insertCollection(CollectionEntity(name = cleanName, icon = "book", sortOrder = nextOrder))
    }

    suspend fun renameCollection(collection: CollectionEntity, name: String) {
        val cleanName = name.trim()
        if (cleanName.isNotEmpty()) dao.updateCollection(collection.copy(name = cleanName))
    }

    suspend fun deleteCollection(collection: CollectionEntity) {
        database.withTransaction {
            var nextStandaloneOrder = dao.getSets(null).size
            dao.getSets(collection.id).forEach { set ->
                dao.moveSet(set.id, null, nextStandaloneOrder++)
            }
            dao.deleteCollection(collection)
        }
    }

    suspend fun deleteCollectionForUndo(collection: CollectionEntity): DeletedCollectionSnapshot = database.withTransaction {
        val sets = dao.getSets(collection.id)
        var nextStandaloneOrder = dao.getSets(null).size
        sets.forEach { set -> dao.moveSet(set.id, null, nextStandaloneOrder++) }
        dao.deleteCollection(collection)
        DeletedCollectionSnapshot(collection, sets)
    }

    suspend fun restoreCollection(snapshot: DeletedCollectionSnapshot) = database.withTransaction {
        dao.restoreCollections(listOf(snapshot.collection))
        // Deleting a collection only moves its sets to the independent section.
        // Update those rows in-place so their memberships are never cascaded away.
        snapshot.sets.forEach { set -> dao.updateSet(set) }
    }

    suspend fun createSet(name: String, collectionId: Long?) {
        val cleanName = name.trim()
        if (cleanName.isEmpty()) return
        val nextOrder = dao.getSets(collectionId).size
        dao.insertSet(
            FlashcardSetEntity(
                name = cleanName,
                collectionId = collectionId,
                sortOrder = nextOrder,
                createdAt = System.currentTimeMillis(),
            ),
        )
    }

    suspend fun renameSet(set: FlashcardSetEntity, name: String) {
        val cleanName = name.trim()
        if (cleanName.isNotEmpty()) dao.updateSet(set.copy(name = cleanName))
    }

    suspend fun deleteSet(set: FlashcardSetEntity) = dao.deleteSet(set)

    suspend fun deleteSetForUndo(set: FlashcardSetEntity): DeletedSetSnapshot = database.withTransaction {
        val memberships = dao.getAllMemberships().filter { it.setId == set.id }
        dao.deleteSet(set)
        DeletedSetSnapshot(set, memberships)
    }

    suspend fun restoreSet(snapshot: DeletedSetSnapshot) = database.withTransaction {
        dao.restoreSets(listOf(snapshot.set))
        if (snapshot.memberships.isNotEmpty()) dao.restoreMemberships(snapshot.memberships)
    }

    suspend fun moveSet(set: FlashcardSetEntity, collectionId: Long?) {
        if (set.collectionId == collectionId) return
        dao.moveSet(set.id, collectionId, dao.getSets(collectionId).size)
    }

    suspend fun moveCollection(collection: CollectionEntity, direction: Int) {
        database.withTransaction {
            val ordered = dao.getCollections()
            val index = ordered.indexOfFirst { it.id == collection.id }
            val target = index + direction
            if (index < 0 || target !in ordered.indices) return@withTransaction
            dao.updateCollection(ordered[index].copy(sortOrder = ordered[target].sortOrder))
            dao.updateCollection(ordered[target].copy(sortOrder = ordered[index].sortOrder))
        }
    }

    suspend fun moveSetOrder(set: FlashcardSetEntity, direction: Int) {
        database.withTransaction {
            val ordered = dao.getSets(set.collectionId)
            val index = ordered.indexOfFirst { it.id == set.id }
            val target = index + direction
            if (index < 0 || target !in ordered.indices) return@withTransaction
            dao.updateSet(ordered[index].copy(sortOrder = ordered[target].sortOrder))
            dao.updateSet(ordered[target].copy(sortOrder = ordered[index].sortOrder))
        }
    }

    suspend fun saveWord(draft: WordDraft): Long = database.withTransaction {
        validateWordDraft(draft)?.let { throw IllegalArgumentException(it) }

        val now = System.currentTimeMillis()
        val existing = if (draft.id == 0L) null else dao.getConcept(draft.id)
        val entity = VocabularyConceptEntity(
            id = draft.id,
            finnishLemma = draft.finnish.trim(),
            persianTranslations = draft.persian.trim().ifBlank { null },
            englishTranslations = draft.english.trim().ifBlank { null },
            partOfSpeech = draft.partOfSpeech.trim().ifBlank { null },
            notes = draft.notes.trim().ifBlank { null },
            pronunciationUri = existing?.pronunciationUri,
            spokenFinnish = draft.spokenFinnish.trim().ifBlank { null },
            createdAt = existing?.createdAt ?: now,
            updatedAt = now,
            archived = existing?.archived ?: false,
        )
        val conceptId = if (draft.id == 0L) dao.insertConcept(entity) else {
            dao.updateConcept(entity)
            draft.id
        }

        dao.deleteFormsForConcept(conceptId)
        val forms = draft.forms
            .filter { it.form.isNotBlank() }
            .mapIndexed { index, form ->
                WordFormEntity(
                    conceptId = conceptId,
                    form = form.form.trim(),
                    grammaticalLabel = form.label.trim().ifBlank { null },
                    explanation = form.explanation.trim().ifBlank { null },
                    decomposition = form.decomposition.trim().ifBlank { null },
                    persianMeaning = form.persianMeaning.trim().ifBlank { null },
                    persianNote = form.persianNote.trim().ifBlank { null },
                    sortOrder = index,
                )
            }
        if (forms.isNotEmpty()) dao.insertForms(forms)

        dao.deleteExamplesForConcept(conceptId)
        if (draft.exampleFinnish.isNotBlank()) {
            dao.insertExamples(
                listOf(
                    ExampleSentenceEntity(
                        conceptId = conceptId,
                        finnishSentence = draft.exampleFinnish.trim(),
                        persianTranslation = draft.examplePersian.trim().ifBlank { null },
                        englishTranslation = draft.exampleEnglish.trim().ifBlank { null },
                    ),
                ),
            )
        }

        dao.deleteMembershipsForConcept(conceptId)
        draft.setIds.forEach { setId ->
            dao.insertMembership(SetMembershipEntity(conceptId, setId, now))
        }
        dao.insertLearningStates(
            LearningSkill.entries.map { LearningStateEntity(conceptId = conceptId, skill = it.name) },
        )
        conceptId
    }

    suspend fun deleteWord(concept: VocabularyConceptEntity) = dao.deleteConcept(concept)

    suspend fun deleteWordForUndo(details: ConceptWithDetails): DeletedWordSnapshot = database.withTransaction {
        val conceptId = details.concept.id
        val snapshot = DeletedWordSnapshot(
            details = details,
            memberships = dao.getAllMemberships().filter { it.conceptId == conceptId },
            reviewLogs = dao.getReviewLogsForConcept(conceptId),
        )
        dao.deleteConcept(details.concept)
        snapshot
    }

    suspend fun restoreWord(snapshot: DeletedWordSnapshot) = database.withTransaction {
        dao.restoreConcepts(listOf(snapshot.details.concept))
        if (snapshot.details.forms.isNotEmpty()) dao.restoreForms(snapshot.details.forms)
        if (snapshot.details.examples.isNotEmpty()) dao.restoreExamples(snapshot.details.examples)
        if (snapshot.memberships.isNotEmpty()) dao.restoreMemberships(snapshot.memberships)
        if (snapshot.details.learningStates.isNotEmpty()) dao.restoreLearningStates(snapshot.details.learningStates)
        if (snapshot.reviewLogs.isNotEmpty()) dao.restoreReviewLogs(snapshot.reviewLogs)
    }

    suspend fun seedIfEmpty() {
        if (dao.conceptCount() > 0 || dao.setCount() > 0) return
        database.withTransaction {
            if (dao.conceptCount() > 0 || dao.setCount() > 0) return@withTransaction
            val now = System.currentTimeMillis()
            val collectionId = dao.insertCollection(
                CollectionEntity(name = "Suomen Mestari 3", icon = "book", sortOrder = 0),
            )
            val chapter1 = dao.insertSet(FlashcardSetEntity(name = "Chapter 1", collectionId = collectionId, sortOrder = 0, createdAt = now))
            val chapter2 = dao.insertSet(FlashcardSetEntity(name = "Chapter 2", collectionId = collectionId, sortOrder = 1, createdAt = now))
            dao.insertSet(FlashcardSetEntity(name = "Chapter 3", collectionId = collectionId, sortOrder = 2, createdAt = now))
            val work = dao.insertSet(FlashcardSetEntity(name = "Work Finnish", sortOrder = 0, createdAt = now))

            seedWord(
                "kauppa", "فروشگاه، مغازه", "store, shop", "Menen kauppaan.", "kauppaan",
                listOf(
                    SeedForm("kaupan", "genitive", "of the store", "فروشگاهِ", "kauppa + n"),
                    SeedForm("kauppaa", "partitive", "store (partitive)", "فروشگاه را / از فروشگاه", "kauppa + a"),
                    SeedForm("kaupassa", "inessive", "in the store", "در فروشگاه", "kauppa + ssa", "-ssa / -ssä ≈ در"),
                    SeedForm("kaupasta", "elative", "from the store", "از فروشگاه", "kauppa + sta", "-sta / -stä ≈ از"),
                    SeedForm("kauppaan", "illative", "into the store", "به داخل فروشگاه", "kauppa + an", "-Vn ≈ به داخل"),
                    SeedForm("kaupat", "plural", "the stores", "فروشگاه‌ها", "kauppa + t"),
                    SeedForm("kauppoja", "plural partitive", "stores", "فروشگاه‌هایی", "kauppa + oja"),
                ),
                setOf(chapter1), now,
            )
            seedWord(
                "ystävä", "دوست", "friend", "Hän on minun ystäväni.", "ystäväni",
                listOf(
                    SeedForm("ystävän", "genitive", "friend's", "دوستِ", "ystävä + n"),
                    SeedForm("ystävälle", "allative", "to the friend", "به دوست", "ystävä + lle", "-lle ≈ به"),
                    SeedForm("ystäväni", "possessive", "my friend", "دوست من", "ystävä + ni", "-ni ≈ من"),
                ),
                setOf(chapter1), now,
            )
            seedWord("ymmärtää", "فهمیدن", "to understand", "Ymmärrän hyvin.", "ymmärrän", listOf(
                SeedForm("ymmärrän", "present, I", "I understand", "می‌فهمم", "ymmärtää → ymmärrä + n"),
                SeedForm("ymmärsin", "past, I", "I understood", "فهمیدم", "ymmärtää → ymmärsi + n"),
            ), setOf(chapter2), now)
            seedWord("vaikuttaa", "تأثیر گذاشتن", "to affect; seem", "Se vaikuttaa hyvältä.", "vaikuttaa", listOf(
                SeedForm("vaikutan", "present, I", "I affect", "تأثیر می‌گذارم", "vaikutta + n"),
                SeedForm("vaikutti", "past", "affected", "تأثیر گذاشت", "vaikutta → vaikutti"),
            ), setOf(chapter2, work), now)
            seedWord("kokous", "جلسه", "meeting", "Kokous alkaa pian.", "kokous", listOf(
                SeedForm("kokouksen", "genitive", "of the meeting", "جلسهٔ", "kokous → kokoukse + n"),
                SeedForm("kokouksessa", "inessive", "in the meeting", "در جلسه", "kokous → kokoukse + ssa", "-ssa / -ssä ≈ در"),
            ), setOf(work), now)
            seedWord("minä olen", "من هستم", "I am", "Minä olen täällä.", "minä olen", emptyList(), setOf(chapter1), now, spoken = "mä oon")
            seedWord("tuli", "آتش", "fire", "Tuli on lämmin.", "tuli", emptyList(), setOf(chapter1), now)
            seedWord("tuuli", "باد", "wind", "Tuuli on kylmä.", "tuuli", emptyList(), setOf(chapter1), now)
        }
    }

    private suspend fun seedWord(
        finnish: String,
        persian: String,
        english: String,
        example: String,
        exampleTarget: String,
        forms: List<SeedForm>,
        setIds: Set<Long>,
        now: Long,
        spoken: String? = null,
    ) {
        val id = dao.insertConcept(
            VocabularyConceptEntity(
                finnishLemma = finnish,
                persianTranslations = persian,
                englishTranslations = english,
                spokenFinnish = spoken,
                createdAt = now,
                updatedAt = now,
            ),
        )
        dao.insertExamples(
            listOf(
                ExampleSentenceEntity(
                    conceptId = id,
                    finnishSentence = example,
                    targetWordOrForm = exampleTarget,
                ),
            ),
        )
        dao.insertForms(
            forms.mapIndexed { index, form ->
                WordFormEntity(
                    conceptId = id,
                    form = form.surface,
                    grammaticalLabel = form.label,
                    explanation = form.englishMeaning,
                    decomposition = form.decomposition,
                    persianMeaning = form.persianMeaning,
                    persianNote = form.persianNote,
                    sortOrder = index,
                )
            },
        )
        setIds.forEach { dao.insertMembership(SetMembershipEntity(id, it, now)) }
        dao.insertLearningStates(LearningSkill.entries.map { LearningStateEntity(conceptId = id, skill = it.name) })
    }

    private data class SeedForm(
        val surface: String,
        val label: String,
        val englishMeaning: String,
        val persianMeaning: String,
        val decomposition: String,
        val persianNote: String? = null,
    )
}
