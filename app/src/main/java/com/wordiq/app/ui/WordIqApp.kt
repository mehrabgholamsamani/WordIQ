package com.wordiq.app.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.LibraryBooks
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.wordiq.app.WordIqViewModel
import com.wordiq.app.PracticeViewModel
import com.wordiq.app.audio.AndroidFinnishTtsPlayer
import com.wordiq.app.learning.PracticeFocus
import com.wordiq.app.learning.PracticeRequest
import com.wordiq.app.learning.PracticeScopeType
import com.wordiq.app.ui.screens.CollectionScreen
import com.wordiq.app.ui.screens.HomeScreen
import com.wordiq.app.ui.screens.OnboardingScreen
import com.wordiq.app.ui.screens.LibraryScreen
import com.wordiq.app.ui.screens.ProgressScreen
import com.wordiq.app.ui.screens.PracticeScreen
import com.wordiq.app.ui.screens.SetScreen
import com.wordiq.app.ui.screens.SettingsScreen
import com.wordiq.app.ui.screens.WordDetailScreen
import com.wordiq.app.ui.screens.WordEditorScreen
import com.wordiq.app.ui.screens.WordXRayScreen
import kotlinx.coroutines.flow.flowOf
import com.wordiq.app.ui.theme.BluePrimary
import com.wordiq.app.ui.theme.BlueSoft
import com.wordiq.app.ui.theme.BackgroundPrimary
import com.wordiq.app.ui.theme.SurfaceElevated
import com.wordiq.app.ui.theme.TextSecondary

private object Routes {
    const val HOME = "home"
    const val LIBRARY = "library"
    const val PROGRESS = "progress"
    const val SETTINGS = "settings"
    const val SET = "set/{setId}"
    const val COLLECTION = "collection/{collectionId}"
    const val WORD = "word/{wordId}"
    const val XRAY = "xray/{wordId}/{formId}"
    const val EDITOR = "editor?wordId={wordId}&setId={setId}"
    const val PRACTICE = "practice/{scope}/{scopeId}/{manual}/{focus}"

    fun set(id: Long) = "set/$id"
    fun collection(id: Long) = "collection/$id"
    fun word(id: Long) = "word/$id"
    fun xray(wordId: Long, formId: Long) = "xray/$wordId/$formId"
    fun editor(wordId: Long = 0, setId: Long = -1) = "editor?wordId=$wordId&setId=$setId"
    fun practice(
        scope: PracticeScopeType,
        id: Long = -1,
        manual: Boolean = false,
        focus: PracticeFocus = PracticeFocus.SMART,
    ) = "practice/${scope.name}/$id/$manual/${focus.name}"
}

private data class PrimaryDestination(val route: String, val label: String)

