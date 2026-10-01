package com.wordiq.app.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.intl.LocaleList
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.wordiq.app.PracticeFeedback
import com.wordiq.app.PracticeUiState
import com.wordiq.app.learning.AnswerOutcome
import com.wordiq.app.learning.ExerciseType
import com.wordiq.app.learning.PracticeItem
import com.wordiq.app.learning.PromptLanguage
import com.wordiq.app.learning.RescueContent
import com.wordiq.app.ui.components.AppCard
import com.wordiq.app.ui.components.ScreenPadding
import com.wordiq.app.ui.components.motionEnabled
import com.wordiq.app.ui.theme.BluePrimary
import com.wordiq.app.ui.theme.BlueSoft
import com.wordiq.app.ui.theme.ErrorSoft
import com.wordiq.app.ui.theme.SuccessSoft
import com.wordiq.app.ui.theme.TextSecondary
import kotlinx.coroutines.delay

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun PracticeScreen(
    title: String,
    state: PracticeUiState,
    hapticsEnabled: Boolean,
    autoPronounceEnabled: Boolean,
    emptyMessage: String?,
    onClose: () -> Unit,
    onAnswerChange: (String) -> Unit,
    onSelectOption: (String) -> Unit,
    onSubmit: () -> Unit,
    onRemembered: () -> Unit,
    onRevealSelfCheck: () -> Unit,
    onReveal: () -> Unit,
    onHint: () -> Unit,
    onContinueIntroduction: () -> Unit,
    onNext: () -> Unit,
    onRetry: () -> Unit,
    onStudyAgain: () -> Unit,
    onPlayAudio: (String) -> Unit,
) {
    val haptics = LocalHapticFeedback.current
    val density = LocalDensity.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val keyboardVisible = WindowInsets.ime.getBottom(density) > 0
    val compactTypedLayout = keyboardVisible && state.current?.exerciseType in typedExercises
    val animateMotion = motionEnabled()
    LaunchedEffect(state.feedback?.grade?.outcome) {
        if (!hapticsEnabled) return@LaunchedEffect
        when (state.feedback?.grade?.outcome) {
            AnswerOutcome.EXACT, AnswerOutcome.TYPO, AnswerOutcome.PARTIAL ->
                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            AnswerOutcome.WRONG, AnswerOutcome.REVEALED ->
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            null -> Unit
        }
    }
    LaunchedEffect(state.complete) {
        if (hapticsEnabled && state.complete && state.items.isNotEmpty()) {
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        }
    }
    val automaticAudio = automaticPronunciationText(state)
    LaunchedEffect(autoPronounceEnabled, state.index, automaticAudio) {
        if (autoPronounceEnabled && !automaticAudio.isNullOrBlank()) onPlayAudio(automaticAudio)
    }
    LaunchedEffect(state.index, state.selfCheckRevealed, state.saving, state.error) {
        if (shouldAutoAdvanceSelfCheck(state)) {
            delay(SELF_CHECK_REVEAL_MS)
            onNext()
        }
    }
    LaunchedEffect(state.index, state.feedback, state.saving, state.error) {
        if (shouldAutoAdvanceCorrectAnswer(state)) {
            delay(CORRECT_ANSWER_REVEAL_MS)
            onNext()
        }
    }
    LaunchedEffect(state.feedback?.grade?.outcome) {
        val outcome = state.feedback?.grade?.outcome
        if (outcome != null && outcome != AnswerOutcome.EXACT) keyboardController?.hide()
    }

    Scaffold { padding ->
        Column(Modifier.fillMaxSize().padding(padding).imePadding()) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = if (compactTypedLayout) 0.dp else 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onClose) { Icon(Icons.Default.Close, contentDescription = "Close practice") }
                if (compactTypedLayout) {
                    Spacer(Modifier.weight(1f))
                } else {
                    Text(
                        title,
                        color = TextSecondary,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.padding(24.dp))
            }
            LinearProgressIndicator(
                progress = { state.progress },
                modifier = Modifier.fillMaxWidth().height(3.dp),
                color = BluePrimary,
                trackColor = BlueSoft,
                gapSize = 0.dp,
                drawStopIndicator = {},
            )
            when {
                state.loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                state.error != null && state.current == null ->
                    MessageState("Couldn’t continue", "Check the words in this mode and try again.", "Try again", onRetry)
                state.empty -> EmptyPractice(emptyMessage, onStudyAgain, onClose)
                state.complete -> CompletionState(state.strengthened, state.needsWork, onStudyAgain, onClose)
                else -> AnimatedContent(
                    targetState = state.index,
                    transitionSpec = {
                        if (animateMotion) {
                            (slideInHorizontally(animationSpec = spring(dampingRatio = 1f, stiffness = 700f)) { it / 7 } + fadeIn())
                                .togetherWith(slideOutHorizontally { -it / 10 } + fadeOut())
                        } else {
                            fadeIn().togetherWith(fadeOut())
                        }
                    },
                    label = "practiceCard",
                    modifier = Modifier.weight(1f),
                ) { targetIndex ->
                    ExerciseContent(
                        state = state.copy(index = targetIndex),
                        compactKeyboard = compactTypedLayout,
                        onAnswerChange = onAnswerChange,
                        onSelectOption = onSelectOption,
                        onSubmit = onSubmit,
                        onRemembered = onRemembered,
                        onRevealSelfCheck = onRevealSelfCheck,
                        onReveal = onReveal,
                        onHint = onHint,
                        onContinueIntroduction = onContinueIntroduction,
                        onNext = onNext,
                        onRetry = onRetry,
                        onPlayAudio = onPlayAudio,
                    )
                }
            }
        }
    }
}

