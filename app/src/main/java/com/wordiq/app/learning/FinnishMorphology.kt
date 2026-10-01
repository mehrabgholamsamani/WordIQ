package com.wordiq.app.learning

import com.wordiq.app.data.local.ConceptWithDetails
import com.wordiq.app.data.local.LearningStateEntity
import com.wordiq.app.data.local.WordFormEntity

data class MorphologyAnalysis(
    val formId: Long,
    val surface: String,
    val lemma: String,
    val grammaticalLabel: String?,
    val englishMeaning: String?,
    val persianMeaning: String?,
    val persianNote: String?,
    val decomposition: String?,
)

/** Boundary for a future deterministic analyzer such as Omorfi. */
interface FinnishMorphologyService {
    fun analyses(concept: ConceptWithDetails): List<MorphologyAnalysis>
    fun analyze(surface: String, concept: ConceptWithDetails): MorphologyAnalysis?
    fun unlockedAnalyses(concept: ConceptWithDetails, state: LearningStateEntity): List<MorphologyAnalysis>
}

/** Uses only forms explicitly stored by the learner or bundled sample data. */
class ManualFinnishMorphologyService : FinnishMorphologyService {
    override fun analyses(concept: ConceptWithDetails): List<MorphologyAnalysis> =
        concept.forms.sortedBy { it.sortOrder }.map { it.toAnalysis(concept.concept.finnishLemma) }

    override fun analyze(surface: String, concept: ConceptWithDetails): MorphologyAnalysis? =
        analyses(concept).firstOrNull { it.surface.equals(surface, ignoreCase = true) }

    override fun unlockedAnalyses(
        concept: ConceptWithDetails,
        state: LearningStateEntity,
    ): List<MorphologyAnalysis> {
        val unlockedCount = (1 + state.successfulRecalls / 2).coerceAtMost(concept.forms.size)
        return analyses(concept).take(unlockedCount)
    }

    private fun WordFormEntity.toAnalysis(lemma: String) = MorphologyAnalysis(
        formId = id,
        surface = form,
        lemma = lemma,
        grammaticalLabel = grammaticalLabel,
        englishMeaning = explanation,
        persianMeaning = persianMeaning,
        persianNote = persianNote,
        decomposition = decomposition,
    )
}

enum class FinnishQuantityType { VOWEL, CONSONANT }

data class FinnishQuantityIssue(
    val type: FinnishQuantityType,
    val expectedSegment: String,
    val expectedStart: Int,
)

object FinnishQuantityDetector {
    private val vowels = setOf('a', 'e', 'i', 'o', 'u', 'y', 'ä', 'ö')

    fun detect(normalizedAnswer: String, normalizedExpected: String): FinnishQuantityIssue? {
        val answerRuns = runs(normalizedAnswer)
        val expectedRuns = runs(normalizedExpected)
        if (answerRuns.map { it.char } != expectedRuns.map { it.char }) return null
        val differences = answerRuns.indices.filter { answerRuns[it].length != expectedRuns[it].length }
        if (differences.size != 1) return null
        val index = differences.single()
        val answerRun = answerRuns[index]
        val expectedRun = expectedRuns[index]
        if (kotlin.math.abs(answerRun.length - expectedRun.length) != 1) return null
        if (maxOf(answerRun.length, expectedRun.length) != 2) return null
        val start = expectedRuns.take(index).sumOf { it.length }
        return FinnishQuantityIssue(
            type = if (expectedRun.char in vowels) FinnishQuantityType.VOWEL else FinnishQuantityType.CONSONANT,
            expectedSegment = expectedRun.char.toString().repeat(expectedRun.length),
            expectedStart = start,
        )
    }

    private fun runs(value: String): List<Run> {
        if (value.isEmpty()) return emptyList()
        val result = mutableListOf<Run>()
        value.forEach { char ->
            val last = result.lastOrNull()
            if (last?.char == char) result[result.lastIndex] = last.copy(length = last.length + 1)
            else result += Run(char, 1)
        }
        return result
    }

    private data class Run(val char: Char, val length: Int)
}

data class FinnishMinimalPair(
    val target: String,
    val contrast: String,
    val quantityType: FinnishQuantityType,
)

interface FinnishSoundPairService {
    fun findPair(target: ConceptWithDetails, pool: List<ConceptWithDetails>): FinnishMinimalPair?
}

class LocalFinnishSoundPairService : FinnishSoundPairService {
    override fun findPair(target: ConceptWithDetails, pool: List<ConceptWithDetails>): FinnishMinimalPair? {
        val targetWord = target.concept.finnishLemma.lowercase()
        return pool.asSequence()
            .filter { it.concept.id != target.concept.id }
            .mapNotNull { candidate ->
                val contrast = candidate.concept.finnishLemma.lowercase()
                val issue = FinnishQuantityDetector.detect(targetWord, contrast)
                    ?: FinnishQuantityDetector.detect(contrast, targetWord)
                issue?.let { FinnishMinimalPair(target.concept.finnishLemma, candidate.concept.finnishLemma, it.type) }
            }
            .firstOrNull()
    }
}