@Composable
fun WordIqApp(viewModel: WordIqViewModel) {
    val context = LocalContext.current
    val pronunciationPlayer = remember { AndroidFinnishTtsPlayer(context.applicationContext) }
    DisposableEffect(pronunciationPlayer) {
        onDispose { pronunciationPlayer.release() }
    }
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val primaryRoutes = setOf(Routes.HOME, Routes.LIBRARY, Routes.PROGRESS)
    val destinations = listOf(
        PrimaryDestination(Routes.HOME, "Home"),
        PrimaryDestination(Routes.LIBRARY, "Library"),
        PrimaryDestination(Routes.PROGRESS, "Progress"),
    )
    val library by viewModel.library.collectAsStateWithLifecycle()
    val preferences by viewModel.preferences.collectAsStateWithLifecycle()
    val preferencesLoaded by viewModel.preferencesLoaded.collectAsStateWithLifecycle()
    val progress by viewModel.progress.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val searchResults by viewModel.searchResults.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    if (!preferencesLoaded) {
        Surface(Modifier.fillMaxSize(), color = BackgroundPrimary) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("WordIQ", color = TextSecondary, style = androidx.compose.material3.MaterialTheme.typography.headlineMedium)
            }
        }
        return
    }
    if (!preferences.onboardingCompleted) {
        OnboardingScreen(onComplete = viewModel::completeOnboarding)
        return
    }

    viewModel.notice?.let { notice ->
        LaunchedEffect(notice) {
            val result = snackbarHostState.showSnackbar(
                message = notice.message,
                actionLabel = notice.actionLabel,
                duration = SnackbarDuration.Short,
            )
            if (result == SnackbarResult.ActionPerformed) viewModel.performNoticeAction()
            else viewModel.consumeNotice()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            if (currentRoute in primaryRoutes) {
                NavigationBar(containerColor = SurfaceElevated, tonalElevation = 0.dp) {
                    destinations.forEachIndexed { index, destination ->
                        val icon = when (index) {
                            0 -> Icons.Default.Home
                            1 -> Icons.AutoMirrored.Filled.LibraryBooks
                            else -> Icons.Default.Insights
                        }
                        NavigationBarItem(
                            selected = currentRoute == destination.route,
                            onClick = {
                                navController.navigate(destination.route) {
                                    popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(icon, contentDescription = destination.label) },
                            label = { Text(destination.label) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = BluePrimary,
                                selectedTextColor = BluePrimary,
                                indicatorColor = BlueSoft,
                                unselectedIconColor = TextSecondary,
                                unselectedTextColor = TextSecondary,
                            ),
                        )
                    }
                }
            }
        },
    ) { outerPadding ->
        NavHost(
            navController = navController,
            startDestination = Routes.HOME,
            modifier = Modifier.padding(outerPadding),
        ) {
            composable(Routes.HOME) {
                HomeScreen(
                    state = library,
                    preferences = preferences,
                    onSettings = { navController.navigate(Routes.SETTINGS) },
                    onPractice = { navController.navigate(Routes.practice(PracticeScopeType.GLOBAL)) },
                    onOpenLibrary = { navController.navigate(Routes.LIBRARY) },
                )
            }
            composable(Routes.LIBRARY) {
                LibraryScreen(
                    state = library,
                    searchQuery = searchQuery,
                    searchResults = searchResults,
                    explanationLanguage = preferences.explanationLanguage,
                    onSearchQuery = viewModel::updateSearchQuery,
                    onOpenWord = { navController.navigate(Routes.word(it)) },
                    onOpenCollection = { navController.navigate(Routes.collection(it)) },
                    onOpenSet = { navController.navigate(Routes.set(it)) },
                    onCreateCollection = viewModel::createCollection,
                    onRenameCollection = viewModel::renameCollection,
                    onDeleteCollection = viewModel::deleteCollection,
                    onMoveCollection = viewModel::moveCollection,
                    onCreateSet = viewModel::createSet,
                    onRenameSet = viewModel::renameSet,
                    onDeleteSet = viewModel::deleteSet,
                    onMoveSet = viewModel::moveSet,
                    onMoveSetOrder = viewModel::moveSetOrder,
                )
            }
            composable(Routes.PROGRESS) { ProgressScreen(progress) }
            composable(Routes.SETTINGS) {
                SettingsScreen(
                    preferences = preferences,
                    onBack = navController::popBackStack,
                    onDailyMinutes = viewModel::setDailyMinutes,
                    onLanguage = viewModel::setExplanationLanguage,
                    onHaptics = viewModel::setHapticsEnabled,
                    onAudio = viewModel::setAudioEnabled,
                    onAutoPronounce = viewModel::setAutoPronounceEnabled,
                    onExportCsv = viewModel::exportCsv,
                    onImportCsv = viewModel::importCsv,
                    onExportBackup = viewModel::exportBackup,
                    onRestoreBackup = viewModel::restoreBackup,
                    onResetProgress = viewModel::resetLearningProgress,
                )
            }
            composable(
                route = Routes.COLLECTION,
                arguments = listOf(navArgument("collectionId") { type = NavType.LongType }),
            ) { entry ->
                val collectionId = entry.arguments?.getLong("collectionId") ?: return@composable
                val collectionFlow = remember(collectionId) { viewModel.observeCollection(collectionId) }
                val collection by collectionFlow.collectAsStateWithLifecycle(initialValue = null)
                val progressFlow = remember(collectionId) { viewModel.observeCollectionSetProgress(collectionId) }
                val collectionProgress by progressFlow.collectAsStateWithLifecycle(initialValue = emptyList())
                CollectionScreen(
                    item = collection,
                    progress = collectionProgress,
                    onBack = navController::popBackStack,
                    onPractice = { navController.navigate(Routes.practice(PracticeScopeType.COLLECTION, collectionId)) },
                    onOpenSet = { navController.navigate(Routes.set(it)) },
                    onCreateSet = { name -> viewModel.createSet(name, collectionId) },
                )
            }
            composable(
                route = Routes.SET,
                arguments = listOf(navArgument("setId") { type = NavType.LongType }),
            ) { entry ->
                val setId = entry.arguments?.getLong("setId") ?: return@composable
                val setFlow = remember(setId) { viewModel.observeSet(setId) }
                val set by setFlow.collectAsStateWithLifecycle(initialValue = null)
                val progressFlow = remember(setId) { viewModel.observeSetProgress(setId) }
                val setProgress by progressFlow.collectAsStateWithLifecycle(initialValue = null)
                SetScreen(
                    item = set,
                    progress = setProgress,
                    explanationLanguage = preferences.explanationLanguage,
                    onBack = navController::popBackStack,
                    onAddWord = { navController.navigate(Routes.editor(setId = setId)) },
                    onOpenWord = { navController.navigate(Routes.word(it)) },
                    onPractice = { focus, manual ->
                        navController.navigate(Routes.practice(PracticeScopeType.SET, setId, manual, focus))
                    },
                )
            }
            composable(
                route = Routes.PRACTICE,
                arguments = listOf(
                    navArgument("scope") { type = NavType.StringType },
                    navArgument("scopeId") { type = NavType.LongType },
                    navArgument("manual") { type = NavType.BoolType },
                    navArgument("focus") { type = NavType.StringType },
                ),
            ) { entry ->
                val scope = entry.arguments?.getString("scope")
                    ?.let { runCatching { PracticeScopeType.valueOf(it) }.getOrNull() }
                    ?: PracticeScopeType.GLOBAL
                val scopeId = (entry.arguments?.getLong("scopeId") ?: -1L).takeIf { it >= 0 }
                val manual = entry.arguments?.getBoolean("manual") ?: false
                val focus = entry.arguments?.getString("focus")
                    ?.let { runCatching { PracticeFocus.valueOf(it) }.getOrNull() }
                    ?: PracticeFocus.SMART
                val request = remember(scope, scopeId, manual, focus, preferences.dailyMinutes, preferences.explanationLanguage) {
                    PracticeRequest(
                        scopeType = scope,
                        scopeId = scopeId,
                        manual = manual,
                        dailyMinutes = preferences.dailyMinutes,
                        explanationLanguage = preferences.explanationLanguage,
                        focus = focus,
                    )
                }
                val practiceViewModel: PracticeViewModel = androidx.lifecycle.viewmodel.compose.viewModel(
                    key = "${scope.name}-$scopeId-$manual-${focus.name}",
                    factory = viewModel.practiceFactory(request),
                )
                DisposableEffect(practiceViewModel) {
                    onDispose { pronunciationPlayer.stop() }
                }
                val practiceState by practiceViewModel.uiState.collectAsStateWithLifecycle()
                val title = when (scope) {
                    PracticeScopeType.GLOBAL -> "Practice"
                    PracticeScopeType.SET -> library.allSets.firstOrNull { it.id == scopeId }?.name ?: "Set"
                    PracticeScopeType.COLLECTION -> library.collections.firstOrNull { it.collection.id == scopeId }?.collection?.name ?: "Collection"
                }
                PracticeScreen(
                    title = title,
                    state = practiceState,
                    hapticsEnabled = preferences.hapticsEnabled,
                    autoPronounceEnabled = preferences.audioEnabled && preferences.autoPronounceEnabled,
                    emptyMessage = practiceEmptyMessage(focus),
                    onClose = navController::popBackStack,
                    onAnswerChange = practiceViewModel::updateAnswer,
                    onSelectOption = practiceViewModel::selectOption,
                    onSubmit = practiceViewModel::submitTyped,
                    onRemembered = practiceViewModel::rememberedFreeRecall,
                    onRevealSelfCheck = practiceViewModel::revealSelfCheck,
                    onReveal = practiceViewModel::reveal,
                    onHint = practiceViewModel::useHint,
                    onContinueIntroduction = practiceViewModel::continueIntroduction,
                    onNext = practiceViewModel::next,
                    onRetry = practiceViewModel::retry,
                    onStudyAgain = practiceViewModel::studyAgain,
                    onPlayAudio = { text ->
                        practiceViewModel.registerAudioPlay()
                        if (!preferences.audioEnabled) viewModel.showNotice("Audio is turned off in Settings")
                        else pronunciationPlayer.speakFinnish(text) { viewModel.showNotice("Finnish voice isn’t installed on this device") }
                    },
                )
            }
            composable(
                route = Routes.WORD,
                arguments = listOf(navArgument("wordId") { type = NavType.LongType }),
            ) { entry ->
                val wordId = entry.arguments?.getLong("wordId") ?: return@composable
                val wordFlow = remember(wordId) { viewModel.observeConcept(wordId) }
                val details by wordFlow.collectAsStateWithLifecycle(initialValue = null)
                WordDetailScreen(
                    details = details,
                    onBack = navController::popBackStack,
                    onEdit = { navController.navigate(Routes.editor(wordId = wordId)) },
                    onDelete = { details?.let { viewModel.deleteWord(it, navController::popBackStack) } },
                    onPlayAudio = { word ->
                        if (!preferences.audioEnabled) viewModel.showNotice("Audio is turned off in Settings")
                        else pronunciationPlayer.speakFinnish(word) { viewModel.showNotice("Finnish voice isn’t installed on this device") }
                    },
                    onOpenXRay = { formId -> navController.navigate(Routes.xray(wordId, formId)) },
                )
            }
            composable(
                route = Routes.XRAY,
                arguments = listOf(
                    navArgument("wordId") { type = NavType.LongType },
                    navArgument("formId") { type = NavType.LongType },
                ),
            ) { entry ->
                val wordId = entry.arguments?.getLong("wordId") ?: return@composable
                val formId = entry.arguments?.getLong("formId") ?: return@composable
                val wordFlow = remember(wordId) { viewModel.observeConcept(wordId) }
                val details by wordFlow.collectAsStateWithLifecycle(initialValue = null)
                WordXRayScreen(
                    details = details,
                    formId = formId,
                    onBack = navController::popBackStack,
                    onPlayAudio = { text ->
                        if (!preferences.audioEnabled) viewModel.showNotice("Audio is turned off in Settings")
                        else pronunciationPlayer.speakFinnish(text) { viewModel.showNotice("Finnish voice isn’t installed on this device") }
                    },
                )
            }
            composable(
                route = Routes.EDITOR,
                arguments = listOf(
                    navArgument("wordId") { type = NavType.LongType; defaultValue = 0L },
                    navArgument("setId") { type = NavType.LongType; defaultValue = -1L },
                ),
            ) { entry ->
                val wordId = entry.arguments?.getLong("wordId") ?: 0L
                val setId = (entry.arguments?.getLong("setId") ?: -1L).takeIf { it >= 0 }
                val wordFlow = remember(wordId) {
                    if (wordId == 0L) flowOf(null) else viewModel.observeConcept(wordId)
                }
                val details by wordFlow.collectAsStateWithLifecycle(initialValue = null)
                WordEditorScreen(
                    wordId = wordId,
                    initialSetId = setId,
                    details = details,
                    allSets = library.allSets,
                    collections = library.collections.map { it.collection },
                    onBack = navController::popBackStack,
                    onCreateSet = { name -> viewModel.createSet(name, null) },
                    onSave = { draft, onFailure ->
                        viewModel.saveWord(
                            draft = draft,
                            onSaved = { savedId ->
                                navController.popBackStack()
                                if (wordId == 0L) navController.navigate(Routes.word(savedId))
                            },
                            onFailure = onFailure,
                        )
                    },
                )
            }
        }
    }
}

private fun practiceEmptyMessage(focus: PracticeFocus): String? = when (focus) {
    PracticeFocus.MULTIPLE_CHOICE_MEANING -> "Add at least two translated words to use multiple choice."
    PracticeFocus.MULTIPLE_CHOICE_PERSIAN -> "Add Persian meanings to at least two words."
    PracticeFocus.MULTIPLE_CHOICE_ENGLISH -> "Add English meanings to at least two words."
    PracticeFocus.SELF_CHECK_PERSIAN, PracticeFocus.SPELLING_PERSIAN -> "Add Persian meanings to words in this Set."
    PracticeFocus.SELF_CHECK_ENGLISH, PracticeFocus.SPELLING_ENGLISH -> "Add English meanings to words in this Set."
    PracticeFocus.SELF_CHECK_MEANING -> "Add meanings to words in this Set."
    else -> null
}