@Composable
private fun ExerciseContent(
    state: PracticeUiState,
    compactKeyboard: Boolean,
    onAnswerChange: (String) -> Unit,
    onSelectOption: (String) -> Unit,
    onSubmit: () -> Unit,
    onRemembered: () -> Unit,
    onRevealSelfCheck: () -> Unit,
    onReveal: () -> Unit,
    onHint: () -> Unit,
    onContinueIntroduction: () -> Unit,
    onNext: () -> Unit,
    onRetry: () -> Unit,
    onPlayAudio: (String) -> Unit,
) {
    val item = state.current ?: return
    Column(
        Modifier.fillMaxSize().padding(
            horizontal = ScreenPadding,
            vertical = if (compactKeyboard) 8.dp else ScreenPadding,
        ),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (!compactKeyboard) {
            Text(if (item.selfCheck) "Self-check" else exerciseLabel(item.exerciseType), color = TextSecondary, style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(20.dp))
        }
        AppCard(modifier = Modifier.weight(1f)) {
            Column(
                Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                if (item.selfCheck) {
                    SelfCheckExercise(item, state, onRevealSelfCheck)
                } else when (item.exerciseType) {
                    ExerciseType.INTRODUCTION -> IntroductionExercise(item, onPlayAudio)
                    ExerciseType.MEANING_RECOGNITION, ExerciseType.REVERSE_RECOGNITION ->
                        ChoiceExercise(item, state.feedback, onSelectOption, optionLanguage(item))
                    ExerciseType.FINNISH_PRODUCTION, ExerciseType.SPELLING_RECALL ->
                        TypedExercise(item, state, playAudio = onPlayAudio, compact = compactKeyboard)
                    ExerciseType.FREE_RECALL -> FreeRecallExercise(item, state.feedback, onRemembered, onReveal)
                    ExerciseType.LISTEN_MEANING, ExerciseType.LISTEN_CHOICE ->
                        ListeningChoiceExercise(item, state.feedback, onSelectOption, onPlayAudio)
                    ExerciseType.LISTEN_TYPE ->
                        ListeningTypeExercise(item, onPlayAudio)
                    ExerciseType.CLOZE_CHOICE ->
                        ClozeChoiceExercise(item, state.feedback, onSelectOption, onPlayAudio)
                    ExerciseType.CLOZE_TYPE ->
                        TypedExercise(item, state, playAudio = onPlayAudio, context = true, compact = compactKeyboard)
                    ExerciseType.RESCUE -> RescueExercise(item.rescueContent, item.audioText, onPlayAudio)
                    ExerciseType.FORM_RECOGNITION_LEMMA, ExerciseType.FORM_RECOGNITION_MEANING,
                    ExerciseType.SPOKEN_RECOGNITION ->
                        FormChoiceExercise(item, state.feedback, onSelectOption, onPlayAudio)
                    ExerciseType.FORM_PRODUCTION ->
                        TypedExercise(item, state, playAudio = onPlayAudio, compact = compactKeyboard)
                    ExerciseType.MINIMAL_PAIR_LISTENING ->
                        ListeningChoiceExercise(item, state.feedback, onSelectOption, onPlayAudio)
                }
            }
        }
        if (item.exerciseType in typedExercises) {
            Spacer(Modifier.height(if (compactKeyboard) 6.dp else 14.dp))
            AnswerField(item, state, onAnswerChange, onSubmit)
        }
        state.hint?.let {
            Text(it, style = MaterialTheme.typography.titleLarge, color = BluePrimary, modifier = Modifier.padding(top = 14.dp))
        }
        state.feedback?.let { feedback ->
            if (!item.selfCheck && item.options.isEmpty()) FeedbackPanel(feedback)
            item.persianNote?.let { SecondaryDirectionalText(it) }
        }
        state.error?.let { error ->
            Surface(color = ErrorSoft, shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth().padding(top = 14.dp)) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(error, modifier = Modifier.weight(1f))
                    TextButton(onClick = onRetry, enabled = !state.saving) { Text("Try again") }
                }
            }
        }
        Spacer(Modifier.height(14.dp))
        when {
            state.feedback != null && !item.selfCheck && !isAutoAdvanceCandidate(state) -> Button(
                onClick = onNext,
                enabled = !state.saving && state.error == null,
                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                shape = RoundedCornerShape(18.dp),
            ) { Text(if (state.saving) "Saving…" else "Continue") }
            item.exerciseType in setOf(ExerciseType.INTRODUCTION, ExerciseType.RESCUE) -> Button(
                onClick = onContinueIntroduction,
                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                shape = RoundedCornerShape(18.dp),
            ) { Text("Continue") }
            item.exerciseType in typedExercises -> Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                OutlinedButton(onClick = onHint, modifier = Modifier.heightIn(min = if (compactKeyboard) 48.dp else 56.dp)) {
                    Icon(Icons.Default.Lightbulb, contentDescription = null)
                    Text(if (state.hintDepth >= 3) "Reveal" else "Hint")
                }
                Button(
                    onClick = onSubmit,
                    enabled = state.answer.isNotBlank(),
                    modifier = Modifier.weight(1f).heightIn(min = if (compactKeyboard) 48.dp else 56.dp),
                    shape = RoundedCornerShape(18.dp),
                ) { Text("Check") }
            }
            item.selfCheck || item.exerciseType == ExerciseType.FREE_RECALL -> Unit
            else -> TextButton(onClick = onReveal) { Text("Reveal") }
        }
    }
}

