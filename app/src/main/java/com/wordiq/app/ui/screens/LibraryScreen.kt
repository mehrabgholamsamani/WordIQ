@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.wordiq.app.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DriveFileMove
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.wordiq.app.LibraryUiState
import com.wordiq.app.data.local.CollectionEntity
import com.wordiq.app.data.local.CollectionWithSets
import com.wordiq.app.data.local.ConceptWithDetails
import com.wordiq.app.data.local.FlashcardSetEntity
import com.wordiq.app.data.local.SetProgressSnapshot
import com.wordiq.app.ui.components.CardRadius
import com.wordiq.app.ui.components.EmptyState
import com.wordiq.app.ui.components.MasteryIndicator
import com.wordiq.app.ui.components.ScreenPadding
import com.wordiq.app.ui.theme.BluePrimary
import com.wordiq.app.ui.theme.BlueSoft
import com.wordiq.app.ui.theme.Radius
import com.wordiq.app.ui.theme.Spacing
import com.wordiq.app.ui.theme.SurfaceElevated
import com.wordiq.app.ui.theme.TextSecondary

private enum class LibrarySheet { CREATE_COLLECTION, CREATE_SET, MOVE_SET }

@Composable
fun LibraryScreen(
    state: LibraryUiState,
    searchQuery: String,
    searchResults: List<ConceptWithDetails>,
    explanationLanguage: String,
    onSearchQuery: (String) -> Unit,
    onOpenWord: (Long) -> Unit,
    onOpenCollection: (Long) -> Unit,
    onOpenSet: (Long) -> Unit,
    onCreateCollection: (String) -> Unit,
    onRenameCollection: (CollectionEntity, String) -> Unit,
    onDeleteCollection: (CollectionEntity) -> Unit,
    onMoveCollection: (CollectionEntity, Int) -> Unit,
    onCreateSet: (String, Long?) -> Unit,
    onRenameSet: (FlashcardSetEntity, String) -> Unit,
    onDeleteSet: (FlashcardSetEntity) -> Unit,
    onMoveSet: (FlashcardSetEntity, Long?) -> Unit,
    onMoveSetOrder: (FlashcardSetEntity, Int) -> Unit,
) {
    var sheet by remember { mutableStateOf<LibrarySheet?>(null) }
    var selectedSet by remember { mutableStateOf<FlashcardSetEntity?>(null) }
    var renameCollection by remember { mutableStateOf<CollectionEntity?>(null) }
    var renameSet by remember { mutableStateOf<FlashcardSetEntity?>(null) }
    val expanded = remember { mutableStateMapOf<Long, Boolean>() }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(ScreenPadding),
        verticalArrangement = Arrangement.spacedBy(Spacing.M),
    ) {
        item { Text("Library", style = MaterialTheme.typography.headlineLarge) }
        item {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = onSearchQuery,
                placeholder = { Text("Search words") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) IconButton(onClick = { onSearchQuery("") }) {
                        Icon(Icons.Default.Close, contentDescription = "Clear search")
                    }
                },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        if (searchQuery.isNotBlank()) {
            if (searchResults.isEmpty()) {
                item { EmptyState("No matching words") }
            } else {
                items(searchResults, key = { "search-${it.concept.id}" }) { details ->
                    SearchResultRow(details, searchQuery, explanationLanguage) { onOpenWord(details.concept.id) }
                }
            }
        } else {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.S)) {
                    FilledTonalButton(onClick = { sheet = LibrarySheet.CREATE_SET }) {
                        Icon(Icons.Default.Add, contentDescription = null)
                        Text("New set", Modifier.padding(start = Spacing.S))
                    }
                    TextButton(onClick = { sheet = LibrarySheet.CREATE_COLLECTION }) {
                        Icon(Icons.Default.Folder, contentDescription = null)
                        Text("New collection", Modifier.padding(start = Spacing.S))
                    }
                }
            }

            itemsIndexed(state.collections, key = { _, item -> "collection-${item.collection.id}" }) { collectionIndex, item ->
                CollectionGroup(
                    item = item,
                    progress = state.setProgress,
                    expanded = expanded[item.collection.id] ?: false,
                    onToggle = { expanded[item.collection.id] = !(expanded[item.collection.id] ?: false) },
                    onOpenCollection = { onOpenCollection(item.collection.id) },
                    onOpenSet = onOpenSet,
                    onRenameCollection = { renameCollection = item.collection },
                    onDeleteCollection = { onDeleteCollection(item.collection) },
                    onMoveCollection = { onMoveCollection(item.collection, it) },
                    canMoveCollectionUp = collectionIndex > 0,
                    canMoveCollectionDown = collectionIndex < state.collections.lastIndex,
                    onRenameSet = { renameSet = it },
                    onDeleteSet = onDeleteSet,
                    onMoveSet = { selectedSet = it; sheet = LibrarySheet.MOVE_SET },
                    onMoveSetOrder = onMoveSetOrder,
                )
            }

            if (state.standaloneSets.isNotEmpty()) {
                item { Text("Sets", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = Spacing.S)) }
                itemsIndexed(state.standaloneSets, key = { _, set -> "set-${set.id}" }) { setIndex, set ->
                    SetRow(
                        set = set,
                        progress = state.setProgress[set.id],
                        onOpen = { onOpenSet(set.id) },
                        onRename = { renameSet = set },
                        onDelete = { onDeleteSet(set) },
                        onMove = { selectedSet = set; sheet = LibrarySheet.MOVE_SET },
                        onMoveOrder = { onMoveSetOrder(set, it) },
                        canMoveUp = setIndex > 0,
                        canMoveDown = setIndex < state.standaloneSets.lastIndex,
                    )
                }
            }

            if (!state.loading && state.collections.isEmpty() && state.standaloneSets.isEmpty()) {
                item { EmptyState("No sets yet") }
            }
        }
    }

    when (sheet) {
        LibrarySheet.CREATE_COLLECTION -> NameSheet("New collection", "Name", { sheet = null }) {
            onCreateCollection(it); sheet = null
        }
        LibrarySheet.CREATE_SET -> CreateSetSheet(
            collections = state.collections.map { it.collection },
            onDismiss = { sheet = null },
            onSave = { name, collectionId -> onCreateSet(name, collectionId); sheet = null },
        )
        LibrarySheet.MOVE_SET -> selectedSet?.let { set ->
            MoveSetSheet(set, state.collections.map { it.collection }, { sheet = null }) {
                onMoveSet(set, it); sheet = null
            }
        }
        null -> Unit
    }

    renameCollection?.let { item ->
        RenameDialog("Rename collection", item.name, { renameCollection = null }) {
            onRenameCollection(item, it); renameCollection = null
        }
    }
    renameSet?.let { item ->
        RenameDialog("Rename set", item.name, { renameSet = null }) {
            onRenameSet(item, it); renameSet = null
        }
    }
}

