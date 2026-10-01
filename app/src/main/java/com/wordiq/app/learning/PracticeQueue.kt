package com.wordiq.app.learning

import com.wordiq.app.data.local.ConceptWithDetails
import com.wordiq.app.data.local.ExampleSentenceEntity
import com.wordiq.app.data.local.LearningSkill
import com.wordiq.app.data.local.LearningStateEntity
import kotlin.math.roundToInt

enum class PracticeScopeType { GLOBAL, SET, COLLECTION }
enum class PromptLanguage { PERSIAN, ENGLISH, FINNISH }
enum class PracticeFocus {
    SMART,
    LEARN_NEW,
    REVIEW,
    MEANING_PERSIAN,
    PRODUCTION_PERSIAN,
    MEANING_ENGLISH,
    PRODUCTION_ENGLISH,
    LISTENING,
    CONTEXT,
    FORMS,
    SPOKEN,
    WEAK,
    SELF_CHECK_MEANING,
    SELF_CHECK_PERSIAN,
    SELF_CHECK_ENGLISH,
    MULTIPLE_CHOICE_MEANING,
    MULTIPLE_CHOICE_PERSIAN,
    MULTIPLE_CHOICE_ENGLISH,
    SPELLING_PERSIAN,
    SPELLING_ENGLISH,
}

data class PracticeRequest(
    val scopeType: PracticeScopeType,
    val scopeId: Long? = null,
    val manual: Boolean = false,
    val dailyMinutes: Int = 5,
    val explanationLanguage: String = "Both",
    val focus: PracticeFocus = PracticeFocus.SMART,
)

data class PracticeItem(
    val concept: ConceptWithDetails,
    val exerciseType: ExerciseType,
    val skill: LearningSkill,
    val promptLanguage: PromptLanguage,
    val prompt: String,
    val expectedAnswers: List<String>,
    val options: List<String> = emptyList(),
    val scheduled: Boolean,
    val audioText: String? = null,
    val contextTranslation: String? = null,
    val rescueContent: RescueContent? = null,
    val expectedLemma: String? = null,
    val morphologyAnalysis: MorphologyAnalysis? = null,
    val persianNote: String? = null,
    val selfCheck: Boolean = false,
    val answerLanguage: PromptLanguage = promptLanguage,
) {
    val state: LearningStateEntity
        get() = concept.learningStates.firstOrNull { it.skill == skill.name }
            ?: LearningStateEntity(conceptId = concept.concept.id, skill = skill.name)
}

data class PracticePlan(
    val items: List<PracticeItem>,
    val estimatedMinutes: Int,
    val newConceptCount: Int,
)