@Composable
private fun SelfCheckExercise(
    item: PracticeItem,
    state: PracticeUiState,
    onReveal: () -> Unit,
) {
    PromptText(item.prompt, item.promptLanguage)
    if (!state.selfCheckRevealed && state.feedback == null) {
        OutlinedButton(
            onClick = onReveal,
            modifier = Modifier.padding(top = 24.dp).heightIn(min = 52.dp),
        ) {
            Icon(Icons.Default.Visibility, contentDescription = null)
            Text("View definition", modifier = Modifier.padding(start = 8.dp))
        }
    }

    if (state.selfCheckRevealed || state.feedback != null) {
        Surface(
            color = when (state.feedback?.grade?.outcome) {
                AnswerOutcome.EXACT, AnswerOutcome.TYPO, AnswerOutcome.PARTIAL -> SuccessSoft
                AnswerOutcome.WRONG, AnswerOutcome.REVEALED -> ErrorSoft
                null -> BlueSoft
            },
            shape = RoundedCornerShape(18.dp),
            modifier = Modifier.fillMaxWidth().padding(top = 24.dp),
        ) {
            Column(Modifier.padding(20.dp)) {
                PromptText(
                    item.expectedAnswers.joinToString(" · "),
                    if (item.promptLanguage == PromptLanguage.FINNISH) item.answerLanguage else PromptLanguage.FINNISH,
                    MaterialTheme.typography.headlineMedium,
                )
            }
        }
    }
}

@Composable
private fun IntroductionExercise(item: PracticeItem, onPlayAudio: (String) -> Unit) {
    val concept = item.concept.concept
    Text(concept.finnishLemma, style = MaterialTheme.typography.displaySmall, textAlign = TextAlign.Center)
    item.audioText?.let { AudioButton { onPlayAudio(it) } }
    concept.englishTranslations?.let {
        Text(it, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 16.dp), textAlign = TextAlign.Center)
    }
    concept.persianTranslations?.let {
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
            Text(it, style = MaterialTheme.typography.titleLarge, modifier = Modifier.fillMaxWidth().padding(top = 10.dp), textAlign = TextAlign.Center)
        }
    }
    item.concept.examples.firstOrNull()?.let {
        Text(it.finnishSentence, color = TextSecondary, modifier = Modifier.padding(top = 20.dp), textAlign = TextAlign.Center)
    }
}

