package com.wordiq.app.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(tableName = "collections")
data class CollectionEntity(
    @androidx.room.PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val icon: String? = null,
    val sortOrder: Int,
)

@Entity(
    tableName = "flashcard_sets",
    foreignKeys = [
        ForeignKey(
            entity = CollectionEntity::class,
            parentColumns = ["id"],
            childColumns = ["collectionId"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [Index("collectionId")],
)
data class FlashcardSetEntity(
    @androidx.room.PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val collectionId: Long? = null,
    val sortOrder: Int,
    val createdAt: Long,
)

@Entity(tableName = "vocabulary_concepts", indices = [Index("finnishLemma")])
data class VocabularyConceptEntity(
    @androidx.room.PrimaryKey(autoGenerate = true) val id: Long = 0,
    val finnishLemma: String,
    val persianTranslations: String? = null,
    val englishTranslations: String? = null,
    val partOfSpeech: String? = null,
    val notes: String? = null,
    val pronunciationUri: String? = null,
    val spokenFinnish: String? = null,
    val createdAt: Long,
    val updatedAt: Long,
    val archived: Boolean = false,
)

@Entity(
    tableName = "word_forms",
    foreignKeys = [
        ForeignKey(
            entity = VocabularyConceptEntity::class,
            parentColumns = ["id"],
            childColumns = ["conceptId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("conceptId"), Index("form")],
)
data class WordFormEntity(
    @androidx.room.PrimaryKey(autoGenerate = true) val id: Long = 0,
    val conceptId: Long,
    val form: String,
    val grammaticalLabel: String? = null,
    val explanation: String? = null,
    val decomposition: String? = null,
    val persianMeaning: String? = null,
    val persianNote: String? = null,
    val sortOrder: Int,
)

@Entity(
    tableName = "example_sentences",
    foreignKeys = [
        ForeignKey(
            entity = VocabularyConceptEntity::class,
            parentColumns = ["id"],
            childColumns = ["conceptId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("conceptId")],
)
data class ExampleSentenceEntity(
    @androidx.room.PrimaryKey(autoGenerate = true) val id: Long = 0,
    val conceptId: Long,
    val finnishSentence: String,
    val persianTranslation: String? = null,
    val englishTranslation: String? = null,
    val targetWordOrForm: String? = null,
    val source: String? = null,
)

@Entity(
    tableName = "set_memberships",
    primaryKeys = ["conceptId", "setId"],
    foreignKeys = [
        ForeignKey(
            entity = VocabularyConceptEntity::class,
            parentColumns = ["id"],
            childColumns = ["conceptId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = FlashcardSetEntity::class,
            parentColumns = ["id"],
            childColumns = ["setId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("setId")],
)
data class SetMembershipEntity(
    val conceptId: Long,
    val setId: Long,
    val addedAt: Long,
)

enum class LearningSkill {
    MEANING_RECOGNITION,
    FINNISH_PRODUCTION,
    SPELLING,
    LISTENING,
    MORPHOLOGY,
    CONTEXT,
    SPOKEN_RECOGNITION,
}

@Entity(
    tableName = "learning_states",
    primaryKeys = ["conceptId", "skill"],
    foreignKeys = [
        ForeignKey(
            entity = VocabularyConceptEntity::class,
            parentColumns = ["id"],
            childColumns = ["conceptId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("conceptId"), Index("nextReview")],
)
data class LearningStateEntity(
    val conceptId: Long,
    val skill: String,
    val lastReview: Long? = null,
    val nextReview: Long? = null,
    val stability: Double = 0.0,
    val difficulty: Double = 0.0,
    val lapses: Int = 0,
    val successfulRecalls: Int = 0,
    val failedRecalls: Int = 0,
    val recentResponseLatencyMs: Long? = null,
    val recentHintUsage: Int = 0,
)

@Entity(
    tableName = "review_logs",
    foreignKeys = [
        ForeignKey(
            entity = VocabularyConceptEntity::class,
            parentColumns = ["id"],
            childColumns = ["conceptId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("conceptId"), Index("reviewedAt"), Index("scheduled")],
)
data class ReviewLogEntity(
    @androidx.room.PrimaryKey(autoGenerate = true) val id: Long = 0,
    val conceptId: Long,
    val skill: String,
    val exerciseType: String,
    val outcome: String,
    val internalRating: String,
    val responseLatencyMs: Long,
    val hintsUsed: Int,
    val audioReplayCount: Int = 0,
    val lemmaRemembered: Boolean = false,
    val revealed: Boolean,
    val scheduled: Boolean,
    val reviewedAt: Long,
)
