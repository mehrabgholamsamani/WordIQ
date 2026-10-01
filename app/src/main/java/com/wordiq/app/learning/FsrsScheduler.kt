package com.wordiq.app.learning

import com.wordiq.app.data.local.LearningStateEntity
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.roundToLong

enum class InternalRating(val value: Int) { AGAIN(1), HARD(2), GOOD(3), EASY(4) }

enum class ExerciseType {
    INTRODUCTION,
    MEANING_RECOGNITION,
    FINNISH_PRODUCTION,
    REVERSE_RECOGNITION,
    FREE_RECALL,
    SPELLING_RECALL,
    LISTEN_MEANING,
    LISTEN_TYPE,
    LISTEN_CHOICE,
    CLOZE_CHOICE,
    CLOZE_TYPE,
    RESCUE,
    FORM_RECOGNITION_LEMMA,
    FORM_RECOGNITION_MEANING,
    FORM_PRODUCTION,
    SPOKEN_RECOGNITION,
    MINIMAL_PAIR_LISTENING,
}

data class ReviewObservation(
    val outcome: AnswerOutcome,
    val responseLatencyMs: Long,
    val hintsUsed: Int,
    val revealed: Boolean,
    val exerciseType: ExerciseType,
    val audioReplayCount: Int = 0,
)

class BehaviorRatingMapper {
    fun rating(observation: ReviewObservation, previous: LearningStateEntity): InternalRating {
        if (observation.revealed || observation.outcome == AnswerOutcome.REVEALED || observation.outcome == AnswerOutcome.WRONG) {
            return InternalRating.AGAIN
        }
        if (observation.outcome == AnswerOutcome.PARTIAL || observation.hintsUsed >= 2) return InternalRating.HARD
        if (observation.exerciseType.isListening && observation.audioReplayCount >= 2) return InternalRating.HARD
        if (observation.exerciseType.isListening && observation.audioReplayCount == 1) return InternalRating.GOOD
        if (observation.outcome == AnswerOutcome.TYPO || observation.hintsUsed == 1) return InternalRating.GOOD

        val fastThreshold = when (observation.exerciseType) {
            ExerciseType.MEANING_RECOGNITION, ExerciseType.REVERSE_RECOGNITION -> 4_000L
            ExerciseType.FINNISH_PRODUCTION, ExerciseType.FREE_RECALL -> 7_000L
            ExerciseType.SPELLING_RECALL, ExerciseType.LISTEN_TYPE, ExerciseType.CLOZE_TYPE,
            ExerciseType.FORM_PRODUCTION -> 8_000L
            ExerciseType.LISTEN_MEANING, ExerciseType.LISTEN_CHOICE, ExerciseType.CLOZE_CHOICE,
            ExerciseType.FORM_RECOGNITION_LEMMA, ExerciseType.FORM_RECOGNITION_MEANING,
            ExerciseType.SPOKEN_RECOGNITION, ExerciseType.MINIMAL_PAIR_LISTENING -> 5_000L
            ExerciseType.INTRODUCTION, ExerciseType.RESCUE -> Long.MIN_VALUE
        }
        return if (
            observation.responseLatencyMs <= fastThreshold &&
            previous.successfulRecalls >= 2 &&
            previous.lapses == 0
        ) InternalRating.EASY else InternalRating.GOOD
    }
}

val ExerciseType.isListening: Boolean
    get() = this == ExerciseType.LISTEN_MEANING ||
        this == ExerciseType.LISTEN_TYPE ||
        this == ExerciseType.LISTEN_CHOICE ||
        this == ExerciseType.MINIMAL_PAIR_LISTENING

interface ReviewScheduler {
    fun schedule(state: LearningStateEntity, rating: InternalRating, now: Long): LearningStateEntity
}

/**
 * FSRS-style scheduler using the published stability/difficulty/retrievability model.
 * The weights are isolated here so a future FSRS version can replace this class.
 */
class FsrsStyleScheduler(
    private val desiredRetention: Double = 0.90,
) : ReviewScheduler {
    private val w = doubleArrayOf(
        0.4, 0.6, 2.4, 5.8, 4.93, 0.94, 0.86, 0.01,
        1.49, 0.14, 0.94, 2.18, 0.05, 0.34, 1.26,
    )

    override fun schedule(state: LearningStateEntity, rating: InternalRating, now: Long): LearningStateEntity {
        val firstReview = state.lastReview == null || state.stability <= 0.0
        val difficulty = if (firstReview) {
            initialDifficulty(rating)
        } else {
            nextDifficulty(state.difficulty, rating)
        }
        val stability = if (firstReview) {
            w[rating.value - 1]
        } else {
            val elapsedDays = max(0.0, (now - state.lastReview) / DAY_MS.toDouble())
            val retrievability = (1.0 + FACTOR * elapsedDays / state.stability.coerceAtLeast(0.1)).pow(DECAY)
            if (rating == InternalRating.AGAIN) {
                w[11] * difficulty.pow(-w[12]) * ((state.stability + 1.0).pow(w[13]) - 1.0) * exp((1.0 - retrievability) * w[14])
            } else {
                val hardPenalty = if (rating == InternalRating.HARD) 0.8 else 1.0
                val easyBonus = if (rating == InternalRating.EASY) 1.3 else 1.0
                state.stability * (
                    1.0 + exp(w[8]) * (11.0 - difficulty) * state.stability.pow(-w[9]) *
                        (exp((1.0 - retrievability) * w[10]) - 1.0) * hardPenalty * easyBonus
                    )
            }.coerceAtLeast(0.1)
        }

        val nextReview = if (rating == InternalRating.AGAIN) {
            now + 10 * 60 * 1_000L
        } else {
            now + intervalDays(stability).coerceAtLeast(1L) * DAY_MS
        }
        val successful = rating != InternalRating.AGAIN
        return state.copy(
            lastReview = now,
            nextReview = nextReview,
            stability = stability,
            difficulty = difficulty,
            lapses = state.lapses + if (successful) 0 else 1,
            successfulRecalls = state.successfulRecalls + if (successful) 1 else 0,
            failedRecalls = state.failedRecalls + if (successful) 0 else 1,
        )
    }

    private fun initialDifficulty(rating: InternalRating): Double =
        (w[4] - (rating.value - 3) * w[5]).coerceIn(1.0, 10.0)

    private fun nextDifficulty(current: Double, rating: InternalRating): Double {
        val shifted = current - w[6] * (rating.value - 3)
        val meanReverted = w[7] * initialDifficulty(InternalRating.GOOD) + (1.0 - w[7]) * shifted
        return meanReverted.coerceIn(1.0, 10.0)
    }

    private fun intervalDays(stability: Double): Long {
        val interval = stability / FACTOR * (desiredRetention.pow(1.0 / DECAY) - 1.0)
        return interval.roundToLong().coerceAtMost(36_500L)
    }

    companion object {
        private const val DAY_MS = 86_400_000L
        private const val DECAY = -0.5
        private const val FACTOR = 19.0 / 81.0
    }
}
