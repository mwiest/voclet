package com.github.mwiest.voclet.ui.practice

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeContentPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import com.github.mwiest.voclet.ui.components.AnimatedImage
import com.github.mwiest.voclet.ui.theme.VocletTheme

/** Widest the stats block and the action buttons are allowed to grow. */
private val ContentMaxWidth = 400.dp

/** Aspect ratio of `fox_thumb.webp`, so the fox can be measured without decoding it. */
private const val FoxAspectRatio = 640f / 367f

/** Share of the celebration pane the fox may occupy, leaving the rest to the score and its margins. */
private const val FoxPaneHeightFraction = 0.45f

@Composable
fun PracticeResultsScreen(
    navController: NavController,
    windowSizeClass: WindowSizeClass,
    correctCount: Int,
    incorrectCount: Int,
    onPracticeAgain: () -> Unit,
    onBackToHome: () -> Unit
) {
    PracticeResultsContent(
        navController = navController,
        wideWindow = windowSizeClass.isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_MEDIUM_LOWER_BOUND),
        tallWindow = windowSizeClass.isHeightAtLeastBreakpoint(WindowSizeClass.HEIGHT_DP_EXPANDED_LOWER_BOUND),
        shortWindow = !windowSizeClass.isHeightAtLeastBreakpoint(WindowSizeClass.HEIGHT_DP_MEDIUM_LOWER_BOUND),
        correctCount = correctCount,
        incorrectCount = incorrectCount,
        onPracticeAgain = onPracticeAgain,
        onBackToHome = onBackToHome
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PracticeResultsContent(
    navController: NavController,
    wideWindow: Boolean,
    tallWindow: Boolean,
    shortWindow: Boolean,
    correctCount: Int,
    incorrectCount: Int,
    onPracticeAgain: () -> Unit,
    onBackToHome: () -> Unit
) {
    val total = correctCount + incorrectCount
    val percentage = if (total > 0) (correctCount * 100) / total else 0

    // Stacking everything only works while there is height to stack into. A window that is wide
    // but not tall splits into two panes instead, spending the dimension it actually has.
    val twoPane = wideWindow && !tallWindow
    // A window that is short and too narrow to split has room for neither arrangement, so the fox
    // steps aside rather than pushing the score itself off screen.
    val showFox = percentage > 50 && (twoPane || !shortWindow)

    Scaffold(
        topBar = {
            // A bar costs around 88dp of a 400dp-tall window to repeat a title the score and its
            // message already carry, so short windows keep only the back button. Leaving the slot
            // empty measures 0x0, and Scaffold then falls back to the status bar inset.
            if (!twoPane) {
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
        }
    ) { paddingValues ->
        // Scaffold sizes paddingValues from the system bars but does not consume them, so without
        // the consume call the safeContentPadding below would apply the very same bars a second
        // time - a doubled gap under the top bar and a doubled squeeze at the bottom.
        val body = Modifier
            .fillMaxSize()
            .padding(paddingValues)
            .consumeWindowInsets(paddingValues)
            .safeContentPadding()
            .padding(horizontal = 24.dp)

        if (twoPane) {
            // No bar and no back arrow here: the actions pane already offers "Back to home" and
            // the system back gesture still works, so nothing of the window's scarce height is
            // spent on a second way out.
            Row(
                modifier = body,
                horizontalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                // Celebration pane
                BoxWithConstraints(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                ) {
                    // Capping the fox against the pane rather than only against what the score
                    // leaves stops it from growing to fill a tall pane and crowding the text.
                    val foxMaxHeight = maxHeight * FoxPaneHeightFraction

                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(top = 8.dp, bottom = 24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        if (showFox) {
                            // The score is unweighted, so it is measured first and always gets
                            // the height it asks for; the fox takes its own aspect within the
                            // smaller of the cap and the remainder, leaving the arrangement
                            // slack to centre with.
                            AnimatedImage(
                                resId = R.drawable.fox_thumb,
                                modifier = Modifier
                                    .weight(1f, fill = false)
                                    .heightIn(max = foxMaxHeight)
                                    .aspectRatio(FoxAspectRatio)
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                        }
                        ScoreHeadline(percentage)
                    }
                }

                // Scoreboard and actions pane
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    StatsBlock(correctCount, incorrectCount, total)
                    Spacer(modifier = Modifier.height(24.dp))
                    ActionButtons(onPracticeAgain, onBackToHome)
                }
            }
        } else {
            Column(
                modifier = body,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    if (showFox) {
                        // Scrolling hands out an unbounded height, which weight() cannot divide
                        // up, so here the fox does get a fixed slice.
                        AnimatedImage(
                            resId = R.drawable.fox_thumb,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(if (tallWindow) 200.dp else 160.dp)
                                .padding(bottom = 8.dp)
                        )
                    }
                    ScoreHeadline(percentage)
                    Spacer(modifier = Modifier.height(24.dp))
                    StatsBlock(correctCount, incorrectCount, total)
                }

                ActionButtons(
                    onPracticeAgain = onPracticeAgain,
                    onBackToHome = onBackToHome,
                    modifier = Modifier.padding(top = 24.dp)
                )
            }
        }
    }
}

@Composable
private fun ScoreHeadline(percentage: Int) {
    Text(
        text = "$percentage%",
        style = MaterialTheme.typography.displayLarge,
        color = MaterialTheme.colorScheme.primary
    )

    Spacer(modifier = Modifier.height(16.dp))

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
}

@Composable
private fun StatsBlock(correctCount: Int, incorrectCount: Int, total: Int) {
    Column(
        modifier = Modifier
            .widthIn(max = ContentMaxWidth)
            .fillMaxWidth()
            .padding(vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
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
}

@Composable
private fun ActionButtons(
    onPracticeAgain: () -> Unit,
    onBackToHome: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .widthIn(max = ContentMaxWidth)
            .fillMaxWidth(),
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

/** Phone portrait: stacked, fox included. */
@Preview(showBackground = true, widthDp = 450, heightDp = 800)
@Composable
fun PracticeResultsScreenPreview() {
    VocletTheme {
        PracticeResultsScreen(
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
fun PracticeResultsScreenPerfectPreview() {
    VocletTheme {
        PracticeResultsScreen(
            navController = rememberNavController(),
            windowSizeClass = currentWindowAdaptiveInfo().windowSizeClass,
            correctCount = 10,
            incorrectCount = 0,
            onPracticeAgain = {},
            onBackToHome = {}
        )
    }
}

/** Failing score: no fox in any arrangement. */
@Preview(showBackground = true, widthDp = 1000, heightDp = 600)
@Composable
fun PracticeResultsScreenDarkPreview() {
    VocletTheme(darkTheme = true) {
        PracticeResultsScreen(
            navController = rememberNavController(),
            windowSizeClass = currentWindowAdaptiveInfo().windowSizeClass,
            correctCount = 5,
            incorrectCount = 5,
            onPracticeAgain = {},
            onBackToHome = {}
        )
    }
}

/** Phone landscape: two panes. */
@Preview(showBackground = true, widthDp = 800, heightDp = 400)
@Composable
fun PracticeResultsScreenLandscapePreview() {
    VocletTheme {
        PracticeResultsScreen(
            navController = rememberNavController(),
            windowSizeClass = currentWindowAdaptiveInfo().windowSizeClass,
            correctCount = 8,
            incorrectCount = 2,
            onPracticeAgain = {},
            onBackToHome = {}
        )
    }
}

/** Tablet landscape: two panes with room to breathe. */
@Preview(showBackground = true, widthDp = 1280, heightDp = 800)
@Composable
fun PracticeResultsScreenTabletLandscapePreview() {
    VocletTheme {
        PracticeResultsScreen(
            navController = rememberNavController(),
            windowSizeClass = currentWindowAdaptiveInfo().windowSizeClass,
            correctCount = 9,
            incorrectCount = 1,
            onPracticeAgain = {},
            onBackToHome = {}
        )
    }
}

/** Tablet portrait: stacked, taller fox. */
@Preview(showBackground = true, widthDp = 800, heightDp = 1280)
@Composable
fun PracticeResultsScreenTabletPortraitPreview() {
    VocletTheme {
        PracticeResultsScreen(
            navController = rememberNavController(),
            windowSizeClass = currentWindowAdaptiveInfo().windowSizeClass,
            correctCount = 9,
            incorrectCount = 1,
            onPracticeAgain = {},
            onBackToHome = {}
        )
    }
}

/** Short and narrow, e.g. split screen: room for neither arrangement, so the fox is dropped. */
@Preview(showBackground = true, widthDp = 500, heightDp = 360)
@Composable
fun PracticeResultsScreenSmallWindowPreview() {
    VocletTheme {
        PracticeResultsScreen(
            navController = rememberNavController(),
            windowSizeClass = currentWindowAdaptiveInfo().windowSizeClass,
            correctCount = 8,
            incorrectCount = 2,
            onPracticeAgain = {},
            onBackToHome = {}
        )
    }
}
