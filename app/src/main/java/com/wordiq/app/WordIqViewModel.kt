package com.wordiq.app

import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.wordiq.app.data.UserPreferences
import com.wordiq.app.data.UserPreferencesRepository
import com.wordiq.app.data.DataPortabilityRepository
import com.wordiq.app.data.ProgressSummary
import com.wordiq.app.data.PracticeRepository
import com.wordiq.app.data.WordDraft
import com.wordiq.app.data.WordFormDraft
import com.wordiq.app.data.WordRepository
import com.wordiq.app.data.local.CollectionEntity
import com.wordiq.app.data.local.CollectionWithSets
import com.wordiq.app.data.local.ConceptWithDetails
import com.wordiq.app.data.local.FlashcardSetEntity
import com.wordiq.app.data.local.SetWithWords
import com.wordiq.app.data.local.SetProgressSnapshot
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import com.wordiq.app.learning.PracticeRequest

data class UiNotice(val message: String, val actionLabel: String? = null)

data class LibraryUiState(
    val collections: List<CollectionWithSets> = emptyList(),
    val standaloneSets: List<FlashcardSetEntity> = emptyList(),
    val allSets: List<FlashcardSetEntity> = emptyList(),
    val conceptCount: Int = 0,
    val setProgress: Map<Long, SetProgressSnapshot> = emptyMap(),
    val loading: Boolean = true,
)

