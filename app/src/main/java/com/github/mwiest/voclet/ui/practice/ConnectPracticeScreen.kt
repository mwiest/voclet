package com.github.mwiest.voclet.ui.practice

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavController
import androidx.window.core.layout.WindowSizeClass
import com.github.mwiest.voclet.R

@Composable
fun ConnectPracticeScreen(
    navController: NavController,
    windowSizeClass: WindowSizeClass = currentWindowAdaptiveInfo().windowSizeClass,
    viewModel: ConnectPracticeViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val density = LocalDensity.current
    ConnectPracticeScreen(
        navController = navController,
        windowSizeClass = windowSizeClass,
        uiState = uiState,
        onInitializeSession = { width, height ->
            viewModel.initializeSession(
                width,
                height,
                density = density
            )
        },
        onDragStart = { slotId, position -> viewModel.handleDragStart(slotId, position) },
        onDragMove = { offset -> viewModel.handleDragMove(offset) },
        onDragEnd = { viewModel.handleDragEnd() },
        onResetPractice = { viewModel.resetPractice() }
    )
}

@Composable
fun ConnectPracticeScreen(
    navController: NavController,
    windowSizeClass: WindowSizeClass,
    uiState: ConnectPracticeUiState,
    onInitializeSession: (Dp, Dp) -> Unit,
    onDragStart: (Int, Offset) -> Unit,
    onDragMove: (Offset) -> Unit,
    onDragEnd: () -> Unit,
    onResetPractice: () -> Unit
) {
    if (uiState.practiceComplete) {
        FlashcardPracticeResultsScreen(
            navController = navController,
            windowSizeClass = windowSizeClass,
            correctCount = uiState.correctMatchCount,
            incorrectCount = uiState.incorrectAttemptCount,
            onPracticeAgain = onResetPractice,
            onBackToHome = { navController.navigate("home") { popUpTo("home") } }
        )
    } else {
        ConnectPracticeContent(
            navController = navController,
            uiState = uiState,
            onInitializeSession = onInitializeSession,
            onDragStart = onDragStart,
            onDragMove = onDragMove,
            onDragEnd = onDragEnd
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ConnectPracticeContent(
    navController: NavController,
    uiState: ConnectPracticeUiState,
    onInitializeSession: (Dp, Dp) -> Unit,
    onDragStart: (Int, Offset) -> Unit,
    onDragMove: (Offset) -> Unit,
    onDragEnd: () -> Unit
) {
    val density = LocalDensity.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "${uiState.correctMatchCount}/${uiState.totalPairs} ${
                            stringResource(id = R.string.connect)
                        }"
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { navController.navigateUp() }) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(id = R.string.back)
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
                    // Initialize session when screen size is known
                    if (!uiState.playgroundInitialized) {
                        val widthDp = with(density) { coordinates.size.width.toDp() }
                        val heightDp = with(density) { coordinates.size.height.toDp() }
                        onInitializeSession(widthDp, heightDp)
                    }
                }
        ) {
            // Show loading indicator OR playground canvas/cards
            if (uiState.isLoading) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            } else {
                // Debug: Draw playground
                Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                ) {
                    val playground = uiState.playgroundDimensions

                    // Draw grid cells
                    for (row in 0 until playground.rows) {
                        for (col in 0 until playground.cols) {
                            val x = playground.offsetX + (col * playground.cellWidth.value).dp
                            val y = playground.offsetY + (row * playground.cellHeight.value).dp

                            drawRect(
                                color = Color.LightGray,
                                topLeft = Offset(x.toPx(), y.toPx()),
                                size = Size(
                                    playground.cellWidth.toPx(),
                                    playground.cellHeight.toPx()
                                ),
                                style = Stroke(
                                    width = 2f,
                                    pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(
                                        floatArrayOf(10f, 10f)
                                    )
                                )
                            )
                        }
                    }
                }

                // Draw cards
                if (uiState.playground != null) {
                    val playgroundDimensions = uiState.playgroundDimensions
                    uiState.playground.gridCells.forEach { (coordinates, card) ->
                        val cardPosition =
                            calculateCardPosition(card, coordinates, playgroundDimensions)
                        ConnectCard(
                            connectCard = card,
                            position = cardPosition,
                            isCorrect = card.cardId in uiState.correctMatchSlots,
                            isIncorrect = card.cardId in uiState.incorrectMatchSlots,
                            isFadingIncorrect = card.cardId in uiState.fadingIncorrectSlots,
                            isVanishing = card.cardId in uiState.vanishingSlots,
                            isAppearing = card.cardId in uiState.appearingSlots,
                            isSelected = card.cardId == uiState.selectedCardSlot,
                            isHovered = card.cardId == uiState.hoveredCardSlot,
                            onDragStart = { startOffset -> onDragStart(card.cardId, startOffset) },
                            onDragMove = onDragMove,
                            onDragEnd = onDragEnd,
                            isBlocked = uiState.isUserBlocked
                        )
                    }
                }

                // Draw connection line
                if (uiState.selectedCardSlot != null &&
                    uiState.dragStartPosition != null &&
                    uiState.dragPosition != null
                ) {
                    val lineColor = MaterialTheme.colorScheme.outline
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        // Convert dp coordinates to pixels for canvas
                        val startPx = Offset(
                            x = uiState.dragStartPosition.x,
                            y = uiState.dragStartPosition.y,
                        )
                        val endPx = Offset(
                            x = uiState.dragPosition.x,
                            y = uiState.dragPosition.y,
                        )
                        drawLine(
                            color = lineColor,
                            start = startPx,
                            end = endPx,
                            cap = StrokeCap.Round,
                            blendMode = BlendMode.Darken,
                            strokeWidth = 4.dp.toPx()
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ConnectCard(
    connectCard: ConnectCard,
    position: CardPosition,
    isCorrect: Boolean,
    isIncorrect: Boolean,
    isFadingIncorrect: Boolean,
    isVanishing: Boolean,
    isAppearing: Boolean,
    isSelected: Boolean,
    isHovered: Boolean,
    onDragStart: (Offset) -> Unit,
    onDragMove: (Offset) -> Unit,
    onDragEnd: () -> Unit,
    isBlocked: Boolean
) {
    // Fade animation for appearing and vanishing
    val alpha by animateFloatAsState(
        targetValue = when {
            isVanishing -> 0f
            isAppearing -> 1f
            else -> 1f
        },
        animationSpec = tween(durationMillis = 500),
        label = "cardAlpha"
    )

    // Default background colors
    val defaultColor = if (connectCard.showWord1) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.secondaryContainer
    }

    // Animated color transition for incorrect matches fading back to normal
    val backgroundColor by androidx.compose.animation.animateColorAsState(
        targetValue = when {
            isCorrect -> Color(0xFF4CAF50).copy(alpha = 0.8f)
            isIncorrect -> Color(0xFFE57373).copy(alpha = 0.8f)
            isFadingIncorrect -> defaultColor
            else -> defaultColor
        },
        animationSpec = tween(durationMillis = 500),
        label = "cardBackgroundColor"
    )

    Card(
        modifier = Modifier
            .offset(x = position.offsetX, y = position.offsetY)
            .size(width = position.width, height = position.height)
            .pointerInput(connectCard.cardId) {
                if (!isBlocked) {
                    detectDragGestures(
                        onDragStart = { _ ->
                            // Use card center for drag line endpoint
                            val cardCenterX = position.offsetX.toPx() + position.width.toPx() / 2f
                            val cardCenterY = position.offsetY.toPx() + position.height.toPx() / 2f
                            val screenOffset = Offset(x = cardCenterX, y = cardCenterY)
                            onDragStart(screenOffset)
                        },
                        onDrag = { change, _ ->
                            val screenOffset = Offset(
                                x = position.offsetX.toPx() + change.position.x,
                                y = position.offsetY.toPx() + change.position.y,
                            )
                            onDragMove(screenOffset)
                        },
                        onDragEnd = {
                            onDragEnd()
                        }
                    )
                }
            }
            .graphicsLayer(
                alpha = alpha,
                rotationZ = position.rotation // Needs to go after the pointerInput in order to not rotate the drag coordinate system.
            ),
        colors = CardDefaults.cardColors(
            containerColor = backgroundColor
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = if (isSelected || isHovered) 10.dp else 4.dp
        )
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = if (connectCard.showWord1) connectCard.wordPair.word1 else connectCard.wordPair.word2,
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(8.dp)
            )
        }
    }
}
