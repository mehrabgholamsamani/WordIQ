@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.wordiq.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.FactCheck
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Hearing
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Quiz
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Spellcheck
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.wordiq.app.data.local.SetProgressSnapshot
import com.wordiq.app.data.local.SetWithWords
import com.wordiq.app.data.local.VocabularyConceptEntity
import com.wordiq.app.learning.PracticeFocus
import com.wordiq.app.ui.components.AppCard
import com.wordiq.app.ui.components.MasteryIndicator
import com.wordiq.app.ui.components.PrimaryPracticeButton
import com.wordiq.app.ui.components.ScreenPadding
import com.wordiq.app.ui.components.WordIqTopBar
import com.wordiq.app.ui.theme.BluePrimary
import com.wordiq.app.ui.theme.Radius
import com.wordiq.app.ui.theme.Spacing
import com.wordiq.app.ui.theme.TextSecondary

@Composable
fun SetScreen(
    item: SetWithWords?,
    progress: SetProgressSnapshot?,
    explanationLanguage: String,
    onBack: () -> Unit,
    onAddWord: () -> Unit,
    onOpenWord: (Long) -> Unit,
    onPractice: (PracticeFocus, Boolean) -> Unit,
) {
    var modesOpen by remember { mutableStateOf(false) }
    Scaffold(
        topBar = {
            WordIqTopBar(item?.set?.name.orEmpty(), onBack) {
                if (item?.words?.isNotEmpty() == true) {
                    IconButton(onClick = { modesOpen = true }) {
                        Icon(Icons.Default.MoreHoriz, contentDescription = "Study options")
                    }
                }
            }
        },
        floatingActionButton = {
            if (item?.words?.isNotEmpty() == true) {
                FloatingActionButton(onClick = onAddWord) {
                    Icon(Icons.Default.Add, contentDescription = "Add word")
                }
            }
        },
    ) { padding ->
        if (item == null) return@Scaffold
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                start = ScreenPadding,
                top = ScreenPadding,
                end = ScreenPadding,
                bottom = ScreenPadding,
            ),
            verticalArrangement = Arrangement.spacedBy(Spacing.M),
        ) {
            item {
                val status = when {
                    item.words.isEmpty() -> "Empty"
                    progress == null || progress.reviewedCount == 0 -> "New"
                    progress.readyCount > 0 -> "${progress.readyCount} ready"
                    progress.mastery >= 0.75 -> "Strong"
                    else -> "Learning"
                }
                Text("${wordCountLabel(item.words.size)} · $status", color = TextSecondary)
                Spacer(Modifier.padding(top = Spacing.XS))
                if (item.words.isEmpty()) {
                    PrimaryPracticeButton(minutes = null, onClick = onAddWord, label = "Add first word", icon = Icons.Default.Add)
                } else {
                    PrimaryPracticeButton(minutes = null, onClick = { onPractice(PracticeFocus.SMART, false) })
                }
            }
            if (item.words.isNotEmpty()) {
                item { MasteryIndicator(progress?.mastery?.toFloat() ?: 0f, Modifier.padding(vertical = Spacing.S)) }
                item { Text("Words", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = Spacing.S)) }
                items(item.words.sortedBy { it.finnishLemma.lowercase() }, key = { it.id }) { word ->
                    WordRow(word, explanationLanguage) { onOpenWord(word.id) }
                }
            }
        }
    }
    if (modesOpen) {
        PracticeModesSheet(
            onDismiss = { modesOpen = false },
            onPractice = { focus, manual -> modesOpen = false; onPractice(focus, manual) },
        )
    }
}

