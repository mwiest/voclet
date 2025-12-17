package com.github.mwiest.voclet.ui.practice

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

// Layout constants
val PATHWAY_TOP_SECTION_HEIGHT = 120.dp
val PATHWAY_BOTTOM_SECTION_HEIGHT = 150.dp
val PATHWAY_EDGE_MARGIN = 32.dp
val FOOTPRINT_SIZE = 48.dp
val FOOTPRINT_MIN_SPACING = 16.dp
val LETTER_CARD_SIZE = 56.dp
val LETTER_CARD_SPACING = 8.dp
const val LETTER_ROTATION_RANGE = 15f
const val FOX_ANIMATION_STEPS = 60  // 60 steps for smooth 3-second animation

/**
 * Represents a single footprint position on the pathway.
 */
data class PathwayPoint(
    val x: Dp,
    val y: Dp,
    val letter: Char,
    val index: Int  // Position in word (0-based)
)

/**
 * Represents a draggable letter card at the bottom of the screen.
 */
data class DraggableLetter(
    val id: Int,  // Unique ID for Compose key
    val letter: Char,
    val isCorrect: Boolean,  // True if part of correct word
    val offsetX: Dp,
    val offsetY: Dp,
    val rotation: Float = 0f
)

/**
 * Represents the state of a footprint on the pathway.
 */
data class FootprintState(
    val pathwayPoint: PathwayPoint,
    val placedLetter: Char? = null,  // null if empty
    val isCorrect: Boolean? = null  // null=not placed, true=correct, false=wrong
)

/**
 * Represents the fox's animation state.
 */
data class FoxAnimationState(
    val currentStep: Int = 0,
    val isAnimating: Boolean = false,
    val position: Offset = Offset.Zero
)

/**
 * Generates a winding S-shaped pathway based on word length and screen orientation.
 *
 * Algorithm:
 * - Portrait: Vertical progression with horizontal sine wave oscillation
 * - Landscape: Horizontal progression with vertical sine wave oscillation
 * - Dynamic: More letters = increased frequency for more curves
 * - Spacing: Ensures footprints don't overlap (~40dp minimum spacing)
 *
 * @param word The target word to spell
 * @param screenWidth Available screen width in Dp
 * @param screenHeight Available screen height in Dp
 * @param isPortrait Screen orientation flag
 * @return List of pathway points for each letter
 */
fun generatePathwayPoints(
    word: String,
    screenWidth: Dp,
    screenHeight: Dp,
    isPortrait: Boolean
): List<PathwayPoint> {
    val normalizedWord = word.uppercase()
    val letterCount = normalizedWord.length

    if (letterCount == 0) return emptyList()

    // Calculate available space for pathway
    val availableWidth = screenWidth - PATHWAY_EDGE_MARGIN * 2
    val availableHeight = screenHeight - PATHWAY_TOP_SECTION_HEIGHT - PATHWAY_BOTTOM_SECTION_HEIGHT - PATHWAY_EDGE_MARGIN * 2

    val points = mutableListOf<PathwayPoint>()

    if (isPortrait) {
        // Portrait: Vertical progression with horizontal oscillation
        val verticalStep = availableHeight / (letterCount + 1)

        // Amplitude of horizontal oscillation (less for short words, more for long words)
        val amplitude = when {
            letterCount <= 3 -> (availableWidth.value * 0.2f).dp
            letterCount <= 6 -> (availableWidth.value * 0.3f).dp
            else -> (availableWidth.value * 0.35f).dp
        }

        // Frequency: more letters = more curves
        val frequency = when {
            letterCount <= 3 -> PI.toFloat() / 2
            letterCount <= 6 -> PI.toFloat()
            letterCount <= 10 -> PI.toFloat() * 1.5f
            else -> PI.toFloat() * 2f
        }

        val centerX = screenWidth / 2
        val startY = PATHWAY_TOP_SECTION_HEIGHT + PATHWAY_EDGE_MARGIN

        for (i in 0 until letterCount) {
            val t = i.toFloat() / (letterCount - 1).coerceAtLeast(1)
            val y = startY + verticalStep * (i + 1)
            val xOffset = amplitude * sin(frequency * t)
            val x = centerX + xOffset

            points.add(
                PathwayPoint(
                    x = x,
                    y = y,
                    letter = normalizedWord[i],
                    index = i
                )
            )
        }
    } else {
        // Landscape: Horizontal progression with vertical oscillation
        val horizontalStep = availableWidth / (letterCount + 1)

        // Amplitude of vertical oscillation
        val amplitude = when {
            letterCount <= 3 -> (availableHeight.value * 0.2f).dp
            letterCount <= 6 -> (availableHeight.value * 0.3f).dp
            else -> (availableHeight.value * 0.35f).dp
        }

        // Frequency: more letters = more curves
        val frequency = when {
            letterCount <= 3 -> PI.toFloat() / 2
            letterCount <= 6 -> PI.toFloat()
            letterCount <= 10 -> PI.toFloat() * 1.5f
            else -> PI.toFloat() * 2f
        }

        val startX = PATHWAY_EDGE_MARGIN
        val centerY = PATHWAY_TOP_SECTION_HEIGHT + availableHeight / 2

        for (i in 0 until letterCount) {
            val t = i.toFloat() / (letterCount - 1).coerceAtLeast(1)
            val x = startX + horizontalStep * (i + 1)
            val yOffset = amplitude * sin(frequency * t)
            val y = centerY + yOffset

            points.add(
                PathwayPoint(
                    x = x,
                    y = y,
                    letter = normalizedWord[i],
                    index = i
                )
            )
        }
    }

    return points
}

