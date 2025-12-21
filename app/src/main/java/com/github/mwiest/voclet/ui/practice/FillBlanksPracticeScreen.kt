package com.github.mwiest.voclet.ui.practice

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavController
import androidx.window.core.layout.WindowSizeClass
import com.github.mwiest.voclet.R
import kotlin.math.roundToInt

// Level 1: Container with ViewModel injection
@Composable
fun FillBlanksPracticeScreen(
    navController: NavController,
    windowSizeClass: WindowSizeClass = currentWindowAdaptiveInfo().windowSizeClass,
    viewModel: FillBlanksPracticeViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    FillBlanksPracticeScreen(
        navController = navController,
        windowSizeClass = windowSizeClass,
        uiState = uiState,
        onInitializeSession = { width, height, density -> viewModel.initializeSession(width, height, density) },
        onRotation = { width, height -> viewModel.handleRotation(width, height) },
        onDragStart = { letterId, position -> viewModel.handleDragStart(letterId, position) },
        onDragMove = { offset -> viewModel.handleDragMove(offset) },
        onDragEnd = { viewModel.handleDragEnd() },
        onResetPractice = { viewModel.resetPractice() }
    )
}

// Level 2: State Management
@Composable
fun FillBlanksPracticeScreen(
    navController: NavController,
    windowSizeClass: WindowSizeClass,
    uiState: FillBlanksPracticeUiState,
    onInitializeSession: (Dp, Dp, Float) -> Unit,
    onRotation: (Dp, Dp) -> Unit,
    onDragStart: (Int, Offset) -> Unit,
    onDragMove: (Offset) -> Unit,
    onDragEnd: () -> Unit,
    onResetPractice: () -> Unit
) {
    if (uiState.practiceComplete) {
        PracticeResultsScreen(
            navController = navController,
            windowSizeClass = windowSizeClass,
            correctCount = uiState.correctWordsCount,
            incorrectCount = uiState.incorrectWordsCount,
            onPracticeAgain = onResetPractice,
            onBackToHome = { navController.navigate("home") { popUpTo("home") } }
        )
    } else {
        FillBlanksPracticeContent(
            navController = navController,
            uiState = uiState,
            onInitializeSession = onInitializeSession,
            onRotation = onRotation,
            onDragStart = onDragStart,
            onDragMove = onDragMove,
            onDragEnd = onDragEnd
        )
    }
}

// Level 3: Content Rendering
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FillBlanksPracticeContent(
    navController: NavController,
    uiState: FillBlanksPracticeUiState,
    onInitializeSession: (Dp, Dp, Float) -> Unit,
    onRotation: (Dp, Dp) -> Unit,
    onDragStart: (Int, Offset) -> Unit,
    onDragMove: (Offset) -> Unit,
    onDragEnd: () -> Unit
) {
    val density = LocalDensity.current

    // Track screen dimensions to detect rotation
    var lastDimensions by remember { mutableStateOf<Pair<Dp, Dp>?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "${uiState.currentWordIndex + 1} / ${uiState.wordPairs.size} ${
                            stringResource(
                                id = R.string.fill_blanks
                            )
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
                }
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .onGloballyPositioned { coordinates ->
                    val widthDp = with(density) { coordinates.size.width.toDp() }
                    val heightDp = with(density) { coordinates.size.height.toDp() }
                    val currentDimensions = Pair(widthDp, heightDp)

                    // Initialize session when screen size is known
                    if (!uiState.sessionInitialized) {
                        onInitializeSession(widthDp, heightDp, density.density)
                    } else if (lastDimensions != currentDimensions) {
                        lastDimensions = currentDimensions
                        onRotation(widthDp, heightDp)
                    }
                }
        ) {
            if (!uiState.isLoading) {
                if (uiState.wordPairs.isEmpty()) {
                    // Empty state - no word pairs to practice
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
                    Column(modifier = Modifier.fillMaxSize()) {
                        // Top Section: Prompt word only
                        PromptSection(
                            prompt = uiState.currentPrompt,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp)
                        )

                        // Center Section: Solution word with letter slots
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            contentAlignment = Alignment.Center
                        ) {
                            SolutionSection(
                                solutionWord = uiState.currentWord,
                                letterSlotStates = uiState.letterSlotStates,
                                hoveredSlotIndex = uiState.hoveredSlotIndex
                            )
                        }

                        // Bottom Section: Draggable Letters
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(FILL_BLANKS_BOTTOM_SECTION_HEIGHT)
                        ) {
                            BottomSection(
                                draggableLetters = uiState.draggableLetters,
                                selectedLetterId = uiState.selectedLetterId,
                                dragPosition = uiState.dragPosition,
                                isUserBlocked = uiState.isUserBlocked,
                                onDragStart = onDragStart,
                                onDragMove = onDragMove,
                                onDragEnd = onDragEnd
                            )
                        }
                    }
                }
            }
        }
    }
}

