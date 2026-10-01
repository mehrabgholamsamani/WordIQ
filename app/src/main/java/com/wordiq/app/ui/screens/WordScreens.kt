@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.wordiq.app.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.RemoveCircleOutline
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.intl.LocaleList
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.wordiq.app.data.WordDraft
import com.wordiq.app.data.WordFormDraft
import com.wordiq.app.data.local.CollectionEntity
import com.wordiq.app.data.local.ConceptWithDetails
import com.wordiq.app.data.local.FlashcardSetEntity
import com.wordiq.app.toDraft
import com.wordiq.app.ui.components.AppCard
import com.wordiq.app.ui.components.ScreenPadding
import com.wordiq.app.ui.components.WordIqTopBar
import com.wordiq.app.ui.theme.BluePrimary
import com.wordiq.app.ui.theme.BlueSoft
import com.wordiq.app.ui.theme.Radius
import com.wordiq.app.ui.theme.Spacing
import com.wordiq.app.ui.theme.TextSecondary

@Composable
fun WordDetailScreen(
    details: ConceptWithDetails?,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onPlayAudio: (String) -> Unit,
    onOpenXRay: (Long) -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    Scaffold(
        topBar = {
            WordIqTopBar(
                title = "",
                onBack = onBack,
                actions = {
                    IconButton(onClick = { details?.concept?.finnishLemma?.let(onPlayAudio) }) {
                        Icon(Icons.AutoMirrored.Filled.VolumeUp, contentDescription = "Play Finnish pronunciation")
                    }
                    IconButton(onClick = onEdit) { Icon(Icons.Default.Edit, contentDescription = "Edit word") }
                    IconButton(onClick = { menuOpen = true }) { Icon(Icons.Default.MoreHoriz, contentDescription = "Word actions") }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text("Delete") },
                            leadingIcon = { Icon(Icons.Default.DeleteOutline, contentDescription = null) },
                            onClick = { menuOpen = false; onDelete() },
                        )
                    }
                },
            )
        },
    ) { padding ->
        if (details == null) {
            Box(Modifier.fillMaxSize().padding(padding))
            return@Scaffold
        }
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(ScreenPadding),
            verticalArrangement = Arrangement.spacedBy(Spacing.L),
        ) {
            Text(
                details.concept.finnishLemma,
                style = MaterialTheme.typography.displaySmall,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
            details.concept.partOfSpeech?.let { Text(it, color = TextSecondary) }
            details.concept.englishTranslations?.let { Text(it, style = MaterialTheme.typography.titleLarge) }
            details.concept.persianTranslations?.let {
                PersianText(it, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onBackground)
            }

            details.concept.spokenFinnish?.takeIf { it.isNotBlank() }?.let { spoken ->
                Text("Spoken Finnish", style = MaterialTheme.typography.titleMedium)
                AppCard {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(details.concept.finnishLemma, color = TextSecondary, maxLines = 2)
                            Text(spoken, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = Spacing.XS), maxLines = 3)
                        }
                        IconButton(onClick = { onPlayAudio(spoken) }) {
                            Icon(Icons.AutoMirrored.Filled.VolumeUp, contentDescription = "Play spoken Finnish")
                        }
                    }
                }
            }

            details.examples.firstOrNull()?.let { example ->
                Text("Example", style = MaterialTheme.typography.titleMedium)
                AppCard {
                    Text(example.finnishSentence, style = MaterialTheme.typography.bodyLarge)
                    example.englishTranslation?.let { Text(it, color = TextSecondary, modifier = Modifier.padding(top = Spacing.S)) }
                    example.persianTranslation?.let { PersianText(it, modifier = Modifier.padding(top = Spacing.S)) }
                }
            }

            if (details.forms.isNotEmpty()) {
                Text("Forms", style = MaterialTheme.typography.titleMedium)
                AppCard {
                    details.forms.sortedBy { it.sortOrder }.forEach { form ->
                        Surface(
                            onClick = { onOpenXRay(form.id) },
                            color = Color.Transparent,
                            shape = RoundedCornerShape(Radius.Small),
                        ) {
                            Row(
                                Modifier.fillMaxWidth().padding(vertical = Spacing.M),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(form.form, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                    val description = listOfNotNull(form.grammaticalLabel, form.explanation).joinToString(" · ")
                                    if (description.isNotEmpty()) Text(description, color = TextSecondary, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                    form.persianMeaning?.let { PersianText(it, modifier = Modifier.padding(top = Spacing.XS)) }
                                }
                                Icon(Icons.Default.ChevronRight, contentDescription = null, tint = TextSecondary)
                            }
                        }
                    }
                }
            }

            details.concept.notes?.takeIf { it.isNotBlank() }?.let {
                Text("Notes", style = MaterialTheme.typography.titleMedium)
                Text(it, color = TextSecondary, style = MaterialTheme.typography.bodyLarge)
            }
            if (details.sets.isNotEmpty()) {
                Text("Sets", style = MaterialTheme.typography.titleMedium)
                Text(details.sets.joinToString(" · ") { it.name }, color = TextSecondary, style = MaterialTheme.typography.bodyLarge)
            }
            Spacer(Modifier.height(Spacing.L))
        }
    }

}