@Composable
private fun WordRow(word: VocabularyConceptEntity, explanationLanguage: String, onClick: () -> Unit) {
    val meaning = preferredMeaning(word.persianTranslations, word.englishTranslations, explanationLanguage)
    AppCard(onClick = onClick) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.XS)) {
                Text(word.finnishLemma, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                meaning?.let {
                    if (it.any { char -> char.code in 0x0600..0x06FF }) {
                        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                            Text(it, color = TextSecondary, textAlign = TextAlign.Start, modifier = Modifier.fillMaxWidth(), maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                    } else {
                        Text(it, color = TextSecondary, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = TextSecondary)
        }
    }
}

private data class StudyMode(
    val label: String,
    val detail: String,
    val icon: ImageVector,
    val focus: PracticeFocus,
    val manual: Boolean,
)

private enum class TestMode(val label: String, val detail: String, val icon: ImageVector) {
    SELF_CHECK("Self-check", "Say it, reveal it, decide", Icons.AutoMirrored.Filled.FactCheck),
    MULTIPLE_CHOICE("Multiple choice", "Choose one answer", Icons.Default.Quiz),
    SPELLING("Spelling", "Type the Finnish word", Icons.Default.Spellcheck),
}

@Composable
private fun PracticeModesSheet(onDismiss: () -> Unit, onPractice: (PracticeFocus, Boolean) -> Unit) {
    var selectedTest by remember { mutableStateOf<TestMode?>(null) }
    val quick = listOf(
        StudyMode("Learn new", "Introduce unseen words", Icons.Default.School, PracticeFocus.LEARN_NEW, false),
        StudyMode("Review", "Due words only", Icons.Default.Refresh, PracticeFocus.REVIEW, false),
        StudyMode("Weak words", "Practice trouble spots", Icons.Default.WarningAmber, PracticeFocus.WEAK, true),
        StudyMode("Cram", "Extra study without changing schedules", Icons.Default.AutoStories, PracticeFocus.SMART, true),
    )
    val skills = listOf(
        StudyMode("Listening", "Finnish by sound", Icons.Default.Hearing, PracticeFocus.LISTENING, true),
        StudyMode("Context", "Words in sentences", Icons.Default.EditNote, PracticeFocus.CONTEXT, true),
        StudyMode("Forms", "Useful Finnish forms", Icons.Default.Language, PracticeFocus.FORMS, true),
        StudyMode("Spoken Finnish", "Standard and spoken forms", Icons.Default.Translate, PracticeFocus.SPOKEN, true),
    )
    val directions = listOf(
        StudyMode("Finnish → Persian", "Recognition", Icons.Default.Translate, PracticeFocus.MEANING_PERSIAN, true),
        StudyMode("Persian → Finnish", "Recall", Icons.Default.Translate, PracticeFocus.PRODUCTION_PERSIAN, true),
        StudyMode("Finnish → English", "Recognition", Icons.Default.Translate, PracticeFocus.MEANING_ENGLISH, true),
        StudyMode("English → Finnish", "Recall", Icons.Default.Translate, PracticeFocus.PRODUCTION_ENGLISH, true),
    )
    ModalBottomSheet(onDismissRequest = onDismiss) {
        LazyColumn(
            Modifier.fillMaxWidth().heightIn(max = 640.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = ScreenPadding, vertical = Spacing.S),
        ) {
            val test = selectedTest
            if (test == null) {
                item { Text("Study options", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(bottom = Spacing.M)) }
                item { ModeHeading("Tests") }
                items(TestMode.entries, key = { it.name }) { mode ->
                    TestModeRow(mode) { selectedTest = mode }
                }
                item { ModeHeading("Quick practice") }
                items(quick, key = { it.label }) { ModeRow(it, onPractice) }
                item { ModeHeading("Skills") }
                items(skills, key = { it.label }) { ModeRow(it, onPractice) }
                item { ModeHeading("Direction") }
                items(directions, key = { it.label }) { ModeRow(it, onPractice) }
            } else {
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { selectedTest = null }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to study options")
                        }
                        Text(test.label, style = MaterialTheme.typography.headlineMedium)
                    }
                }
                item { Text("Choose direction", color = TextSecondary, modifier = Modifier.padding(vertical = Spacing.M)) }
                items(testDirections(test), key = { it.label }) { ModeRow(it, onPractice) }
            }
            item { Spacer(Modifier.padding(bottom = Spacing.XL)) }
        }
    }
}

private fun testDirections(mode: TestMode): List<StudyMode> = when (mode) {
    TestMode.SELF_CHECK -> listOf(
        StudyMode("Finnish → meaning", "Uses your language preference", Icons.Default.Translate, PracticeFocus.SELF_CHECK_MEANING, false),
        StudyMode("Persian → Finnish", "Recall aloud", Icons.Default.Translate, PracticeFocus.SELF_CHECK_PERSIAN, false),
        StudyMode("English → Finnish", "Recall aloud", Icons.Default.Translate, PracticeFocus.SELF_CHECK_ENGLISH, false),
    )
    TestMode.MULTIPLE_CHOICE -> listOf(
        StudyMode("Finnish → meaning", "Choose the meaning", Icons.Default.Translate, PracticeFocus.MULTIPLE_CHOICE_MEANING, false),
        StudyMode("Persian → Finnish", "Choose the Finnish word", Icons.Default.Translate, PracticeFocus.MULTIPLE_CHOICE_PERSIAN, false),
        StudyMode("English → Finnish", "Choose the Finnish word", Icons.Default.Translate, PracticeFocus.MULTIPLE_CHOICE_ENGLISH, false),
    )
    TestMode.SPELLING -> listOf(
        StudyMode("Persian → Finnish", "Type the Finnish word", Icons.Default.Translate, PracticeFocus.SPELLING_PERSIAN, false),
        StudyMode("English → Finnish", "Type the Finnish word", Icons.Default.Translate, PracticeFocus.SPELLING_ENGLISH, false),
    )
}

@Composable
private fun TestModeRow(mode: TestMode, onClick: () -> Unit) {
    Surface(onClick = onClick, color = androidx.compose.ui.graphics.Color.Transparent, shape = RoundedCornerShape(Radius.Small)) {
        Row(
            Modifier.fillMaxWidth().padding(vertical = Spacing.M),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.M),
        ) {
            Icon(mode.icon, contentDescription = null, tint = BluePrimary)
            Column(Modifier.weight(1f)) {
                Text(mode.label, style = MaterialTheme.typography.titleMedium)
                Text(mode.detail, color = TextSecondary, style = MaterialTheme.typography.bodyMedium)
            }
            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = TextSecondary)
        }
    }
}

@Composable
private fun ModeHeading(text: String) {
    Text(text, style = MaterialTheme.typography.labelLarge, color = TextSecondary, modifier = Modifier.padding(top = Spacing.L, bottom = Spacing.XS))
}

@Composable
private fun ModeRow(mode: StudyMode, onPractice: (PracticeFocus, Boolean) -> Unit) {
    Surface(onClick = { onPractice(mode.focus, mode.manual) }, color = androidx.compose.ui.graphics.Color.Transparent, shape = RoundedCornerShape(Radius.Small)) {
        Row(
            Modifier.fillMaxWidth().padding(vertical = Spacing.M),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.M),
        ) {
            Icon(mode.icon, contentDescription = null, tint = BluePrimary)
            Column(Modifier.weight(1f)) {
                Text(mode.label, style = MaterialTheme.typography.titleMedium)
                Text(mode.detail, color = TextSecondary, style = MaterialTheme.typography.bodyMedium)
            }
            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = TextSecondary)
        }
    }
}
