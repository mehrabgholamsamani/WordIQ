package com.wordiq.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.wordiq.app.data.PracticeRepository
import com.wordiq.app.learning.AnswerGrader
import com.wordiq.app.learning.AnswerOutcome
import com.wordiq.app.learning.ExerciseType
import com.wordiq.app.learning.GradeResult
import com.wordiq.app.learning.HintGenerator
import com.wordiq.app.learning.PracticeItem
import com.wordiq.app.learning.PracticeRequest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class PracticeFeedback(
    val grade: GradeResult,
    val selectedAnswer: String,
)

data class PracticeUiState(
    val loading: Boolean = true,
    val items: List<PracticeItem> = emptyList(),
    val index: Int = 0,
    val answer: String = "",
    val hintDepth: Int = 0,
    val audioPlayCount: Int = 0,
    val feedback: PracticeFeedback? = null,
    val selfCheckRevealed: Boolean = false,
    val strengthened: Int = 0,
    val needsWork: Int = 0,
    val estimatedMinutes: Int = 0,
    val saving: Boolean = false,
    val error: String? = null,
) {
    val current: PracticeItem? get() = items.getOrNull(index)
    val complete: Boolean get() = !loading && index >= items.size
    val empty: Boolean get() = !loading && items.isEmpty()
    val progress: Float get() = if (items.isEmpty()) 0f else index.toFloat() / items.size
    val hint: String?
        get() = current?.expectedAnswers?.firstOrNull()?.takeIf { hintDepth > 0 }?.let {
            HintGenerator.hint(it, hintDepth)
        }
}

