package com.wordiq.app.data.portability

data class VocabularyCsvRow(
    val finnish: String,
    val persian: String = "",
    val english: String = "",
    val set: String = "",
    val collection: String = "",
    val example: String = "",
    val partOfSpeech: String = "",
    val notes: String = "",
    val spokenFinnish: String = "",
    val meaningStability: Double? = null,
    val productionStability: Double? = null,
    val listeningStability: Double? = null,
    val lastReview: Long? = null,
    val nextReview: Long? = null,
)

data class CsvImportResult(
    val rows: List<VocabularyCsvRow>,
    val skippedRows: Int,
)

/** Small RFC 4180-compatible codec. It accepts reordered columns, CRLF and quoted newlines. */
object VocabularyCsvCodec {
    val headers = listOf(
        "Finnish", "Persian", "English", "Set", "Collection", "Example",
        "Part of speech", "Notes", "Spoken Finnish", "Meaning stability",
        "Production stability", "Listening stability", "Last review", "Next review",
    )

    fun encode(rows: List<VocabularyCsvRow>): String = buildString {
        append('\uFEFF')
        appendLine(headers.joinToString(",", transform = ::escape))
        rows.forEach { row ->
            val values = listOf(
                row.finnish, row.persian, row.english, row.set, row.collection, row.example,
                row.partOfSpeech, row.notes, row.spokenFinnish,
                row.meaningStability?.toString().orEmpty(),
                row.productionStability?.toString().orEmpty(),
                row.listeningStability?.toString().orEmpty(),
                row.lastReview?.toString().orEmpty(), row.nextReview?.toString().orEmpty(),
            )
            appendLine(values.joinToString(",", transform = ::escape))
        }
    }

    fun decode(csv: String): CsvImportResult {
        val records = parse(csv.removePrefix("\uFEFF"))
        require(records.isNotEmpty()) { "The CSV file is empty" }
        val header = records.first().mapIndexed { index, value -> normalizeHeader(value) to index }.toMap()
        require(header.containsKey("finnish")) { "CSV must include a Finnish column" }
        var skipped = 0
        val rows = records.drop(1).mapNotNull { values ->
            fun value(name: String): String = header[normalizeHeader(name)]?.let { values.getOrNull(it) }.orEmpty().trim()
            val finnish = value("Finnish")
            val persian = value("Persian")
            val english = value("English")
            if (finnish.isBlank() || (persian.isBlank() && english.isBlank())) {
                skipped++
                null
            } else {
                VocabularyCsvRow(
                    finnish = finnish,
                    persian = persian,
                    english = english,
                    set = value("Set"),
                    collection = value("Collection"),
                    example = value("Example"),
                    partOfSpeech = value("Part of speech"),
                    notes = value("Notes"),
                    spokenFinnish = value("Spoken Finnish"),
                    meaningStability = value("Meaning stability").toDoubleOrNull(),
                    productionStability = value("Production stability").toDoubleOrNull(),
                    listeningStability = value("Listening stability").toDoubleOrNull(),
                    lastReview = value("Last review").toLongOrNull(),
                    nextReview = value("Next review").toLongOrNull(),
                )
            }
        }
        require(rows.isNotEmpty()) { "No valid vocabulary rows were found" }
        return CsvImportResult(rows, skipped)
    }

    private fun escape(value: String): String = if (value.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) {
        "\"${value.replace("\"", "\"\"")}\""
    } else value

    private fun normalizeHeader(value: String): String = value.trim().lowercase().replace("_", " ").replace("-", " ")

    private fun parse(text: String): List<List<String>> {
        val records = mutableListOf<List<String>>()
        var record = mutableListOf<String>()
        val field = StringBuilder()
        var quoted = false
        var index = 0
        while (index < text.length) {
            val char = text[index]
            when {
                quoted && char == '"' && text.getOrNull(index + 1) == '"' -> {
                    field.append('"')
                    index++
                }
                char == '"' -> quoted = !quoted
                !quoted && char == ',' -> {
                    record += field.toString()
                    field.clear()
                }
                !quoted && (char == '\n' || char == '\r') -> {
                    if (char == '\r' && text.getOrNull(index + 1) == '\n') index++
                    record += field.toString()
                    field.clear()
                    if (record.any { it.isNotBlank() }) records += record
                    record = mutableListOf()
                }
                else -> field.append(char)
            }
            index++
        }
        require(!quoted) { "CSV contains an unclosed quoted field" }
        if (field.isNotEmpty() || record.isNotEmpty()) {
            record += field.toString()
            if (record.any { it.isNotBlank() }) records += record
        }
        return records
    }
}