// Prompt Section: Shows prompt word only
@Composable
private fun PromptSection(
    prompt: String,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(vertical = 16.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = prompt,
            style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center
        )
    }
}

// Solution Section: Centered letter slots showing word being built
@Composable
private fun SolutionSection(
    solutionWord: String,
    letterSlotStates: List<LetterSlotState>,
    hoveredSlotIndex: Int?
) {
    // Calculate max items per row based on available width
    androidx.compose.foundation.layout.BoxWithConstraints(
        modifier = Modifier.fillMaxWidth()
    ) {
        val sidePadding = 32.dp
        val cardSpacing = 8.dp  // 4.dp on each side
        val availableWidth = maxWidth - (sidePadding * 2)

        // Calculate how many cards can fit per row
        val maxItemsPerRow = ((availableWidth + cardSpacing) / (LETTER_CARD_SIZE + cardSpacing))
            .toInt()
            .coerceAtLeast(1)

        // Arrange letter slots horizontally in rows
        androidx.compose.foundation.layout.FlowRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = sidePadding),
            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
            verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(12.dp),
            maxItemsInEachRow = maxItemsPerRow
        ) {
            letterSlotStates.forEachIndexed { index, state ->
                val isHovered = index == hoveredSlotIndex

                Box(
                    modifier = Modifier.padding(horizontal = 4.dp)
                ) {
                    LetterSlotIcon(
                        letterSlotState = state,
                        isHovered = isHovered,
                        modifier = Modifier
                    )
                }
            }
        }
    }
}

// Letter Slot Icon Component - shows blank underscores or letter cards
@Composable
private fun LetterSlotIcon(
    letterSlotState: LetterSlotState,
    isHovered: Boolean,
    modifier: Modifier = Modifier
) {
    if (letterSlotState.placedLetter != null) {
        // Show as a card (pre-filled or user-placed)
        val backgroundColor = when {
            letterSlotState.isCorrect == true -> MaterialTheme.colorScheme.primaryContainer
            letterSlotState.isCorrect == false -> MaterialTheme.colorScheme.errorContainer
            else -> MaterialTheme.colorScheme.tertiaryContainer  // Pre-filled letters
        }

        Card(
            modifier = modifier.size(LETTER_CARD_SIZE),
            colors = CardDefaults.cardColors(containerColor = backgroundColor),
            shape = MaterialTheme.shapes.medium,
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = letterSlotState.placedLetter.toString(),
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    } else {
        // Empty slot - show with clear hover state
        Card(
            modifier = modifier.size(LETTER_CARD_SIZE),
            colors = CardDefaults.cardColors(
                containerColor = if (isHovered)
                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                else
                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
            ),
            shape = MaterialTheme.shapes.medium,
            elevation = CardDefaults.cardElevation(defaultElevation = if (isHovered) 4.dp else 0.dp),
            border = if (isHovered)
                androidx.compose.foundation.BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
            else
                androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.BottomCenter
            ) {
                androidx.compose.foundation.Canvas(
                    modifier = Modifier
                        .fillMaxWidth(0.7f)
                        .height(3.dp)
                        .padding(bottom = 8.dp)
                ) {
                    drawLine(
                        color = if (isHovered)
                            androidx.compose.ui.graphics.Color(0xFF000000).copy(alpha = 0.8f)
                        else
                            androidx.compose.ui.graphics.Color(0xFF000000).copy(alpha = 0.3f),
                        start = androidx.compose.ui.geometry.Offset(0f, size.height / 2),
                        end = androidx.compose.ui.geometry.Offset(size.width, size.height / 2),
                        strokeWidth = if (isHovered) 4.dp.toPx() else 3.dp.toPx()
                    )
                }
            }
        }
    }
}

