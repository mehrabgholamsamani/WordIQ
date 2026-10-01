package com.wordiq.app.data

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import org.junit.Assert.assertEquals
import org.junit.Test

class WordDraftPersistenceTest {
    @Test
    fun draftRoundTripsThroughSavedStateSerialization() {
        val draft = WordDraft(
            finnish = "ystävä",
            persian = "دوست",
            english = "friend",
            notes = "Important",
            forms = listOf(WordFormDraft("ystävän", label = "genitive")),
            setIds = setOf(2L, 7L),
        )

        val bytes = ByteArrayOutputStream().use { output ->
            ObjectOutputStream(output).use { it.writeObject(draft) }
            output.toByteArray()
        }
        val restored = ObjectInputStream(ByteArrayInputStream(bytes)).use { it.readObject() as WordDraft }

        assertEquals(draft, restored)
    }
}
