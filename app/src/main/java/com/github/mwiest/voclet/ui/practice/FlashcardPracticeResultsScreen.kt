package com.github.mwiest.voclet.ui.practice

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeContentPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController
import androidx.window.core.layout.WindowSizeClass
import com.github.mwiest.voclet.R
import com.github.mwiest.voclet.ui.theme.VocletTheme

@Composable
fun FlashcardPracticeResultsScreen(
    navController: NavController,
    windowSizeClass: WindowSizeClass,
    correctCount: Int,
    incorrectCount: Int,
    onPracticeAgain: () -> Unit,
    onBackToHome: () -> Unit
) {
    FlashcardPracticeResultsContent(
        navController = navController,
        largeScreen = windowSizeClass.isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_EXPANDED_LOWER_BOUND),
        correctCount = correctCount,
        incorrectCount = incorrectCount,
        onPracticeAgain = onPracticeAgain,
        onBackToHome = onBackToHome
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FlashcardPracticeResultsContent(
    navController: NavController,
    largeScreen: Boolean,
    correctCount: Int,
    incorrectCount: Int,
    onPracticeAgain: () -> Unit,
    onBackToHome: () -> Unit
) {
    val total = correctCount + incorrectCount
    val percentage = if (total > 0) (correctCount * 100) / total else 0

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(id = R.string.practice_complete)) },
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .safeContentPadding()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Percentage display
            Text(
                text = "$percentage%",
                style = MaterialTheme.typography.displayLarge,
                color = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Motivational message
            val message = when {
                percentage >= 80 -> stringResource(id = R.string.great_job)
                percentage >= 60 -> stringResource(id = R.string.good_effort)
                else -> stringResource(id = R.string.keep_practicing)
            }

            Text(
                text = message,
                style = MaterialTheme.typography.titleLarge,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(48.dp))

            // Stats breakdown
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 24.dp, horizontal = if (largeScreen) 200.dp else 25.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                StatItem(
                    labelRes = R.string.results_correct,
                    value = correctCount,
                    color = MaterialTheme.colorScheme.tertiary
                )

                StatItem(
                    labelRes = R.string.results_incorrect,
                    value = incorrectCount,
                    color = MaterialTheme.colorScheme.error
                )

                StatItem(
                    labelRes = R.string.results_total,
                    value = total,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            Spacer(modifier = Modifier.height(48.dp))

            // Action buttons
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    onClick = { onPracticeAgain() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                ) {
                    Icon(
                        Icons.Default.Replay,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        stringResource(id = R.string.practice_again),
                        style = MaterialTheme.typography.labelLarge
                    )
                }

                Button(
                    onClick = { onBackToHome() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                ) {
                    Text(
                        stringResource(id = R.string.back_to_home),
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }
        }
    }
}

@Composable
private fun StatItem(
    labelRes: Int,
    value: Int,
    color: Color
) {
    Row(
        modifier = Modifier
            .fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = stringResource(id = labelRes),
            style = MaterialTheme.typography.bodyLarge
        )

        Text(
            text = value.toString(),
            style = MaterialTheme.typography.headlineSmall,
            color = color
        )
    }
}

@Preview(showBackground = true, widthDp = 450, heightDp = 800)
@Composable
fun FlashcardPracticeResultsScreenPreview() {
    VocletTheme {
        FlashcardPracticeResultsScreen(
            navController = rememberNavController(),
            windowSizeClass = currentWindowAdaptiveInfo().windowSizeClass,
            correctCount = 8,
            incorrectCount = 2,
            onPracticeAgain = {},
            onBackToHome = {}
        )
    }
}

@Preview(showBackground = true, widthDp = 450, heightDp = 800)
@Composable
fun FlashcardPracticeResultsScreenPerfectPreview() {
    VocletTheme {
        FlashcardPracticeResultsScreen(
            navController = rememberNavController(),
            windowSizeClass = currentWindowAdaptiveInfo().windowSizeClass,
            correctCount = 10,
            incorrectCount = 0,
            onPracticeAgain = {},
            onBackToHome = {}
        )
    }
}

@Preview(showBackground = true, widthDp = 1000, heightDp = 600)
@Composable
fun FlashcardPracticeResultsScreenDarkPreview() {
    VocletTheme(darkTheme = true) {
        FlashcardPracticeResultsScreen(
            navController = rememberNavController(),
            windowSizeClass = currentWindowAdaptiveInfo().windowSizeClass,
            correctCount = 5,
            incorrectCount = 5,
            onPracticeAgain = {},
            onBackToHome = {}
        )
    }
}
