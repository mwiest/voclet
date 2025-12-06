package com.github.mwiest.voclet.ui.practice

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
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

    ConnectPracticeScreen(
        navController = navController,
        windowSizeClass = windowSizeClass,
        uiState = uiState,
        onInitializeSession = { width, height -> viewModel.initializeSession(width, height) },
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
    onInitializeSession: (Float, Float) -> Unit,
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
    onInitializeSession: (Float, Float) -> Unit,
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
        if (uiState.totalPairs == 0) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                Text(stringResource(id = R.string.no_words_to_practice))
            }
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .onGloballyPositioned { coordinates ->
                        // Initialize session when screen size is known
                        if (uiState.screenWidth == 0f) {
                            val widthDp = with(density) { coordinates.size.width.toDp().value }
                            val heightDp = with(density) { coordinates.size.height.toDp().value }
                            onInitializeSession(widthDp, heightDp)
                        }
                    }
            ) {
                // Draw cards
                uiState.visibleCardSlots.forEach { slotId ->
                    val cardSlot = uiState.allCardSlots.find { it.slotId == slotId }
                    val position = uiState.cardPositions[slotId]

                    if (cardSlot != null && position != null) {
                        ConnectCard(
                            cardSlot = cardSlot,
                            position = position,
                            isCorrect = slotId in uiState.correctMatchSlots,
                            isIncorrect = slotId in uiState.incorrectMatchSlots,
                            isFadingIncorrect = slotId in uiState.fadingIncorrectSlots,
                            isVanishing = slotId in uiState.vanishingSlots,
                            isAppearing = slotId in uiState.appearingSlots,
                            isSelected = slotId == uiState.selectedCardSlot,
                            isHovered = slotId == uiState.hoveredCardSlot,
                            onDragStart = { startOffset -> onDragStart(slotId, startOffset) },
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
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        // Convert dp coordinates to pixels for canvas
                        val startPx = Offset(
                            x = uiState.dragStartPosition.x.dp.toPx(),
                            y = uiState.dragStartPosition.y.dp.toPx()
                        )
                        val endPx = Offset(
                            x = uiState.dragPosition.x.dp.toPx(),
                            y = uiState.dragPosition.y.dp.toPx()
                        )
                        drawLine(
                            color = Color.Black,
                            start = startPx,
                            end = endPx,
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
    cardSlot: CardSlot,
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
    val defaultColor = if (cardSlot.showWord1) {
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

    val density = LocalDensity.current

    Card(
        modifier = Modifier
            .offset(x = position.offsetX, y = position.offsetY)
            .size(width = position.width, height = position.height)
            .graphicsLayer(alpha = alpha)
            .pointerInput(cardSlot.slotId) {
                if (!isBlocked) {
                    detectDragGestures(
                        onDragStart = { offset ->
                            // offset is in pixels within the card
                            // position.offsetX/Y is the card's top-left corner in dp
                            // Convert pixels to dp and add to card position
                            val screenOffset = Offset(
                                x = position.offsetX.value + offset.x / density.density,
                                y = position.offsetY.value + offset.y / density.density
                            )
                            onDragStart(screenOffset)
                        },
                        onDrag = { change, _ ->
                            // change.position is in pixels within the card
                            val screenOffset = Offset(
                                x = position.offsetX.value + change.position.x / density.density,
                                y = position.offsetY.value + change.position.y / density.density
                            )
                            onDragMove(screenOffset)
                        },
                        onDragEnd = {
                            onDragEnd()
                        }
                    )
                }
            },
        colors = CardDefaults.cardColors(
            containerColor = backgroundColor
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = if (isSelected || isHovered) 8.dp else 4.dp
        )
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = if (cardSlot.showWord1) cardSlot.wordPair.word1 else cardSlot.wordPair.word2,
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(8.dp)
            )
        }
    }
}
