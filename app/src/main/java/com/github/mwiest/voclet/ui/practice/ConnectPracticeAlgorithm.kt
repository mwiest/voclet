package com.github.mwiest.voclet.ui.practice

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.github.mwiest.voclet.data.database.WordPair
import kotlin.random.Random

const val MAX_CARDS_ON_SCREEN = 14
val CARD_WIDTH = 140.dp
val CARD_HEIGHT = 80.dp
val GRID_EDGE_OFFSET = 16.dp
val GRID_CELL_INSET = 30.dp  // Keep as reference, but will calculate dynamically
const val CARD_ROTATION_RANGE = 10f

// New constants for flexible padding
val MIN_GRID_CELL_INSET = 8.dp   // Minimum padding around cards
val MAX_GRID_CELL_INSET = 60.dp  // Maximum padding around cards
const val MIN_PADDING_FOR_TWO_COLS = 10f  // Minimum dp padding to use 2 columns

/**
 * Represents a single card instance in the Connect practice mode.
 * Each WordPair generates two CardSlots (one for word1, one for word2).
 */
data class ConnectCard(
    val cardId: Int,  // unique identifier for this card instance
    val wordPair: WordPair, // The full pair
    val showWord1: Boolean,  // true = show word1, false = show word2
    val offsetX: Dp, // Horizontal offset inside the cell
    val offsetY: Dp, // Vertical offset inside the cell
    val rotation: Float = 0f  // Rotation in degrees
)

/**
 * Represents the calculated position and size of a card on screen, relative to the canvas.
 */
data class CardPosition(
    val offsetX: Dp,
    val offsetY: Dp,
    val width: Dp = CARD_WIDTH,
    val height: Dp = CARD_HEIGHT,
    val rotation: Float = 0f  // Rotation in degrees
)

/**
 * Represents the possible grid cells on the playground.
 */
data class PlaygroundDimensions(
    val offsetX: Dp,
    val offsetY: Dp,
    val cols: Int,
    val rows: Int,
    val cellWidth: Dp,
    val cellHeight: Dp
)

data class PlaygroundCoordinates(
    val row: Int,
    val col: Int,
)

data class Playground(
    val gridCells: Map<PlaygroundCoordinates, ConnectCard>,
)

/**
 * Calculates the optimal grid layout for the Connect practice mode.
 * Uses flexible padding to maximize cards on screen while maintaining at least 2 columns when possible.
 */
