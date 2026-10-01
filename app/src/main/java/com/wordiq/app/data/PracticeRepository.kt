package com.wordiq.app.data

import androidx.room.withTransaction
import com.wordiq.app.data.local.LearningStateEntity
import com.wordiq.app.data.local.LearningSkill
import com.wordiq.app.data.local.ReviewLogEntity
import com.wordiq.app.data.local.WordIqDatabase
import com.wordiq.app.learning.AnswerOutcome
import com.wordiq.app.learning.BehaviorRatingMapper
import com.wordiq.app.learning.FsrsStyleScheduler
import com.wordiq.app.learning.InternalRating
import com.wordiq.app.learning.PracticeItem
import com.wordiq.app.learning.PracticePlan
import com.wordiq.app.learning.PracticeQueueBuilder
import com.wordiq.app.learning.PracticeRequest
import com.wordiq.app.learning.PracticeScopeType
import com.wordiq.app.learning.ReviewObservation
import com.wordiq.app.learning.ReviewScheduler
import com.wordiq.app.learning.ExerciseType
import com.wordiq.app.learning.isListening

data class RecordedReview(
    val rating: InternalRating,
    val updatedState: LearningStateEntity?,
)

class PracticeRepository(
    private val database: WordIqDatabase,
    private val queueBuilder: PracticeQueueBuilder = PracticeQueueBuilder(),
    private val scheduler: ReviewScheduler = FsrsStyleScheduler(),
    private val ratingMapper: BehaviorRatingMapper = BehaviorRatingMapper(),
) {
    private val dao = database.wordIqDao()

    suspend fun buildPlan(request: PracticeRequest, now: Long = System.currentTimeMillis()): PracticePlan {
        val scoped = when (request.scopeType) {
            PracticeScopeType.GLOBAL -> dao.getAllPracticeConcepts()
            PracticeScopeType.SET -> dao.getPracticeConceptsForSet(requireNotNull(request.scopeId))
            PracticeScopeType.COLLECTION -> dao.getPracticeConceptsForCollection(requireNotNull(request.scopeId))
        }
        val all = dao.getAllPracticeConcepts()
        val recentExercises = if (scoped.isEmpty()) {
            emptyMap()
        } else {
            dao.recentReviewLogs(scoped.map { it.concept.id })
                .distinctBy { it.conceptId }
                .mapNotNull { log ->
                    runCatching { ExerciseType.valueOf(log.exerciseType) }.getOrNull()?.let { log.conceptId to it }
                }
                .toMap()
        }
        val averageLatency = dao.averageSuccessfulLatency()?.toLong() ?: 12_000L
        return queueBuilder.build(scoped, all, request, averageLatency, now, recentExercises)
    }

    suspend fun record(
        item: PracticeItem,
        outcome: AnswerOutcome,
        latencyMs: Long,
        hintsUsed: Int,
        revealed: Boolean,
        audioReplayCount: Int = 0,
        lemmaRemembered: Boolean = false,
        now: Long = System.currentTimeMillis(),
    ): RecordedReview = database.withTransaction {
        val current = dao.getLearningState(item.concept.concept.id, item.skill.name)
            ?: LearningStateEntity(conceptId = item.concept.concept.id, skill = item.skill.name)
        val observation = ReviewObservation(outcome, latencyMs, hintsUsed, revealed, item.exerciseType, audioReplayCount)
        val rating = if (lemmaRemembered && item.expectedLemma != null) {
            InternalRating.AGAIN
        } else {
            ratingMapper.rating(observation, current)
        }
        val updated = if (item.scheduled) {
            scheduler.schedule(current, rating, now).copy(
                recentResponseLatencyMs = latencyMs,
                recentHintUsage = hintsUsed + if (item.exerciseType.isListening) audioReplayCount else 0,
            ).also { dao.upsertLearningState(it) }
        } else {
            null
        }
        if (item.scheduled && lemmaRemembered && item.expectedLemma != null) {
            val lexicalSkill = LearningSkill.FINNISH_PRODUCTION.name
            val lexical = dao.getLearningState(item.concept.concept.id, lexicalSkill)
                ?: LearningStateEntity(conceptId = item.concept.concept.id, skill = lexicalSkill)
            scheduler.schedule(lexical, InternalRating.GOOD, now).copy(
                recentResponseLatencyMs = latencyMs,
                recentHintUsage = hintsUsed,
            ).also { dao.upsertLearningState(it) }
        }
        dao.insertReviewLog(
            ReviewLogEntity(
                conceptId = item.concept.concept.id,
                skill = item.skill.name,
                exerciseType = item.exerciseType.name,
                outcome = outcome.name,
                internalRating = rating.name,
                responseLatencyMs = latencyMs,
                hintsUsed = hintsUsed,
                audioReplayCount = audioReplayCount,
                lemmaRemembered = lemmaRemembered,
                revealed = revealed,
                scheduled = item.scheduled,
                reviewedAt = now,
            ),
        )
        RecordedReview(rating, updated)
    }
}
