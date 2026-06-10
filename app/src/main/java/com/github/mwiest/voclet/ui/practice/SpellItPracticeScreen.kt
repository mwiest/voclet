package com.github.mwiest.voclet.ui.practice

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeContentPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController
import androidx.window.core.layout.WindowSizeClass
import com.github.mwiest.voclet.R
import com.github.mwiest.voclet.data.database.WordPair
import com.github.mwiest.voclet.ui.components.TtsErrorDialog
import com.github.mwiest.voclet.ui.components.TtsToggleButton
import com.github.mwiest.voclet.ui.theme.LocalExtendedColors
import com.github.mwiest.voclet.ui.theme.VocletTheme

// Level 1: Container
@Composable
fun SpellItPracticeScreen(
    navController: NavController,
    windowSizeClass: WindowSizeClass = currentWindowAdaptiveInfo().windowSizeClass,
    viewModel: SpellItPracticeViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val isTtsEnabled by viewModel.ttsDelegate.isTtsEnabled.collectAsState()
    val ttsError by viewModel.ttsDelegate.errorToShow.collectAsState()
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        viewModel.initializeSession()
    }

    SpellItPracticeScreen(
        navController = navController,
        windowSizeClass = windowSizeClass,
        uiState = uiState,
        isTtsEnabled = isTtsEnabled,
        onInputChange = viewModel::onInputChange,
        onSubmit = viewModel::submit,
        onSkip = viewModel::skip,
        onNext = viewModel::next,
        onResetPractice = viewModel::resetPractice,
        onToggleTts = { viewModel.ttsDelegate.toggle() }
    )

    val error = ttsError
    if (error != null) {
        TtsErrorDialog(
            error = error,
            onDismiss = { viewModel.ttsDelegate.dismissError() },
            onFix = { intent ->
                viewModel.ttsDelegate.onFixStarted()
                context.startActivity(intent)
            }
        )
    }
}

// Level 2: State Management
@Composable
fun SpellItPracticeScreen(
    navController: NavController,
    windowSizeClass: WindowSizeClass,
    uiState: SpellItUiState,
    isTtsEnabled: Boolean = true,
    onInputChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onSkip: () -> Unit,
    onNext: () -> Unit,
    onResetPractice: () -> Unit = {},
    onToggleTts: () -> Unit = {}
) {
    if (uiState.practiceComplete) {
        PracticeResultsScreen(
            navController = navController,
            windowSizeClass = windowSizeClass,
            correctCount = uiState.correctCount,
            incorrectCount = uiState.incorrectCount,
            onPracticeAgain = onResetPractice,
            onBackToHome = { navController.navigate("home") { popUpTo("home") } }
        )
    } else {
        SpellItPracticeContent(
            navController = navController,
            uiState = uiState,
            isTtsEnabled = isTtsEnabled,
            onInputChange = onInputChange,
            onSubmit = onSubmit,
            onSkip = onSkip,
            onNext = onNext,
            onToggleTts = onToggleTts
        )
    }
}

// Level 3: Content
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SpellItPracticeContent(
    navController: NavController,
    uiState: SpellItUiState,
    isTtsEnabled: Boolean,
    onInputChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onSkip: () -> Unit,
    onNext: () -> Unit,
    onToggleTts: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "${uiState.currentIndex + 1} / ${uiState.wordPairs.size} ${
                            stringResource(R.string.spell_it)
                        }",
                        style = MaterialTheme.typography.titleMedium
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { navController.navigateUp() }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back)
                        )
                    }
                },
                actions = {
                    TtsToggleButton(isEnabled = isTtsEnabled, onToggle = onToggleTts)
                }
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            if (uiState.isLoading) {
                // Empty body during load
            } else if (uiState.wordPairs.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(R.string.no_words_to_practice),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                val pair = uiState.currentPair ?: return@Box
                SpellItSession(
                    pair = pair,
                    uiState = uiState,
                    onInputChange = onInputChange,
                    onSubmit = onSubmit,
                    onSkip = onSkip,
                    onNext = onNext
                )
            }
        }
    }
}