/**
 * Generates draggable letters: correct letters + 50% random wrong letters.
 * Shuffles and positions them at bottom of screen with random offsets/rotations.
 *
 * @param word The target word (correct letters)
 * @param bottomAreaWidth Width of the bottom letter area
 * @param bottomAreaHeight Height of the bottom letter area
 * @return List of draggable letters with positions
 */
fun generateDraggableLetters(
    word: String,
    bottomAreaWidth: Dp,
    bottomAreaHeight: Dp
): List<DraggableLetter> {
    val normalizedWord = word.uppercase()
    val correctLetters = normalizedWord.toList()

    // Calculate 50% extra wrong letters (at least 1 for very short words)
    val wrongLetterCount = (correctLetters.size / 2).coerceAtLeast(1)

    // All uppercase letters excluding those in the word
    val availableWrongLetters = ('A'..'Z').filter { it !in correctLetters }

    // Generate random wrong letters
    val wrongLetters = if (availableWrongLetters.isNotEmpty()) {
        List(wrongLetterCount) {
            availableWrongLetters.random()
        }
    } else {
        emptyList()
    }

    // Combine and shuffle all letters
    val allLetters = (correctLetters + wrongLetters).shuffled()

    // Calculate grid layout for letters at bottom
    val totalLetters = allLetters.size
    val maxCols = ((bottomAreaWidth - PATHWAY_EDGE_MARGIN * 2) / (LETTER_CARD_SIZE + LETTER_CARD_SPACING)).toInt().coerceAtLeast(1)
    val cols = minOf(maxCols, totalLetters)
    val rows = (totalLetters + cols - 1) / cols

    val gridWidth = (cols * (LETTER_CARD_SIZE + LETTER_CARD_SPACING).value - LETTER_CARD_SPACING.value).dp
    val gridHeight = (rows * (LETTER_CARD_SIZE + LETTER_CARD_SPACING).value - LETTER_CARD_SPACING.value).dp

    val startX = (bottomAreaWidth - gridWidth) / 2
    val startY = (bottomAreaHeight - gridHeight) / 2

    return allLetters.mapIndexed { index, letter ->
        val row = index / cols
        val col = index % cols

        // Calculate base position with slight random offset
        val baseX = startX + (col * (LETTER_CARD_SIZE + LETTER_CARD_SPACING).value).dp
        val baseY = startY + (row * (LETTER_CARD_SIZE + LETTER_CARD_SPACING).value).dp

        // Add small random offset for natural look
        val offsetX = baseX + (Random.nextFloat() * 8f - 4f).dp
        val offsetY = baseY + (Random.nextFloat() * 8f - 4f).dp
        val rotation = Random.nextFloat() * LETTER_ROTATION_RANGE * 2 - LETTER_ROTATION_RANGE

        DraggableLetter(
            id = index,
            letter = letter,
            isCorrect = letter in correctLetters,
            offsetX = offsetX,
            offsetY = offsetY,
            rotation = rotation
        )
    }
}

