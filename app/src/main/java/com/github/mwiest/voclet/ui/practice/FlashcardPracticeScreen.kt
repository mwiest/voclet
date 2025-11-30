package com.github.mwiest.voclet.ui.practice

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeContentPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavController
import com.github.mwiest.voclet.R

@Composable
fun FlashcardPracticeScreen(
    navController: NavController,
    viewModel: FlashcardPracticeViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    if (uiState.practiceComplete) {
        FlashcardPracticeResultsScreen(
            navController = navController,
            correctCount = uiState.correctCount,
            incorrectCount = uiState.incorrectCount,
            onPracticeAgain = { navController.navigateUp() },
            onBackToHome = { navController.navigate("home") { popUpTo("home") } }
        )
    } else {
        FlashcardPracticeContent(
            navController = navController,
            uiState = uiState,
            viewModel = viewModel
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FlashcardPracticeContent(
    navController: NavController,
    uiState: FlashcardPracticeUiState,
    viewModel: FlashcardPracticeViewModel
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "${uiState.currentCardIndex + 1} / ${uiState.wordPairs.size}",
                        style = MaterialTheme.typography.titleMedium
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
        if (uiState.wordPairs.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                Text("No words to practice")
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .safeContentPadding(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Spacer(modifier = Modifier.height(1.dp))

                // Animated Flashcard
                val currentPair = uiState.wordPairs[uiState.currentCardIndex]
                AnimatedFlashcard(
                    isFlipped = uiState.isFlipped,
                    word1 = currentPair.word1,
                    word2 = currentPair.word2,
                    cardIndex = uiState.currentCardIndex
                )

                // Button Area
                ButtonArea(
                    isFlipped = uiState.isFlipped,
                    onFlip = { viewModel.flipCard() },
                    onCorrect = { viewModel.markCorrect() },
                    onIncorrect = { viewModel.markIncorrect() }
                )

                Spacer(modifier = Modifier.height(1.dp))
            }
        }
    }
}

@Composable
private fun AnimatedFlashcard(
    isFlipped: Boolean,
    word1: String,
    word2: String,
    cardIndex: Int
) {
    // Flip rotation animation
    val flipRotation = remember { Animatable(0f) }

    LaunchedEffect(isFlipped) {
        flipRotation.animateTo(
            targetValue = if (isFlipped) 180f else 0f,
            animationSpec = tween(
                durationMillis = 500,
                easing = CubicBezierEasing(0.4f, 0.0f, 0.2f, 1.0f)
            )
        )
    }

    // Card scale animation (subtle elevation effect)
    val scaleValue = remember { Animatable(1f) }

    LaunchedEffect(isFlipped) {
        if (isFlipped) {
            scaleValue.animateTo(1.05f, animationSpec = tween(250))
            scaleValue.animateTo(1f, animationSpec = tween(250))
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth(0.7f)
            .height(300.dp)
            .graphicsLayer(
                rotationY = flipRotation.value,
                scaleX = scaleValue.value,
                scaleY = scaleValue.value,
                cameraDistance = 12f * 10
            ),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier.fillMaxSize(),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                // Display text based on flip state
                // At 90 degrees rotation, switch the text
                val displayText = if (flipRotation.value > 90) word2 else word1

                Text(
                    text = displayText,
                    style = MaterialTheme.typography.headlineLarge,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .padding(24.dp)
                        .alpha(1f)
                )
            }
        }
    }
}

@Composable
private fun ButtonArea(
    isFlipped: Boolean,
    onFlip: () -> Unit,
    onCorrect: () -> Unit,
    onIncorrect: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (!isFlipped) {
            // Flip button
            Button(
                onClick = onFlip,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
            ) {
                Text("Flip", style = MaterialTheme.typography.labelLarge)
            }
        } else {
            // Correct and Incorrect buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    onClick = onIncorrect,
                    modifier = Modifier
                        .weight(1f)
                        .height(50.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("Incorrect", style = MaterialTheme.typography.labelLarge)
                }

                Button(
                    onClick = onCorrect,
                    modifier = Modifier
                        .weight(1f)
                        .height(50.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.tertiary
                    )
                ) {
                    Text("Correct", style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}
