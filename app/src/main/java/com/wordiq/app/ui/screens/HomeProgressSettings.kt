@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.wordiq.app.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.wordiq.app.LibraryUiState
import com.wordiq.app.data.ProgressSummary
import com.wordiq.app.data.UserPreferences
import com.wordiq.app.ui.components.AppCard
import com.wordiq.app.ui.components.EmptyState
import com.wordiq.app.ui.components.PrimaryPracticeButton
import com.wordiq.app.ui.components.ScreenPadding
import com.wordiq.app.ui.components.WordIqTopBar
import com.wordiq.app.ui.theme.BluePrimary
import com.wordiq.app.ui.theme.Radius
import com.wordiq.app.ui.theme.Spacing
import com.wordiq.app.ui.theme.TextSecondary

@Composable
fun HomeScreen(
    state: LibraryUiState,
    preferences: UserPreferences,
    onSettings: () -> Unit,
    onPractice: () -> Unit,
    onOpenLibrary: () -> Unit,
) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(ScreenPadding),
        verticalArrangement = Arrangement.spacedBy(Spacing.L),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Hei", style = MaterialTheme.typography.displaySmall)
            Spacer(Modifier.weight(1f))
            IconButton(onClick = onSettings) { Icon(Icons.Default.Settings, contentDescription = "Settings", tint = TextSecondary) }
        }
        PrimaryPracticeButton(minutes = preferences.dailyMinutes, onClick = onPractice)
        if (!state.loading && state.allSets.isEmpty()) {
            AppCard {
                EmptyState("Start with your first set", Modifier.padding(vertical = 0.dp), "Open Library", onOpenLibrary)
            }
        }
    }
}

@Composable
fun ProgressScreen(progress: ProgressSummary) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(ScreenPadding),
        verticalArrangement = Arrangement.spacedBy(Spacing.L),
    ) {
        Text("Progress", style = MaterialTheme.typography.headlineLarge)
        AppCard {
            Text(wordCountLabel(progress.totalConcepts), style = MaterialTheme.typography.headlineMedium)
            if (progress.totalConcepts > 0) {
                Text("${progress.learningConcepts} learning · ${progress.strongConcepts} strong", color = TextSecondary, modifier = Modifier.padding(top = Spacing.XS))
            } else {
                Text("No practice yet", color = TextSecondary, modifier = Modifier.padding(top = Spacing.XS))
            }
        }
        Text("Skills", style = MaterialTheme.typography.titleLarge)
        AppCard {
            ProgressRow("Recognize", progress.recognizedConcepts)
            ProgressRow("Recall Finnish", progress.recalledConcepts)
            ProgressRow("Listen", progress.listenedConcepts)
        }
        Text("This week", style = MaterialTheme.typography.titleLarge)
        AppCard {
            ProgressRow("Practice days", progress.practiceDays, "of 7")
            ProgressRow("Weak words", progress.weakConcepts)
        }
    }
}

@Composable
private fun ProgressRow(label: String, value: Int, suffix: String = "words") {
    Row(Modifier.fillMaxWidth().padding(vertical = Spacing.M), verticalAlignment = Alignment.CenterVertically) {
        Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        val detail = if (suffix == "words") wordCountLabel(value) else "$value $suffix"
        Text(detail, color = TextSecondary)
    }
}

