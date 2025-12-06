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
val GRID_CELL_INSET = 30.dp
const val CARD_ROTATION_RANGE = 10f

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
 * Calculates the number of x and y grid slots to put cards in on screen.
 */
fun calculatePlayground(
    screenWidth: Dp,
    screenHeight: Dp,
): PlaygroundDimensions {
    // This logic is derived from `calculateCardPositions`.
    val cellWidth = CARD_WIDTH.value + (2 * GRID_CELL_INSET.value)   // 140 + 32 = 172f
    val cellHeight = CARD_HEIGHT.value + (2 * GRID_CELL_INSET.value) // 80 + 32 = 112f

    // Available area calculation also comes from `calculateCardPositions`. It subtracts the edge margins.
    val availableWidth = screenWidth - (2 * GRID_EDGE_OFFSET.value).dp
    val availableHeight = screenHeight - (2 * GRID_EDGE_OFFSET.value).dp

    // Calculate how many cells can fit into the available space.
    val cols = (availableWidth.value / cellWidth).toInt().coerceAtLeast(1)
    val rows = (availableHeight.value / cellHeight).toInt().coerceAtLeast(1)

    return PlaygroundDimensions(
        offsetX = GRID_EDGE_OFFSET + ((availableWidth.value - cols * cellWidth) / 2f).dp,
        offsetY = GRID_EDGE_OFFSET + ((availableHeight.value - rows * cellHeight) / 2f).dp,
        cols = cols,
        rows = rows,
        cellWidth = cellWidth.dp,
        cellHeight = cellHeight.dp,
    )
}

/**
 * Generates the complete sequence of cards that will appear during the practice session.
 *
 * Simple algorithm: Shuffle pairs randomly, then create a flat list with both cards from each pair together.
 * This guarantees that any window of maxCardsOnScreen (typically 16) contains 7-8 complete pairs,
 * far exceeding the minimum requirement of 3 matching pairs.
 *
 * Randomness comes from:
 * - Random pair ordering
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
    var cardId = 0
    val maxXOffset = playgroundDimensions.cellWidth - CARD_WIDTH
    val maxYOffset = playgroundDimensions.cellHeight - CARD_HEIGHT

    return shuffledPairs.flatMap { pair ->
        listOf(
            ConnectCard(
                cardId = cardId++, wordPair = pair, showWord1 = true,
                offsetX = Random.nextInt(maxXOffset.value.toInt()).dp,
                offsetY = Random.nextInt(maxYOffset.value.toInt()).dp,
                rotation = (Random.nextFloat() - 0.5f) * CARD_ROTATION_RANGE * 2,
            ),
            ConnectCard(
                cardId = cardId++, wordPair = pair, showWord1 = false,
                offsetX = Random.nextInt(maxXOffset.value.toInt()).dp,
                offsetY = Random.nextInt(maxYOffset.value.toInt()).dp,
                rotation = (Random.nextFloat() - 0.5f) * CARD_ROTATION_RANGE * 2,
            )
        )
    }.toMutableList()
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