class PracticeQueueBuilder(
    private val troubleWordDetector: TroubleWordDetector = TroubleWordDetector(),
    private val rescueContentProvider: RescueContentProvider = StoredRescueContentProvider(),
    private val morphologyService: FinnishMorphologyService = ManualFinnishMorphologyService(),
    private val soundPairService: FinnishSoundPairService = LocalFinnishSoundPairService(),
    private val shuffleConcepts: (List<ConceptWithDetails>) -> List<ConceptWithDetails> = { it.shuffled() },
    private val shuffleOptions: (List<String>) -> List<String> = { it.shuffled() },
) {
    fun build(
        concepts: List<ConceptWithDetails>,
        distractorPool: List<ConceptWithDetails>,
        request: PracticeRequest,
        averageLatencyMs: Long,
        now: Long,
        recentExercises: Map<Long, ExerciseType> = emptyMap(),
    ): PracticePlan {
        if (concepts.isEmpty()) return PracticePlan(emptyList(), 0, 0)
        val sessionMinutes = request.dailyMinutes.coerceIn(5, 20)
        val secondsPerExercise = averageLatencyMs.coerceIn(6_000, 25_000) / 1_000.0
        val capacity = (sessionMinutes * 60 / secondsPerExercise).roundToInt().coerceIn(8, 100)

        if (request.focus.isDedicatedTest) {
            val items = shuffleConcepts(
                concepts.sortedWith(compareByDescending<ConceptWithDetails>(::weaknessScore).thenBy { it.concept.id }),
            )
                .mapIndexedNotNull { index, concept ->
                    createDedicatedTestItem(
                        concept = concept,
                        request = request,
                        pool = distractorPool,
                        scheduled = !request.manual,
                        index = index,
                    )
                }
                .take(capacity)
            return PracticePlan(items, estimateMinutes(items.size, averageLatencyMs), 0)
        }

        if (request.manual) {
            val prioritized = if (request.focus == PracticeFocus.WEAK) {
                concepts.sortedByDescending(::weaknessScore)
            } else {
                concepts.sortedBy { it.concept.id }
            }
            val ordered = shuffleConcepts(prioritized)
            val items = if (request.focus == PracticeFocus.FORMS) {
                ordered.flatMapIndexed { index, concept ->
                    listOfNotNull(
                        createItem(concept, ExerciseType.FORM_RECOGNITION_LEMMA, request, distractorPool, false, index),
                        createItem(concept, ExerciseType.FORM_RECOGNITION_MEANING, request, distractorPool, false, index),
                        createItem(concept, ExerciseType.FORM_PRODUCTION, request, distractorPool, false, index),
                    )
                }
            } else {
                ordered.mapIndexedNotNull { index, concept ->
                    val type = manualExercise(concept, request.focus, index, recentExercises[concept.concept.id])
                    createItem(concept, type, request.withFocusLanguage(), distractorPool, false, index)
                }
            }.take(capacity)
            return PracticePlan(items, estimateMinutes(items.size, averageLatencyMs), 0)
        }

        val dueItems = mutableListOf<PracticeItem>()
        if (request.focus != PracticeFocus.LEARN_NEW) {
            concepts.sortedByDescending(::weaknessScore).forEachIndexed { index, concept ->
                val conceptDue = mutableListOf<PracticeItem>()
                fun addDue(skill: LearningSkill, type: ExerciseType) {
                    val state = concept.state(skill)
                    if (state.lastReview != null && state.nextReview.orZero() <= now) {
                        createItem(concept, type, request, distractorPool, true, index)?.let(conceptDue::add)
                    }
                }

                if (request.focus in setOf(PracticeFocus.SMART, PracticeFocus.REVIEW)) {
                    val meaning = concept.state(LearningSkill.MEANING_RECOGNITION)
                    val production = concept.state(LearningSkill.FINNISH_PRODUCTION)
                    addDue(
                        LearningSkill.MEANING_RECOGNITION,
                        if (meaning.successfulRecalls > 0 && meaning.successfulRecalls % 3 == 0) {
                            ExerciseType.REVERSE_RECOGNITION
                        } else {
                            ExerciseType.MEANING_RECOGNITION
                        },
                    )
                    addDue(
                        LearningSkill.FINNISH_PRODUCTION,
                        if (production.failedRecalls > production.successfulRecalls / 2 || production.recentHintUsage > 0) {
                            ExerciseType.FINNISH_PRODUCTION
                        } else {
                            ExerciseType.FREE_RECALL
                        },
                    )
                    addDue(LearningSkill.SPELLING, ExerciseType.SPELLING_RECALL)
                    addDue(
                        LearningSkill.LISTENING,
                        chooseListeningExercise(concept.state(LearningSkill.LISTENING), recentExercises[concept.concept.id]),
                    )
                    addDue(
                        LearningSkill.CONTEXT,
                        if (concept.state(LearningSkill.CONTEXT).successfulRecalls >= 2) ExerciseType.CLOZE_TYPE else ExerciseType.CLOZE_CHOICE,
                    )
                    val morphology = concept.state(LearningSkill.MORPHOLOGY)
                    addDue(
                        LearningSkill.MORPHOLOGY,
                        when {
                            morphology.successfulRecalls >= 3 -> ExerciseType.FORM_PRODUCTION
                            morphology.successfulRecalls % 2 == 1 -> ExerciseType.FORM_RECOGNITION_MEANING
                            else -> ExerciseType.FORM_RECOGNITION_LEMMA
                        },
                    )
                    addDue(LearningSkill.SPOKEN_RECOGNITION, ExerciseType.SPOKEN_RECOGNITION)
                }

                if (conceptDue.isNotEmpty() && troubleWordDetector.needsRescue(concept)) {
                    dueItems += rescueItem(concept, distractorPool)
                }
                dueItems += conceptDue
            }
        }

        val learned = concepts.filter { it.state(LearningSkill.MEANING_RECOGNITION).lastReview != null }
        val expansions = if (request.focus == PracticeFocus.SMART && dueItems.size < capacity) {
            learned.asSequence().flatMapIndexed { index, concept ->
                buildList {
                    if (concept.state(LearningSkill.FINNISH_PRODUCTION).lastReview == null) {
                        createItem(concept, ExerciseType.FINNISH_PRODUCTION, request, distractorPool, true, index)?.let(::add)
                    }
                    if (concept.state(LearningSkill.LISTENING).lastReview == null) {
                        createItem(
                            concept,
                            chooseListeningExercise(concept.state(LearningSkill.LISTENING), recentExercises[concept.concept.id]),
                            request,
                            distractorPool,
                            true,
                            index,
                        )?.let(::add)
                    }
                    if (concept.state(LearningSkill.SPELLING).lastReview == null) {
                        createItem(concept, ExerciseType.SPELLING_RECALL, request, distractorPool, true, index)?.let(::add)
                    }
                    if (concept.state(LearningSkill.CONTEXT).lastReview == null) {
                        createItem(concept, ExerciseType.CLOZE_CHOICE, request, distractorPool, true, index)?.let(::add)
                    }
                    if (
                        concept.forms.isNotEmpty() &&
                        concept.state(LearningSkill.MORPHOLOGY).lastReview == null &&
                        concept.state(LearningSkill.MEANING_RECOGNITION).successfulRecalls > 0
                    ) {
                        createItem(concept, ExerciseType.FORM_RECOGNITION_LEMMA, request, distractorPool, true, index)?.let(::add)
                    }
                    if (
                        !concept.concept.spokenFinnish.isNullOrBlank() &&
                        concept.state(LearningSkill.SPOKEN_RECOGNITION).lastReview == null
                    ) {
                        createItem(concept, ExerciseType.SPOKEN_RECOGNITION, request, distractorPool, true, index)?.let(::add)
                    }
                }.asSequence()
            }.take((capacity / 3).coerceAtLeast(1)).toList()
        } else {
            emptyList()
        }

        val newConcepts = if (request.focus == PracticeFocus.REVIEW) emptyList() else concepts.filter {
            it.state(LearningSkill.MEANING_RECOGNITION).lastReview == null
        }.sortedBy { it.concept.createdAt }
        val newLimit = if (dueItems.size >= capacity * 2 / 3) {
            0
        } else {
            (request.dailyMinutes / 5).coerceIn(1, 4)
                .coerceAtMost((capacity - dueItems.size - expansions.size).coerceAtLeast(0) / 6)
        }
        val selectedNew = newConcepts.take(newLimit)
        val newItems = selectedNew.flatMapIndexed { index, concept ->
            buildList {
                createItem(concept, ExerciseType.INTRODUCTION, request, distractorPool, true, index)?.let(::add)
                createItem(concept, ExerciseType.MEANING_RECOGNITION, request, distractorPool, true, index)?.let(::add)
                createItem(concept, ExerciseType.FINNISH_PRODUCTION, request, distractorPool, true, index)?.let(::add)
                createItem(concept, ExerciseType.LISTEN_CHOICE, request, distractorPool, true, index)?.let(::add)
                createItem(concept, ExerciseType.SPELLING_RECALL, request, distractorPool, true, index)?.let(::add)
                createItem(concept, ExerciseType.CLOZE_CHOICE, request, distractorPool, true, index)?.let(::add)
            }
        }
        val items = (dueItems + expansions + newItems).take(capacity)
        return PracticePlan(items, estimateMinutes(items.size, averageLatencyMs), selectedNew.size)
    }

    private fun manualExercise(
        concept: ConceptWithDetails,
        focus: PracticeFocus,
        index: Int,
        recent: ExerciseType?,
    ): ExerciseType = when (focus) {
        PracticeFocus.MEANING_PERSIAN, PracticeFocus.MEANING_ENGLISH -> ExerciseType.MEANING_RECOGNITION
        PracticeFocus.PRODUCTION_PERSIAN, PracticeFocus.PRODUCTION_ENGLISH -> ExerciseType.FINNISH_PRODUCTION
        PracticeFocus.LISTENING -> chooseListeningExercise(concept.state(LearningSkill.LISTENING), recent)
        PracticeFocus.CONTEXT -> if (concept.state(LearningSkill.CONTEXT).successfulRecalls >= 2) ExerciseType.CLOZE_TYPE else ExerciseType.CLOZE_CHOICE
        PracticeFocus.FORMS -> when (index % 3) {
            0 -> ExerciseType.FORM_RECOGNITION_LEMMA
            1 -> ExerciseType.FORM_RECOGNITION_MEANING
            else -> ExerciseType.FORM_PRODUCTION
        }
        PracticeFocus.SPOKEN -> ExerciseType.SPOKEN_RECOGNITION
        PracticeFocus.SELF_CHECK_MEANING,
        PracticeFocus.SELF_CHECK_PERSIAN,
        PracticeFocus.SELF_CHECK_ENGLISH,
        PracticeFocus.MULTIPLE_CHOICE_MEANING,
        PracticeFocus.MULTIPLE_CHOICE_PERSIAN,
        PracticeFocus.MULTIPLE_CHOICE_ENGLISH,
        PracticeFocus.SPELLING_PERSIAN,
        PracticeFocus.SPELLING_ENGLISH -> error("Dedicated test modes are built separately")
        else -> {
            val adaptive = when (weakestSkill(concept)) {
                LearningSkill.LISTENING -> chooseListeningExercise(concept.state(LearningSkill.LISTENING), recent)
                LearningSkill.SPELLING -> ExerciseType.SPELLING_RECALL
                LearningSkill.CONTEXT -> ExerciseType.CLOZE_CHOICE
                LearningSkill.MORPHOLOGY -> ExerciseType.FORM_PRODUCTION
                LearningSkill.SPOKEN_RECOGNITION -> ExerciseType.SPOKEN_RECOGNITION
                LearningSkill.FINNISH_PRODUCTION -> ExerciseType.FINNISH_PRODUCTION
                else -> if (index % 2 == 0) ExerciseType.MEANING_RECOGNITION else ExerciseType.FREE_RECALL
            }
            if (adaptive == recent) ExerciseType.FREE_RECALL else adaptive
        }
    }

    private fun createDedicatedTestItem(
        concept: ConceptWithDetails,
        request: PracticeRequest,
        pool: List<ConceptWithDetails>,
        scheduled: Boolean,
        index: Int,
    ): PracticeItem? {
        val explicitLanguage = when (request.focus) {
            PracticeFocus.SELF_CHECK_PERSIAN,
            PracticeFocus.MULTIPLE_CHOICE_PERSIAN,
            PracticeFocus.SPELLING_PERSIAN -> "فارسی"
            PracticeFocus.SELF_CHECK_ENGLISH,
            PracticeFocus.MULTIPLE_CHOICE_ENGLISH,
            PracticeFocus.SPELLING_ENGLISH -> "English"
            else -> request.explanationLanguage
        }
        if (explicitLanguage == "فارسی" && concept.concept.persianTranslations.isNullOrBlank()) return null
        if (explicitLanguage == "English" && concept.concept.englishTranslations.isNullOrBlank()) return null
        val directed = request.copy(explanationLanguage = explicitLanguage)
        val base = when (request.focus) {
            PracticeFocus.SELF_CHECK_MEANING ->
                createItem(concept, ExerciseType.MEANING_RECOGNITION, directed, pool, scheduled, index)?.copy(selfCheck = true, options = emptyList())
            PracticeFocus.SELF_CHECK_PERSIAN, PracticeFocus.SELF_CHECK_ENGLISH ->
                createItem(concept, ExerciseType.FREE_RECALL, directed, pool, scheduled, index)?.copy(selfCheck = true)
            PracticeFocus.MULTIPLE_CHOICE_MEANING ->
                createItem(concept, ExerciseType.MEANING_RECOGNITION, directed, pool, scheduled, index)
            PracticeFocus.MULTIPLE_CHOICE_PERSIAN, PracticeFocus.MULTIPLE_CHOICE_ENGLISH ->
                createItem(concept, ExerciseType.REVERSE_RECOGNITION, directed, pool, scheduled, index)
            PracticeFocus.SPELLING_PERSIAN, PracticeFocus.SPELLING_ENGLISH ->
                createItem(concept, ExerciseType.SPELLING_RECALL, directed, pool, scheduled, index)
            else -> null
        } ?: return null
        if (base.expectedAnswers.none { it.isNotBlank() }) return null
        if (request.focus in multipleChoiceFocuses && base.options.distinct().size < 2) return null
        return base
    }

    private fun chooseListeningExercise(state: LearningStateEntity, recent: ExerciseType?): ExerciseType {
        val preferred = when {
            state.failedRecalls > state.successfulRecalls -> ExerciseType.LISTEN_CHOICE
            state.recentHintUsage >= 2 -> ExerciseType.LISTEN_MEANING
            state.successfulRecalls >= 2 -> ExerciseType.LISTEN_TYPE
            else -> ExerciseType.LISTEN_CHOICE
        }
        if (preferred != recent) return preferred
        return when (preferred) {
            ExerciseType.LISTEN_CHOICE -> ExerciseType.LISTEN_MEANING
            ExerciseType.LISTEN_MEANING -> ExerciseType.LISTEN_TYPE
            else -> ExerciseType.LISTEN_CHOICE
        }
    }

    private fun createItem(
        concept: ConceptWithDetails,
        type: ExerciseType,
        request: PracticeRequest,
        pool: List<ConceptWithDetails>,
        scheduled: Boolean,
        index: Int,
    ): PracticeItem? {
        val language = chooseLanguage(concept, request.explanationLanguage, index)
        val translation = translation(concept, language)
        val translationAnswers = splitAcceptedAnswers(translation)
        val finnish = concept.concept.finnishLemma
        return when (type) {
            ExerciseType.INTRODUCTION -> PracticeItem(
                concept, type, LearningSkill.MEANING_RECOGNITION, PromptLanguage.FINNISH, finnish,
                translationAnswers, scheduled = scheduled, audioText = finnish, answerLanguage = language,
            )
            ExerciseType.MEANING_RECOGNITION -> PracticeItem(
                concept, type, LearningSkill.MEANING_RECOGNITION, PromptLanguage.FINNISH, finnish,
                translationAnswers, translationOptions(concept, language, pool), scheduled, audioText = finnish,
                answerLanguage = language,
            )
            ExerciseType.FINNISH_PRODUCTION -> PracticeItem(concept, type, LearningSkill.FINNISH_PRODUCTION, language, translation, listOf(finnish), scheduled = scheduled)
            ExerciseType.REVERSE_RECOGNITION -> PracticeItem(concept, type, LearningSkill.FINNISH_PRODUCTION, language, translation, listOf(finnish), finnishOptions(concept, pool), scheduled)
            ExerciseType.FREE_RECALL -> PracticeItem(concept, type, LearningSkill.FINNISH_PRODUCTION, language, translation, listOf(finnish), scheduled = scheduled)
            ExerciseType.SPELLING_RECALL -> PracticeItem(concept, type, LearningSkill.SPELLING, language, translation, listOf(finnish), scheduled = scheduled, audioText = finnish)
            ExerciseType.LISTEN_MEANING -> PracticeItem(concept, type, LearningSkill.LISTENING, language, "", translationAnswers, translationOptions(concept, language, pool), scheduled, audioText = finnish)
            ExerciseType.LISTEN_TYPE -> PracticeItem(concept, type, LearningSkill.LISTENING, PromptLanguage.FINNISH, "", listOf(finnish), scheduled = scheduled, audioText = finnish)
            ExerciseType.LISTEN_CHOICE -> {
                val pair = if (!scheduled || concept.state(LearningSkill.LISTENING).successfulRecalls > 0) {
                    soundPairService.findPair(concept, pool)
                } else {
                    null
                }
                if (pair == null) {
                    PracticeItem(concept, type, LearningSkill.LISTENING, PromptLanguage.FINNISH, "", listOf(finnish), finnishOptions(concept, pool), scheduled, audioText = finnish)
                } else {
                    PracticeItem(
                        concept,
                        ExerciseType.MINIMAL_PAIR_LISTENING,
                        LearningSkill.LISTENING,
                        PromptLanguage.FINNISH,
                        "",
                        listOf(pair.target),
                        listOf(pair.target, pair.contrast).let { if (concept.concept.id % 2L == 0L) it.reversed() else it },
                        scheduled,
                        audioText = pair.target,
                    )
                }
            }
            ExerciseType.CLOZE_CHOICE, ExerciseType.CLOZE_TYPE -> contextItem(concept, type, language, pool, scheduled)
            ExerciseType.RESCUE -> rescueItem(concept, pool)
            ExerciseType.FORM_RECOGNITION_LEMMA,
            ExerciseType.FORM_RECOGNITION_MEANING,
            ExerciseType.FORM_PRODUCTION -> formItem(concept, type, language, pool, scheduled)
            ExerciseType.SPOKEN_RECOGNITION -> spokenItem(concept, pool, scheduled)
            ExerciseType.MINIMAL_PAIR_LISTENING -> null
        }
    }

    private fun formItem(
        concept: ConceptWithDetails,
        type: ExerciseType,
        language: PromptLanguage,
        pool: List<ConceptWithDetails>,
        scheduled: Boolean,
    ): PracticeItem? {
        val state = concept.state(LearningSkill.MORPHOLOGY)
        val unlocked = morphologyService.unlockedAnalyses(concept, state)
        if (unlocked.isEmpty()) return null
        val analysis = unlocked[(state.successfulRecalls + state.failedRecalls) % unlocked.size]
        val meaning = formMeaning(analysis, concept, language)
        val note = analysis.persianNote.takeIf { language == PromptLanguage.PERSIAN }
        return when (type) {
            ExerciseType.FORM_RECOGNITION_LEMMA -> PracticeItem(
                concept = concept,
                exerciseType = type,
                skill = LearningSkill.MORPHOLOGY,
                promptLanguage = PromptLanguage.FINNISH,
                prompt = analysis.surface,
                expectedAnswers = listOf(analysis.lemma),
                options = finnishOptions(concept, pool),
                scheduled = scheduled,
                audioText = analysis.surface,
                morphologyAnalysis = analysis,
                persianNote = note,
            )
            ExerciseType.FORM_RECOGNITION_MEANING -> PracticeItem(
                concept = concept,
                exerciseType = type,
                skill = LearningSkill.MORPHOLOGY,
                promptLanguage = PromptLanguage.FINNISH,
                prompt = analysis.surface,
                expectedAnswers = splitAcceptedAnswers(meaning),
                options = formMeaningOptions(analysis, concept, language, pool),
                scheduled = scheduled,
                audioText = analysis.surface,
                morphologyAnalysis = analysis,
                persianNote = note,
            )
            ExerciseType.FORM_PRODUCTION -> PracticeItem(
                concept = concept,
                exerciseType = type,
                skill = LearningSkill.MORPHOLOGY,
                promptLanguage = language,
                prompt = meaning,
                expectedAnswers = listOf(analysis.surface),
                scheduled = scheduled,
                audioText = analysis.surface,
                expectedLemma = analysis.lemma,
                morphologyAnalysis = analysis,
                persianNote = note,
            )
            else -> null
        }
    }

    private fun spokenItem(
        concept: ConceptWithDetails,
        pool: List<ConceptWithDetails>,
        scheduled: Boolean,
    ): PracticeItem? {
        val spoken = concept.concept.spokenFinnish?.takeIf { it.isNotBlank() } ?: return null
        return PracticeItem(
            concept = concept,
            exerciseType = ExerciseType.SPOKEN_RECOGNITION,
            skill = LearningSkill.SPOKEN_RECOGNITION,
            promptLanguage = PromptLanguage.FINNISH,
            prompt = spoken,
            expectedAnswers = listOf(concept.concept.finnishLemma),
            options = finnishOptions(concept, pool),
            scheduled = scheduled,
            audioText = spoken,
        )
    }

    private fun contextItem(
        concept: ConceptWithDetails,
        type: ExerciseType,
        language: PromptLanguage,
        pool: List<ConceptWithDetails>,
        scheduled: Boolean,
    ): PracticeItem? {
        val cloze = concept.examples.asSequence().mapNotNull { buildCloze(concept, it, language) }.firstOrNull() ?: return null
        val options = if (type == ExerciseType.CLOZE_CHOICE) {
            positioned(
                cloze.answer,
                (listOf(concept.concept.finnishLemma) + concept.forms.map { it.form } + pool.flatMap { candidate ->
                    candidate.forms.take(1).map { form -> form.form }.ifEmpty { listOf(candidate.concept.finnishLemma) }
                })
                    .filter { !it.equals(cloze.answer, true) }
                    .distinct()
                    .take(3),
                concept.concept.id,
            )
        } else {
            emptyList()
        }
        val isInflectedTarget = !cloze.answer.equals(concept.concept.finnishLemma, ignoreCase = true) &&
            morphologyService.analyze(cloze.answer, concept) != null
        return PracticeItem(
            concept = concept,
            exerciseType = type,
            skill = if (isInflectedTarget) LearningSkill.MORPHOLOGY else LearningSkill.CONTEXT,
            promptLanguage = PromptLanguage.FINNISH,
            prompt = cloze.sentence,
            expectedAnswers = listOf(cloze.answer),
            options = options,
            scheduled = scheduled,
            audioText = cloze.originalSentence,
            contextTranslation = cloze.translation,
            expectedLemma = concept.concept.finnishLemma.takeIf { isInflectedTarget },
            morphologyAnalysis = morphologyService.analyze(cloze.answer, concept),
        )
    }

    private fun buildCloze(
        concept: ConceptWithDetails,
        example: ExampleSentenceEntity,
        language: PromptLanguage,
    ): Cloze? {
        val sentence = example.finnishSentence
        val explicit = example.targetWordOrForm?.takeIf { sentence.contains(it, ignoreCase = true) }
        val known = (concept.forms.map { it.form } + concept.concept.finnishLemma)
            .sortedByDescending { it.length }
            .firstOrNull { sentence.contains(it, ignoreCase = true) }
        val answer = explicit ?: known ?: return null
        val blanked = sentence.replace(Regex(Regex.escape(answer), RegexOption.IGNORE_CASE), "______")
        val translation = when (language) {
            PromptLanguage.PERSIAN -> example.persianTranslation ?: example.englishTranslation
            PromptLanguage.ENGLISH -> example.englishTranslation ?: example.persianTranslation
            PromptLanguage.FINNISH -> null
        }
        return Cloze(blanked, answer, sentence, translation)
    }

    private fun rescueItem(concept: ConceptWithDetails, pool: List<ConceptWithDetails>): PracticeItem {
        val skill = weakestSkill(concept)
        return PracticeItem(
            concept = concept,
            exerciseType = ExerciseType.RESCUE,
            skill = skill,
            promptLanguage = PromptLanguage.FINNISH,
            prompt = concept.concept.finnishLemma,
            expectedAnswers = emptyList(),
            scheduled = false,
            audioText = concept.concept.finnishLemma,
            rescueContent = rescueContentProvider.content(concept, pool),
        )
    }

    private fun chooseLanguage(concept: ConceptWithDetails, preference: String, index: Int): PromptLanguage {
        val hasPersian = !concept.concept.persianTranslations.isNullOrBlank()
        val hasEnglish = !concept.concept.englishTranslations.isNullOrBlank()
        return when {
            preference == "فارسی" && hasPersian -> PromptLanguage.PERSIAN
            preference == "English" && hasEnglish -> PromptLanguage.ENGLISH
            preference == "Both" && index % 2 == 0 && hasPersian -> PromptLanguage.PERSIAN
            hasEnglish -> PromptLanguage.ENGLISH
            else -> PromptLanguage.PERSIAN
        }
    }

    private fun translation(concept: ConceptWithDetails, language: PromptLanguage): String = when (language) {
        PromptLanguage.PERSIAN -> concept.concept.persianTranslations ?: concept.concept.englishTranslations.orEmpty()
        PromptLanguage.ENGLISH -> concept.concept.englishTranslations ?: concept.concept.persianTranslations.orEmpty()
        PromptLanguage.FINNISH -> concept.concept.finnishLemma
    }

    private fun translationOptions(target: ConceptWithDetails, language: PromptLanguage, pool: List<ConceptWithDetails>): List<String> {
        val expected = splitAcceptedAnswers(translation(target, language)).firstOrNull().orEmpty()
        val distractors = pool.asSequence()
            .filter { it.concept.id != target.concept.id }
            .map { splitAcceptedAnswers(translation(it, language)).firstOrNull().orEmpty() }
            .filter { it.isNotBlank() && it != expected }
            .distinct()
            .take(3)
            .toList()
        return positioned(expected, distractors, target.concept.id)
    }

    private fun formMeaning(
        analysis: MorphologyAnalysis,
        concept: ConceptWithDetails,
        language: PromptLanguage,
    ): String = when (language) {
        PromptLanguage.PERSIAN -> analysis.persianMeaning
            ?: analysis.grammaticalLabel
            ?: concept.concept.persianTranslations
            ?: analysis.englishMeaning
            ?: concept.concept.englishTranslations.orEmpty()
        PromptLanguage.ENGLISH -> analysis.englishMeaning
            ?: analysis.grammaticalLabel
            ?: concept.concept.englishTranslations
            ?: analysis.persianMeaning
            ?: concept.concept.persianTranslations.orEmpty()
        PromptLanguage.FINNISH -> analysis.grammaticalLabel ?: analysis.surface
    }

    private fun formMeaningOptions(
        target: MorphologyAnalysis,
        concept: ConceptWithDetails,
        language: PromptLanguage,
        pool: List<ConceptWithDetails>,
    ): List<String> {
        val expected = splitAcceptedAnswers(formMeaning(target, concept, language)).firstOrNull().orEmpty()
        val distractors = (morphologyService.analyses(concept).filter { it.formId != target.formId }.map {
            splitAcceptedAnswers(formMeaning(it, concept, language)).firstOrNull().orEmpty()
        } + pool.flatMap { candidate ->
            morphologyService.analyses(candidate).take(1).map {
                splitAcceptedAnswers(formMeaning(it, candidate, language)).firstOrNull().orEmpty()
            }
        }).filter { it.isNotBlank() && it != expected }.distinct().take(3)
        return positioned(expected, distractors, concept.concept.id + target.formId)
    }

    private fun finnishOptions(target: ConceptWithDetails, pool: List<ConceptWithDetails>): List<String> {
        val expected = target.concept.finnishLemma
        val distractors = pool.asSequence()
            .filter { it.concept.id != target.concept.id }
            .map { it.concept.finnishLemma }
            .filter { it != expected }
            .distinct()
            .take(3)
            .toList()
        return positioned(expected, distractors, target.concept.id)
    }

    private fun positioned(expected: String, distractors: List<String>, seed: Long): List<String> {
        val result = distractors.toMutableList()
        result.add((seed % (result.size + 1)).toInt(), expected)
        return shuffleOptions(result)
    }

    private fun weakestSkill(concept: ConceptWithDetails): LearningSkill = listOf(
        LearningSkill.LISTENING,
        LearningSkill.SPELLING,
        LearningSkill.FINNISH_PRODUCTION,
        LearningSkill.CONTEXT,
        LearningSkill.MORPHOLOGY,
        LearningSkill.SPOKEN_RECOGNITION,
        LearningSkill.MEANING_RECOGNITION,
    ).maxByOrNull { skillWeakness(concept.state(it)) } ?: LearningSkill.MEANING_RECOGNITION

    private fun weaknessScore(concept: ConceptWithDetails): Int =
        concept.learningStates.sumOf { skillWeakness(it) } + if (troubleWordDetector.needsRescue(concept)) 20 else 0

    private fun skillWeakness(state: LearningStateEntity): Int =
        state.failedRecalls * 4 + state.lapses * 3 + state.recentHintUsage * 2 - state.successfulRecalls

    private fun estimateMinutes(count: Int, latencyMs: Long): Int =
        ((count * latencyMs) / 60_000.0).roundToInt().coerceAtLeast(if (count > 0) 1 else 0)

    private fun ConceptWithDetails.state(skill: LearningSkill): LearningStateEntity =
        learningStates.firstOrNull { it.skill == skill.name }
            ?: LearningStateEntity(conceptId = concept.id, skill = skill.name)

    private fun Long?.orZero(): Long = this ?: 0L

    private fun PracticeRequest.withFocusLanguage(): PracticeRequest = when (focus) {
        PracticeFocus.MEANING_PERSIAN, PracticeFocus.PRODUCTION_PERSIAN -> copy(explanationLanguage = "فارسی")
        PracticeFocus.MEANING_ENGLISH, PracticeFocus.PRODUCTION_ENGLISH -> copy(explanationLanguage = "English")
        else -> this
    }

    private val PracticeFocus.isDedicatedTest: Boolean
        get() = this in dedicatedTestFocuses

    private data class Cloze(
        val sentence: String,
        val answer: String,
        val originalSentence: String,
        val translation: String?,
    )
}

private val dedicatedTestFocuses = setOf(
    PracticeFocus.SELF_CHECK_MEANING,
    PracticeFocus.SELF_CHECK_PERSIAN,
    PracticeFocus.SELF_CHECK_ENGLISH,
    PracticeFocus.MULTIPLE_CHOICE_MEANING,
    PracticeFocus.MULTIPLE_CHOICE_PERSIAN,
    PracticeFocus.MULTIPLE_CHOICE_ENGLISH,
    PracticeFocus.SPELLING_PERSIAN,
    PracticeFocus.SPELLING_ENGLISH,
)

private val multipleChoiceFocuses = setOf(
    PracticeFocus.MULTIPLE_CHOICE_MEANING,
    PracticeFocus.MULTIPLE_CHOICE_PERSIAN,
    PracticeFocus.MULTIPLE_CHOICE_ENGLISH,
)
