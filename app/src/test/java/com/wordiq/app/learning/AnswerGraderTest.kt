package com.wordiq.app.learning

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AnswerGraderTest {
    private val grader = AnswerGrader()

    @Test
    fun normalizesCaseWhitespaceAndPunctuation() {
        assertEquals(AnswerOutcome.EXACT, grader.grade("  KAUPPA! ", listOf("kauppa")).outcome)
    }

    @Test
    fun normalizesArabicAndPersianCodePointsAndHalfSpaces() {
        assertEquals("می روم", grader.normalize("مِي\u200Cروم"))
        assertEquals("یک", grader.normalize("يك"))
    }

    @Test
    fun distinguishesTypoPartialWrongAndReveal() {
        assertEquals(AnswerOutcome.TYPO, grader.grade("kaupasa", listOf("kaupassa")).outcome)
        val partial = grader.grade("kauppa", listOf("kaupasta"), expectedLemma = "kauppa")
        assertEquals(AnswerOutcome.PARTIAL, partial.outcome)
        assertTrue(partial.lemmaRemembered)
        assertEquals(AnswerOutcome.WRONG, grader.grade("ystävä", listOf("kauppa")).outcome)
        assertEquals(AnswerOutcome.REVEALED, grader.grade("", listOf("kauppa"), revealed = true).outcome)
    }

    @Test
    fun parsesAlternativeTranslations() {
        val answers = splitAcceptedAnswers("store, shop / market; business")
        assertEquals(listOf("store", "shop", "market", "business"), answers)
    }

    @Test
    fun hintsRevealProgressively() {
        val first = HintGenerator.hint("ymmärtää", 1)
        val second = HintGenerator.hint("ymmärtää", 2)
        val third = HintGenerator.hint("ymmärtää", 3)
        assertEquals("y_______", first)
        assertTrue(second.count { it != '_' } > first.count { it != '_' })
        assertTrue(third.count { it != '_' } > second.count { it != '_' })
        assertFalse(third == "ymmärtää")
    }

    @Test
    fun identifiesFinnishConsonantAndVowelLengthTypos() {
        val consonant = grader.grade("kaupasa", listOf("kaupassa"))
        assertEquals(AnswerOutcome.TYPO, consonant.outcome)
        assertEquals(FinnishQuantityType.CONSONANT, consonant.quantityIssue?.type)
        assertEquals("ss", consonant.quantityIssue?.expectedSegment)

        val vowel = grader.grade("tuli", listOf("tuuli"))
        assertEquals(AnswerOutcome.TYPO, vowel.outcome)
        assertEquals(FinnishQuantityType.VOWEL, vowel.quantityIssue?.type)
        assertEquals("uu", vowel.quantityIssue?.expectedSegment)
    }
}