/**
 * Generates fox animation path following pathway points with smooth interpolation.
 * Creates intermediate positions for smooth 3-second animation (60 steps at 50ms each).
 *
 * @param pathwayPoints The pathway points to follow
 * @return List of Offset positions for each animation step
 */
fun generateFoxAnimationPath(pathwayPoints: List<PathwayPoint>): List<Offset> {
    if (pathwayPoints.isEmpty()) return emptyList()
    if (pathwayPoints.size == 1) {
        return List(FOX_ANIMATION_STEPS) { Offset(pathwayPoints[0].x.value, pathwayPoints[0].y.value) }
    }

    val animationPath = mutableListOf<Offset>()

    // Distribute animation steps evenly across all pathway segments
    val segmentCount = pathwayPoints.size - 1
    val stepsPerSegment = FOX_ANIMATION_STEPS.toFloat() / segmentCount

    for (segmentIndex in 0 until segmentCount) {
        val startPoint = pathwayPoints[segmentIndex]
        val endPoint = pathwayPoints[segmentIndex + 1]

        val stepsInThisSegment = if (segmentIndex == segmentCount - 1) {
            // Last segment gets remaining steps
            FOX_ANIMATION_STEPS - animationPath.size
        } else {
            stepsPerSegment.toInt()
        }

        for (step in 0 until stepsInThisSegment) {
            val t = step.toFloat() / stepsInThisSegment

            // Linear interpolation between start and end point
            val x = startPoint.x.value + (endPoint.x.value - startPoint.x.value) * t
            val y = startPoint.y.value + (endPoint.y.value - startPoint.y.value) * t

            animationPath.add(Offset(x, y))
        }
    }

    // Add final position
    val lastPoint = pathwayPoints.last()
    animationPath.add(Offset(lastPoint.x.value, lastPoint.y.value))

    return animationPath
}

/**
 * Calculates the position for the fox icon (start of pathway).
 */
fun getFoxStartPosition(pathwayPoints: List<PathwayPoint>): Offset {
    if (pathwayPoints.isEmpty()) return Offset.Zero

    val firstPoint = pathwayPoints.first()
    // Position fox slightly before and above the first footprint
    return Offset(
        x = firstPoint.x.value - FOOTPRINT_SIZE.value - 16f,
        y = firstPoint.y.value - FOOTPRINT_SIZE.value / 2
    )
}

/**
 * Calculates the position for the shoe icon (end of pathway).
 */
fun getShoePosition(pathwayPoints: List<PathwayPoint>): Offset {
    if (pathwayPoints.isEmpty()) return Offset.Zero

    val lastPoint = pathwayPoints.last()
    // Position shoe slightly after and below the last footprint
    return Offset(
        x = lastPoint.x.value + FOOTPRINT_SIZE.value + 16f,
        y = lastPoint.y.value + FOOTPRINT_SIZE.value / 2
    )
}

/**
 * Finds the nearest footprint to a given position (used for drag-drop).
 *
 * @param position The current drag position
 * @param footprintStates List of footprint states
 * @param threshold Maximum distance to consider (in dp)
 * @return Index of nearest footprint or null if none within threshold
 */
fun findNearestFootprint(
    position: Offset,
    footprintStates: List<FootprintState>,
    threshold: Dp = FOOTPRINT_SIZE * 1.5f
): Int? {
    var nearestIndex: Int? = null
    var nearestDistance = Float.MAX_VALUE

    footprintStates.forEachIndexed { index, state ->
        // Skip already filled footprints
        if (state.placedLetter != null) return@forEachIndexed

        val footprintPos = Offset(state.pathwayPoint.x.value, state.pathwayPoint.y.value)
        val distance = (position - footprintPos).getDistance()

        if (distance < nearestDistance && distance < threshold.value) {
            nearestDistance = distance
            nearestIndex = index
        }
    }

    return nearestIndex
}
