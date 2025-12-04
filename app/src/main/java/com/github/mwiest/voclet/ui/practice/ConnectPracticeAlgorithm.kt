package com.github.mwiest.voclet.ui.practice

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.github.mwiest.voclet.data.database.WordPair
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * Represents a single card instance in the Connect practice mode.
 * Each WordPair generates two CardSlots (one for word1, one for word2).
 */
data class CardSlot(
    val wordPair: WordPair,
    val showWord1: Boolean,  // true = show word1, false = show word2
    val slotId: Int  // unique identifier for this card instance
)

/**
 * Represents the position and size of a card on screen.
 */
data class CardPosition(
    val offsetX: Dp,
    val offsetY: Dp,
    val width: Dp = 140.dp,
    val height: Dp = 80.dp
) {
    /**
     * Checks if this card overlaps with another card, including minimum spacing.
     */
    fun overlaps(other: CardPosition, minSpacing: Dp): Boolean {
        val thisRight = offsetX + width
        val thisBottom = offsetY + height
        val otherRight = other.offsetX + other.width
        val otherBottom = other.offsetY + other.height

        return !(thisRight + minSpacing < other.offsetX ||
                otherRight + minSpacing < offsetX ||
                thisBottom + minSpacing < other.offsetY ||
                otherBottom + minSpacing < offsetY)
    }
}

/**
 * Calculates the maximum number of cards that can fit on screen.
 * Returns an even number between 6 (min 3 pairs) and 16 (max 8 pairs).
 */
fun calculateMaxCardsOnScreen(
    screenWidthDp: Float,
    screenHeightDp: Float
): Int {
    // Card dimensions
    val cardWidth = 140f
    val cardHeight = 80f
    val edgeMargin = 16f
    val cardSpacing = 12f

    // Available area (reserve 100dp for top bar)
    val availableWidth = screenWidthDp - (2 * edgeMargin)
    val availableHeight = screenHeightDp - (2 * edgeMargin) - 100f

    // Estimate card area including spacing
    val cardAreaWithSpacing = (cardWidth + cardSpacing) * (cardHeight + cardSpacing)
    val screenArea = availableWidth * availableHeight

    // Calculate max cards
    val estimatedCards = (screenArea / cardAreaWithSpacing).toInt()

    // Ensure even number (pairs) and apply bounds (min 3 pairs, max 8 pairs)
    val boundedCards = estimatedCards.coerceIn(6, 16)
    return if (boundedCards % 2 == 0) boundedCards else boundedCards - 1
}

/**
 * Generates the complete sequence of cards that will appear during the practice session.
 * Ensures that at least 3 matching pairs are visible on screen at all times (or all pairs if <3 total).
 *
 * Algorithm:
 * 1. Initial screen shows floor(maxCards/2) complete pairs + spare cards
 * 2. Remaining cards are queued to maintain the 3-pair minimum invariant
 * 3. When a pair is matched, 2 new cards appear: one completes a spare, one is a new spare
 */
fun generateCardSequence(
    wordPairs: List<WordPair>,
    maxCardsOnScreen: Int
): List<CardSlot> {
    if (wordPairs.isEmpty()) {
        return emptyList()
    }

    // Edge case: fewer than 3 pairs total - show all pairs without enforcing constraint
    if (wordPairs.size < 3) {
        return generateSimpleSequence(wordPairs)
    }

    val minPairsOnScreen = 3
    val maxPairsOnScreen = maxCardsOnScreen / 2
    val requiredPairsOnScreen = minPairsOnScreen.coerceAtMost(maxPairsOnScreen)
    val spareCardSlots = maxCardsOnScreen - (requiredPairsOnScreen * 2)

    val shuffledPairs = wordPairs.shuffled()
    val result = mutableListOf<CardSlot>()
    var slotIdCounter = 0

    // Phase 1: Initial screen setup
    val initialCompletePairs = shuffledPairs.take(requiredPairsOnScreen)
    val remainingPairs = shuffledPairs.drop(requiredPairsOnScreen).toMutableList()

    // Add complete pairs first
    initialCompletePairs.forEach { pair ->
        result.add(CardSlot(pair, showWord1 = true, slotId = slotIdCounter++))
        result.add(CardSlot(pair, showWord1 = false, slotId = slotIdCounter++))
    }

    // Track which pairs have a spare card showing
    val spareCards = mutableListOf<Pair<WordPair, Boolean>>()  // pair, showWord1

    // Add spare cards from different upcoming pairs
    var remainingIndex = 0
    repeat(spareCardSlots.coerceAtMost(remainingPairs.size)) {
        if (remainingIndex < remainingPairs.size) {
            val pair = remainingPairs[remainingIndex]
            val showWord1 = Random.nextBoolean()
            result.add(CardSlot(pair, showWord1, slotId = slotIdCounter++))
            spareCards.add(Pair(pair, showWord1))
            remainingIndex++
        }
    }

    // Phase 2: Generate replacement cards
    // Each time a pair is matched, 2 cards disappear and 2 new cards appear
    // Strategy: Complete one spare + add a new spare
    while (spareCards.isNotEmpty() && remainingIndex < remainingPairs.size) {
        // Complete the first spare card
        val (sparePair, spareShowWord1) = spareCards.removeAt(0)
        result.add(CardSlot(sparePair, showWord1 = !spareShowWord1, slotId = slotIdCounter++))

        // Add a new spare from the next pair
        if (remainingIndex < remainingPairs.size) {
            val nextPair = remainingPairs[remainingIndex]
            val showWord1 = Random.nextBoolean()
            result.add(CardSlot(nextPair, showWord1, slotId = slotIdCounter++))
            spareCards.add(Pair(nextPair, showWord1))
            remainingIndex++
        }
    }

    // Phase 3: Add any remaining pairs as complete pairs
    while (remainingIndex < remainingPairs.size) {
        val pair = remainingPairs[remainingIndex]
        result.add(CardSlot(pair, showWord1 = true, slotId = slotIdCounter++))
        result.add(CardSlot(pair, showWord1 = false, slotId = slotIdCounter++))
        remainingIndex++
    }

    // Complete any remaining spare cards
    spareCards.forEach { (pair, showWord1) ->
        result.add(CardSlot(pair, showWord1 = !showWord1, slotId = slotIdCounter++))
    }

    return result
}