@Composable
private fun SearchResultRow(details: ConceptWithDetails, query: String, language: String, onOpen: () -> Unit) {
    Surface(onClick = onOpen, color = SurfaceElevated, shape = RoundedCornerShape(CardRadius), shadowElevation = 1.dp) {
        Row(Modifier.fillMaxWidth().padding(Spacing.L), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.XS)) {
                Text(details.concept.finnishLemma, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                preferredMeaning(details.concept.persianTranslations, details.concept.englishTranslations, language)?.let {
                    if (it.containsPersian()) {
                        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                            Text(
                                it,
                                color = TextSecondary,
                                textAlign = TextAlign.Start,
                                modifier = Modifier.fillMaxWidth(),
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    } else {
                        Text(it, color = TextSecondary, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                }
                details.forms.firstOrNull { it.form.contains(query, ignoreCase = true) }?.let {
                    Text("Form · ${it.form}", color = BluePrimary, style = MaterialTheme.typography.bodyMedium, maxLines = 1)
                }
            }
            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = TextSecondary)
        }
    }
}

@Composable
private fun CollectionGroup(
    item: CollectionWithSets,
    progress: Map<Long, SetProgressSnapshot>,
    expanded: Boolean,
    onToggle: () -> Unit,
    onOpenCollection: () -> Unit,
    onOpenSet: (Long) -> Unit,
    onRenameCollection: () -> Unit,
    onDeleteCollection: () -> Unit,
    onMoveCollection: (Int) -> Unit,
    canMoveCollectionUp: Boolean,
    canMoveCollectionDown: Boolean,
    onRenameSet: (FlashcardSetEntity) -> Unit,
    onDeleteSet: (FlashcardSetEntity) -> Unit,
    onMoveSet: (FlashcardSetEntity) -> Unit,
    onMoveSetOrder: (FlashcardSetEntity, Int) -> Unit,
) {
    Surface(color = SurfaceElevated, shape = RoundedCornerShape(CardRadius), shadowElevation = 2.dp) {
        Column {
            Row(
                Modifier.fillMaxWidth().padding(start = Spacing.L, end = Spacing.XS, top = Spacing.S, bottom = Spacing.S),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(color = BlueSoft, shape = RoundedCornerShape(Radius.Small)) {
                    Icon(Icons.Default.Folder, contentDescription = null, tint = BluePrimary, modifier = Modifier.padding(Spacing.M))
                }
                Column(Modifier.weight(1f).clickable(onClick = onOpenCollection).padding(horizontal = Spacing.M, vertical = Spacing.S)) {
                    Text(item.collection.name, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text("${item.sets.size} ${if (item.sets.size == 1) "set" else "sets"}", color = TextSecondary, style = MaterialTheme.typography.bodyMedium)
                }
                IconButton(onClick = onToggle) {
                    Icon(Icons.Default.ExpandMore, contentDescription = if (expanded) "Collapse ${item.collection.name}" else "Expand ${item.collection.name}", modifier = Modifier.rotate(if (expanded) 180f else 0f))
                }
                CollectionMenu(
                    onRenameCollection,
                    onDeleteCollection,
                    onMoveCollection,
                    canMoveCollectionUp,
                    canMoveCollectionDown,
                )
            }
            AnimatedVisibility(expanded) {
                Column(Modifier.padding(bottom = Spacing.S)) {
                    if (item.sets.isEmpty()) Text("No sets yet", color = TextSecondary, modifier = Modifier.padding(start = 76.dp, bottom = Spacing.M))
                    val orderedSets = item.sets.sortedBy { it.sortOrder }
                    orderedSets.forEachIndexed { setIndex, set ->
                        SetRow(
                            set = set,
                            progress = progress[set.id],
                            nested = true,
                            onOpen = { onOpenSet(set.id) },
                            onRename = { onRenameSet(set) },
                            onDelete = { onDeleteSet(set) },
                            onMove = { onMoveSet(set) },
                            onMoveOrder = { onMoveSetOrder(set, it) },
                            canMoveUp = setIndex > 0,
                            canMoveDown = setIndex < orderedSets.lastIndex,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SetRow(
    set: FlashcardSetEntity,
    progress: SetProgressSnapshot?,
    nested: Boolean = false,
    onOpen: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    onMove: () -> Unit,
    onMoveOrder: (Int) -> Unit,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
) {
    Surface(
        onClick = onOpen,
        color = if (nested) Color.Transparent else SurfaceElevated,
        shape = RoundedCornerShape(if (nested) Radius.Small else CardRadius),
        shadowElevation = if (nested) 0.dp else 2.dp,
    ) {
        Row(
            Modifier.fillMaxWidth().padding(start = if (nested) 76.dp else Spacing.L, end = Spacing.XS, top = Spacing.M, bottom = Spacing.M),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(set.name, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(wordCountLabel(progress?.wordCount ?: 0), color = TextSecondary, style = MaterialTheme.typography.bodyMedium)
                if ((progress?.wordCount ?: 0) > 0) MasteryIndicator(progress?.mastery?.toFloat() ?: 0f, Modifier.padding(top = Spacing.S))
            }
            SetMenu(onRename, onDelete, onMove, onMoveOrder, canMoveUp, canMoveDown)
            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = TextSecondary)
        }
    }
}

@Composable
private fun CollectionMenu(
    onRename: () -> Unit,
    onDelete: () -> Unit,
    onMove: (Int) -> Unit,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
) {
    var open by remember { mutableStateOf(false) }
    IconButton(onClick = { open = true }) { Icon(Icons.Default.MoreHoriz, contentDescription = "Collection actions") }
    DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
        DropdownMenuItem(text = { Text("Rename") }, leadingIcon = { Icon(Icons.Default.Edit, null) }, onClick = { open = false; onRename() })
        DropdownMenuItem(text = { Text("Move up") }, leadingIcon = { Icon(Icons.Default.KeyboardArrowUp, null) }, enabled = canMoveUp, onClick = { open = false; onMove(-1) })
        DropdownMenuItem(text = { Text("Move down") }, leadingIcon = { Icon(Icons.Default.KeyboardArrowDown, null) }, enabled = canMoveDown, onClick = { open = false; onMove(1) })
        DropdownMenuItem(text = { Text("Delete") }, leadingIcon = { Icon(Icons.Default.DeleteOutline, null) }, onClick = { open = false; onDelete() })
    }
}

@Composable
private fun SetMenu(
    onRename: () -> Unit,
    onDelete: () -> Unit,
    onMove: () -> Unit,
    onMoveOrder: (Int) -> Unit,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
) {
    var open by remember { mutableStateOf(false) }
    IconButton(onClick = { open = true }) { Icon(Icons.Default.MoreHoriz, contentDescription = "Set actions") }
    DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
        DropdownMenuItem(text = { Text("Rename") }, leadingIcon = { Icon(Icons.Default.Edit, null) }, onClick = { open = false; onRename() })
        DropdownMenuItem(text = { Text("Move") }, leadingIcon = { Icon(Icons.AutoMirrored.Filled.DriveFileMove, null) }, onClick = { open = false; onMove() })
        DropdownMenuItem(text = { Text("Move up") }, leadingIcon = { Icon(Icons.Default.KeyboardArrowUp, null) }, enabled = canMoveUp, onClick = { open = false; onMoveOrder(-1) })
        DropdownMenuItem(text = { Text("Move down") }, leadingIcon = { Icon(Icons.Default.KeyboardArrowDown, null) }, enabled = canMoveDown, onClick = { open = false; onMoveOrder(1) })
        DropdownMenuItem(text = { Text("Delete") }, leadingIcon = { Icon(Icons.Default.DeleteOutline, null) }, onClick = { open = false; onDelete() })
    }
}

@Composable
private fun NameSheet(title: String, label: String, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var name by rememberSaveable { mutableStateOf("") }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().padding(horizontal = ScreenPadding).padding(bottom = Spacing.XXL), verticalArrangement = Arrangement.spacedBy(Spacing.L)) {
            Text(title, style = MaterialTheme.typography.headlineMedium)
            OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text(label) }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Button(onClick = { onSave(name) }, enabled = name.isNotBlank(), modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) { Text("Create") }
        }
    }
}

@Composable
private fun CreateSetSheet(collections: List<CollectionEntity>, onDismiss: () -> Unit, onSave: (String, Long?) -> Unit) {
    var name by rememberSaveable { mutableStateOf("") }
    var collectionId by rememberSaveable { mutableStateOf<Long?>(null) }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().padding(horizontal = ScreenPadding).padding(bottom = Spacing.XXL), verticalArrangement = Arrangement.spacedBy(Spacing.M)) {
            Text("New set", style = MaterialTheme.typography.headlineMedium)
            OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Text("Location", style = MaterialTheme.typography.titleMedium)
            LazyColumn(Modifier.fillMaxWidth().heightIn(max = 300.dp)) {
                item { LocationRow("Independent", collectionId == null) { collectionId = null } }
                items(collections, key = { it.id }) { collection -> LocationRow(collection.name, collectionId == collection.id) { collectionId = collection.id } }
            }
            Button(onClick = { onSave(name, collectionId) }, enabled = name.isNotBlank(), modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) { Text("Create") }
        }
    }
}

@Composable
private fun MoveSetSheet(set: FlashcardSetEntity, collections: List<CollectionEntity>, onDismiss: () -> Unit, onMove: (Long?) -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().padding(horizontal = ScreenPadding).padding(bottom = Spacing.XXL)) {
            Text("Move ${set.name}", style = MaterialTheme.typography.headlineMedium, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(bottom = Spacing.M))
            LazyColumn(Modifier.fillMaxWidth().heightIn(max = 420.dp)) {
                item { LocationRow("Independent", false) { onMove(null) } }
                items(collections, key = { it.id }) { collection -> LocationRow(collection.name, set.collectionId == collection.id) { onMove(collection.id) } }
            }
        }
    }
}

@Composable
private fun LocationRow(name: String, selected: Boolean, onClick: () -> Unit) {
    Surface(onClick = onClick, color = Color.Transparent, shape = RoundedCornerShape(Radius.Small)) {
        Row(Modifier.fillMaxWidth().padding(vertical = Spacing.M), verticalAlignment = Alignment.CenterVertically) {
            Text(name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (selected) Icon(Icons.Default.Check, contentDescription = "Selected", tint = BluePrimary)
        }
    }
}

@Composable
private fun RenameDialog(title: String, initial: String, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var name by rememberSaveable(initial) { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { OutlinedTextField(value = name, onValueChange = { name = it }, singleLine = true) },
        confirmButton = { TextButton(onClick = { onSave(name) }, enabled = name.isNotBlank()) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

internal fun preferredMeaning(persian: String?, english: String?, language: String): String? = when (language) {
    "فارسی" -> persian ?: english
    "English" -> english ?: persian
    else -> persian ?: english
}

internal fun wordCountLabel(count: Int): String = "$count ${if (count == 1) "word" else "words"}"

private fun String.containsPersian(): Boolean = any { it.code in 0x0600..0x06FF }
