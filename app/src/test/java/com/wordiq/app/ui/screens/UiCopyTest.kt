package com.wordiq.app.ui.screens

import org.junit.Assert.assertEquals
import org.junit.Test

class UiCopyTest {
    @Test
    fun wordCountUsesReadableSingularAndPlural() {
        assertEquals("0 words", wordCountLabel(0))
        assertEquals("1 word", wordCountLabel(1))
        assertEquals("2 words", wordCountLabel(2))
    }

    @Test
    fun compactWordRowsRespectExplanationPreference() {
        assertEquals("فروشگاه", preferredMeaning("فروشگاه", "store", "فارسی"))
        assertEquals("store", preferredMeaning("فروشگاه", "store", "English"))
        assertEquals("فروشگاه", preferredMeaning("فروشگاه", "store", "Both"))
        assertEquals("store", preferredMeaning(null, "store", "فارسی"))
    }
}
