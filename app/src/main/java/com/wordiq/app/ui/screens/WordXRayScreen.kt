@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.wordiq.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.wordiq.app.data.local.ConceptWithDetails
import com.wordiq.app.ui.components.AppCard
import com.wordiq.app.ui.components.ScreenPadding
import com.wordiq.app.ui.components.WordIqTopBar
import com.wordiq.app.ui.theme.BluePrimary
import com.wordiq.app.ui.theme.TextSecondary

@Composable
fun WordXRayScreen(
    details: ConceptWithDetails?,
    formId: Long,
    onBack: () -> Unit,
    onPlayAudio: (String) -> Unit,
) {
    val form = details?.forms?.firstOrNull { it.id == formId }
    Scaffold(
        topBar = {
            WordIqTopBar(
                title = "Word X-Ray",
                onBack = onBack,
                actions = {
                    form?.let {
                        IconButton(onClick = { onPlayAudio(it.form) }) {
                            Icon(Icons.AutoMirrored.Filled.VolumeUp, contentDescription = "Play Finnish form")
                        }
                    }
                },
            )
        },
    ) { padding ->
        if (details == null || form == null) {
            Column(Modifier.padding(padding).padding(ScreenPadding)) { Text("Loading…", color = TextSecondary) }
            return@Scaffold
        }
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(ScreenPadding),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(form.form, style = MaterialTheme.typography.displaySmall, textAlign = TextAlign.Center)
            Text("↓", color = BluePrimary, style = MaterialTheme.typography.headlineMedium)
            Text(details.concept.finnishLemma, style = MaterialTheme.typography.headlineLarge)
            details.concept.englishTranslations?.let { Text(it, color = TextSecondary, textAlign = TextAlign.Center) }
            details.concept.persianTranslations?.let {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    Text(it, color = TextSecondary, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                }
            }
            Text("↓", color = BluePrimary, style = MaterialTheme.typography.headlineMedium)
            AppCard {
                Text(
                    form.decomposition ?: form.form,
                    style = MaterialTheme.typography.headlineSmall,
                    color = BluePrimary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
                form.grammaticalLabel?.let { Text(it, color = TextSecondary, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) }
                form.explanation?.let { Text(it, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(top = 6.dp)) }
                form.persianMeaning?.let {
                    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                        Text(it, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
                    }
                }
                form.persianNote?.let {
                    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                        Text(it, color = TextSecondary, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
                    }
                }
            }
        }
    }
}