@Composable
fun SettingsScreen(
    preferences: UserPreferences,
    onBack: () -> Unit,
    onDailyMinutes: (Int) -> Unit,
    onLanguage: (String) -> Unit,
    onHaptics: (Boolean) -> Unit,
    onAudio: (Boolean) -> Unit,
    onAutoPronounce: (Boolean) -> Unit,
    onExportCsv: (Uri) -> Unit,
    onImportCsv: (Uri) -> Unit,
    onExportBackup: (Uri) -> Unit,
    onRestoreBackup: (Uri) -> Unit,
    onResetProgress: () -> Unit,
) {
    var restoreUri by remember { mutableStateOf<Uri?>(null) }
    var confirmReset by remember { mutableStateOf(false) }
    val exportCsv = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { it?.let(onExportCsv) }
    val importCsv = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { it?.let(onImportCsv) }
    val exportBackup = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { it?.let(onExportBackup) }
    val importBackup = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { restoreUri = it }

    Scaffold(topBar = { WordIqTopBar("Settings", onBack) }) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(ScreenPadding),
            verticalArrangement = Arrangement.spacedBy(Spacing.L),
        ) {
            SettingsHeading("Learning")
            AppCard {
                Text("Daily practice", style = MaterialTheme.typography.titleMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalArrangement = Arrangement.spacedBy(Spacing.S), modifier = Modifier.padding(top = Spacing.S)) {
                    listOf(5, 10, 15, 20).forEach { minutes ->
                        FilterChip(preferences.dailyMinutes == minutes, { onDailyMinutes(minutes) }, { Text("$minutes min") })
                    }
                }
                Text("Language", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = Spacing.L))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalArrangement = Arrangement.spacedBy(Spacing.S), modifier = Modifier.padding(top = Spacing.S)) {
                    listOf("فارسی", "English", "Both").forEach { language ->
                        FilterChip(preferences.explanationLanguage == language, { onLanguage(language) }, { Text(language) })
                    }
                }
            }

            SettingsHeading("Sound & feel")
            AppCard {
                ToggleRow("Audio", "Finnish pronunciation", preferences.audioEnabled, onAudio)
                ToggleRow(
                    "Automatic pronunciation",
                    "Play Finnish during practice",
                    preferences.autoPronounceEnabled,
                    onAutoPronounce,
                    enabled = preferences.audioEnabled,
                )
                ToggleRow("Haptics", "Subtle answer feedback", preferences.hapticsEnabled, onHaptics)
            }

            SettingsHeading("Data")
            AppCard {
                SettingsAction("Import CSV", "Add words from a spreadsheet", Icons.Default.Download) {
                    importCsv.launch(arrayOf("text/csv", "text/comma-separated-values", "text/plain"))
                }
                SettingsAction("Export CSV", "Open your words elsewhere", Icons.Default.Upload) {
                    exportCsv.launch("wordiq-vocabulary.csv")
                }
                SettingsAction("Backup", "Save everything", Icons.Default.Backup) {
                    exportBackup.launch("wordiq-backup.json")
                }
                SettingsAction("Restore", "Replace data from a backup", Icons.Default.Restore) {
                    importBackup.launch(arrayOf("application/json", "text/plain"))
                }
                SettingsAction("Reset progress", "Keep words and sets", Icons.Default.RestartAlt) { confirmReset = true }
            }
            Spacer(Modifier.height(Spacing.XL))
        }
    }

    restoreUri?.let { uri ->
        AlertDialog(
            onDismissRequest = { restoreUri = null },
            title = { Text("Restore backup?") },
            text = { Text("This replaces your current library and learning history.") },
            confirmButton = { TextButton(onClick = { restoreUri = null; onRestoreBackup(uri) }) { Text("Restore") } },
            dismissButton = { TextButton(onClick = { restoreUri = null }) { Text("Cancel") } },
        )
    }
    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            title = { Text("Reset progress?") },
            text = { Text("Words and sets stay. Learning history starts fresh.") },
            confirmButton = { TextButton(onClick = { confirmReset = false; onResetProgress() }) { Text("Reset") } },
            dismissButton = { TextButton(onClick = { confirmReset = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun SettingsHeading(text: String) {
    Text(text, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(start = Spacing.XS))
}

@Composable
private fun ToggleRow(
    title: String,
    detail: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
    enabled: Boolean = true,
) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = Spacing.M).alpha(if (enabled) 1f else 0.55f),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(detail, color = TextSecondary, style = MaterialTheme.typography.bodyMedium)
        }
        Switch(checked = checked, onCheckedChange = onChange, enabled = enabled)
    }
}

@Composable
private fun SettingsAction(title: String, detail: String, icon: ImageVector, onClick: () -> Unit) {
    Surface(onClick = onClick, color = Color.Transparent, shape = RoundedCornerShape(Radius.Small)) {
        Row(
            Modifier.fillMaxWidth().padding(vertical = Spacing.M),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.M),
        ) {
            Icon(icon, contentDescription = null, tint = BluePrimary)
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(detail, color = TextSecondary, style = MaterialTheme.typography.bodyMedium)
            }
            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = TextSecondary)
        }
    }
}
