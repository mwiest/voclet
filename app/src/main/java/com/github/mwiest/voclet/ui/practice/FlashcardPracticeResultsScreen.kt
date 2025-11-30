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
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.tooling.preview.Preview
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController
import com.github.mwiest.voclet.ui.theme.VocletTheme

@Composable
fun FlashcardPracticeResultsScreen(
    navController: NavController,
    correctCount: Int,
    incorrectCount: Int,
    onPracticeAgain: () -> Unit,
    onBackToHome: () -> Unit
) {
    FlashcardPracticeResultsContent(
        navController = navController,
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
                title = { Text("Practice Complete") }
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
                percentage >= 80 -> "Great job!"
                percentage >= 60 -> "Good effort!"
                else -> "Keep practicing!"
            }

            Text(
                text = message,
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(48.dp))

            // Stats breakdown
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                StatItem(
                    label = "Correct",
                    value = correctCount,
                    color = MaterialTheme.colorScheme.tertiary
                )

                StatItem(
                    label = "Incorrect",
                    value = incorrectCount,
                    color = MaterialTheme.colorScheme.error
                )

                StatItem(
                    label = "Total",
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
                        .height(48.dp)
                ) {
                    Text("Practice Again", style = MaterialTheme.typography.labelLarge)
                }

                Button(
                    onClick = { onBackToHome() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                ) {
                    Text("Back to Home", style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}

@Composable
private fun StatItem(
    label: String,
    value: Int,
    color: Color
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
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
            correctCount = 5,
            incorrectCount = 5,
            onPracticeAgain = {},
            onBackToHome = {}
        )
    }
}