@Composable
private fun ChoiceExercise(
    item: PracticeItem,
    feedback: PracticeFeedback?,
    onSelect: (String) -> Unit,
    language: PromptLanguage,
) {
    if (item.prompt.isNotBlank()) PromptText(item.prompt, item.promptLanguage)
    Spacer(Modifier.height(24.dp))
    ChoiceOptions(item, feedback, onSelect, language)
}

@Composable
private fun ListeningChoiceExercise(
    item: PracticeItem,
    feedback: PracticeFeedback?,
    onSelect: (String) -> Unit,
    onPlayAudio: (String) -> Unit,
) {
    Text("Listen and choose", color = TextSecondary)
    item.audioText?.let { AudioButton(large = true) { onPlayAudio(it) } }
    Spacer(Modifier.height(20.dp))
    ChoiceOptions(item, feedback, onSelect, optionLanguage(item))
}

@Composable
private fun FormChoiceExercise(
    item: PracticeItem,
    feedback: PracticeFeedback?,
    onSelect: (String) -> Unit,
    onPlayAudio: (String) -> Unit,
) {
    PromptText(item.prompt, PromptLanguage.FINNISH)
    item.audioText?.let { AudioButton { onPlayAudio(it) } }
    Text(
        when (item.exerciseType) {
            ExerciseType.FORM_RECOGNITION_LEMMA -> "What basic word is this?"
            ExerciseType.FORM_RECOGNITION_MEANING -> "What does it mean here?"
            ExerciseType.SPOKEN_RECOGNITION -> "Standard Finnish"
            else -> "Choose"
        },
        color = TextSecondary,
        modifier = Modifier.padding(top = 4.dp),
    )
    Spacer(Modifier.height(18.dp))
    ChoiceOptions(item, feedback, onSelect, optionLanguage(item))
}

@Composable
private fun ListeningTypeExercise(
    item: PracticeItem,
    onPlayAudio: (String) -> Unit,
) {
    Text("Type what you hear", color = TextSecondary)
    item.audioText?.let { AudioButton(large = true) { onPlayAudio(it) } }
}

@Composable
private fun ClozeChoiceExercise(
    item: PracticeItem,
    feedback: PracticeFeedback?,
    onSelect: (String) -> Unit,
    onPlayAudio: (String) -> Unit,
) {
    PromptText(item.prompt, PromptLanguage.FINNISH)
    item.contextTranslation?.let { SecondaryDirectionalText(it) }
    Spacer(Modifier.height(18.dp))
    ChoiceOptions(item, feedback, onSelect, PromptLanguage.FINNISH)
    item.audioText?.let { fullSentence ->
        if (feedback != null) AudioButton { onPlayAudio(fullSentence) }
    }
}

@Composable
private fun ChoiceOptions(
    item: PracticeItem,
    feedback: PracticeFeedback?,
    onSelect: (String) -> Unit,
    language: PromptLanguage,
) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item.options.forEach { option ->
            OutlinedButton(
                onClick = { onSelect(option) },
                enabled = feedback == null,
                modifier = Modifier.fillMaxWidth().heightIn(min = 54.dp),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(
                    2.dp,
                    when {
                        feedback != null && option in item.expectedAnswers -> CorrectBorder
                        feedback?.selectedAnswer == option -> WrongBorder
                        else -> MaterialTheme.colorScheme.outline
                    },
                ),
                colors = ButtonDefaults.outlinedButtonColors(
                    containerColor = when {
                        feedback != null && option in item.expectedAnswers -> SuccessSoft
                        feedback?.selectedAnswer == option -> ErrorSoft
                        else -> MaterialTheme.colorScheme.surface
                    },
                ),
            ) { PromptText(option, language, MaterialTheme.typography.titleMedium) }
        }
    }
}