@OptIn(kotlinx.coroutines.FlowPreview::class, kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class WordIqViewModel(
    private val words: WordRepository,
    private val practice: PracticeRepository,
    private val preferencesRepository: UserPreferencesRepository,
    private val portability: DataPortabilityRepository,
) : ViewModel() {
    val library = combine(
        words.collections,
        words.standaloneSets,
        words.allSets,
        words.conceptCount,
        words.allSetProgress,
    ) { collections, standaloneSets, allSets, count, setProgress ->
        LibraryUiState(collections, standaloneSets, allSets, count, setProgress.associateBy { it.setId }, loading = false)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LibraryUiState())

    private val _preferencesLoaded = MutableStateFlow(false)
    val preferencesLoaded = _preferencesLoaded

    val preferences = preferencesRepository.preferences.onEach { _preferencesLoaded.value = true }.stateIn(
        viewModelScope,
        SharingStarted.Eagerly,
        UserPreferences(),
    )

    val progress = words.observeProgress().stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        ProgressSummary(),
    )

    private val _searchQuery = MutableStateFlow("")
    val searchQuery = _searchQuery
    val searchResults = _searchQuery
        .debounce(150)
        .flatMapLatest { query -> if (query.isBlank()) flowOf(emptyList()) else words.searchConcepts(query) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    var notice by mutableStateOf<UiNotice?>(null)
        private set
    private var pendingNoticeAction: (suspend () -> Unit)? = null

    init {
        viewModelScope.launch { words.seedIfEmpty() }
    }

    fun observeSet(setId: Long): Flow<SetWithWords?> = words.observeSet(setId)
    fun observeSetMastery(setId: Long): Flow<Double> = words.observeSetMastery(setId)
    fun observeSetProgress(setId: Long): Flow<SetProgressSnapshot?> = words.observeSetProgress(setId)
    fun observeCollectionSetProgress(collectionId: Long): Flow<List<SetProgressSnapshot>> =
        words.observeCollectionSetProgress(collectionId)
    fun observeCollection(collectionId: Long): Flow<CollectionWithSets?> = words.observeCollection(collectionId)
    fun observeConcept(conceptId: Long): Flow<ConceptWithDetails?> = words.observeConcept(conceptId)

    fun createCollection(name: String) = launch { words.createCollection(name) }
    fun renameCollection(collection: CollectionEntity, name: String) = launch { words.renameCollection(collection, name) }
    fun deleteCollection(collection: CollectionEntity) {
        viewModelScope.launch {
            runCatching { words.deleteCollectionForUndo(collection) }
                .onSuccess { snapshot ->
                    pendingNoticeAction = { words.restoreCollection(snapshot) }
                    notice = UiNotice("Collection deleted", "Undo")
                }
                .onFailure { notice = UiNotice("Couldn’t delete collection") }
        }
    }
    fun moveCollection(collection: CollectionEntity, direction: Int) = launch { words.moveCollection(collection, direction) }

    fun createSet(name: String, collectionId: Long?) = launch { words.createSet(name, collectionId) }
    fun renameSet(set: FlashcardSetEntity, name: String) = launch { words.renameSet(set, name) }
    fun deleteSet(set: FlashcardSetEntity) {
        viewModelScope.launch {
            runCatching { words.deleteSetForUndo(set) }
                .onSuccess { snapshot ->
                    pendingNoticeAction = { words.restoreSet(snapshot) }
                    notice = UiNotice("Set deleted", "Undo")
                }
                .onFailure { notice = UiNotice("Couldn’t delete set") }
        }
    }
    fun moveSet(set: FlashcardSetEntity, collectionId: Long?) = launch { words.moveSet(set, collectionId) }
    fun moveSetOrder(set: FlashcardSetEntity, direction: Int) = launch { words.moveSetOrder(set, direction) }

    fun saveWord(draft: WordDraft, onSaved: (Long) -> Unit, onFailure: () -> Unit = {}) {
        viewModelScope.launch {
            runCatching { words.saveWord(draft) }
                .onSuccess(onSaved)
                .onFailure {
                    notice = UiNotice((it as? IllegalArgumentException)?.message ?: "Couldn’t save word")
                    onFailure()
                }
        }
    }

    fun deleteWord(details: ConceptWithDetails, onDeleted: () -> Unit) {
        viewModelScope.launch {
            runCatching { words.deleteWordForUndo(details) }
                .onSuccess { snapshot ->
                    pendingNoticeAction = { words.restoreWord(snapshot) }
                    notice = UiNotice("Word deleted", "Undo")
                    onDeleted()
                }
                .onFailure { notice = UiNotice("Couldn’t delete word") }
        }
    }

    fun setDailyMinutes(minutes: Int) = launch { preferencesRepository.setDailyMinutes(minutes) }
    fun setExplanationLanguage(language: String) = launch { preferencesRepository.setExplanationLanguage(language) }
    fun setHapticsEnabled(enabled: Boolean) = launch { preferencesRepository.setHapticsEnabled(enabled) }
    fun setAudioEnabled(enabled: Boolean) = launch { preferencesRepository.setAudioEnabled(enabled) }
    fun setAutoPronounceEnabled(enabled: Boolean) = launch { preferencesRepository.setAutoPronounceEnabled(enabled) }
    fun completeOnboarding(language: String, minutes: Int) = launch {
        preferencesRepository.completeOnboarding(language, minutes)
    }
    fun updateSearchQuery(query: String) { _searchQuery.value = query }

    fun exportCsv(uri: Uri) = launch {
        val count = portability.exportCsv(uri)
        notice = UiNotice("Exported $count vocabulary rows")
    }

    fun importCsv(uri: Uri) = launch {
        val result = portability.importCsv(uri)
        notice = UiNotice(buildString {
            append("Imported ${result.importedRows} rows")
            if (result.skippedRows > 0) append(" · ${result.skippedRows} skipped")
        })
    }

    fun exportBackup(uri: Uri) = launch {
        val count = portability.exportBackup(uri)
        notice = UiNotice("Backup saved · $count words")
    }

    fun restoreBackup(uri: Uri) = launch {
        val count = portability.restoreBackup(uri)
        notice = UiNotice("Backup restored · $count words")
    }

    fun resetLearningProgress() = launch {
        words.resetLearningProgress()
        notice = UiNotice("Learning progress reset")
    }

    fun practiceFactory(request: PracticeRequest): ViewModelProvider.Factory =
        PracticeViewModel.factory(practice, request)

    fun showNotice(message: String) {
        pendingNoticeAction = null
        notice = UiNotice(message)
    }

    fun consumeNotice() {
        notice = null
        pendingNoticeAction = null
    }

    fun performNoticeAction() {
        val action = pendingNoticeAction ?: return consumeNotice()
        pendingNoticeAction = null
        notice = null
        viewModelScope.launch {
            runCatching { action() }
                .onSuccess { notice = UiNotice("Restored") }
                .onFailure { notice = UiNotice("Could not restore") }
        }
    }

    private fun launch(block: suspend () -> Unit) {
        viewModelScope.launch {
            runCatching { block() }.onFailure { error ->
                val message = (error as? IllegalArgumentException)?.message?.takeIf { it.isNotBlank() }
                    ?: "Something went wrong"
                notice = UiNotice(message)
            }
        }
    }

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    WordIqViewModel(container.words, container.practice, container.preferences, container.portability) as T
            }
    }
}

fun ConceptWithDetails.toDraft(): WordDraft {
    val example = examples.firstOrNull()
    return WordDraft(
        id = concept.id,
        finnish = concept.finnishLemma,
        persian = concept.persianTranslations.orEmpty(),
        english = concept.englishTranslations.orEmpty(),
        partOfSpeech = concept.partOfSpeech.orEmpty(),
        notes = concept.notes.orEmpty(),
        spokenFinnish = concept.spokenFinnish.orEmpty(),
        exampleFinnish = example?.finnishSentence.orEmpty(),
        examplePersian = example?.persianTranslation.orEmpty(),
        exampleEnglish = example?.englishTranslation.orEmpty(),
        forms = forms.sortedBy { it.sortOrder }.map {
            WordFormDraft(
                form = it.form,
                label = it.grammaticalLabel.orEmpty(),
                explanation = it.explanation.orEmpty(),
                decomposition = it.decomposition.orEmpty(),
                persianMeaning = it.persianMeaning.orEmpty(),
                persianNote = it.persianNote.orEmpty(),
            )
        },
        setIds = sets.map { it.id }.toSet(),
    )
}