/**
 * Simple sequence generation for cases with fewer than 3 pairs.
 * Just shows all cards in random order.
 */
private fun generateSimpleSequence(wordPairs: List<WordPair>): List<CardSlot> {
    val result = mutableListOf<CardSlot>()
    var slotId = 0

    wordPairs.forEach { pair ->
        result.add(CardSlot(pair, showWord1 = true, slotId = slotId++))
        result.add(CardSlot(pair, showWord1 = false, slotId = slotId++))
    }

    return result.shuffled()
}

/**
 * Calculates random non-overlapping positions for cards on screen.
 * Uses random placement with collision detection and falls back to grid layout if needed.
 */
fun calculateCardPositions(
    screenWidthDp: Float,
    screenHeightDp: Float,
    cardSlots: Set<Int>,  // slot IDs to position
    existingPositions: Map<Int, CardPosition> = emptyMap()  // already positioned cards
): Map<Int, CardPosition> {
    val cardWidth = 140.dp
    val cardHeight = 80.dp
    val edgeMargin = 16.dp
    val minCardSpacing = 12.dp

    val availableWidth = screenWidthDp.dp - (2 * edgeMargin.value).dp - cardWidth
    val availableHeight = screenHeightDp.dp - (2 * edgeMargin.value).dp - 100.dp - cardHeight

    val newPositions = mutableMapOf<Int, CardPosition>()
    val allPositions = existingPositions.toMutableMap()
    val maxAttempts = 100

    cardSlots.forEach { slotId ->
        var placed = false
        var attempt = 0

        while (!placed && attempt < maxAttempts) {
            // Generate random position within bounds
            val x = edgeMargin + (Random.nextFloat() * availableWidth.value).dp
            val y = edgeMargin + 100.dp + (Random.nextFloat() * availableHeight.value).dp

            val candidate = CardPosition(x, y, cardWidth, cardHeight)

            // Check for overlaps with existing cards
            val hasOverlap = allPositions.values.any { existing ->
                candidate.overlaps(existing, minCardSpacing)
            }

            if (!hasOverlap) {
                newPositions[slotId] = candidate
                allPositions[slotId] = candidate
                placed = true
            }

            attempt++
        }

        // Fallback: use grid-based placement if random fails
        if (!placed) {
            val gridPosition = fallbackGridPosition(
                allPositions.size,
                screenWidthDp,
                screenHeightDp,
                cardWidth,
                cardHeight,
                edgeMargin
            )
            newPositions[slotId] = gridPosition
            allPositions[slotId] = gridPosition
        }
    }

    return newPositions
}

/**
 * Fallback grid-based positioning when random placement fails.
 */
private fun fallbackGridPosition(
    index: Int,
    screenWidthDp: Float,
    screenHeightDp: Float,
    cardWidth: Dp,
    cardHeight: Dp,
    margin: Dp
): CardPosition {
    val cols = sqrt((screenWidthDp / cardWidth.value)).toInt().coerceAtLeast(2)
    val row = index / cols
    val col = index % cols

    val x = margin + (col * (cardWidth + margin).value).dp
    val y = margin + 100.dp + (row * (cardHeight + margin).value).dp
    
    return CardPosition(x, y, cardWidth, cardHeight)
}
