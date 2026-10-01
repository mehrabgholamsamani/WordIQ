package com.wordiq.app.learning

import com.wordiq.app.data.local.ConceptWithDetails

data class RescueContent(
    val finnish: String,
    val persianExplanation: String?,
    val englishExplanation: String?,
    val example: String?,
    val exampleTranslation: String?,
    val spellingChunks: String,
    val memoryNote: String?,
    val importantForm: String?,
    val confusingWord: String?,
)

class TroubleWordDetector(
    private val minimumFailures: Int = 3,
) {
    fun needsRescue(concept: ConceptWithDetails): Boolean {
        val totalFailures = concept.learningStates.sumOf { it.failedRecalls }
        val repeatedSkillFailure = concept.learningStates.any { it.failedRecalls >= minimumFailures || it.lapses >= 2 }
        return repeatedSkillFailure || totalFailures >= minimumFailures + 1
    }
}

interface RescueContentProvider {
    fun content(concept: ConceptWithDetails, comparisonPool: List<ConceptWithDetails>): RescueContent
}

/** Builds rescue material exclusively from trusted content already stored on the device. */
class StoredRescueContentProvider : RescueContentProvider {
    override fun content(concept: ConceptWithDetails, comparisonPool: List<ConceptWithDetails>): RescueContent {
        val example = concept.examples.firstOrNull()
        val confusing = comparisonPool.asSequence()
            .filter { it.concept.id != concept.concept.id }
            .map { it to commonPrefixLength(concept.concept.finnishLemma, it.concept.finnishLemma) }
            .maxByOrNull { it.second }
            ?.takeIf { it.second >= 2 }
            ?.first?.concept?.finnishLemma
        return RescueContent(
            finnish = concept.concept.finnishLemma,
            persianExplanation = concept.concept.persianTranslations,
            englishExplanation = concept.concept.englishTranslations,
            example = example?.finnishSentence,
            exampleTranslation = example?.persianTranslation ?: example?.englishTranslation,
            spellingChunks = chunkSpelling(concept.concept.finnishLemma),
            memoryNote = concept.concept.notes,
            importantForm = concept.forms.minByOrNull { it.sortOrder }?.form,
            confusingWord = confusing,
        )
    }

    private fun chunkSpelling(value: String): String = value.chunked(3).joinToString(" · ")

    private fun commonPrefixLength(first: String, second: String): Int =
        first.lowercase().zip(second.lowercase()).takeWhile { (a, b) -> a == b }.size
}
