package com.github.mwiest.voclet.ui.practice

import androidx.compose.ui.unit.dp
import com.github.mwiest.voclet.data.database.WordPair
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for the simplified Connect practice card sequencing algorithm.
 */
class ConnectSequenceTest {

    /**
     * Helper function to create a PlaygroundDimensions for testing.
     * The specific dimensions don't matter for algorithm tests, only the card sequencing logic.
     */
    private fun createTestPlaygroundDimensions(): PlaygroundDimensions {
        return PlaygroundDimensions(
            offsetX = 16.dp,
            offsetY = 16.dp,
            cols = 4,
            rows = 4,
            cellWidth = 172.dp,
            cellHeight = 112.dp
        )
    }

    @Test
    fun `any window of MAX_CARDS_ON_SCREEN cards contains at least MIN_MATCHING_PAIRS matching pairs`() {
        val pairs = createTestPairs(20)  // Increased to ensure enough pairs for testing
        val sequence = generateShuffledCardStack(pairs, createTestPlaygroundDimensions())

        // Test every possible MAX_CARDS_ON_SCREEN-card window
        val windowSize = MAX_CARDS_ON_SCREEN
        for (start in 0..(sequence.size - windowSize)) {
            val window = sequence.subList(start, start + windowSize)
            val matchingPairs = countMatchingPairs(window)

            assertTrue(
                "Window [$start, ${start + windowSize}) has only $matchingPairs matching pairs, expected ≥ 5",
                matchingPairs >= 5
            )
        }
    }

    @Test
    fun `any window of 12 cards contains at least required matching pairs`() {
        val pairs = createTestPairs(20)
        val sequence = generateShuffledCardStack(pairs, createTestPlaygroundDimensions())

        val windowSize = 12
        val minPairsFor12Cards =
            (windowSize / 2) * 2 / 3  // 4 pairs for 12 cards (using same formula as MIN_MATCHING_PAIRS)
        for (start in 0..(sequence.size - windowSize)) {
            val window = sequence.subList(start, start + windowSize)
            val matchingPairs = countMatchingPairs(window)

            assertTrue(
                "Window [$start, ${start + windowSize}) has only $matchingPairs matching pairs, expected ≥$minPairsFor12Cards",
                matchingPairs >= minPairsFor12Cards
            )
        }
    }

    @Test
    fun `all pairs appear exactly once in sequence`() {
        val pairs = createTestPairs(15)
        val sequence = generateShuffledCardStack(pairs, createTestPlaygroundDimensions())

        // Count occurrences of each pair
        val pairCounts = sequence.groupBy { it.wordPair.id }.mapValues { it.value.size }

        pairs.forEach { pair ->
            assertEquals(
                "Pair ${pair.id} should appear exactly twice (word1 and word2)",
                2,
                pairCounts[pair.id]
            )
        }

        assertEquals("Total pairs count", pairs.size, pairCounts.size)
    }

    @Test
    fun `sequence has correct total length`() {
        val pairs = createTestPairs(10)
        val sequence = generateShuffledCardStack(pairs, createTestPlaygroundDimensions())

        assertEquals("Sequence length should be 2× number of pairs", 20, sequence.size)
    }

    @Test
    fun `each pair has both word1 and word2 cards`() {
        val pairs = createTestPairs(5)
        val sequence = generateShuffledCardStack(pairs, createTestPlaygroundDimensions())

        pairs.forEach { pair ->
            val cardsForPair = sequence.filter { it.wordPair.id == pair.id }

            assertEquals("Pair ${pair.id} should have 2 cards", 2, cardsForPair.size)
            assertTrue(
                "Pair ${pair.id} should have word1 card",
                cardsForPair.any { it.showWord1 }
            )
            assertTrue(
                "Pair ${pair.id} should have word2 card",
                cardsForPair.any { !it.showWord1 }
            )
        }
    }

    @Test
    fun `edge case - exactly 3 pairs`() {
        val pairs = createTestPairs(3)
        val sequence = generateShuffledCardStack(pairs, createTestPlaygroundDimensions())

        assertEquals(6, sequence.size)

        // The entire sequence is one window - should have all 3 pairs
        val matchingPairs = countMatchingPairs(sequence)
        assertEquals(3, matchingPairs)
    }

    @Test
    fun `edge case - small list with 2 pairs`() {
        val pairs = createTestPairs(2)
        val sequence = generateShuffledCardStack(pairs, createTestPlaygroundDimensions())

        assertEquals(4, sequence.size)

        val matchingPairs = countMatchingPairs(sequence)
        assertEquals(2, matchingPairs)
    }

    @Test
    fun `edge case - single pair`() {
        val pairs = createTestPairs(1)
        val sequence = generateShuffledCardStack(pairs, createTestPlaygroundDimensions())

        assertEquals(2, sequence.size)

        val matchingPairs = countMatchingPairs(sequence)
        assertEquals(1, matchingPairs)
    }

    @Test
    fun `large list - 50 pairs`() {
        val pairs = createTestPairs(50)
        val sequence = generateShuffledCardStack(pairs, createTestPlaygroundDimensions())

        assertEquals(100, sequence.size)

        // Verify invariant holds for large lists
        val windowSize = MAX_CARDS_ON_SCREEN
        for (start in 0..(sequence.size - windowSize).coerceAtLeast(0)) {
            val window = sequence.subList(start, start + windowSize)
            val matchingPairs = countMatchingPairs(window)

            assertTrue(
                "Large list: Window [$start, ${start + windowSize}) has only $matchingPairs pairs, expected ≥$ 5",
                matchingPairs >= 5
            )
        }
    }

    // Helper functions

    private fun createTestPairs(count: Int): List<WordPair> {
        return (1..count).map { i ->
            WordPair(
                id = i.toLong(),
                wordListId = 1L,
                word1 = "word${i}a",
                word2 = "word${i}b"
            )
        }
    }

    private fun countMatchingPairs(cards: List<ConnectCard>): Int {
        return cards.groupBy { it.wordPair.id }
            .count { (_, slots) ->
                // A matching pair has both word1 and word2 present
                slots.any { it.showWord1 } && slots.any { !it.showWord1 }
            }
    }
}
