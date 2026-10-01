package com.wordiq.app.data.local

import androidx.room.Embedded
import androidx.room.Junction
import androidx.room.Relation

data class CollectionWithSets(
    @Embedded val collection: CollectionEntity,
    @Relation(parentColumn = "id", entityColumn = "collectionId")
    val sets: List<FlashcardSetEntity>,
)

data class SetWithWords(
    @Embedded val set: FlashcardSetEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "id",
        associateBy = Junction(
            value = SetMembershipEntity::class,
            parentColumn = "setId",
            entityColumn = "conceptId",
        ),
    )
    val words: List<VocabularyConceptEntity>,
)

data class ConceptWithDetails(
    @Embedded val concept: VocabularyConceptEntity,
    @Relation(parentColumn = "id", entityColumn = "conceptId")
    val forms: List<WordFormEntity>,
    @Relation(parentColumn = "id", entityColumn = "conceptId")
    val examples: List<ExampleSentenceEntity>,
    @Relation(
        parentColumn = "id",
        entityColumn = "id",
        associateBy = Junction(
            value = SetMembershipEntity::class,
            parentColumn = "conceptId",
            entityColumn = "setId",
        ),
    )
    val sets: List<FlashcardSetEntity>,
    @Relation(parentColumn = "id", entityColumn = "conceptId")
    val learningStates: List<LearningStateEntity>,
)

data class SetProgressSnapshot(
    val setId: Long,
    val wordCount: Int,
    val reviewedCount: Int,
    val readyCount: Int,
    val mastery: Double,
)