@Composable
private fun TypedExercise(
    item: PracticeItem,
    state: PracticeUiState,
    playAudio: (String) -> Unit,
    context: Boolean = false,
    compact: Boolean = false,
) {
    PromptText(
        item.prompt,
        item.promptLanguage,
        if (compact) MaterialTheme.typography.headlineMedium else MaterialTheme.typography.headlineLarge,
    )
    if (context) item.contextTranslation?.let { SecondaryDirectionalText(it) }
    if (!context && item.exerciseType == ExerciseType.SPELLING_RECALL) {
        item.audioText?.let { AudioButton { playAudio(it) } }
    }
    if (context && state.feedback != null) item.audioText?.let { AudioButton { playAudio(it) } }
}

@Composable
private fun AnswerField(
    item: PracticeItem,
    state: PracticeUiState,
    onAnswerChange: (String) -> Unit,
    onSubmit: () -> Unit,
) {
    val requester = remember { FocusRequester() }
    OutlinedTextField(
        value = state.answer,
        onValueChange = onAnswerChange,
        readOnly = state.feedback != null,
        label = { Text("Finnish") },
        singleLine = true,
        textStyle = MaterialTheme.typography.headlineMedium.copy(textAlign = TextAlign.Center),
        keyboardOptions = KeyboardOptions(
            capitalization = KeyboardCapitalization.None,
            autoCorrectEnabled = false,
            imeAction = ImeAction.Done,
            hintLocales = LocaleList("fi"),
        ),
        keyboardActions = KeyboardActions(onDone = { if (state.feedback == null && state.answer.isNotBlank()) onSubmit() }),
        modifier = Modifier.fillMaxWidth().focusRequester(requester),
    )
    LaunchedEffect(state.index, item.exerciseType) { requester.requestFocus() }
}

@Composable
private fun FreeRecallExercise(
    item: PracticeItem,
    feedback: PracticeFeedback?,
    onRemembered: () -> Unit,
    onReveal: () -> Unit,
) {
    PromptText(item.prompt, item.promptLanguage)
    Text("Think of the Finnish word", color = TextSecondary, modifier = Modifier.padding(top = 12.dp))
    if (feedback == null) {
        Spacer(Modifier.height(30.dp))
        Button(onClick = onRemembered, modifier = Modifier.fillMaxWidth().heightIn(min = 54.dp)) { Text("I remember") }
        TextButton(onClick = onReveal) { Text("Reveal") }
    }
}

@Composable
private fun RescueExercise(content: RescueContent?, audioText: String?, onPlayAudio: (String) -> Unit) {
    if (content == null) return
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(content.finnish, style = MaterialTheme.typography.displaySmall, textAlign = TextAlign.Center)
        audioText?.let { AudioButton { onPlayAudio(it) } }
        Text(content.spellingChunks, style = MaterialTheme.typography.titleLarge, color = BluePrimary)
        content.englishExplanation?.let { Text(it, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center) }
        content.persianExplanation?.let { SecondaryDirectionalText(it) }
        content.example?.let {
            Surface(color = BlueSoft, shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text(it, style = MaterialTheme.typography.titleMedium)
                    content.exampleTranslation?.let { translation -> SecondaryDirectionalText(translation) }
                }
            }
        }
        content.importantForm?.let { Text("Form  $it", color = TextSecondary) }
        content.confusingWord?.let { Text("Compare  $it", color = TextSecondary) }
        content.memoryNote?.let { Text(it, color = TextSecondary, textAlign = TextAlign.Center) }
    }
}

@Composable
private fun AudioButton(large: Boolean = false, onClick: () -> Unit) {
    IconButton(onClick = onClick, modifier = if (large) Modifier.padding(8.dp) else Modifier) {
        Icon(
            Icons.AutoMirrored.Filled.VolumeUp,
            contentDescription = "Play Finnish pronunciation",
            tint = BluePrimary,
        )
    }
}

