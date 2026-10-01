package com.wordiq.app.data.portability

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VocabularyCsvCodecTest {
    @Test
    fun roundTripPreservesCommasQuotesPersianAndNewlines() {
        val original = VocabularyCsvRow(
            finnish = "vaikuttaa",
            persian = "تأثیر گذاشتن، به نظر رسیدن",
            english = "affect, \"seem\"",
            set = "YKI, work",
            collection = "My Finnish",
            example = "Se vaikuttaa\nhyvältä.",
            notes = "A useful, common verb",
            meaningStability = 12.5,
            nextReview = 123456L,
        )

        val csv = VocabularyCsvCodec.encode(listOf(original))
        val decoded = VocabularyCsvCodec.decode(csv)

        assertEquals(listOf(original), decoded.rows)
        assertEquals(0, decoded.skippedRows)
    }

    @Test
    fun acceptsReorderedMinimalColumnsAndCrLf() {
        val csv = "English,Finnish,Persian\r\nshop,kauppa,فروشگاه\r\n"

        val result = VocabularyCsvCodec.decode(csv)

        assertEquals("kauppa", result.rows.single().finnish)
        assertEquals("shop", result.rows.single().english)
    }

    @Test
    fun skipsRowsWithoutAUsableTranslation() {
        val csv = "Finnish,Persian,English\npuu,,tree\ntalo,,\n,خانه,house\n"

        val result = VocabularyCsvCodec.decode(csv)

        assertEquals(listOf("puu"), result.rows.map { it.finnish })
        assertEquals(2, result.skippedRows)
    }

    @Test
    fun rejectsUnclosedQuotes() {
        val failure = runCatching { VocabularyCsvCodec.decode("Finnish,English\nword,\"broken") }
        assertTrue(failure.exceptionOrNull()?.message.orEmpty().contains("unclosed"))
    }
}
