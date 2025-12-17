package com.github.mwiest.voclet.ui.practice

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Pets
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.material.icons.outlined.Hiking
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
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
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavController
import androidx.window.core.layout.WindowSizeClass
import com.github.mwiest.voclet.R
import kotlin.math.roundToInt

// Level 1: Container with ViewModel injection
@Composable
fun PathwayPracticeScreen(
    navController: NavController,
    windowSizeClass: WindowSizeClass = currentWindowAdaptiveInfo().windowSizeClass,
    viewModel: PathwayPracticeViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    PathwayPracticeScreen(
        navController = navController,
        windowSizeClass = windowSizeClass,
        uiState = uiState,
        onInitializeSession = { width, height -> viewModel.initializeSession(width, height) },
        onRotation = { width, height -> viewModel.handleRotation(width, height) },
        onDragStart = { letterId, position -> viewModel.handleDragStart(letterId, position) },
        onDragMove = { offset -> viewModel.handleDragMove(offset) },
        onDragEnd = { viewModel.handleDragEnd() },
        onResetPractice = { viewModel.resetPractice() }
    )
}

// Level 2: State Management
@Composable
fun PathwayPracticeScreen(
    navController: NavController,
    windowSizeClass: WindowSizeClass,
    uiState: PathwayPracticeUiState,
    onInitializeSession: (Dp, Dp) -> Unit,
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
        PathwayPracticeContent(
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
private fun PathwayPracticeContent(
    navController: NavController,
    uiState: PathwayPracticeUiState,
    onInitializeSession: (Dp, Dp) -> Unit,
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
                    if (uiState.wordPairs.isNotEmpty()) {
                        Text(
                            text = "${uiState.currentWordIndex + 1} / ${uiState.wordPairs.size} ${
                                stringResource(
                                    id = R.string.pathway
                                )
                            }",
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
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

                    if (lastDimensions == null) {
                        // First initialization
                        lastDimensions = currentDimensions
                        onInitializeSession(widthDp, heightDp)
                    } else if (lastDimensions != currentDimensions) {
                        // Screen rotated
                        lastDimensions = currentDimensions
                        onRotation(widthDp, heightDp)
                    }
                }
        ) {
            if (!uiState.isLoading && uiState.screenDimensions != null) {
                Column(modifier = Modifier.fillMaxSize()) {
                    // Top Section: Prompt and Solution Word
                    TopSection(
                        prompt = uiState.currentPrompt,
                        solutionWord = uiState.currentWord,
                        footprintStates = uiState.footprintStates,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(PATHWAY_TOP_SECTION_HEIGHT)
                    )

                    // Pathway Section: Canvas with pathway, footprints, fox, and shoe
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                    ) {
                        PathwaySection(
                            pathwayPoints = uiState.pathwayPoints,
                            footprintStates = uiState.footprintStates,
                            foxAnimation = uiState.foxAnimation,
                            shoeScale = uiState.shoeScale,
                            hoveredFootprintIndex = uiState.hoveredFootprintIndex
                        )
                    }

                    // Bottom Section: Draggable Letters
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(PATHWAY_BOTTOM_SECTION_HEIGHT)
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

// Top Section: Shows prompt word and solution with gaps
@Composable
private fun TopSection(
    prompt: String,
    solutionWord: String,
    footprintStates: List<FootprintState>,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Prompt word (word1 - source language)
        Text(
            text = prompt,
            style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.primary,
            textAlign = TextAlign.Center
        )

        // Solution word with gaps (fills as user completes)
        Text(
            text = buildSolutionDisplay(solutionWord, footprintStates),
            style = MaterialTheme.typography.headlineSmall.copy(letterSpacing = 8.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp)
        )
    }
}

// Build solution word display with gaps or filled letters
private fun buildSolutionDisplay(word: String, footprintStates: List<FootprintState>): String {
    return word.uppercase().mapIndexed { index, char ->
        footprintStates.getOrNull(index)?.placedLetter?.toString() ?: "_"
    }.joinToString(" ")
}

// Pathway Section: Canvas rendering of pathway, footprints, fox, and shoe
@Composable
private fun PathwaySection(
    pathwayPoints: List<PathwayPoint>,
    footprintStates: List<FootprintState>,
    foxAnimation: FoxAnimationState,
    shoeScale: Float,
    hoveredFootprintIndex: Int?
) {
    val animatedShoeScale by animateFloatAsState(
        targetValue = shoeScale,
        animationSpec = tween(300),
        label = "shoeScale"
    )

    Canvas(modifier = Modifier.fillMaxSize()) {
        if (pathwayPoints.isEmpty()) return@Canvas

        // Draw dashed pathway line connecting footprints
        for (i in 0 until pathwayPoints.size - 1) {
            val start = pathwayPoints[i]
            val end = pathwayPoints[i + 1]

            drawLine(
                color = Color.Gray,
                start = Offset(start.x.toPx(), start.y.toPx()),
                end = Offset(end.x.toPx(), end.y.toPx()),
                strokeWidth = 4f,
                cap = StrokeCap.Round,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f)
            )
        }
    }

    // Draw footprints as individual composables (so they can have different states)
    footprintStates.forEachIndexed { index, state ->
        val isHovered = index == hoveredFootprintIndex

        FootprintIcon(
            footprintState = state,
            isHovered = isHovered,
            modifier = Modifier.offset {
                IntOffset(
                    state.pathwayPoint.x.roundToPx() - (FOOTPRINT_SIZE / 2).roundToPx(),
                    state.pathwayPoint.y.roundToPx() - (FOOTPRINT_SIZE / 2).roundToPx()
                )
            }
        )
    }

    // Draw fox icon
    if (foxAnimation.isAnimating) {
        Icon(
            imageVector = Icons.Default.Pets,
            contentDescription = "Fox",
            tint = MaterialTheme.colorScheme.tertiary,
            modifier = Modifier
                .size(40.dp)
                .offset {
                    IntOffset(
                        foxAnimation.position.x.roundToInt() - 20.dp.roundToPx(),
                        foxAnimation.position.y.roundToInt() - 20.dp.roundToPx()
                    )
                }
        )
    } else if (pathwayPoints.isNotEmpty()) {
        // Fox at start position
        val foxPos = getFoxStartPosition(pathwayPoints)
        Icon(
            imageVector = Icons.Default.Pets,
            contentDescription = "Fox",
            tint = MaterialTheme.colorScheme.tertiary,
            modifier = Modifier
                .size(40.dp)
                .offset {
                    IntOffset(
                        foxPos.x.roundToInt(),
                        foxPos.y.roundToInt()
                    )
                }
        )
    }

    // Draw shoe icon at end
    if (pathwayPoints.isNotEmpty()) {
        val shoePos = getShoePosition(pathwayPoints)
        Icon(
            imageVector = Icons.Outlined.Hiking,
            contentDescription = "Shoe",
            tint = MaterialTheme.colorScheme.secondary,
            modifier = Modifier
                .size(40.dp)
                .scale(animatedShoeScale)
                .offset {
                    IntOffset(
                        shoePos.x.roundToInt(),
                        shoePos.y.roundToInt()
                    )
                }
        )
    }
}

// Footprint Icon Component
@Composable
private fun FootprintIcon(
    footprintState: FootprintState,
    isHovered: Boolean,
    modifier: Modifier = Modifier
) {
    val backgroundColor = when {
        footprintState.isCorrect == true -> MaterialTheme.colorScheme.primaryContainer
        footprintState.isCorrect == false -> MaterialTheme.colorScheme.errorContainer
        isHovered -> MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
        else -> MaterialTheme.colorScheme.surface
    }

    val borderColor = when {
        isHovered -> MaterialTheme.colorScheme.secondary
        footprintState.placedLetter != null -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.outline
    }

    Card(
        modifier = modifier
            .size(FOOTPRINT_SIZE)
            .border(2.dp, borderColor, CircleShape),
        colors = CardDefaults.cardColors(containerColor = backgroundColor),
        shape = CircleShape
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            if (footprintState.placedLetter != null) {
                Text(
                    text = footprintState.placedLetter.toString(),
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )
            } else {
                // Empty footprint
                Icon(
                    imageVector = Icons.Outlined.Circle,
                    contentDescription = "Empty footprint",
                    tint = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.size(24.dp)
                )
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
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
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
    val offset = dragPosition ?: Offset(letter.offsetX.value, letter.offsetY.value)

    Card(
        modifier = Modifier
            .offset {
                IntOffset(
                    offset.x.roundToInt(),
                    offset.y.roundToInt()
                )
            }
            .size(LETTER_CARD_SIZE)
            .rotate(if (isSelected) 0f else letter.rotation)
            .graphicsLayer {
                scaleX = if (isSelected) 1.1f else 1.0f
                scaleY = if (isSelected) 1.1f else 1.0f
                shadowElevation = if (isSelected) 8f else 2f
            }
            .pointerInput(letter.id) {
                if (!isBlocked) {
                    detectDragGestures(
                        onDragStart = { startOffset ->
                            onDragStart(
                                letter.id,
                                Offset(offset.x + startOffset.x, offset.y + startOffset.y)
                            )
                        },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            onDragMove(offset + dragAmount)
                        },
                        onDragEnd = {
                            onDragEnd()
                        }
                    )
                }
            },
        colors = CardDefaults.cardColors(
            containerColor = if (letter.isCorrect) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.tertiaryContainer
            }
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isSelected) 8.dp else 2.dp)
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