@Composable
private fun PromptText(
    text: String,
    language: PromptLanguage,
    style: androidx.compose.ui.text.TextStyle = MaterialTheme.typography.headlineLarge,
) {
    if (language == PromptLanguage.PERSIAN) {
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
            Text(text, style = style, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        }
    } else {
        Text(text, style = style, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun SecondaryDirectionalText(text: String) {
    val rtl = text.any { it.code in 0x0600..0x06FF }
    CompositionLocalProvider(LocalLayoutDirection provides if (rtl) LayoutDirection.Rtl else LayoutDirection.Ltr) {
        Text(text, color = TextSecondary, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
    }
}

@Composable
private fun FeedbackPanel(feedback: PracticeFeedback) {
    val (label, color) = when (feedback.grade.outcome) {
        AnswerOutcome.EXACT -> "Correct" to SuccessSoft
        AnswerOutcome.TYPO -> when (feedback.grade.quantityIssue?.type) {
            com.wordiq.app.learning.FinnishQuantityType.VOWEL -> "Long vowel" to BlueSoft
            com.wordiq.app.learning.FinnishQuantityType.CONSONANT -> "Double consonant" to BlueSoft
            null -> "Almost" to BlueSoft
        }
        AnswerOutcome.PARTIAL -> if (feedback.grade.lemmaRemembered) "Word ✓" to BlueSoft else "Partly there" to BlueSoft
        AnswerOutcome.WRONG -> "Not yet" to ErrorSoft
        AnswerOutcome.REVEALED -> "Answer" to BlueSoft
    }
    Surface(color = color, shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth().padding(top = 14.dp)) {
        Column(Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(label, fontWeight = FontWeight.SemiBold)
            if (feedback.grade.outcome != AnswerOutcome.EXACT) {
                HighlightedExpected(feedback.grade)
            }
        }
    }
}

@Composable
private fun HighlightedExpected(grade: com.wordiq.app.learning.GradeResult) {
    val issue = grade.quantityIssue
    if (issue == null || issue.expectedStart !in grade.expected.indices) {
        if (grade.expected.any { it.code in 0x0600..0x06FF }) {
            PromptText(grade.expected, PromptLanguage.PERSIAN, MaterialTheme.typography.titleLarge)
        } else {
            Text(grade.expected, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 4.dp))
        }
        return
    }
    val start = issue.expectedStart.coerceIn(0, grade.expected.length)
    val end = (start + issue.expectedSegment.length).coerceIn(start, grade.expected.length)
    Text(
        buildAnnotatedString {
            append(grade.expected.substring(0, start))
            withStyle(SpanStyle(color = BluePrimary, fontWeight = FontWeight.Bold)) {
                append(grade.expected.substring(start, end))
            }
            append(grade.expected.substring(end))
        },
        style = MaterialTheme.typography.titleLarge,
        modifier = Modifier.padding(top = 4.dp),
    )
}

@Composable
private fun EmptyPractice(message: String?, onStudy: () -> Unit, onDone: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(ScreenPadding), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Text(if (message == null) "All caught up" else "Not enough words", style = MaterialTheme.typography.headlineLarge)
        Text(message ?: "Nothing due right now", color = TextSecondary, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 8.dp))
        Button(onClick = onDone, modifier = Modifier.fillMaxWidth().padding(top = 28.dp)) { Text("Done") }
        if (message == null) TextButton(onClick = onStudy) { Text("Extra practice") }
    }
}

@Composable
private fun CompletionState(strengthened: Int, needsWork: Int, onAgain: () -> Unit, onDone: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(ScreenPadding), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Surface(color = BlueSoft, shape = RoundedCornerShape(28.dp)) {
            Text("✓", color = BluePrimary, style = MaterialTheme.typography.displaySmall, modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp))
        }
        Text("$strengthened strengthened", style = MaterialTheme.typography.headlineLarge, modifier = Modifier.padding(top = 24.dp))
        if (needsWork > 0) Text("$needsWork to revisit", color = TextSecondary, modifier = Modifier.padding(top = 6.dp))
        Button(onClick = onDone, modifier = Modifier.fillMaxWidth().padding(top = 30.dp)) { Text("Done") }
        TextButton(onClick = onAgain) { Text("Again") }
    }
}

@Composable
private fun MessageState(title: String, detail: String, action: String, onAction: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(ScreenPadding), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Text(title, style = MaterialTheme.typography.headlineMedium)
        Text(detail, color = TextSecondary, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 8.dp))
        Button(onClick = onAction, modifier = Modifier.padding(top = 24.dp)) { Text(action) }
    }
}

private fun optionLanguage(item: PracticeItem): PromptLanguage = when (item.exerciseType) {
    ExerciseType.MEANING_RECOGNITION, ExerciseType.LISTEN_MEANING -> item.answerLanguage
    ExerciseType.FORM_RECOGNITION_MEANING -> if (item.expectedAnswers.any { answer -> answer.any { it.code in 0x0600..0x06FF } }) {
        PromptLanguage.PERSIAN
    } else {
        PromptLanguage.ENGLISH
    }
    else -> PromptLanguage.FINNISH
}