fun calculatePlayground(
    screenWidth: Dp,
    screenHeight: Dp,
): PlaygroundDimensions {
    val availableWidth = screenWidth - (2 * GRID_EDGE_OFFSET.value).dp
    val availableHeight = screenHeight - (2 * GRID_EDGE_OFFSET.value).dp

    // Calculate theoretical max columns/rows based on minimum viable cell sizes
    val minCellWidth = CARD_WIDTH + (2 * MIN_GRID_CELL_INSET.value).dp
    val minCellHeight = CARD_HEIGHT + (2 * MIN_GRID_CELL_INSET.value).dp

    val maxCols = (availableWidth / minCellWidth).toInt().coerceAtLeast(1)
    val maxRows = (availableHeight / minCellHeight).toInt().coerceAtLeast(1)

    // Generate and evaluate candidate configurations
    data class Candidate(
        val cols: Int,
        val rows: Int,
        val cellWidth: Dp,
        val cellHeight: Dp,
        val insetWidth: Dp,
        val insetHeight: Dp,
        val cardCount: Int
    )

    val candidates = mutableListOf<Candidate>()

    for (cols in 1..maxCols) {
        for (rows in 1..maxRows) {
            val totalCards = (cols * rows).coerceAtMost(MAX_CARDS_ON_SCREEN)

            // Only consider configurations that show at least 4 cards
            if (totalCards < 4) continue

            // Calculate cell dimensions to fit this grid
            val cellWidth = availableWidth / cols
            val cellHeight = availableHeight / rows

            // Calculate required insets
            val insetWidth = (cellWidth - CARD_WIDTH) / 2
            val insetHeight = (cellHeight - CARD_HEIGHT) / 2

            // Check if insets are within acceptable range
            if (insetWidth < MIN_GRID_CELL_INSET || insetWidth > MAX_GRID_CELL_INSET) continue
            if (insetHeight < MIN_GRID_CELL_INSET || insetHeight > MAX_GRID_CELL_INSET) continue

            // For 2+ columns, enforce minimum padding threshold (10dp)
            if (cols >= 2 && (insetWidth.value < MIN_PADDING_FOR_TWO_COLS || insetHeight.value < MIN_PADDING_FOR_TWO_COLS)) {
                continue
            }

            candidates.add(
                Candidate(
                    cols = cols,
                    rows = rows,
                    cellWidth = cellWidth,
                    cellHeight = cellHeight,
                    insetWidth = insetWidth,
                    insetHeight = insetHeight,
                    cardCount = totalCards
                )
            )
        }
    }

    // Simple heuristic: Select best candidate
    val best = candidates
        .sortedWith(
            compareByDescending<Candidate> { it.cols >= 2 }  // Prefer 2+ columns first
                .thenByDescending { it.cardCount }  // Then maximize cards
                .thenBy {  // Then prefer padding closest to 30dp
                    kotlin.math.abs(it.insetWidth.value - GRID_CELL_INSET.value) +
                    kotlin.math.abs(it.insetHeight.value - GRID_CELL_INSET.value)
                }
        )
        .firstOrNull()

    // Fallback if no valid configuration found (shouldn't happen in practice)
    val selected = best ?: run {
        val cols = if (availableWidth >= minCellWidth * 2) 2 else 1
        val rows = (availableHeight / minCellHeight).toInt().coerceAtLeast(2)
        val cellWidth = availableWidth / cols
        val cellHeight = availableHeight / rows

        Candidate(
            cols = cols,
            rows = rows,
            cellWidth = cellWidth,
            cellHeight = cellHeight,
            insetWidth = (cellWidth - CARD_WIDTH) / 2,
            insetHeight = (cellHeight - CARD_HEIGHT) / 2,
            cardCount = cols * rows
        )
    }

    // Center the grid in available space
    val totalGridWidth = selected.cellWidth * selected.cols
    val totalGridHeight = selected.cellHeight * selected.rows

    return PlaygroundDimensions(
        offsetX = GRID_EDGE_OFFSET + (availableWidth - totalGridWidth) / 2,
        offsetY = GRID_EDGE_OFFSET + (availableHeight - totalGridHeight) / 2,
        cols = selected.cols,
        rows = selected.rows,
        cellWidth = selected.cellWidth,
        cellHeight = selected.cellHeight
    )
}

/**
 * Generates the complete sequence of cards that will appear during the practice session.
 *
 * Controlled gap algorithm: Shuffle pairs randomly, then create cards with small random gaps
 * between word1 and word2 of each pair (gap ∈ {0, 1, 2, 3}).
 * This adds unpredictability (~75% of newly popped pairs won't match) while guaranteeing
 * that any window of MAX_CARDS_ON_SCREEN contains ≥MIN_MATCHING_PAIRS complete matching pairs.
 * (Currently: any 14-card window must contain ≥4 matching pairs)
 *
 * Randomness comes from:
 * - Random pair ordering
 * - Random gaps between word1 and word2 of each pair
 * - Random spatial positioning on screen (via calculateCardPositions)
 */
