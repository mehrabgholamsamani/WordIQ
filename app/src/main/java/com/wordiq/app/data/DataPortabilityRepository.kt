package com.wordiq.app.data

import android.content.Context
import android.net.Uri
import androidx.room.withTransaction
import com.wordiq.app.data.local.CollectionEntity
import com.wordiq.app.data.local.ExampleSentenceEntity
import com.wordiq.app.data.local.FlashcardSetEntity
import com.wordiq.app.data.local.LearningSkill
import com.wordiq.app.data.local.LearningStateEntity
import com.wordiq.app.data.local.ReviewLogEntity
import com.wordiq.app.data.local.SetMembershipEntity
import com.wordiq.app.data.local.VocabularyConceptEntity
import com.wordiq.app.data.local.WordFormEntity
import com.wordiq.app.data.local.WordIqDatabase
import com.wordiq.app.data.portability.CsvImportResult
import com.wordiq.app.data.portability.VocabularyCsvCodec
import com.wordiq.app.data.portability.VocabularyCsvRow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream

data class ImportSummary(val importedRows: Int, val skippedRows: Int)

class DataPortabilityRepository(
    private val context: Context,
    private val database: WordIqDatabase,
) {
    private val dao = database.wordIqDao()

    suspend fun exportCsv(uri: Uri): Int = withContext(Dispatchers.IO) {
        val collections = dao.getCollections().associateBy { it.id }
        val rows = dao.getAllPracticeConcepts().flatMap { details ->
            val states = details.learningStates.associateBy { it.skill }
            val base = VocabularyCsvRow(
                finnish = details.concept.finnishLemma,
                persian = details.concept.persianTranslations.orEmpty(),
                english = details.concept.englishTranslations.orEmpty(),
                example = details.examples.firstOrNull()?.finnishSentence.orEmpty(),
                partOfSpeech = details.concept.partOfSpeech.orEmpty(),
                notes = details.concept.notes.orEmpty(),
                spokenFinnish = details.concept.spokenFinnish.orEmpty(),
                meaningStability = states[LearningSkill.MEANING_RECOGNITION.name]?.stability,
                productionStability = states[LearningSkill.FINNISH_PRODUCTION.name]?.stability,
                listeningStability = states[LearningSkill.LISTENING.name]?.stability,
                lastReview = states.values.mapNotNull { it.lastReview }.maxOrNull(),
                nextReview = states.values.mapNotNull { it.nextReview }.minOrNull(),
            )
            details.sets.ifEmpty { listOf(null) }.map { set ->
                base.copy(
                    set = set?.name.orEmpty(),
                    collection = set?.collectionId?.let { collections[it]?.name }.orEmpty(),
                )
            }
        }
        writeText(uri, VocabularyCsvCodec.encode(rows))
        rows.size
    }

    suspend fun importCsv(uri: Uri): ImportSummary = withContext(Dispatchers.IO) {
        val decoded = VocabularyCsvCodec.decode(readText(uri, 10 * 1024 * 1024))
        importRows(decoded)
        ImportSummary(decoded.rows.size, decoded.skippedRows)
    }

    private suspend fun importRows(decoded: CsvImportResult) = database.withTransaction {
        val now = System.currentTimeMillis()
        val collections = dao.getCollections().associateBy { it.name.trim().lowercase() }.toMutableMap()
        val sets = dao.getAllSets().associateBy { "${it.collectionId}:${it.name.trim().lowercase()}" }.toMutableMap()
        val concepts = dao.getAllPracticeConcepts().associateBy { it.concept.finnishLemma.trim().lowercase() }.toMutableMap()
        val existingExamples = dao.getAllExamples().groupBy { it.conceptId }.mapValues { it.value.map { example -> example.finnishSentence }.toMutableSet() }.toMutableMap()

        decoded.rows.forEach { row ->
            val collectionId = row.collection.trim().takeIf { it.isNotEmpty() }?.let { collectionName ->
                collections.getOrPut(collectionName.lowercase()) {
                    val entity = CollectionEntity(
                        id = dao.insertCollection(CollectionEntity(name = collectionName, icon = "book", sortOrder = collections.size)),
                        name = collectionName,
                        icon = "book",
                        sortOrder = collections.size,
                    )
                    entity
                }.id
            }
            val setName = row.set.trim().ifEmpty { "Imported" }
            val setKey = "$collectionId:${setName.lowercase()}"
            val set = sets.getOrPut(setKey) {
                val order = sets.values.count { it.collectionId == collectionId }
                val id = dao.insertSet(FlashcardSetEntity(name = setName, collectionId = collectionId, sortOrder = order, createdAt = now))
                FlashcardSetEntity(id, setName, collectionId, order, now)
            }
            val conceptKey = row.finnish.trim().lowercase()
            var details = concepts[conceptKey]
            val conceptId = if (details == null) {
                val entity = VocabularyConceptEntity(
                    finnishLemma = row.finnish.trim(),
                    persianTranslations = row.persian.ifBlank { null },
                    englishTranslations = row.english.ifBlank { null },
                    partOfSpeech = row.partOfSpeech.ifBlank { null },
                    notes = row.notes.ifBlank { null },
                    spokenFinnish = row.spokenFinnish.ifBlank { null },
                    createdAt = now,
                    updatedAt = now,
                )
                val id = dao.insertConcept(entity)
                dao.insertLearningStates(LearningSkill.entries.map { LearningStateEntity(id, it.name) })
                id
            } else {
                val old = details.concept
                dao.updateConcept(
                    old.copy(
                        persianTranslations = old.persianTranslations ?: row.persian.ifBlank { null },
                        englishTranslations = old.englishTranslations ?: row.english.ifBlank { null },
                        partOfSpeech = old.partOfSpeech ?: row.partOfSpeech.ifBlank { null },
                        notes = old.notes ?: row.notes.ifBlank { null },
                        spokenFinnish = old.spokenFinnish ?: row.spokenFinnish.ifBlank { null },
                        updatedAt = now,
                    ),
                )
                old.id
            }
            dao.insertMembership(SetMembershipEntity(conceptId, set.id, now))
            if (row.example.isNotBlank() && existingExamples.getOrPut(conceptId) { mutableSetOf() }.add(row.example.trim())) {
                dao.insertExamples(listOf(ExampleSentenceEntity(conceptId = conceptId, finnishSentence = row.example.trim())))
            }
            upsertImportedState(conceptId, LearningSkill.MEANING_RECOGNITION, row.meaningStability, row.lastReview, row.nextReview)
            upsertImportedState(conceptId, LearningSkill.FINNISH_PRODUCTION, row.productionStability, row.lastReview, row.nextReview)
            upsertImportedState(conceptId, LearningSkill.LISTENING, row.listeningStability, row.lastReview, row.nextReview)
            if (details == null) {
                details = dao.getAllPracticeConcepts().firstOrNull { it.concept.id == conceptId }
                if (details != null) concepts[conceptKey] = details
            }
        }
    }

    private suspend fun upsertImportedState(
        conceptId: Long,
        skill: LearningSkill,
        stability: Double?,
        lastReview: Long?,
        nextReview: Long?,
    ) {
        if (stability == null && lastReview == null && nextReview == null) return
        val current = dao.getLearningState(conceptId, skill.name) ?: LearningStateEntity(conceptId, skill.name)
        dao.upsertLearningState(
            current.copy(
                stability = stability?.coerceAtLeast(0.0) ?: current.stability,
                lastReview = lastReview ?: current.lastReview,
                nextReview = nextReview ?: current.nextReview,
            ),
        )
    }

    suspend fun exportBackup(uri: Uri): Int = withContext(Dispatchers.IO) {
        val root = JSONObject()
            .put("format", "wordiq-backup")
            .put("version", 1)
            .put("exportedAt", System.currentTimeMillis())
            .put("collections", dao.getCollections().toJsonArray { it.toJson() })
            .put("sets", dao.getAllSets().toJsonArray { it.toJson() })
            .put("concepts", dao.getAllConcepts().toJsonArray { it.toJson() })
            .put("forms", dao.getAllForms().toJsonArray { it.toJson() })
            .put("examples", dao.getAllExamples().toJsonArray { it.toJson() })
            .put("memberships", dao.getAllMemberships().toJsonArray { it.toJson() })
            .put("learningStates", dao.getAllLearningStates().toJsonArray { it.toJson() })
            .put("reviewLogs", dao.getAllReviewLogs().toJsonArray { it.toJson() })
        writeText(uri, root.toString(2))
        root.getJSONArray("concepts").length()
    }

    suspend fun restoreBackup(uri: Uri): Int = withContext(Dispatchers.IO) {
        val root = JSONObject(readText(uri, 25 * 1024 * 1024))
        require(root.optString("format") == "wordiq-backup" && root.optInt("version") == 1) {
            "This is not a supported WordIQ backup"
        }
        val collections = root.array("collections").mapObjects(::collectionFromJson)
        val sets = root.array("sets").mapObjects(::setFromJson)
        val concepts = root.array("concepts").mapObjects(::conceptFromJson)
        val forms = root.array("forms").mapObjects(::formFromJson)
        val examples = root.array("examples").mapObjects(::exampleFromJson)
        val memberships = root.array("memberships").mapObjects(::membershipFromJson)
        val states = root.array("learningStates").mapObjects(::stateFromJson)
        val logs = root.array("reviewLogs").mapObjects(::logFromJson)
        require(concepts.all { it.finnishLemma.isNotBlank() }) { "Backup contains an invalid Finnish word" }

        database.withTransaction {
            dao.clearReviewLogs()
            dao.clearMemberships()
            dao.clearExamples()
            dao.clearForms()
            dao.clearLearningStates()
            dao.clearConcepts()
            dao.clearSets()
            dao.clearCollections()
            if (collections.isNotEmpty()) dao.restoreCollections(collections)
            if (sets.isNotEmpty()) dao.restoreSets(sets)
            if (concepts.isNotEmpty()) dao.restoreConcepts(concepts)
            if (forms.isNotEmpty()) dao.restoreForms(forms)
            if (examples.isNotEmpty()) dao.restoreExamples(examples)
            if (memberships.isNotEmpty()) dao.restoreMemberships(memberships)
            if (states.isNotEmpty()) dao.restoreLearningStates(states)
            if (logs.isNotEmpty()) dao.restoreReviewLogs(logs)
        }
        concepts.size
    }

    private fun writeText(uri: Uri, value: String) {
        context.contentResolver.openOutputStream(uri, "wt")?.bufferedWriter(Charsets.UTF_8)?.use { it.write(value) }
            ?: error("Could not open the selected file")
    }

    private fun readText(uri: Uri, maxBytes: Int): String {
        val input = context.contentResolver.openInputStream(uri) ?: error("Could not open the selected file")
        return input.use { stream ->
            val output = ByteArrayOutputStream()
            val buffer = ByteArray(8_192)
            var total = 0
            while (true) {
                val read = stream.read(buffer)
                if (read < 0) break
                total += read
                require(total <= maxBytes) { "The selected file is too large" }
                output.write(buffer, 0, read)
            }
            output.toString(Charsets.UTF_8.name())
        }
    }
}

