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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Timelapse
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.wordiq.app.data.local.CollectionWithSets
import com.wordiq.app.data.local.SetProgressSnapshot
import com.wordiq.app.ui.components.AppCard
import com.wordiq.app.ui.components.PrimaryPracticeButton
import com.wordiq.app.ui.components.ScreenPadding
import com.wordiq.app.ui.components.WordIqTopBar
import com.wordiq.app.ui.theme.BluePrimary
import com.wordiq.app.ui.theme.Spacing
import com.wordiq.app.ui.theme.TextSecondary

@Composable
fun CollectionScreen(
    item: CollectionWithSets?,
    progress: List<SetProgressSnapshot>,
    onBack: () -> Unit,
    onPractice: () -> Unit,
    onOpenSet: (Long) -> Unit,
    onCreateSet: (String) -> Unit,
) {
    var addingSet by rememberSaveable { mutableStateOf(false) }
    Scaffold(
        topBar = {
            WordIqTopBar(item?.collection?.name.orEmpty(), onBack) {
                if (item?.sets?.isNotEmpty() == true) {
                    IconButton(onClick = { addingSet = true }) { Icon(Icons.Default.Add, contentDescription = "Add set") }
                }
            }
        },
    ) { padding ->
        if (item == null) return@Scaffold
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(ScreenPadding),
            verticalArrangement = Arrangement.spacedBy(Spacing.M),
        ) {
            item {
                if (item.sets.isEmpty()) {
                    PrimaryPracticeButton(minutes = null, onClick = { addingSet = true }, label = "Add first set", icon = Icons.Default.Add)
                } else {
                    PrimaryPracticeButton(minutes = null, onClick = onPractice)
                }
            }
            if (item.sets.isNotEmpty()) {
                item {
                    Text("${item.sets.size} ${if (item.sets.size == 1) "set" else "sets"}", color = TextSecondary)
                    Text("Sets", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = Spacing.L))
                }
                items(item.sets.sortedBy { it.sortOrder }, key = { it.id }) { set ->
                    val setProgress = progress.firstOrNull { it.setId == set.id }
                    val state = setStatus(setProgress)
                    AppCard(onClick = { onOpenSet(set.id) }) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Icon(state.icon, contentDescription = state.label, tint = if (state.strong) BluePrimary else TextSecondary)
                            Column(Modifier.weight(1f).padding(horizontal = Spacing.M)) {
                                Text(set.name, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                Text(state.detail, color = TextSecondary)
                            }
                            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = TextSecondary)
                        }
                    }
                }
            }
            item { Spacer(Modifier.padding(bottom = Spacing.L)) }
        }
    }
    if (addingSet) {
        NewCollectionSetSheet(
            onDismiss = { addingSet = false },
            onCreate = { onCreateSet(it); addingSet = false },
        )
    }
}

private data class CollectionSetStatus(val label: String, val detail: String, val icon: ImageVector, val strong: Boolean = false)

private fun setStatus(progress: SetProgressSnapshot?): CollectionSetStatus = when {
    progress == null || progress.wordCount == 0 -> CollectionSetStatus("Empty", "No words", Icons.Default.RadioButtonUnchecked)
    progress.reviewedCount == 0 -> CollectionSetStatus("New", "${wordCountLabel(progress.wordCount)} · New", Icons.Default.RadioButtonUnchecked)
    progress.readyCount > 0 -> CollectionSetStatus("Ready", "${wordCountLabel(progress.wordCount)} · ${progress.readyCount} ready", Icons.Default.Timelapse)
    progress.mastery >= 0.75 -> CollectionSetStatus("Strong", "${wordCountLabel(progress.wordCount)} · Strong", Icons.Default.CheckCircle, true)
    else -> CollectionSetStatus("Learning", "${wordCountLabel(progress.wordCount)} · Learning", Icons.Default.Timelapse)
}

@Composable
private fun NewCollectionSetSheet(onDismiss: () -> Unit, onCreate: (String) -> Unit) {
    var name by rememberSaveable { mutableStateOf("") }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = ScreenPadding).padding(bottom = Spacing.XXL),
            verticalArrangement = Arrangement.spacedBy(Spacing.L),
        ) {
            Text("New set", style = MaterialTheme.typography.headlineMedium)
            OutlinedTextField(name, { name = it }, label = { Text("Name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Button(onClick = { onCreate(name) }, enabled = name.isNotBlank(), modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
                Text("Create")
            }
        }
    }
}