private val typedExercises = setOf(
    ExerciseType.FINNISH_PRODUCTION,
    ExerciseType.SPELLING_RECALL,
    ExerciseType.LISTEN_TYPE,
    ExerciseType.CLOZE_TYPE,
    ExerciseType.FORM_PRODUCTION,
)

private fun exerciseLabel(type: ExerciseType): String = when (type) {
    ExerciseType.INTRODUCTION -> "New word"
    ExerciseType.MEANING_RECOGNITION -> "Meaning"
    ExerciseType.FINNISH_PRODUCTION -> "Recall Finnish"
    ExerciseType.REVERSE_RECOGNITION -> "Choose Finnish"
    ExerciseType.FREE_RECALL -> "Free recall"
    ExerciseType.SPELLING_RECALL -> "Spelling"
    ExerciseType.LISTEN_MEANING -> "Listening · meaning"
    ExerciseType.LISTEN_TYPE -> "Listening · type"
    ExerciseType.LISTEN_CHOICE -> "Listening · word"
    ExerciseType.CLOZE_CHOICE, ExerciseType.CLOZE_TYPE -> "In context"
    ExerciseType.RESCUE -> "A closer look"
    ExerciseType.FORM_RECOGNITION_LEMMA -> "Recognize form"
    ExerciseType.FORM_RECOGNITION_MEANING -> "Form meaning"
    ExerciseType.FORM_PRODUCTION -> "Build the form"
    ExerciseType.SPOKEN_RECOGNITION -> "Spoken Finnish"
    ExerciseType.MINIMAL_PAIR_LISTENING -> "Listen closely"
}

internal fun automaticPronunciationText(state: PracticeUiState): String? {
    val item = state.current ?: return null
    if (item.selfCheck) {
        return when {
            item.promptLanguage == PromptLanguage.FINNISH -> item.audioText ?: item.prompt
            state.selfCheckRevealed || state.feedback != null -> item.concept.concept.finnishLemma
            else -> null
        }
    }
    return when (item.exerciseType) {
        ExerciseType.INTRODUCTION,
        ExerciseType.MEANING_RECOGNITION,
        ExerciseType.FORM_RECOGNITION_LEMMA,
        ExerciseType.FORM_RECOGNITION_MEANING,
        ExerciseType.SPOKEN_RECOGNITION,
        ExerciseType.RESCUE,
        ExerciseType.LISTEN_MEANING,
        ExerciseType.LISTEN_TYPE,
        ExerciseType.LISTEN_CHOICE,
        ExerciseType.MINIMAL_PAIR_LISTENING,
        ExerciseType.SPELLING_RECALL -> item.audioText ?: item.prompt.takeIf { item.promptLanguage == PromptLanguage.FINNISH }
        ExerciseType.FINNISH_PRODUCTION,
        ExerciseType.REVERSE_RECOGNITION,
        ExerciseType.FREE_RECALL,
        ExerciseType.FORM_PRODUCTION -> if (state.feedback != null) item.audioText ?: item.expectedAnswers.firstOrNull() else null
        ExerciseType.CLOZE_CHOICE,
        ExerciseType.CLOZE_TYPE -> if (state.feedback != null) item.audioText else null
    }
}

internal fun shouldAutoAdvanceSelfCheck(state: PracticeUiState): Boolean =
    state.current?.selfCheck == true &&
        state.selfCheckRevealed &&
        state.feedback != null &&
        !state.saving &&
        state.error == null

internal fun isAutoAdvanceCandidate(state: PracticeUiState): Boolean {
    val item = state.current ?: return false
    return !item.selfCheck &&
        state.feedback?.grade?.outcome == AnswerOutcome.EXACT &&
        (item.options.isNotEmpty() || item.exerciseType in typedExercises)
}

internal fun shouldAutoAdvanceCorrectAnswer(state: PracticeUiState): Boolean =
    isAutoAdvanceCandidate(state) && !state.saving && state.error == null

private val CorrectBorder = androidx.compose.ui.graphics.Color(0xFF25845F)
private val WrongBorder = androidx.compose.ui.graphics.Color(0xFFC94A4A)

private const val SELF_CHECK_REVEAL_MS = 600L
private const val CORRECT_ANSWER_REVEAL_MS = 650L