private inline fun <T> List<T>.toJsonArray(transform: (T) -> JSONObject) = JSONArray().also { array -> forEach { array.put(transform(it)) } }
private fun JSONObject.array(name: String): JSONArray = optJSONArray(name) ?: JSONArray()
private inline fun <T> JSONArray.mapObjects(transform: (JSONObject) -> T): List<T> = List(length()) { transform(getJSONObject(it)) }
private fun JSONObject.putNull(name: String, value: Any?): JSONObject = put(name, value ?: JSONObject.NULL)
private fun JSONObject.nullableString(name: String) = if (isNull(name)) null else getString(name)
private fun JSONObject.nullableLong(name: String) = if (isNull(name)) null else getLong(name)

private fun CollectionEntity.toJson() = JSONObject().put("id", id).put("name", name).putNull("icon", icon).put("sortOrder", sortOrder)
private fun collectionFromJson(o: JSONObject) = CollectionEntity(o.getLong("id"), o.getString("name"), o.nullableString("icon"), o.getInt("sortOrder"))
private fun FlashcardSetEntity.toJson() = JSONObject().put("id", id).put("name", name).putNull("collectionId", collectionId).put("sortOrder", sortOrder).put("createdAt", createdAt)
private fun setFromJson(o: JSONObject) = FlashcardSetEntity(o.getLong("id"), o.getString("name"), o.nullableLong("collectionId"), o.getInt("sortOrder"), o.getLong("createdAt"))
private fun VocabularyConceptEntity.toJson() = JSONObject().put("id", id).put("finnishLemma", finnishLemma).putNull("persianTranslations", persianTranslations).putNull("englishTranslations", englishTranslations).putNull("partOfSpeech", partOfSpeech).putNull("notes", notes).putNull("pronunciationUri", pronunciationUri).putNull("spokenFinnish", spokenFinnish).put("createdAt", createdAt).put("updatedAt", updatedAt).put("archived", archived)
private fun conceptFromJson(o: JSONObject) = VocabularyConceptEntity(o.getLong("id"), o.getString("finnishLemma"), o.nullableString("persianTranslations"), o.nullableString("englishTranslations"), o.nullableString("partOfSpeech"), o.nullableString("notes"), o.nullableString("pronunciationUri"), o.nullableString("spokenFinnish"), o.getLong("createdAt"), o.getLong("updatedAt"), o.getBoolean("archived"))
private fun WordFormEntity.toJson() = JSONObject().put("id", id).put("conceptId", conceptId).put("form", form).putNull("grammaticalLabel", grammaticalLabel).putNull("explanation", explanation).putNull("decomposition", decomposition).putNull("persianMeaning", persianMeaning).putNull("persianNote", persianNote).put("sortOrder", sortOrder)
private fun formFromJson(o: JSONObject) = WordFormEntity(o.getLong("id"), o.getLong("conceptId"), o.getString("form"), o.nullableString("grammaticalLabel"), o.nullableString("explanation"), o.nullableString("decomposition"), o.nullableString("persianMeaning"), o.nullableString("persianNote"), o.getInt("sortOrder"))
private fun ExampleSentenceEntity.toJson() = JSONObject().put("id", id).put("conceptId", conceptId).put("finnishSentence", finnishSentence).putNull("persianTranslation", persianTranslation).putNull("englishTranslation", englishTranslation).putNull("targetWordOrForm", targetWordOrForm).putNull("source", source)
private fun exampleFromJson(o: JSONObject) = ExampleSentenceEntity(o.getLong("id"), o.getLong("conceptId"), o.getString("finnishSentence"), o.nullableString("persianTranslation"), o.nullableString("englishTranslation"), o.nullableString("targetWordOrForm"), o.nullableString("source"))
private fun SetMembershipEntity.toJson() = JSONObject().put("conceptId", conceptId).put("setId", setId).put("addedAt", addedAt)
private fun membershipFromJson(o: JSONObject) = SetMembershipEntity(o.getLong("conceptId"), o.getLong("setId"), o.getLong("addedAt"))
private fun LearningStateEntity.toJson() = JSONObject().put("conceptId", conceptId).put("skill", skill).putNull("lastReview", lastReview).putNull("nextReview", nextReview).put("stability", stability).put("difficulty", difficulty).put("lapses", lapses).put("successfulRecalls", successfulRecalls).put("failedRecalls", failedRecalls).putNull("recentResponseLatencyMs", recentResponseLatencyMs).put("recentHintUsage", recentHintUsage)
private fun stateFromJson(o: JSONObject) = LearningStateEntity(o.getLong("conceptId"), o.getString("skill"), o.nullableLong("lastReview"), o.nullableLong("nextReview"), o.getDouble("stability"), o.getDouble("difficulty"), o.getInt("lapses"), o.getInt("successfulRecalls"), o.getInt("failedRecalls"), o.nullableLong("recentResponseLatencyMs"), o.getInt("recentHintUsage"))
private fun ReviewLogEntity.toJson() = JSONObject().put("id", id).put("conceptId", conceptId).put("skill", skill).put("exerciseType", exerciseType).put("outcome", outcome).put("internalRating", internalRating).put("responseLatencyMs", responseLatencyMs).put("hintsUsed", hintsUsed).put("audioReplayCount", audioReplayCount).put("lemmaRemembered", lemmaRemembered).put("revealed", revealed).put("scheduled", scheduled).put("reviewedAt", reviewedAt)
private fun logFromJson(o: JSONObject) = ReviewLogEntity(o.getLong("id"), o.getLong("conceptId"), o.getString("skill"), o.getString("exerciseType"), o.getString("outcome"), o.getString("internalRating"), o.getLong("responseLatencyMs"), o.getInt("hintsUsed"), o.optInt("audioReplayCount"), o.optBoolean("lemmaRemembered"), o.getBoolean("revealed"), o.getBoolean("scheduled"), o.getLong("reviewedAt"))
