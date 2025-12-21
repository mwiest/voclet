package com.github.mwiest.voclet.ui.practice

import android.util.Log
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.random.Random

// Layout constants
val FILL_BLANKS_TOP_SECTION_HEIGHT = 120.dp
val FILL_BLANKS_BOTTOM_SECTION_HEIGHT = 150.dp
val FILL_BLANKS_EDGE_MARGIN = 32.dp
val LETTER_CARD_SIZE = 56.dp
val LETTER_CARD_SPACING = 8.dp
const val LETTER_ROTATION_RANGE = 15f

/**
 * Represents a single letter slot position in the centered grid.
 */
data class LetterSlot(
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
 * Represents the state of a letter slot in the word grid.
 */
data class LetterSlotState(
    val letterSlot: LetterSlot,
    val placedLetter: Char? = null,  // null if empty
    val isCorrect: Boolean? = null  // null=not placed, true=correct, false=wrong
)


/**
 * Generates positions for letter slots in a centered grid layout.
 *
 * Algorithm:
 * - Arranges letter slots in rows (calculated based on screen width)
 * - Centers the grid horizontally and vertically
 * - Provides consistent spacing
 *
 * @param word The target word to spell
 * @param screenWidth Available screen width in Dp
 * @param screenHeight Available screen height in Dp
 * @param isPortrait Screen orientation flag
 * @return List of letter slots for each letter
 */
fun generateLetterSlots(
    word: String,
    screenWidth: Dp,
    screenHeight: Dp,
    isPortrait: Boolean
): List<LetterSlot> {
    val normalizedWord = word.uppercase()
    val letterCount = normalizedWord.length

    if (letterCount == 0) return emptyList()

    // Layout configuration
    val sidePadding = 32.dp
    val cardSpacing = 8.dp
    val verticalSpacing = 12.dp

    // Calculate max items that can fit per row
    val availableWidth = screenWidth - (sidePadding * 2)
    val maxItemsPerRow = ((availableWidth + cardSpacing) / (LETTER_CARD_SIZE + cardSpacing))
        .toInt()
        .coerceAtLeast(1)

    // Calculate grid dimensions
    val itemsPerRow = minOf(maxItemsPerRow, letterCount)
    val rowCount = (letterCount + itemsPerRow - 1) / itemsPerRow

    // Calculate total grid size
    val gridWidth = (itemsPerRow * (LETTER_CARD_SIZE + cardSpacing).value - cardSpacing.value).dp
    val gridHeight = (rowCount * (LETTER_CARD_SIZE + verticalSpacing).value - verticalSpacing.value).dp

    // Calculate available space in center section
    val availableHeight = screenHeight - FILL_BLANKS_TOP_SECTION_HEIGHT - FILL_BLANKS_BOTTOM_SECTION_HEIGHT

    // Center the grid
    val startX = (screenWidth - gridWidth) / 2
    val startY = FILL_BLANKS_TOP_SECTION_HEIGHT + (availableHeight - gridHeight) / 2

    val points = mutableListOf<LetterSlot>()

    for (i in 0 until letterCount) {
        val row = i / itemsPerRow
        val col = i % itemsPerRow

        val x = startX + (col * (LETTER_CARD_SIZE + cardSpacing).value).dp
        val y = startY + (row * (LETTER_CARD_SIZE + verticalSpacing).value).dp

        points.add(
            LetterSlot(
                x = x,
                y = y,
                letter = normalizedWord[i],
                index = i
            )
        )
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
    val maxCols = ((bottomAreaWidth - FILL_BLANKS_EDGE_MARGIN * 2) / (LETTER_CARD_SIZE + LETTER_CARD_SPACING)).toInt().coerceAtLeast(1)
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
 * Finds the nearest letter slot to a given position (used for drag-drop).
 *
 * @param positionPx The current drag position in pixels
 * @param letterSlotStates List of letter slot states
 * @param density Density for converting Dp to pixels
 * @param thresholdDp Maximum distance to consider (in dp)
 * @return Index of nearest letter slot or null if none within threshold
 */
fun findNearestLetterSlot(
    positionPx: Offset,
    letterSlotStates: List<LetterSlotState>,
    density: Float,
    thresholdDp: Dp = LETTER_CARD_SIZE * 1.5f
): Int? {
    var nearestIndex: Int? = null
    var nearestDistance = Float.MAX_VALUE
    val thresholdPx = thresholdDp.value * density

    Log.d("FillBlanks", "=== findNearestLetterSlot ===")
    Log.d("FillBlanks", "Drag position (px): $positionPx")
    Log.d("FillBlanks", "Density: $density")
    Log.d("FillBlanks", "Threshold (px): $thresholdPx")
    Log.d("FillBlanks", "Total slots: ${letterSlotStates.size}")

    letterSlotStates.forEachIndexed { index, state ->
        // Skip already filled slots
        if (state.placedLetter != null) {
            Log.d("FillBlanks", "Slot $index: FILLED (${state.placedLetter})")
            return@forEachIndexed
        }

        // Convert slot position from Dp to pixels and add half card size to get center
        val halfCardSizePx = (LETTER_CARD_SIZE.value * density) / 2
        val slotCenterPx = Offset(
            x = state.letterSlot.x.value * density + halfCardSizePx,
            y = state.letterSlot.y.value * density + halfCardSizePx
        )
        val distance = (positionPx - slotCenterPx).getDistance()

        Log.d("FillBlanks", "Slot $index: center=${slotCenterPx}, distance=$distance")

        if (distance < nearestDistance && distance < thresholdPx) {
            nearestDistance = distance
            nearestIndex = index
        }
    }

    Log.d("FillBlanks", "Nearest slot: $nearestIndex (distance: $nearestDistance)")
    return nearestIndex
}