class PracticeViewModel(
    private val repository: PracticeRepository,
    private val request: PracticeRequest,
    private val grader: AnswerGrader = AnswerGrader(),
    private val clock: () -> Long = System::currentTimeMillis,
) : ViewModel() {
    private val _uiState = MutableStateFlow(PracticeUiState())
    val uiState: StateFlow<PracticeUiState> = _uiState.asStateFlow()
    private var itemStartedAt = clock()
    private var pendingRecord: PendingRecord? = null
    private var currentRequest: PracticeRequest = request

    private data class PendingRecord(
        val item: PracticeItem,
        val outcome: AnswerOutcome,
        val latencyMs: Long,
        val hintsUsed: Int,
        val revealed: Boolean,
        val audioReplayCount: Int,
        val lemmaRemembered: Boolean,
    )

    init {
        load(request)
    }

    fun updateAnswer(answer: String) {
        if (_uiState.value.feedback == null) _uiState.update { it.copy(answer = answer) }
    }

    fun continueIntroduction() {
        if (_uiState.value.current?.exerciseType !in setOf(ExerciseType.INTRODUCTION, ExerciseType.RESCUE)) return
        advance()
    }

    fun registerAudioPlay() {
        if (_uiState.value.feedback == null) {
            _uiState.update { it.copy(audioPlayCount = it.audioPlayCount + 1) }
        }
    }

    fun selectOption(option: String) {
        val state = _uiState.value
        val item = state.current ?: return
        if (state.feedback != null) return
        submit(option, grader.grade(option, item.expectedAnswers, expectedLemma = item.expectedLemma))
    }

    fun submitTyped() {
        val state = _uiState.value
        val item = state.current ?: return
        if (state.feedback != null || state.answer.isBlank()) return
        submit(state.answer, grader.grade(state.answer, item.expectedAnswers, expectedLemma = item.expectedLemma))
    }

    fun rememberedFreeRecall() {
        val item = _uiState.value.current ?: return
        if (item.exerciseType != ExerciseType.FREE_RECALL || _uiState.value.feedback != null) return
        val expected = item.expectedAnswers.firstOrNull().orEmpty()
        submit(expected, grader.grade(expected, item.expectedAnswers))
    }

    fun revealSelfCheck() {
        val state = _uiState.value
        val item = state.current ?: return
        if (!item.selfCheck || state.feedback != null) return
        _uiState.update { it.copy(selfCheckRevealed = true) }
        val expected = item.expectedAnswers.firstOrNull().orEmpty()
        submit(expected, grader.grade(expected, item.expectedAnswers))
    }

    fun useHint() {
        val state = _uiState.value
        if (state.feedback != null) return
        if (state.hintDepth >= 3) {
            reveal()
        } else {
            _uiState.update { it.copy(hintDepth = it.hintDepth + 1) }
        }
    }

    fun reveal() {
        val state = _uiState.value
        val item = state.current ?: return
        if (state.feedback != null) return
        val grade = grader.grade(state.answer, item.expectedAnswers, expectedLemma = item.expectedLemma, revealed = true)
        submit(state.answer, grade, revealed = true)
    }

    fun next() {
        if (_uiState.value.feedback == null || _uiState.value.saving || _uiState.value.error != null) return
        advance()
    }

    fun retry() {
        if (_uiState.value.saving) return
        if (pendingRecord != null) persistPendingRecord() else load(currentRequest)
    }

    fun studyAgain() {
        load(request.copy(manual = true))
    }

    private fun load(nextRequest: PracticeRequest) {
        currentRequest = nextRequest
        pendingRecord = null
        _uiState.value = PracticeUiState(loading = true)
        viewModelScope.launch {
            runCatching { repository.buildPlan(nextRequest) }
                .onSuccess { plan ->
                    _uiState.value = PracticeUiState(
                        loading = false,
                        items = plan.items,
                        estimatedMinutes = plan.estimatedMinutes,
                    )
                    itemStartedAt = clock()
                }
                .onFailure {
                    _uiState.value = PracticeUiState(loading = false, error = "Couldn’t start practice")
                }
        }
    }

    private fun submit(answer: String, grade: GradeResult, revealed: Boolean = false) {
        val state = _uiState.value
        val item = state.current ?: return
        val latency = (clock() - itemStartedAt).coerceAtLeast(0L)
        pendingRecord = PendingRecord(
            item = item,
            outcome = grade.outcome,
            latencyMs = latency,
            hintsUsed = state.hintDepth,
            revealed = revealed || grade.outcome == AnswerOutcome.REVEALED,
            audioReplayCount = (state.audioPlayCount - 1).coerceAtLeast(0),
            lemmaRemembered = grade.lemmaRemembered,
        )
        _uiState.update { it.copy(feedback = PracticeFeedback(grade, answer), saving = true, error = null) }
        persistPendingRecord()
    }

    private fun persistPendingRecord() {
        val pending = pendingRecord ?: return
        _uiState.update { it.copy(saving = true, error = null) }
        viewModelScope.launch {
            runCatching {
                repository.record(
                    item = pending.item,
                    outcome = pending.outcome,
                    latencyMs = pending.latencyMs,
                    hintsUsed = pending.hintsUsed,
                    revealed = pending.revealed,
                    audioReplayCount = pending.audioReplayCount,
                    lemmaRemembered = pending.lemmaRemembered,
                )
            }.onSuccess {
                pendingRecord = null
                _uiState.update { it.copy(saving = false, error = null) }
            }.onFailure {
                _uiState.update { it.copy(saving = false, error = "Couldn’t save progress") }
            }
        }
    }

    private fun advance() {
        val feedback = _uiState.value.feedback
        val success = feedback?.grade?.outcome in setOf(AnswerOutcome.EXACT, AnswerOutcome.TYPO, AnswerOutcome.PARTIAL)
        val failed = feedback?.grade?.outcome in setOf(AnswerOutcome.WRONG, AnswerOutcome.REVEALED) ||
            feedback?.grade?.lemmaRemembered == true
        _uiState.update {
            it.copy(
                index = it.index + 1,
                answer = "",
                hintDepth = 0,
                audioPlayCount = 0,
                feedback = null,
                selfCheckRevealed = false,
                error = null,
                strengthened = it.strengthened + if (success) 1 else 0,
                needsWork = it.needsWork + if (failed) 1 else 0,
            )
        }
        itemStartedAt = clock()
    }

    companion object {
        fun factory(repository: PracticeRepository, request: PracticeRequest): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    PracticeViewModel(repository, request) as T
            }
    }
}
