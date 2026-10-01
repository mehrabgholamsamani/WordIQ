package com.wordiq.app.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface WordIqDao {
    @Transaction
    @Query("SELECT * FROM collections ORDER BY sortOrder, name COLLATE NOCASE")
    fun observeCollections(): Flow<List<CollectionWithSets>>

    @Query("SELECT * FROM flashcard_sets WHERE collectionId IS NULL ORDER BY sortOrder, name COLLATE NOCASE")
    fun observeStandaloneSets(): Flow<List<FlashcardSetEntity>>

    @Query("SELECT * FROM flashcard_sets ORDER BY collectionId, sortOrder, name COLLATE NOCASE")
    fun observeAllSets(): Flow<List<FlashcardSetEntity>>

    @Transaction
    @Query("SELECT * FROM flashcard_sets WHERE id = :setId")
    fun observeSet(setId: Long): Flow<SetWithWords?>

    @Query(
        """
        SELECT COALESCE(AVG(CASE WHEN learning_states.stability >= 30.0 THEN 1.0 ELSE learning_states.stability / 30.0 END), 0.0)
        FROM learning_states
        INNER JOIN set_memberships ON set_memberships.conceptId = learning_states.conceptId
        WHERE set_memberships.setId = :setId
          AND learning_states.skill IN ('MEANING_RECOGNITION', 'FINNISH_PRODUCTION')
        """,
    )
    fun observeSetMastery(setId: Long): Flow<Double>

    @Query(
        """
        SELECT flashcard_sets.id AS setId,
               COUNT(DISTINCT set_memberships.conceptId) AS wordCount,
               COUNT(DISTINCT CASE WHEN learning_states.lastReview IS NOT NULL THEN set_memberships.conceptId END) AS reviewedCount,
               COUNT(DISTINCT CASE WHEN learning_states.skill IN ('MEANING_RECOGNITION', 'FINNISH_PRODUCTION') AND (learning_states.nextReview IS NULL OR learning_states.nextReview <= :now) THEN set_memberships.conceptId END) AS readyCount,
               COALESCE(AVG(CASE
                   WHEN learning_states.skill IN ('MEANING_RECOGNITION', 'FINNISH_PRODUCTION')
                   THEN MIN(learning_states.stability / 30.0, 1.0)
               END), 0.0) AS mastery
        FROM flashcard_sets
        LEFT JOIN set_memberships ON set_memberships.setId = flashcard_sets.id
        LEFT JOIN learning_states ON learning_states.conceptId = set_memberships.conceptId
        WHERE flashcard_sets.id = :setId
        GROUP BY flashcard_sets.id
        """,
    )
    fun observeSetProgress(setId: Long, now: Long): Flow<SetProgressSnapshot?>

    @Query(
        """
        SELECT flashcard_sets.id AS setId,
               COUNT(DISTINCT set_memberships.conceptId) AS wordCount,
               COUNT(DISTINCT CASE WHEN learning_states.lastReview IS NOT NULL THEN set_memberships.conceptId END) AS reviewedCount,
               COUNT(DISTINCT CASE WHEN learning_states.skill IN ('MEANING_RECOGNITION', 'FINNISH_PRODUCTION') AND (learning_states.nextReview IS NULL OR learning_states.nextReview <= :now) THEN set_memberships.conceptId END) AS readyCount,
               COALESCE(AVG(CASE
                   WHEN learning_states.skill IN ('MEANING_RECOGNITION', 'FINNISH_PRODUCTION')
                   THEN MIN(learning_states.stability / 30.0, 1.0)
               END), 0.0) AS mastery
        FROM flashcard_sets
        LEFT JOIN set_memberships ON set_memberships.setId = flashcard_sets.id
        LEFT JOIN learning_states ON learning_states.conceptId = set_memberships.conceptId
        WHERE flashcard_sets.collectionId = :collectionId
        GROUP BY flashcard_sets.id
        ORDER BY flashcard_sets.sortOrder, flashcard_sets.name COLLATE NOCASE
        """,
    )
    fun observeCollectionSetProgress(collectionId: Long, now: Long): Flow<List<SetProgressSnapshot>>

    @Query(
        """
        SELECT flashcard_sets.id AS setId,
               COUNT(DISTINCT set_memberships.conceptId) AS wordCount,
               COUNT(DISTINCT CASE WHEN learning_states.lastReview IS NOT NULL THEN set_memberships.conceptId END) AS reviewedCount,
               COUNT(DISTINCT CASE WHEN learning_states.skill IN ('MEANING_RECOGNITION', 'FINNISH_PRODUCTION') AND (learning_states.nextReview IS NULL OR learning_states.nextReview <= :now) THEN set_memberships.conceptId END) AS readyCount,
               COALESCE(AVG(CASE
                   WHEN learning_states.skill IN ('MEANING_RECOGNITION', 'FINNISH_PRODUCTION')
                   THEN MIN(learning_states.stability / 30.0, 1.0)
               END), 0.0) AS mastery
        FROM flashcard_sets
        LEFT JOIN set_memberships ON set_memberships.setId = flashcard_sets.id
        LEFT JOIN learning_states ON learning_states.conceptId = set_memberships.conceptId
        GROUP BY flashcard_sets.id
        ORDER BY flashcard_sets.collectionId, flashcard_sets.sortOrder, flashcard_sets.name COLLATE NOCASE
        """,
    )
    fun observeAllSetProgress(now: Long): Flow<List<SetProgressSnapshot>>

    @Transaction
    @Query("SELECT * FROM vocabulary_concepts WHERE id = :conceptId")
    fun observeConcept(conceptId: Long): Flow<ConceptWithDetails?>

    @Transaction
    @Query(
        """
        SELECT DISTINCT vocabulary_concepts.* FROM vocabulary_concepts
        LEFT JOIN word_forms ON word_forms.conceptId = vocabulary_concepts.id
        WHERE vocabulary_concepts.archived = 0 AND (
            vocabulary_concepts.finnishLemma LIKE '%' || :query || '%' COLLATE NOCASE OR
            COALESCE(vocabulary_concepts.persianTranslations, '') LIKE '%' || :query || '%' OR
            COALESCE(vocabulary_concepts.englishTranslations, '') LIKE '%' || :query || '%' COLLATE NOCASE OR
            COALESCE(word_forms.form, '') LIKE '%' || :query || '%' COLLATE NOCASE
        )
        ORDER BY vocabulary_concepts.finnishLemma COLLATE NOCASE
        LIMIT 100
        """,
    )
    fun searchConcepts(query: String): Flow<List<ConceptWithDetails>>

    @Transaction
    @Query("SELECT * FROM collections WHERE id = :collectionId")
    fun observeCollection(collectionId: Long): Flow<CollectionWithSets?>

    @Query("SELECT * FROM vocabulary_concepts WHERE id = :conceptId")
    suspend fun getConcept(conceptId: Long): VocabularyConceptEntity?

    @Transaction
    @Query("SELECT * FROM vocabulary_concepts WHERE archived = 0 ORDER BY updatedAt DESC")
    suspend fun getAllPracticeConcepts(): List<ConceptWithDetails>

    @Transaction
    @Query(
        """
        SELECT DISTINCT vocabulary_concepts.* FROM vocabulary_concepts
        INNER JOIN set_memberships ON set_memberships.conceptId = vocabulary_concepts.id
        WHERE set_memberships.setId = :setId AND vocabulary_concepts.archived = 0
        ORDER BY vocabulary_concepts.updatedAt DESC
        """,
    )
    suspend fun getPracticeConceptsForSet(setId: Long): List<ConceptWithDetails>

    @Transaction
    @Query(
        """
        SELECT DISTINCT vocabulary_concepts.* FROM vocabulary_concepts
        INNER JOIN set_memberships ON set_memberships.conceptId = vocabulary_concepts.id
        INNER JOIN flashcard_sets ON flashcard_sets.id = set_memberships.setId
        WHERE flashcard_sets.collectionId = :collectionId AND vocabulary_concepts.archived = 0
        ORDER BY vocabulary_concepts.updatedAt DESC
        """,
    )
    suspend fun getPracticeConceptsForCollection(collectionId: Long): List<ConceptWithDetails>

    @Query("SELECT COUNT(*) FROM vocabulary_concepts WHERE archived = 0")
    fun observeConceptCount(): Flow<Int>

    @Query("SELECT * FROM learning_states")
    fun observeLearningStates(): Flow<List<LearningStateEntity>>

    @Query("SELECT * FROM review_logs WHERE scheduled = 1 AND reviewedAt >= :since ORDER BY reviewedAt DESC")
    fun observeScheduledReviewLogsSince(since: Long): Flow<List<ReviewLogEntity>>

    @Query("SELECT COUNT(*) FROM vocabulary_concepts")
    suspend fun conceptCount(): Int

    @Query("SELECT COUNT(*) FROM flashcard_sets")
    suspend fun setCount(): Int

    @Query("SELECT * FROM collections ORDER BY sortOrder, id")
    suspend fun getCollections(): List<CollectionEntity>

    @Query("SELECT * FROM flashcard_sets WHERE collectionId IS :collectionId ORDER BY sortOrder, id")
    suspend fun getSets(collectionId: Long?): List<FlashcardSetEntity>

    @Query("SELECT * FROM flashcard_sets ORDER BY collectionId, sortOrder, id")
    suspend fun getAllSets(): List<FlashcardSetEntity>

    @Query("SELECT * FROM vocabulary_concepts ORDER BY id")
    suspend fun getAllConcepts(): List<VocabularyConceptEntity>

    @Query("SELECT * FROM word_forms ORDER BY conceptId, sortOrder, id")
    suspend fun getAllForms(): List<WordFormEntity>

    @Query("SELECT * FROM example_sentences ORDER BY conceptId, id")
    suspend fun getAllExamples(): List<ExampleSentenceEntity>

    @Query("SELECT * FROM set_memberships ORDER BY setId, conceptId")
    suspend fun getAllMemberships(): List<SetMembershipEntity>

    @Query("SELECT * FROM learning_states ORDER BY conceptId, skill")
    suspend fun getAllLearningStates(): List<LearningStateEntity>

    @Query("SELECT * FROM review_logs ORDER BY id")
    suspend fun getAllReviewLogs(): List<ReviewLogEntity>

    @Insert
    suspend fun insertCollection(collection: CollectionEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun restoreCollections(collections: List<CollectionEntity>)

    @Update
    suspend fun updateCollection(collection: CollectionEntity)

    @Delete
    suspend fun deleteCollection(collection: CollectionEntity)

    @Insert
    suspend fun insertSet(set: FlashcardSetEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun restoreSets(sets: List<FlashcardSetEntity>)

    @Update
    suspend fun updateSet(set: FlashcardSetEntity)

    @Delete
    suspend fun deleteSet(set: FlashcardSetEntity)

    @Query("UPDATE flashcard_sets SET collectionId = :collectionId, sortOrder = :sortOrder WHERE id = :setId")
    suspend fun moveSet(setId: Long, collectionId: Long?, sortOrder: Int)

    @Insert
    suspend fun insertConcept(concept: VocabularyConceptEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun restoreConcepts(concepts: List<VocabularyConceptEntity>)

    @Update
    suspend fun updateConcept(concept: VocabularyConceptEntity)

    @Delete
    suspend fun deleteConcept(concept: VocabularyConceptEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertMembership(membership: SetMembershipEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun restoreMemberships(memberships: List<SetMembershipEntity>)

    @Query("DELETE FROM set_memberships WHERE conceptId = :conceptId")
    suspend fun deleteMembershipsForConcept(conceptId: Long)

    @Insert
    suspend fun insertForms(forms: List<WordFormEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun restoreForms(forms: List<WordFormEntity>)

    @Query("DELETE FROM word_forms WHERE conceptId = :conceptId")
    suspend fun deleteFormsForConcept(conceptId: Long)

    @Insert
    suspend fun insertExamples(examples: List<ExampleSentenceEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun restoreExamples(examples: List<ExampleSentenceEntity>)

    @Query("DELETE FROM example_sentences WHERE conceptId = :conceptId")
    suspend fun deleteExamplesForConcept(conceptId: Long)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertLearningStates(states: List<LearningStateEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun restoreLearningStates(states: List<LearningStateEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertLearningState(state: LearningStateEntity)

    @Insert
    suspend fun insertReviewLog(log: ReviewLogEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun restoreReviewLogs(logs: List<ReviewLogEntity>)

    @Query("DELETE FROM review_logs")
    suspend fun clearReviewLogs()

    @Query(
        """
        UPDATE learning_states SET
            lastReview = NULL, nextReview = NULL, stability = 0.0, difficulty = 0.0,
            lapses = 0, successfulRecalls = 0, failedRecalls = 0,
            recentResponseLatencyMs = NULL, recentHintUsage = 0
        """,
    )
    suspend fun resetLearningStates()

    @Query("DELETE FROM set_memberships")
    suspend fun clearMemberships()

    @Query("DELETE FROM example_sentences")
    suspend fun clearExamples()

    @Query("DELETE FROM word_forms")
    suspend fun clearForms()

    @Query("DELETE FROM learning_states")
    suspend fun clearLearningStates()

    @Query("DELETE FROM vocabulary_concepts")
    suspend fun clearConcepts()

    @Query("DELETE FROM flashcard_sets")
    suspend fun clearSets()

    @Query("DELETE FROM collections")
    suspend fun clearCollections()

    @Query("SELECT COUNT(*) FROM review_logs WHERE conceptId = :conceptId AND scheduled = :scheduled")
    suspend fun reviewLogCount(conceptId: Long, scheduled: Boolean): Int

    @Query("SELECT * FROM review_logs WHERE conceptId = :conceptId ORDER BY id DESC LIMIT 1")
    suspend fun latestReviewLog(conceptId: Long): ReviewLogEntity?

    @Query("SELECT * FROM review_logs WHERE conceptId = :conceptId ORDER BY id")
    suspend fun getReviewLogsForConcept(conceptId: Long): List<ReviewLogEntity>

    @Query("SELECT * FROM review_logs WHERE conceptId IN (:conceptIds) ORDER BY reviewedAt DESC, id DESC")
    suspend fun recentReviewLogs(conceptIds: List<Long>): List<ReviewLogEntity>

    @Query("SELECT * FROM learning_states WHERE conceptId = :conceptId AND skill = :skill")
    suspend fun getLearningState(conceptId: Long, skill: String): LearningStateEntity?

    @Query("SELECT AVG(responseLatencyMs) FROM review_logs WHERE scheduled = 1 AND outcome IN ('EXACT', 'TYPO')")
    suspend fun averageSuccessfulLatency(): Double?

    @Query("SELECT COUNT(*) FROM set_memberships WHERE conceptId = :conceptId AND setId = :setId")
    suspend fun membershipCount(conceptId: Long, setId: Long): Int

    @Query("SELECT COUNT(*) FROM learning_states WHERE conceptId = :conceptId")
    suspend fun learningStateCount(conceptId: Long): Int
}
