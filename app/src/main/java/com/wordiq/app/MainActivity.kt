package com.wordiq.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.viewmodel.compose.viewModel
import com.wordiq.app.ui.WordIqApp
import com.wordiq.app.ui.theme.WordIqTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            WordIqTheme {
                val application = application as WordIqApplication
                val wordIqViewModel: WordIqViewModel = viewModel(factory = WordIqViewModel.factory(application.container))
                WordIqApp(wordIqViewModel)
            }
        }
    }
}
