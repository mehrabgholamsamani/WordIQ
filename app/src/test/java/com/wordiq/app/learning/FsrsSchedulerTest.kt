package com.wordiq.app.learning

import com.wordiq.app.data.local.LearningSkill
import com.wordiq.app.data.local.LearningStateEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FsrsSchedulerTest {
    private val scheduler = FsrsStyleScheduler()
    private val mapper = BehaviorRatingMapper()
    private val now = 1_800_000_000_000L

    @Test
    fun newGoodReviewCreatesMultiDaySchedule() {
        val result = scheduler.schedule(newState(), InternalRating.GOOD, now)
        assertEquals(now, result.lastReview)
        assertTrue(result.nextReview!! >= now + DAY_MS)
        assertEquals(1, result.successfulRecalls)
        assertEquals(0, result.failedRecalls)
        assertTrue(result.stability > 0.0)
        assertTrue(result.difficulty in 1.0..10.0)
    }

    @Test
    fun failedReviewReturnsSoonAndIncrementsLapse() {
        val result = scheduler.schedule(newState(), InternalRating.AGAIN, now)
        assertEquals(now + 10 * 60 * 1_000L, result.nextReview)
        assertEquals(1, result.lapses)
        assertEquals(1, result.failedRecalls)
    }

    @Test
    fun successfulLaterRecallGrowsStabilityDeterministically() {
        val first = scheduler.schedule(newState(), InternalRating.GOOD, now)
        val dueAt = requireNotNull(first.nextReview)
        val later = scheduler.schedule(first, InternalRating.GOOD, dueAt)
        val repeated = scheduler.schedule(first, InternalRating.GOOD, dueAt)
        assertTrue(later.stability > first.stability)
        assertEquals(later, repeated)
    }

    @Test
    fun behaviorMapsWithoutUserDifficultyButtons() {
        val practiced = newState().copy(successfulRecalls = 3)
        assertEquals(
            InternalRating.EASY,
            mapper.rating(ReviewObservation(AnswerOutcome.EXACT, 2_000, 0, false, ExerciseType.MEANING_RECOGNITION), practiced),
        )
        assertEquals(
            InternalRating.GOOD,
            mapper.rating(ReviewObservation(AnswerOutcome.TYPO, 5_000, 0, false, ExerciseType.FINNISH_PRODUCTION), practiced),
        )
        assertEquals(
            InternalRating.HARD,
            mapper.rating(ReviewObservation(AnswerOutcome.EXACT, 5_000, 2, false, ExerciseType.FINNISH_PRODUCTION), practiced),
        )
        assertEquals(
            InternalRating.AGAIN,
            mapper.rating(ReviewObservation(AnswerOutcome.REVEALED, 5_000, 3, true, ExerciseType.FREE_RECALL), practiced),
        )
    }

    @Test
    fun repeatedAudioReplaysLowerListeningConfidence() {
        val previous = LearningStateEntity(
            conceptId = 1,
            skill = LearningSkill.LISTENING.name,
            lastReview = 1_000,
            successfulRecalls = 4,
        )
        val rating = mapper.rating(
            ReviewObservation(
                outcome = AnswerOutcome.EXACT,
                responseLatencyMs = 2_000,
                hintsUsed = 0,
                revealed = false,
                exerciseType = ExerciseType.LISTEN_TYPE,
                audioReplayCount = 2,
            ),
            previous,
        )
        assertEquals(InternalRating.HARD, rating)
    }

    private fun newState() = LearningStateEntity(1, LearningSkill.MEANING_RECOGNITION.name)

    companion object { private const val DAY_MS = 86_400_000L }
}