// Bottom Section: Draggable letter cards
@Composable
private fun BottomSection(
    draggableLetters: List<DraggableLetter>,
    selectedLetterId: Int?,
    dragPosition: Offset?,
    isUserBlocked: Boolean,
    onDragStart: (Int, Offset) -> Unit,
    onDragMove: (Offset) -> Unit,
    onDragEnd: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
    ) {
        draggableLetters.forEach { letter ->
            key(letter.id) {
                DraggableLetterCard(
                    letter = letter,
                    isSelected = letter.id == selectedLetterId,
                    dragPosition = if (letter.id == selectedLetterId) dragPosition else null,
                    isBlocked = isUserBlocked,
                    onDragStart = onDragStart,
                    onDragMove = onDragMove,
                    onDragEnd = onDragEnd
                )
            }
        }
    }
}

// Draggable Letter Card Component
@Composable
private fun DraggableLetterCard(
    letter: DraggableLetter,
    isSelected: Boolean,
    dragPosition: Offset?,
    isBlocked: Boolean,
    onDragStart: (Int, Offset) -> Unit,
    onDragMove: (Offset) -> Unit,
    onDragEnd: () -> Unit
) {
    val density = LocalDensity.current

    // Original position in pixels
    val originalOffsetPx = with(density) {
        Offset(letter.offsetX.toPx(), letter.offsetY.toPx())
    }

    // Half card size for centering
    val halfCardSizePx = (LETTER_CARD_SIZE.value * density.density) / 2

    // Track the current finger position (center of card when dragging)
    var fingerPosition by remember { mutableStateOf(Offset.Zero) }

    // Display position: if dragging, center card under finger; otherwise use original position
    val displayOffset = if (isSelected) {
        // Card top-left = finger position - half card size (to center card under finger)
        Offset(
            fingerPosition.x - halfCardSizePx,
            fingerPosition.y - halfCardSizePx
        )
    } else {
        originalOffsetPx
    }

    Card(
        modifier = Modifier
            .offset {
                IntOffset(
                    displayOffset.x.roundToInt(),
                    displayOffset.y.roundToInt()
                )
            }
            .size(LETTER_CARD_SIZE)
            .rotate(if (isSelected) 0f else letter.rotation)
            .graphicsLayer {
                scaleX = if (isSelected) 1.1f else 1.0f
                scaleY = if (isSelected) 1.1f else 1.0f
            }
            .pointerInput(letter.id) {
                if (!isBlocked) {
                    detectDragGestures(
                        onDragStart = { startOffset ->
                            // Calculate absolute finger position: original card position + touch offset
                            fingerPosition = Offset(
                                originalOffsetPx.x + startOffset.x,
                                originalOffsetPx.y + startOffset.y
                            )
                            // Notify ViewModel
                            onDragStart(letter.id, fingerPosition)
                        },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            // Update finger position by adding drag delta
                            fingerPosition = Offset(
                                fingerPosition.x + dragAmount.x,
                                fingerPosition.y + dragAmount.y
                            )
                            // Send current finger position (card center) for footprint detection
                            onDragMove(fingerPosition)
                        },
                        onDragEnd = {
                            onDragEnd()
                        }
                    )
                }
            },
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isSelected) 8.dp else 2.dp),
        border = null
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = letter.letter.toString(),
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}