@Composable
fun WordEditorScreen(
    wordId: Long,
    initialSetId: Long?,
    details: ConceptWithDetails?,
    allSets: List<FlashcardSetEntity>,
    collections: List<CollectionEntity>,
    onBack: () -> Unit,
    onCreateSet: (String) -> Unit,
    onSave: (WordDraft, onFailure: () -> Unit) -> Unit,
) {
    val emptyDraft = remember(wordId, initialSetId) {
        WordDraft(id = wordId, setIds = initialSetId?.let(::setOf).orEmpty())
    }
    var draft by rememberSaveable(wordId, initialSetId) { mutableStateOf(emptyDraft) }
    var baseline by rememberSaveable(wordId, initialSetId) { mutableStateOf(emptyDraft) }
    var loaded by rememberSaveable(wordId) { mutableStateOf(wordId == 0L) }
    var choosingSets by rememberSaveable { mutableStateOf(false) }
    var detailsExpanded by rememberSaveable { mutableStateOf(false) }
    var exampleExpanded by rememberSaveable { mutableStateOf(false) }
    var formsExpanded by rememberSaveable { mutableStateOf(false) }
    var notesExpanded by rememberSaveable { mutableStateOf(false) }
    var saving by rememberSaveable { mutableStateOf(false) }
    var confirmDiscard by rememberSaveable { mutableStateOf(false) }
    val finnishFocus = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current

    LaunchedEffect(details?.concept?.updatedAt) {
        if (wordId != 0L && details != null && !loaded) {
            val persistedDraft = details.toDraft()
            draft = persistedDraft
            baseline = persistedDraft
            loaded = true
        }
    }
    LaunchedEffect(loaded, wordId) {
        if (loaded && wordId == 0L) finnishFocus.requestFocus()
    }

    val canSave = draft.finnish.isNotBlank() &&
        (draft.persian.isNotBlank() || draft.english.isNotBlank()) &&
        draft.setIds.isNotEmpty()
    val hasUnsavedChanges = loaded && draft != baseline
    val requestBack = {
        when {
            saving -> Unit
            hasUnsavedChanges -> confirmDiscard = true
            else -> onBack()
        }
    }

    BackHandler(enabled = saving || hasUnsavedChanges, onBack = requestBack)

    val saveWord = {
        focusManager.clearFocus()
        saving = true
        onSave(draft) { saving = false }
    }
    Scaffold(
        topBar = {
            WordIqTopBar(
                title = if (wordId == 0L) "Add word" else "Edit word",
                onBack = requestBack,
                actions = {
                    TextButton(onClick = saveWord, enabled = canSave && !saving) {
                        Text(if (saving) "Saving…" else if (wordId == 0L) "Add" else "Save")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(ScreenPadding),
            verticalArrangement = Arrangement.spacedBy(Spacing.M),
        ) {
            OutlinedTextField(
                value = draft.finnish,
                onValueChange = { draft = draft.copy(finnish = it) },
                label = { Text("Finnish") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(autoCorrectEnabled = false, imeAction = ImeAction.Next, hintLocales = LocaleList("fi")),
                keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) }),
                modifier = Modifier.fillMaxWidth().focusRequester(finnishFocus),
            )
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                OutlinedTextField(
                    value = draft.persian,
                    onValueChange = { draft = draft.copy(persian = it) },
                    label = { Text("فارسی") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next, hintLocales = LocaleList("fa")),
                    keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) }),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            OutlinedTextField(
                value = draft.english,
                onValueChange = { draft = draft.copy(english = it) },
                label = { Text("English") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done, hintLocales = LocaleList("en")),
                keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                modifier = Modifier.fillMaxWidth(),
            )

            DestinationSelector(
                selected = allSets.filter { it.id in draft.setIds },
                onClick = { choosingSets = true },
            )

            EditorSectionHeader(
                title = "Word details",
                summary = listOfNotNull(
                    draft.partOfSpeech.takeIf { it.isNotBlank() },
                    draft.spokenFinnish.takeIf { it.isNotBlank() }?.let { "spoken form" },
                ).joinToString(" · "),
                expanded = detailsExpanded,
                onClick = { detailsExpanded = !detailsExpanded },
            )
            AnimatedVisibility(detailsExpanded) {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.M)) {
                    OutlinedTextField(
                        value = draft.partOfSpeech,
                        onValueChange = { draft = draft.copy(partOfSpeech = it) },
                        label = { Text("Part of speech") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = draft.spokenFinnish,
                        onValueChange = { draft = draft.copy(spokenFinnish = it) },
                        label = { Text("Spoken Finnish") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(hintLocales = LocaleList("fi")),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            EditorSectionHeader(
                title = "Example",
                summary = if (draft.exampleFinnish.isBlank()) "" else "Added",
                expanded = exampleExpanded,
                onClick = { exampleExpanded = !exampleExpanded },
            )
            AnimatedVisibility(exampleExpanded) {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.M)) {
                    OutlinedTextField(
                        value = draft.exampleFinnish,
                        onValueChange = { draft = draft.copy(exampleFinnish = it) },
                        label = { Text("Finnish sentence") },
                        keyboardOptions = KeyboardOptions(hintLocales = LocaleList("fi")),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                        OutlinedTextField(
                            value = draft.examplePersian,
                            onValueChange = { draft = draft.copy(examplePersian = it) },
                            label = { Text("ترجمه فارسی") },
                            keyboardOptions = KeyboardOptions(hintLocales = LocaleList("fa")),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    OutlinedTextField(
                        value = draft.exampleEnglish,
                        onValueChange = { draft = draft.copy(exampleEnglish = it) },
                        label = { Text("English translation") },
                        keyboardOptions = KeyboardOptions(hintLocales = LocaleList("en")),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            EditorSectionHeader(
                title = "Forms",
                summary = draft.forms.count { it.form.isNotBlank() }.takeIf { it > 0 }?.let { "$it added" }.orEmpty(),
                expanded = formsExpanded,
                onClick = { formsExpanded = !formsExpanded },
            )
            AnimatedVisibility(formsExpanded) {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.M)) {
                    draft.forms.forEachIndexed { index, form ->
                        FormEditorCard(
                            index = index,
                            form = form,
                            onChange = { updated -> draft = draft.copy(forms = draft.forms.updated(index, updated)) },
                            onRemove = { draft = draft.copy(forms = draft.forms.filterIndexed { i, _ -> i != index }) },
                        )
                    }
                    TextButton(onClick = { draft = draft.copy(forms = draft.forms + WordFormDraft("")) }) {
                        Icon(Icons.Default.Add, contentDescription = null)
                        Text("Add form", modifier = Modifier.padding(start = Spacing.S))
                    }
                }
            }

            EditorSectionHeader(
                title = "Notes",
                summary = if (draft.notes.isBlank()) "" else "Added",
                expanded = notesExpanded,
                onClick = { notesExpanded = !notesExpanded },
            )
            AnimatedVisibility(notesExpanded) {
                OutlinedTextField(
                    value = draft.notes,
                    onValueChange = { draft = draft.copy(notes = it) },
                    label = { Text("Notes") },
                    minLines = 3,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Spacer(Modifier.height(Spacing.M))
        }
    }

    if (choosingSets) {
        SetPickerSheet(
            allSets = allSets,
            collections = collections,
            selectedIds = draft.setIds,
            onToggle = { setId ->
                draft = draft.copy(setIds = if (setId in draft.setIds) draft.setIds - setId else draft.setIds + setId)
            },
            onCreateSet = onCreateSet,
            onDismiss = { choosingSets = false },
        )
    }
    if (confirmDiscard) {
        AlertDialog(
            onDismissRequest = { confirmDiscard = false },
            title = { Text("Discard changes?") },
            text = { Text("Your unsaved word changes will be lost.") },
            confirmButton = {
                TextButton(onClick = { confirmDiscard = false; onBack() }) { Text("Discard") }
            },
            dismissButton = {
                TextButton(onClick = { confirmDiscard = false }) { Text("Keep editing") }
            },
        )
    }
}

@Composable
private fun DestinationSelector(selected: List<FlashcardSetEntity>, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        color = if (selected.isEmpty()) BlueSoft else MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(Radius.Medium),
        shadowElevation = 1.dp,
    ) {
        Row(
            Modifier.fillMaxWidth().padding(Spacing.L),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.M),
        ) {
            Icon(Icons.Default.FolderOpen, contentDescription = null, tint = BluePrimary)
            Column(Modifier.weight(1f)) {
                Text(if (selected.isEmpty()) "Choose a set" else if (selected.size == 1) selected.single().name else "${selected.size} sets", style = MaterialTheme.typography.titleMedium)
                if (selected.size > 1) {
                    Text(selected.take(2).joinToString(" · ") { it.name }, color = TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = TextSecondary)
        }
    }
}

@Composable
private fun EditorSectionHeader(title: String, summary: String, expanded: Boolean, onClick: () -> Unit) {
    Surface(onClick = onClick, color = Color.Transparent, shape = RoundedCornerShape(Radius.Small)) {
        Row(
            Modifier.fillMaxWidth().padding(vertical = Spacing.M, horizontal = Spacing.XS),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            if (summary.isNotBlank()) Text(summary, color = TextSecondary, style = MaterialTheme.typography.bodyMedium, maxLines = 1)
            Icon(
                Icons.Default.ExpandMore,
                contentDescription = if (expanded) "Collapse $title" else "Expand $title",
                tint = TextSecondary,
                modifier = Modifier.padding(start = Spacing.S).rotate(if (expanded) 180f else 0f),
            )
        }
    }
}

@Composable
private fun SetPickerSheet(
    allSets: List<FlashcardSetEntity>,
    collections: List<CollectionEntity>,
    selectedIds: Set<Long>,
    onToggle: (Long) -> Unit,
    onCreateSet: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    var creating by rememberSaveable { mutableStateOf(false) }
    var newSetName by rememberSaveable { mutableStateOf("") }
    val collectionNames = collections.associate { it.id to it.name }
    val filtered = allSets.filter { set ->
        query.isBlank() || set.name.contains(query, ignoreCase = true) ||
            set.collectionId?.let { collectionNames[it]?.contains(query, ignoreCase = true) } == true
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier.fillMaxWidth().imePadding().padding(horizontal = ScreenPadding).padding(bottom = Spacing.XL),
            verticalArrangement = Arrangement.spacedBy(Spacing.M),
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Choose sets", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.weight(1f))
                TextButton(onClick = onDismiss) { Text("Done") }
            }
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text("Search sets") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    if (query.isNotEmpty()) IconButton(onClick = { query = "" }) {
                        Icon(Icons.Default.Close, contentDescription = "Clear search")
                    }
                },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            if (creating) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.S)) {
                    OutlinedTextField(
                        value = newSetName,
                        onValueChange = { newSetName = it },
                        label = { Text("New set") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    Button(
                        onClick = { onCreateSet(newSetName); newSetName = ""; creating = false },
                        enabled = newSetName.isNotBlank(),
                    ) { Text("Add") }
                }
            } else {
                TextButton(onClick = { creating = true }) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Text("New set", modifier = Modifier.padding(start = Spacing.S))
                }
            }
            LazyColumn(Modifier.fillMaxWidth().heightIn(max = 480.dp)) {
                if (filtered.isEmpty()) {
                    item { Text("No matching sets", color = TextSecondary, modifier = Modifier.padding(vertical = Spacing.XL)) }
                }
                collections.forEach { collection ->
                    val children = filtered.filter { it.collectionId == collection.id }
                    if (children.isNotEmpty()) {
                        item(key = "heading-${collection.id}") {
                            Text(collection.name, style = MaterialTheme.typography.labelLarge, color = TextSecondary, modifier = Modifier.padding(top = Spacing.M, bottom = Spacing.XS))
                        }
                        items(children, key = { "picker-${it.id}" }) { set -> SetPickerRow(set, set.id in selectedIds, onToggle) }
                    }
                }
                val independent = filtered.filter { it.collectionId == null }
                if (independent.isNotEmpty()) {
                    item(key = "heading-independent") {
                        Text("Independent", style = MaterialTheme.typography.labelLarge, color = TextSecondary, modifier = Modifier.padding(top = Spacing.M, bottom = Spacing.XS))
                    }
                    items(independent, key = { "picker-${it.id}" }) { set -> SetPickerRow(set, set.id in selectedIds, onToggle) }
                }
            }
        }
    }
}

