package com.wordiq.app.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.wordiq.app.ui.components.AppCard
import com.wordiq.app.ui.components.ScreenPadding
import com.wordiq.app.ui.theme.BluePrimary
import com.wordiq.app.ui.theme.BlueSoft
import com.wordiq.app.ui.theme.TextSecondary

@Composable
fun OnboardingScreen(onComplete: (String, Int) -> Unit) {
    var page by rememberSaveable { mutableIntStateOf(0) }
    var language by rememberSaveable { mutableStateOf("Both") }
    var minutes by rememberSaveable { mutableIntStateOf(5) }
    BackHandler(enabled = page > 0) { page-- }

    Column(Modifier.fillMaxSize().padding(ScreenPadding)) {
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(Modifier.fillMaxWidth().height(48.dp)) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 48.dp).align(Alignment.Center),
                    horizontalArrangement = Arrangement.spacedBy(7.dp),
                ) {
                    repeat(3) { index ->
                        Surface(
                            modifier = Modifier.height(7.dp).weight(1f),
                            shape = CircleShape,
                            color = if (index <= page) BluePrimary else BlueSoft,
                        ) {}
                    }
                }
                if (page > 0) {
                    IconButton(onClick = { page-- }, modifier = Modifier.size(48.dp).align(Alignment.CenterStart)) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Previous step")
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                when (page) {
                    0 -> "Finnish"
                    1 -> "Explanations"
                    else -> "Daily practice"
                },
                style = MaterialTheme.typography.headlineLarge,
            )
            Text(
                when (page) {
                    0 -> "Build useful vocabulary."
                    1 -> "Choose the language that helps you learn."
                    else -> "Choose a comfortable pace."
                },
                color = TextSecondary,
                style = MaterialTheme.typography.bodyLarge,
            )
            Spacer(Modifier.height(16.dp))
            when (page) {
                0 -> AppCard {
                    Text("Finnish", style = MaterialTheme.typography.headlineMedium)
                    Text("Suomi", color = TextSecondary)
                }
                1 -> Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    listOf("فارسی", "English", "Both").forEach { option ->
                        FilterChip(
                            selected = language == option,
                            onClick = { language = option },
                            label = { Text(option) },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
                else -> Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    listOf(5, 10, 15, 20).forEach { option ->
                        FilterChip(
                            selected = minutes == option,
                            onClick = { minutes = option },
                            label = { Text("$option minutes") },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        }
        Button(
            onClick = { if (page < 2) page++ else onComplete(language, minutes) },
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp).height(56.dp),
        ) {
            Text(if (page == 2) "Start" else "Continue")
        }
    }
}