fun generateShuffledCardStack(
    wordPairs: List<WordPair>,
    playgroundDimensions: PlaygroundDimensions,
): MutableList<ConnectCard> {
    if (wordPairs.isEmpty()) {
        return mutableListOf()
    }

    val shuffledPairs = wordPairs.shuffled()
    val result = mutableListOf<ConnectCard>()
    val delayed = mutableListOf<Pair<ConnectCard, Int>>() // (card, countdown)
    var cardId = 0
    val minMatchingPairs =
        ((playgroundDimensions.rows * playgroundDimensions.cols).coerceAtMost(MAX_CARDS_ON_SCREEN) / 2) * 2 / 3  // Requires 2/3 of max possible pairs (4 pairs in 14 cards)

    val maxXOffset = playgroundDimensions.cellWidth - CARD_WIDTH
    val maxYOffset = playgroundDimensions.cellHeight - CARD_HEIGHT

    for (pair in shuffledPairs) {
        // Helper to create a card
        fun createCard(showWord1: Boolean) = ConnectCard(
            cardId = cardId++,
            wordPair = pair,
            showWord1 = showWord1,
            offsetX = Random.nextInt(maxXOffset.value.toInt()).dp,
            offsetY = Random.nextInt(maxYOffset.value.toInt()).dp,
            rotation = (Random.nextFloat() - 0.5f) * CARD_ROTATION_RANGE * 2
        )

        // Add word1 immediately
        result.add(createCard(showWord1 = true))

        // Decide gap for word2 (0, 1, 2, or 3 positions)
        val gap = Random.nextInt(0, minMatchingPairs)

        if (gap == 0) {
            // Add word2 immediately after word1
            result.add(createCard(showWord1 = false))
        } else {
            // Delay word2 by 'gap' positions
            delayed.add(createCard(showWord1 = false) to gap)
        }

        // Process delayed cards: decrement counters and add cards that are ready
        val ready = delayed.filter { it.second == 1 }
        ready.forEach { result.add(it.first) }
        delayed.removeAll(ready)

        // Decrement remaining countdowns
        for (i in delayed.indices) {
            delayed[i] = delayed[i].first to delayed[i].second - 1
        }
    }

    // Add remaining delayed cards (sorted by countdown for deterministic order)
    delayed.sortedBy { it.second }.forEach { result.add(it.first) }

    return result
}

fun initializePlayground(
    remainingCardStackShuffled: MutableList<ConnectCard>,
    remainingOpenCoordinatesShuffled: MutableList<PlaygroundCoordinates>,
    playgroundDimensions: PlaygroundDimensions,
): Playground {
    val maxCards = (playgroundDimensions.rows * playgroundDimensions.cols)
        .coerceAtMost(MAX_CARDS_ON_SCREEN)
    val gridCells = mutableMapOf<PlaygroundCoordinates, ConnectCard>()

    repeat(maxCards) {
        if (remainingCardStackShuffled.isNotEmpty()) {
            gridCells[remainingOpenCoordinatesShuffled.removeAt(0)] =
                remainingCardStackShuffled.removeAt(0)
        }
    }

    return Playground(gridCells.toMap())
}

fun calculateCardPosition(
    card: ConnectCard,
    coordinates: PlaygroundCoordinates,
    playgroundDimensions: PlaygroundDimensions
): CardPosition {
    return CardPosition(
        offsetX = playgroundDimensions.offsetX + (coordinates.col * playgroundDimensions.cellWidth.value).dp + card.offsetX,
        offsetY = playgroundDimensions.offsetY + (coordinates.row * playgroundDimensions.cellHeight.value).dp + card.offsetY,
        rotation = card.rotation
    )
}

fun findCardAtPosition(
    offsetDp: Offset,
    playgroundDimensions: PlaygroundDimensions,
    playground: Playground
): Int? {
    val xInDp = offsetDp.x.dp
    val yInDp = offsetDp.y.dp

    val hoveredCell = playground.gridCells.entries.find { (coordinates, card) ->
        val cardPosition = calculateCardPosition(card, coordinates, playgroundDimensions)
        val cellXStart = cardPosition.offsetX
        val cellYStart = cardPosition.offsetY
        val cellXEnd = cellXStart + cardPosition.width
        val cellYEnd = cellYStart + cardPosition.height

        xInDp in cellXStart..cellXEnd && yInDp in cellYStart..cellYEnd
    }

    return hoveredCell?.value?.cardId
}