@Composable
private fun SetPickerRow(set: FlashcardSetEntity, selected: Boolean, onToggle: (Long) -> Unit) {
    Surface(onClick = { onToggle(set.id) }, color = Color.Transparent, shape = RoundedCornerShape(Radius.Small)) {
        Row(Modifier.fillMaxWidth().padding(vertical = Spacing.S), verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = selected, onCheckedChange = null)
            Text(set.name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun FormEditorCard(
    index: Int,
    form: WordFormDraft,
    onChange: (WordFormDraft) -> Unit,
    onRemove: () -> Unit,
) {
    AppCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Form ${index + 1}", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            IconButton(onClick = onRemove) { Icon(Icons.Default.RemoveCircleOutline, contentDescription = "Remove form ${index + 1}") }
        }
        OutlinedTextField(form.form, { onChange(form.copy(form = it)) }, label = { Text("Finnish form") }, singleLine = true, keyboardOptions = KeyboardOptions(hintLocales = LocaleList("fi")), modifier = Modifier.fillMaxWidth())
        OutlinedTextField(form.label, { onChange(form.copy(label = it)) }, label = { Text("Label") }, singleLine = true, modifier = Modifier.fillMaxWidth().padding(top = Spacing.S))
        OutlinedTextField(form.explanation, { onChange(form.copy(explanation = it)) }, label = { Text("Short meaning") }, modifier = Modifier.fillMaxWidth().padding(top = Spacing.S))
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
            OutlinedTextField(form.persianMeaning, { onChange(form.copy(persianMeaning = it)) }, label = { Text("معنی کوتاه") }, keyboardOptions = KeyboardOptions(hintLocales = LocaleList("fa")), modifier = Modifier.fillMaxWidth().padding(top = Spacing.S))
        }
        OutlinedTextField(form.decomposition, { onChange(form.copy(decomposition = it)) }, label = { Text("X-Ray") }, placeholder = { Text("ystävä + lle + ni") }, modifier = Modifier.fillMaxWidth().padding(top = Spacing.S))
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
            OutlinedTextField(form.persianNote, { onChange(form.copy(persianNote = it)) }, label = { Text("نکته فارسی") }, keyboardOptions = KeyboardOptions(hintLocales = LocaleList("fa")), modifier = Modifier.fillMaxWidth().padding(top = Spacing.S))
        }
    }
}

@Composable
private fun PersianText(
    text: String,
    modifier: Modifier = Modifier,
    style: androidx.compose.ui.text.TextStyle = MaterialTheme.typography.bodyLarge,
    color: Color = TextSecondary,
) {
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        Text(text, style = style, color = color, textAlign = TextAlign.Start, modifier = modifier.fillMaxWidth())
    }
}

private fun <T> List<T>.updated(index: Int, value: T): List<T> = mapIndexed { i, old -> if (i == index) value else old }