@Composable
private fun SpellItSession(
    pair: WordPair,
    uiState: SpellItUiState,
    onInputChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onSkip: () -> Unit,
    onNext: () -> Unit
) {
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val successColors = LocalExtendedColors.current.success

    // Auto-focus and re-focus on every new word.
    LaunchedEffect(uiState.currentIndex) {
        if (uiState.submission == null) {
            focusRequester.requestFocus()
            keyboard?.show()
        }
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .imePadding()
    ) {
        // Adapt typography + spacing to available vertical room. When the keyboard
        // opens, maxHeight shrinks (imePadding consumes the IME inset), so we trade
        // the large prompt + generous gap for a compact layout that keeps the
        // field visible without scrolling.
        val compact = maxHeight < 480.dp
        val promptStyle = if (compact) {
            MaterialTheme.typography.headlineMedium
        } else {
            MaterialTheme.typography.displaySmall
        }
        val verticalPadding = if (compact) 12.dp else 32.dp
        val sectionGap = if (compact) 16.dp else 48.dp

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .safeContentPadding()
                .padding(horizontal = 24.dp, vertical = verticalPadding),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Top
        ) {
            // Prompt
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = if (compact) 4.dp else 16.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = pair.word1,
                    style = promptStyle.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center
                )
            }

            Spacer(modifier = Modifier.height(sectionGap))

            // Input field (when awaiting input) or feedback box (after submit)
            val submission = uiState.submission
            when (submission) {
            null -> {
                OutlinedTextField(
                    value = uiState.userInput,
                    onValueChange = onInputChange,
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 480.dp)
                        .focusRequester(focusRequester),
                    singleLine = true,
                    textStyle = MaterialTheme.typography.headlineSmall.copy(
                        textAlign = TextAlign.Center
                    ),
                    keyboardOptions = KeyboardOptions(
                        autoCorrectEnabled = false,
                        keyboardType = KeyboardType.Text,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(
                        onDone = { if (uiState.userInput.isNotBlank()) onSubmit() }
                    )
                )
            }
            is SpellItSubmission.Correct -> {
                FeedbackBox(
                    backgroundColor = successColors.colorContainer,
                    contentColor = successColors.onColorContainer
                ) {
                    Text(
                        text = submission.canonical,
                        style = MaterialTheme.typography.headlineSmall.copy(
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        ),
                        color = successColors.onColorContainer
                    )
                }
            }
            is SpellItSubmission.Wrong -> {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    FeedbackBox(
                        backgroundColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer
                    ) {
                        val annotated = SpellItDiff.render(
                            ops = submission.diff,
                            errorColor = MaterialTheme.colorScheme.error,
                            matchColor = MaterialTheme.colorScheme.onErrorContainer
                        )
                        Text(
                            text = annotated,
                            style = MaterialTheme.typography.headlineSmall.copy(
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center
                            )
                        )
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = submission.canonical,
                        style = MaterialTheme.typography.headlineSmall.copy(
                            fontWeight = FontWeight.Bold
                        ),
                        color = successColors.color,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }

            Spacer(modifier = Modifier.height(sectionGap))

            // Action button
            when (submission) {
            null -> {
                val hasContent = uiState.userInput.isNotBlank()
                Button(
                    onClick = { if (hasContent) onSubmit() else onSkip() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 320.dp)
                        .height(56.dp),
                    colors = if (hasContent) {
                        ButtonDefaults.buttonColors()
                    } else {
                        ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                            contentColor = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                    }
                ) {
                    Text(
                        text = stringResource(if (hasContent) R.string.check else R.string.skip),
                        style = MaterialTheme.typography.titleMedium
                    )
                }
            }
            is SpellItSubmission.Correct -> {
                // No button - auto-advances
            }
            is SpellItSubmission.Wrong -> {
                Button(
                    onClick = onNext,
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 320.dp)
                        .height(56.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                ) {
                    Text(
                        text = stringResource(R.string.next),
                        style = MaterialTheme.typography.titleMedium
                    )
                }
            }
        }
    }
    }
}

@Composable
private fun FeedbackBox(
    backgroundColor: androidx.compose.ui.graphics.Color,
    contentColor: androidx.compose.ui.graphics.Color,
    content: @Composable () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .widthIn(max = 480.dp)
            .background(
                color = backgroundColor,
                shape = RoundedCornerShape(12.dp)
            )
            .padding(vertical = 16.dp, horizontal = 24.dp),
        contentAlignment = Alignment.Center
    ) {
        content()
    }
}

// --- Previews ----------------------------------------------------------------

private val previewPair = WordPair(
    id = 1,
    wordListId = 1,
    word1 = "the key",
    word2 = "la clé"
)

@Preview(showBackground = true, widthDp = 450, heightDp = 800)
@Composable
private fun SpellItPracticePreviewEmptyInput() {
    VocletTheme {
        SpellItPracticeScreen(
            navController = rememberNavController(),
            windowSizeClass = currentWindowAdaptiveInfo().windowSizeClass,
            uiState = SpellItUiState(
                isLoading = false,
                sessionInitialized = true,
                wordPairs = listOf(previewPair),
                userInput = ""
            ),
            onInputChange = {},
            onSubmit = {},
            onSkip = {},
            onNext = {}
        )
    }
}

@Preview(showBackground = true, widthDp = 450, heightDp = 800)
@Composable
private fun SpellItPracticePreviewTyping() {
    VocletTheme {
        SpellItPracticeScreen(
            navController = rememberNavController(),
            windowSizeClass = currentWindowAdaptiveInfo().windowSizeClass,
            uiState = SpellItUiState(
                isLoading = false,
                sessionInitialized = true,
                wordPairs = listOf(previewPair),
                userInput = "la cl"
            ),
            onInputChange = {},
            onSubmit = {},
            onSkip = {},
            onNext = {}
        )
    }
}

@Preview(showBackground = true, widthDp = 450, heightDp = 800)
@Composable
private fun SpellItPracticePreviewCorrect() {
    VocletTheme {
        SpellItPracticeScreen(
            navController = rememberNavController(),
            windowSizeClass = currentWindowAdaptiveInfo().windowSizeClass,
            uiState = SpellItUiState(
                isLoading = false,
                sessionInitialized = true,
                wordPairs = listOf(previewPair),
                userInput = "la clé",
                submission = SpellItSubmission.Correct("la clé"),
                correctCount = 1
            ),
            onInputChange = {},
            onSubmit = {},
            onSkip = {},
            onNext = {}
        )
    }
}

@Preview(showBackground = true, widthDp = 450, heightDp = 800)
@Composable
private fun SpellItPracticePreviewWrong() {
    VocletTheme {
        val ops = SpellItDiff.diff("la clé", "la cle")
        SpellItPracticeScreen(
            navController = rememberNavController(),
            windowSizeClass = currentWindowAdaptiveInfo().windowSizeClass,
            uiState = SpellItUiState(
                isLoading = false,
                sessionInitialized = true,
                wordPairs = listOf(previewPair),
                userInput = "la cle",
                submission = SpellItSubmission.Wrong(ops, "la clé"),
                incorrectCount = 1
            ),
            onInputChange = {},
            onSubmit = {},
            onSkip = {},
            onNext = {}
        )
    }
}
