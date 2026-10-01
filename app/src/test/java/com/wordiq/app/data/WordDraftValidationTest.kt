package com.wordiq.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WordDraftValidationTest {
    @Test
    fun acceptsFinnishWithPersianAndMultipleSets() {
        val draft = WordDraft(finnish = "kauppa", persian = "فروشگاه", setIds = setOf(1, 2))
        assertNull(validateWordDraft(draft))
    }

    @Test
    fun acceptsFinnishWithEnglishOnly() {
        val draft = WordDraft(finnish = "kauppa", english = "shop", setIds = setOf(1))
        assertNull(validateWordDraft(draft))
    }

    @Test
    fun requiresFinnishTranslationAndMembership() {
        assertEquals("Finnish is required", validateWordDraft(WordDraft(english = "shop", setIds = setOf(1))))
        assertEquals("Add a Persian or English translation", validateWordDraft(WordDraft(finnish = "kauppa", setIds = setOf(1))))
        assertEquals("Choose at least one set", validateWordDraft(WordDraft(finnish = "kauppa", english = "shop")))
    }
}
